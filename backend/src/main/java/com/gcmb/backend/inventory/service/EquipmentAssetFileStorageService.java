/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: Kiểm tra loại file, dung lượng và lưu ảnh/chứng từ tài sản theo BR-115, BR-116.
 */
package com.gcmb.backend.inventory.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EquipmentAssetFileStorageService {
    private static final long MAX_BYTES = 20_000_000L;
    private final Path storageRoot;

    // BR-116: Tất cả chứng từ tài sản dùng cùng thư mục quản lý file đã cấu hình cho ứng dụng.
    public EquipmentAssetFileStorageService(@Value("${gcmb.attachments.storage-dir:./data/attachments}") String directory) {
        storageRoot = Path.of(directory).toAbsolutePath().normalize();
    }

    // BR-115/BR-116: Chỉ nhận file hợp lệ có nội dung nhận diện được và kích thước từ 1 đến 20 MB.
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Vui lòng chọn ảnh thiết bị");
        if (file.getSize() > MAX_BYTES) throw invalid("Ảnh thiết bị không được vượt quá 20 MB");
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException e) { throw invalid("Không đọc được ảnh thiết bị đã chọn"); }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid("Ảnh thiết bị phải lớn hơn 0 byte và không vượt quá 20 MB");
        FileType type = FileType.detect(bytes);
        if (type == null) throw invalid("Chỉ nhận JPEG, PNG, WebP, HEIC, HEIF hoặc PDF đúng định dạng");
        UUID id = UUID.randomUUID();
        String objectKey = id + "." + type.extension;
        try {
            Files.createDirectories(storageRoot);
            Files.write(resolve(objectKey), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được ảnh thiết bị", e); }
        String sourceName = file.getOriginalFilename() == null ? "anh-thiet-bi" : file.getOriginalFilename().replace('\\', '/');
        sourceName = sourceName.substring(sourceName.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (sourceName.isEmpty()) sourceName = "anh-thiet-bi";
        if (sourceName.length() > 200) sourceName = sourceName.substring(0, 200);
        return new StoredFile(id, objectKey, sourceName, type.mime, bytes.length, sha256(bytes), bytes);
    }

    // BR-115: Chỉ đọc file theo khóa lưu nội bộ, không nhận đường dẫn tùy ý từ request.
    public byte[] read(String objectKey) throws IOException { return Files.readAllBytes(resolve(objectKey)); }

    // BR-118: Dọn file mới tải lên nếu giao dịch ghi metadata hoặc liên kết tài sản thất bại.
    public void deleteQuietly(String objectKey) { try { Files.deleteIfExists(resolve(objectKey)); } catch (IOException ignored) { /* cleanup best effort */ } }

    // BR-115: Chặn object key thoát khỏi thư mục file đã cấu hình.
    private Path resolve(String key) {
        Path target = storageRoot.resolve(key).normalize();
        if (!target.startsWith(storageRoot)) throw new IllegalArgumentException("Invalid asset image key");
        return target;
    }

    // BR-115/BR-116: Trả lỗi dữ liệu nhập không hợp lệ về dạng HTTP 400.
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    // BR-115: Lưu checksum SHA-256 để metadata file có thể được kiểm tra/truy vết.
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }

    public record StoredFile(UUID id, String objectKey, String originalName, String mimeType,
                             long byteSize, String sha256, byte[] bytes) {}

    private record FileType(String extension, String mime) {
        // BR-115: Nhận dạng bằng chữ ký nhị phân thay vì tin tên/MIME do trình duyệt cung cấp.
        static FileType detect(byte[] b) {
            if (b.length >= 3 && u(b[0]) == 0xff && u(b[1]) == 0xd8 && u(b[2]) == 0xff) return new FileType("jpg", "image/jpeg");
            if (b.length >= 8 && u(b[0]) == 0x89 && u(b[1]) == 0x50 && u(b[2]) == 0x4e && u(b[3]) == 0x47
                    && u(b[4]) == 0x0d && u(b[5]) == 0x0a && u(b[6]) == 0x1a && u(b[7]) == 0x0a) return new FileType("png", "image/png");
            if (b.length >= 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP")) return new FileType("webp", "image/webp");
            if (b.length >= 12 && ascii(b, 4, 4).equals("ftyp")) {
                String brands = ascii(b, 8, Math.min(24, b.length - 8)).toLowerCase(Locale.ROOT);
                if (brands.contains("heic") || brands.contains("heix") || brands.contains("hevc") || brands.contains("hevx")) return new FileType("heic", "image/heic");
                if (brands.contains("mif1") || brands.contains("msf1")) return new FileType("heif", "image/heif");
            }
            if (b.length >= 5 && ascii(b, 0, 5).equals("%PDF-")) return new FileType("pdf", "application/pdf");
            return null;
        }
        // BR-115: So khớp byte chữ ký định dạng thực, không tin MIME hoặc phần mở rộng client gửi.
        private static int u(byte value) { return value & 0xff; }
        // BR-115: Đọc nhãn ASCII trong header để phân biệt định dạng container.
        private static String ascii(byte[] bytes, int offset, int length) { return new String(bytes, offset, length, StandardCharsets.US_ASCII); }
    }
}
