package com.gcmb.backend.booking.service;

import com.gcmb.backend.booking.exception.BookingFailure;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class RefundEvidenceStorageService {
    // BR-115, BR-116: chỉ lưu tệp minh chứng đúng định dạng, tối đa 20.000.000 byte.
    private static final long MAX_FILE_SIZE = 20_000_000L;
    private static final String CLOUD_PREFIX = "s3/";
    private static final Logger log = LoggerFactory.getLogger(RefundEvidenceStorageService.class);

    private final Path storageRoot;
    private final String bucket;
    private final String region;
    private final String endpoint;
    private final boolean pathStyle;
    private volatile S3Client client;
    private final CloudinaryStorageService cloudinary;

    public RefundEvidenceStorageService(
            @Value("${gcmb.attachments.storage-dir:./data/attachments}") String storageDirectory,
            @Value("${gcmb.attachments.s3.bucket:}") String bucket,
            @Value("${gcmb.attachments.s3.region:us-east-1}") String region,
            @Value("${gcmb.attachments.s3.endpoint:}") String endpoint,
            @Value("${gcmb.attachments.s3.path-style:false}") boolean pathStyle) {
        this(storageDirectory, bucket, region, endpoint, pathStyle, null);
    }

    @Autowired
    public RefundEvidenceStorageService(
            @Value("${gcmb.attachments.storage-dir:./data/attachments}") String storageDirectory,
            @Value("${gcmb.attachments.s3.bucket:}") String bucket,
            @Value("${gcmb.attachments.s3.region:us-east-1}") String region,
            @Value("${gcmb.attachments.s3.endpoint:}") String endpoint,
            @Value("${gcmb.attachments.s3.path-style:false}") boolean pathStyle,
            CloudinaryStorageService cloudinary) {
        storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
        this.bucket = bucket.trim();
        this.region = region.trim();
        this.endpoint = endpoint.trim();
        this.pathStyle = pathStyle;
        this.cloudinary = cloudinary;
    }

    public StoredEvidence store(MultipartFile file) {
        return store(file, "khac");
    }

    public StoredEvidence store(MultipartFile file, String area) {
        if (file == null || file.isEmpty()) {
            throw new BookingFailure(400, "image", "Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BookingFailure(400, "image", "Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BookingFailure(400, "image", "Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại.");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new BookingFailure(400, "image", "Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.");
        }
        EvidenceType type = EvidenceType.detect(bytes);
        if (type == null) {
            if (EvidenceType.hasSupportedDeclaration(file.getContentType(), file.getOriginalFilename())) {
                throw new BookingFailure(400, "image", "Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại.");
            }
            throw new BookingFailure(400, "image", "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.");
        }
        if (!type.matches(file.getContentType(), file.getOriginalFilename())) {
            throw new BookingFailure(400, "image", "Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại.");
        }

        UUID id = UUID.randomUUID();
        String objectKey = CLOUD_PREFIX + id + "." + type.extension();
        String originalName = originalName(file.getOriginalFilename());
        // HuyDV / BR-115/116: kiểm tra nội dung trước khi gửi lên Cloudinary; giữ cách đọc tệp S3/local cũ.
        if (cloudinary != null && cloudinary.enabled()) {
            String cloudKey = cloudinary.store(type.mimeType(), bytes, area);
            return new StoredEvidence(id, cloudKey, originalName, type.mimeType(), bytes.length, sha256(bytes));
        }
        try {
            s3().putObject(request -> request.bucket(bucket).key(objectKey).contentType(type.mimeType()),
                RequestBody.fromBytes(bytes));
        } catch (SdkException exception) {
            throw unavailable(exception);
        }
        return new StoredEvidence(id, objectKey, originalName, type.mimeType(), bytes.length, sha256(bytes));
    }

    public byte[] read(String objectKey) throws IOException {
        if (cloudinary != null && cloudinary.owns(objectKey)) return cloudinary.read(objectKey);
        if (!objectKey.startsWith(CLOUD_PREFIX)) return Files.readAllBytes(resolve(objectKey));
        try {
            return s3().getObjectAsBytes(request -> request.bucket(bucket).key(objectKey)).asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new IOException("Attachment not found", exception);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) throw new IOException("Attachment not found", exception);
            throw unavailable(exception);
        } catch (SdkException exception) {
            throw unavailable(exception);
        }
    }

    public void deleteQuietly(String objectKey) {
        try {
            if (cloudinary != null && cloudinary.owns(objectKey)) {
                cloudinary.delete(objectKey);
            } else if (objectKey.startsWith(CLOUD_PREFIX)) {
                s3().deleteObject(request -> request.bucket(bucket).key(objectKey));
            } else {
                Files.deleteIfExists(resolve(objectKey));
            }
        } catch (IOException | RuntimeException exception) {
            // Metadata may already have rolled back or committed; cleanup is best effort.
            log.warn("Could not remove attachment {}", objectKey, exception);
        }
    }

    private S3Client s3() {
        if (bucket.isEmpty() || region.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa cấu hình dịch vụ lưu trữ tệp. Vui lòng liên hệ quản trị viên.");
        }
        S3Client current = client;
        if (current != null) return current;
        synchronized (this) {
            if (client == null) {
                S3ClientBuilder builder = S3Client.builder().region(Region.of(region))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle)
                        .chunkedEncodingEnabled(false).build());
                if (!endpoint.isEmpty()) {
                    URI uri = URI.create(endpoint);
                    boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                        && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                            || "::1".equals(uri.getHost()));
                    if (!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp) {
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Cấu hình dịch vụ lưu trữ tệp không hợp lệ. Vui lòng liên hệ quản trị viên.");
                    }
                    builder.endpointOverride(uri);
                }
                client = builder.build();
            }
            return client;
        }
    }

    private ResponseStatusException unavailable(Exception exception) {
        log.error("Cloud attachment storage failed", exception);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Không thể kết nối dịch vụ lưu trữ tệp. Vui lòng thử lại sau.", exception);
    }

    @PreDestroy
    public void close() {
        if (client != null) client.close();
    }

    private Path resolve(String objectKey) {
        Path target = storageRoot.resolve(objectKey).normalize();
        if (!target.startsWith(storageRoot)) throw new IllegalArgumentException("Invalid attachment object key");
        return target;
    }

    private static String originalName(String source) {
        String normalized = source == null ? "" : source.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String name = normalized.substring(separator + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) name = "anh-chung-minh-hoan-coc";
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record StoredEvidence(UUID id, String objectKey, String originalName,
                                 String mimeType, long byteSize, String sha256) {}

    private record EvidenceType(String extension, String mimeType) {
        private static final EvidenceType JPEG = new EvidenceType("jpg", "image/jpeg");
        private static final EvidenceType PNG = new EvidenceType("png", "image/png");
        private static final EvidenceType WEBP = new EvidenceType("webp", "image/webp");
        private static final EvidenceType HEIC = new EvidenceType("heic", "image/heic");
        private static final EvidenceType HEIF = new EvidenceType("heif", "image/heif");
        private static final EvidenceType PDF = new EvidenceType("pdf", "application/pdf");

        static EvidenceType detect(byte[] bytes) {
            if (bytes.length >= 3 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8
                    && unsigned(bytes[2]) == 0xff) return JPEG;
            if (bytes.length >= 8 && unsigned(bytes[0]) == 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                    && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) return PNG;
            if (bytes.length >= 12 && ascii(bytes, 0, 4).equals("RIFF")
                    && ascii(bytes, 8, 4).equals("WEBP")) return WEBP;
            if (isPdf(bytes)) return PDF;
            EvidenceType heifType = heifType(bytes);
            if (heifType != null) return heifType;
            return null;
        }

        boolean matches(String contentType, String filename) {
            String declaredMime = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
            if (!declaredMime.isEmpty() && !declaredMime.equals("application/octet-stream")
                    && !declaredMime.equals(mimeType)) return false;

            String name = filename == null ? "" : filename;
            int dot = name.lastIndexOf('.');
            if (dot < 0 || dot == name.length() - 1) return true;
            return name.substring(dot + 1).equalsIgnoreCase(extension)
                    || (extension.equals("jpg") && name.substring(dot + 1).equalsIgnoreCase("jpeg"));
        }

        static boolean hasSupportedDeclaration(String contentType, String filename) {
            String declaredMime = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
            if (java.util.Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "application/pdf")
                    .contains(declaredMime)) return true;
            String name = filename == null ? "" : filename;
            int dot = name.lastIndexOf('.');
            if (dot < 0 || dot == name.length() - 1) return false;
            String declaredExtension = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
            return java.util.Set.of("jpg", "jpeg", "png", "webp", "heic", "heif", "pdf").contains(declaredExtension);
        }

        private static boolean isPdf(byte[] bytes) {
            if (bytes.length < 8 || !ascii(bytes, 0, 5).equals("%PDF-")) return false;
            int start = Math.max(0, bytes.length - 1024);
            return ascii(bytes, start, bytes.length - start).contains("%%EOF");
        }

        private static EvidenceType heifType(byte[] bytes) {
            if (bytes.length < 16 || !ascii(bytes, 4, 4).equals("ftyp")) return null;
            long boxSize = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes, 0, 4).getInt());
            if (boxSize < 16 || boxSize > bytes.length) return null;

            boolean heic = isHeicBrand(ascii(bytes, 8, 4));
            boolean heif = isHeifBrand(ascii(bytes, 8, 4));
            for (int offset = 16; offset + 4 <= boxSize; offset += 4) {
                String brand = ascii(bytes, offset, 4);
                heic |= isHeicBrand(brand);
                heif |= isHeifBrand(brand);
            }
            if (heic) return HEIC;
            return heif ? HEIF : null;
        }

        private static boolean isHeicBrand(String brand) {
            return switch (brand) {
                case "heic", "heix", "hevc", "hevx" -> true;
                default -> false;
            };
        }

        private static boolean isHeifBrand(String brand) {
            return switch (brand) {
                case "mif1", "msf1" -> true;
                default -> false;
            };
        }

        private static int unsigned(byte value) { return value & 0xff; }
        private static String ascii(byte[] bytes, int offset, int length) {
            return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
        }
    }
}
