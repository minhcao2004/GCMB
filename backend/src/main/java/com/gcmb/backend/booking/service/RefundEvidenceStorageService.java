package com.gcmb.backend.booking.service;

import com.gcmb.backend.booking.exception.BookingFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RefundEvidenceStorageService {
    // BR-115, BR-116: chỉ lưu tệp minh chứng đúng định dạng, tối đa 20.000.000 byte.
    private static final long MAX_FILE_SIZE = 20_000_000L;

    private final Path storageRoot;

    public RefundEvidenceStorageService(
            @Value("${gcmb.attachments.storage-dir:./data/attachments}") String storageDirectory) {
        storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public StoredEvidence store(MultipartFile file) {
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
        String objectKey = id + "." + type.extension();
        String originalName = originalName(file.getOriginalFilename());
        try {
            Files.createDirectories(storageRoot);
            Files.write(resolve(objectKey), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể lưu ảnh chứng minh hoàn cọc.", exception);
        }
        return new StoredEvidence(id, objectKey, originalName, type.mimeType(), bytes.length, sha256(bytes));
    }

    public byte[] read(String objectKey) throws IOException {
        return Files.readAllBytes(resolve(objectKey));
    }

    public void deleteQuietly(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException ignored) {
            // Metadata rolls back with the refund transaction; cleanup is best effort.
        }
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
