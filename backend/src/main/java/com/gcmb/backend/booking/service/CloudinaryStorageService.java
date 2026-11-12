/** Author: HuyDV. BR-115/116: lưu nguyên bản ảnh/PDF riêng tư; API GCMB tiếp tục kiểm tra quyền xem. */
package com.gcmb.backend.booking.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Component
public class CloudinaryStorageService {
    private static final String PREFIX = "cloudinary:";
    private final boolean enabled;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CloudinaryStorageService(
            @Value("${gcmb.cloudinary.enabled:false}") boolean enabled,
            @Value("${gcmb.cloudinary.cloud-name:}") String cloudName,
            @Value("${gcmb.cloudinary.api-key:}") String apiKey,
            @Value("${gcmb.cloudinary.api-secret:}") String apiSecret, ObjectMapper json) {
        this.enabled = enabled;
        this.cloudName = cloudName.trim();
        this.apiKey = apiKey.trim();
        this.apiSecret = apiSecret.trim();
        this.json = json;
    }

    public boolean enabled() { return enabled; }
    public boolean owns(String key) { return key != null && key.startsWith(PREFIX); }

    public String store(String mimeType, byte[] bytes) {
        return store(mimeType, bytes, "khac");
    }

    public String store(String mimeType, byte[] bytes, String area) {
        configured();
        if (!Set.of("tien-coc", "chot-quy", "cap-ket", "thu-chi", "kho-hang", "thuc-don",
            "thiet-bi", "su-co-thiet-bi", "khac").contains(area)) throw new IllegalArgumentException("Invalid attachment area");
        String extension = switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/heic" -> "heic";
            case "image/heif" -> "heif";
            case "application/pdf" -> "pdf";
            default -> throw new IllegalArgumentException("Unsupported attachment type");
        };
        if (bytes.length == 0 || bytes.length > 20_000_000) throw new IllegalArgumentException("Invalid attachment size");
        // HuyDV: ảnh và PDF dùng Image để Cloudinary tạo thumbnail; HEIC/HEIF giữ Raw để không đổi bản gốc.
        String resourceType = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf").contains(mimeType) ? "image" : "raw";
        String id = "gcmb/" + UUID.randomUUID() + (resourceType.equals("raw") ? "." + extension : "");
        var parameters = parameters();
        parameters.put("asset_folder", "gcmb/" + area);
        parameters.put("public_id", id);
        parameters.put("overwrite", "false");
        parameters.put("signature", signature(parameters));
        parameters.put("api_key", apiKey);
        String boundary = "gcmb-" + UUID.randomUUID();
        try {
            var body = new ByteArrayOutputStream();
            for (var parameter : parameters.entrySet()) {
                body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                    + parameter.getKey() + "\"\r\n\r\n" + parameter.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"attachment."
                + extension + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(bytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] response = send(HttpRequest.newBuilder(api(resourceType, "upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
            var result = json.readTree(response);
            if (!id.equals(result.path("public_id").asText())
                || !"authenticated".equals(result.path("type").asText())
                || !resourceType.equals(result.path("resource_type").asText())) throw new IOException("Invalid upload response");
            return PREFIX + resourceType + ":" + id;
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    public byte[] read(String key) throws IOException {
        configured();
        String id = publicId(key);
        String signed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest(id + apiSecret)).substring(0, 8);
        // HuyDV: URL có chữ ký chỉ dùng trên backend, không đưa URL/quyền truy cập ra trình duyệt.
        URI url = URI.create("https://res.cloudinary.com/" + cloudName + "/" + resourceType(key)
            + "/authenticated/s--" + signed + "--/" + id);
        return send(HttpRequest.newBuilder(url).GET());
    }

    public void delete(String key) throws IOException {
        configured();
        var parameters = parameters();
        parameters.put("public_id", publicId(key));
        parameters.put("invalidate", "true");
        parameters.put("signature", signature(parameters));
        parameters.put("api_key", apiKey);
        String body = parameters.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).reduce((a,b) -> a + "&" + b).orElse("");
        var result = json.readTree(send(HttpRequest.newBuilder(api(resourceType(key), "destroy"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))));
        if (!Set.of("ok", "not found").contains(result.path("result").asText())) throw new IOException("Delete failed");
    }

    private TreeMap<String,String> parameters() {
        var result = new TreeMap<String,String>();
        result.put("type", "authenticated");
        result.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
        return result;
    }

    private String signature(Map<String,String> parameters) {
        String payload = parameters.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
            .reduce((a,b) -> a + "&" + b).orElse("");
        return HexFormat.of().formatHex(digest(payload + apiSecret));
    }

    private static byte[] digest(String text) {
        try { return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private String publicId(String key) throws IOException {
        if (key == null || !(key.matches("cloudinary:raw:gcmb/[0-9a-f-]{36}\\.(jpg|png|webp|heic|heif|pdf)")
            || key.matches("cloudinary:image:gcmb/[0-9a-f-]{36}")))
            throw new IOException("Invalid attachment key");
        return key.split(":", 3)[2];
    }

    private String resourceType(String key) { return key.split(":", 3)[1]; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private URI api(String resourceType, String action) {
        return URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/" + resourceType + "/" + action);
    }

    private byte[] send(HttpRequest.Builder request) throws IOException {
        try {
            var response = client.send(request.timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) throw new IOException("Attachment not found");
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Attachment request interrupted");
        }
    }

    private void configured() {
        if (!cloudName.matches("[a-z0-9_-]+") || !apiKey.matches("[0-9]+") || apiSecret.isEmpty())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa cấu hình Cloudinary. Vui lòng liên hệ quản trị viên.");
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Không thể lưu hoặc tải tệp từ Cloudinary. Vui lòng thử lại hoặc kiểm tra cấu hình lưu trữ.");
    }
}
