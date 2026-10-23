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
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final Path storageRoot;

    public RefundEvidenceStorageService(
            @Value("${gcmb.attachments.storage-dir:./data/attachments}") String storageDirectory) {
        storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public StoredEvidence store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BookingFailure(400, "image", "Vui lòng chọn ảnh chứng minh chuyển khoản");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BookingFailure(400, "image", "Ảnh chứng minh không được vượt quá 5 MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BookingFailure(400, "image", "Không đọc được ảnh chứng minh đã chọn");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new BookingFailure(400, "image", "Ảnh chứng minh không được vượt quá 5 MB");
        }
        ImageType type = ImageType.detect(bytes);
        if (type == null) {
            throw new BookingFailure(400, "image", "Chỉ chấp nhận ảnh JPG, PNG hoặc WebP hợp lệ");
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

    private record ImageType(String extension, String mimeType) {
        private static final ImageType JPEG = new ImageType("jpg", "image/jpeg");
        private static final ImageType PNG = new ImageType("png", "image/png");
        private static final ImageType WEBP = new ImageType("webp", "image/webp");

        static ImageType detect(byte[] bytes) {
            if (bytes.length >= 3 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8
                    && unsigned(bytes[2]) == 0xff) return JPEG;
            if (bytes.length >= 8 && unsigned(bytes[0]) == 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                    && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) return PNG;
            if (bytes.length >= 12 && ascii(bytes, 0, 4).equals("RIFF")
                    && ascii(bytes, 8, 4).equals("WEBP")) return WEBP;
            return null;
        }

        private static int unsigned(byte value) { return value & 0xff; }
        private static String ascii(byte[] bytes, int offset, int length) {
            return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
        }
    }
}
