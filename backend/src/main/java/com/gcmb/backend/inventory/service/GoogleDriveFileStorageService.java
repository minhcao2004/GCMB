package com.gcmb.backend.inventory.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Private Google Drive storage for incident repair documents. */
@Component
public class GoogleDriveFileStorageService {
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive";
    private static final String DRIVE_API = "https://www.googleapis.com/drive/v3/files";
    private static final String UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files";
    private final ObjectMapper json;
    private final String credentialsPath;
    private final String folderId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private volatile Token cachedToken;

    public GoogleDriveFileStorageService(ObjectMapper json,
            @Value("${gcmb.google-drive.credentials-file:}") String credentialsPath,
            @Value("${gcmb.google-drive.folder-id:}") String folderId) {
        this.json = json;
        this.credentialsPath = credentialsPath;
        this.folderId = folderId;
    }

    public boolean isConfigured() { return credentialsPath != null && !credentialsPath.isBlank() && folderId != null && !folderId.isBlank(); }

    public void requireConfigured() {
        if (!isConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa cấu hình Google Drive cho chứng từ sự cố. Cần GOOGLE_DRIVE_CREDENTIALS_FILE và GOOGLE_DRIVE_FOLDER_ID.");
        try {
            if (!Files.isRegularFile(Path.of(credentialsPath))) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Không tìm thấy file thông tin xác thực Google Drive tại GOOGLE_DRIVE_CREDENTIALS_FILE.");
        } catch (java.nio.file.InvalidPathException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Đường dẫn GOOGLE_DRIVE_CREDENTIALS_FILE không hợp lệ.");
        }
    }

    public String upload(String filename, String mimeType, byte[] content) {
        requireConfigured();
        try {
            Map<String,Object> metadata = new LinkedHashMap<>();
            metadata.put("name", filename);
            metadata.put("mimeType", mimeType);
            metadata.put("parents", List.of(folderId));
            byte[] metadataBytes = json.writeValueAsBytes(metadata);
            String token=accessToken();
            HttpRequest initialize = HttpRequest.newBuilder(URI.create(UPLOAD_API+"?uploadType=resumable&supportsAllDrives=true&fields=id,name"))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer "+token)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("X-Upload-Content-Type",mimeType).header("X-Upload-Content-Length",Integer.toString(content.length))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(metadataBytes)).build();
            HttpResponse<byte[]> initialized=http.send(initialize,HttpResponse.BodyHandlers.ofByteArray());
            if(initialized.statusCode()<200||initialized.statusCode()>=300) throw driveError("Không thể mở phiên tải ảnh lên Google Drive",initialized.statusCode());
            String session=initialized.headers().firstValue("Location").orElseThrow(()->new IOException("Google Drive không trả phiên tải file"));
            HttpRequest upload=HttpRequest.newBuilder(URI.create(session)).timeout(Duration.ofSeconds(90))
                    .header("Authorization","Bearer "+token).header("Content-Type",mimeType)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build();
            HttpResponse<byte[]> response=http.send(upload,HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw driveError("Không tải được ảnh lên Google Drive", response.statusCode());
            String id = json.readTree(response.body()).path("id").asText("");
            if (id.isBlank()) throw new IOException("Google Drive không trả về mã file");
            return id;
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Không thể lưu ảnh lên Google Drive. Kiểm tra quyền truy cập thư mục Drive.",e); }
    }

    public byte[] download(String fileId) throws IOException {
        requireConfigured();
        try {
            String safeId = URLEncoder.encode(fileId, StandardCharsets.UTF_8).replace("+", "%20");
            HttpRequest request = HttpRequest.newBuilder(URI.create(DRIVE_API+"/"+safeId+"?alt=media&supportsAllDrives=true"))
                    .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer "+accessToken()).GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IOException("Google Drive download failed with HTTP "+response.statusCode());
            return response.body();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Google Drive download interrupted",e); }
        catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Google Drive download failed",e); }
    }

    public void deleteQuietly(String fileId) {
        if (!isConfigured()) return;
        try {
            String safeId = URLEncoder.encode(fileId, StandardCharsets.UTF_8).replace("+", "%20");
            HttpRequest request = HttpRequest.newBuilder(URI.create(DRIVE_API+"/"+safeId+"?supportsAllDrives=true"))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer "+accessToken()).DELETE().build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { /* best-effort rollback cleanup */ }
    }

    private synchronized String accessToken() throws Exception {
        if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now().plusSeconds(60))) return cachedToken.value();
        JsonNode credentials = json.readTree(Files.readAllBytes(Path.of(credentialsPath)));
        String email = credentials.path("client_email").asText();
        String pem = credentials.path("private_key").asText();
        if (email.isBlank() || pem.isBlank()) throw new IOException("Invalid Google service-account credentials file");
        long now = Instant.now().getEpochSecond();
        String header = base64(json.writeValueAsBytes(Map.of("alg","RS256","typ","JWT")));
        String claims = base64(json.writeValueAsBytes(Map.of("iss",email,"scope",DRIVE_SCOPE,"aud",TOKEN_URI,"iat",now,"exp",now+3600)));
        String unsigned = header+"."+claims;
        String key = pem.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
        var privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key)));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey); signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        String assertion = unsigned+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
        String form = "grant_type="+URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer",StandardCharsets.UTF_8)
                +"&assertion="+URLEncoder.encode(assertion,StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URI)).timeout(Duration.ofSeconds(20))
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<byte[]> response = http.send(request,HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("Google OAuth token request failed with HTTP "+response.statusCode());
        JsonNode token = json.readTree(response.body());
        long expiresIn = token.path("expires_in").asLong(3600);
        cachedToken = new Token(token.path("access_token").asText(),Instant.now().plusSeconds(expiresIn));
        if (cachedToken.value().isBlank()) throw new IOException("Google OAuth returned no access token");
        return cachedToken.value();
    }

    private String base64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private IOException driveError(String message, int status) { return new IOException(message+" (HTTP "+status+")"); }
    private record Token(String value, Instant expiresAt) {}
}
