# Chạy GenZ Cinema & Music Box

## Chạy cả dự án bằng một lệnh

Trong Terminal của IntelliJ:

```powershell
cd "D:\FPT\do an\GCMB\frontend"
npm.cmd start
```

Lệnh này tự chạy backend Java bằng Maven Wrapper, đợi `/api/v1/auth/health` trả `UP`, rồi chạy frontend ở `http://localhost:3000`. Mở `http://localhost:3000/dang-nhap` để đăng nhập. Không cần bấm thêm BackendApplication trong IntelliJ. PostgreSQL vẫn phải chạy như dịch vụ database hiện có.

Nhấn Ctrl+C ở terminal này để dừng cả hai tiến trình. Nếu cổng 3000 hoặc 8080 đang được dùng, lệnh báo lỗi thay vì tự chuyển cổng hoặc tự kết nối vào một backend khác. Dừng phiên cũ rồi chạy lại.

Cần Node.js 20.12+, JDK 17+ và các dependency frontend (`npm.cmd ci` nếu chưa cài). Lần chạy đầu Maven có thể tải dependency. Lệnh `npm.cmd run start:frontend` chỉ chạy React khi chủ động chạy backend riêng trong IntelliJ.

## Cấu hình riêng trên từng máy

Runner đọc `backend/.env` (đã bị Git bỏ qua). Mẫu không chứa secret nằm ở `backend/.env.example`. Biến môi trường hệ thống được ưu tiên hơn file. Frontend không được nhận nội dung file cấu hình database/SMTP này.

Các biến database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Máy hiện tại đã có cấu hình database `gcmb` ở localhost:5432 với user `gcmb_dev`. `JAVA_HOME` trong file có thể chỉ đến JDK đã cài trên máy. Không còn yêu cầu nhập bộ biến AUTH_* cho chính sách xác thực.

Nếu chạy trực tiếp BackendApplication từ IntelliJ, cấu hình môi trường vẫn phải có `SPRING_PROFILES_ACTIVE=dev`, `DB_PASSWORD` và `AUTH_SECURE_COOKIE=false` cho HTTP localhost; Java không tự đọc `.env`. Cách đơn giản được hỗ trợ là `npm start`.

Frontend dùng URL tương đối `/api/v1`, dev server proxy tới backend `127.0.0.1:8080`. Tránh mở nhầm cổng 3001. Bản production cần reverse proxy `/api` tới backend và HTTPS; cookie Secure mặc định được giữ cho production.

## Luồng xác thực theo yêu cầu đã điều chỉnh

Có ba màn hình `/dang-nhap`, `/quen-mat-khau`, `/dat-lai-mat-khau`, dùng ảnh và logo trong assets, thông báo tiếng Việt và lỗi dưới trường. Đăng nhập không gọi hoặc chờ API `/policy`; API này đã bỏ.

Theo xác nhận mới của người dùng, đã bỏ chính sách mật khẩu 12–128 ký tự, giới hạn đăng nhập sai/thử mã/gửi mail, thời gian chờ gửi lại, hạn mã 10 phút và thời hạn phiên 8 giờ/30 ngày. Không còn khóa tạm tự động theo số lần thử. Trạng thái tài khoản LOCKED hoặc REVOKED trong database vẫn chặn truy cập.

Mật khẩu bắt buộc có nội dung, không được toàn khoảng trắng và phải khớp xác nhận; không tự trim. Email được trim, kiểm tra định dạng và giới hạn 254 ký tự; username tối đa 100 ký tự. Mã vẫn đúng 6 chữ số ASCII, sinh bằng SecureRandom, giữ số 0 đầu và gắn với email cùng challenge riêng.

Mã không tự hết hạn theo thời gian; dùng xong bị vô hiệu hóa, gửi lại thành công sẽ vô hiệu hóa mã trước. SMTP lỗi hoàn tác mã mới và giữ mã cũ. Mật khẩu mới dùng PBKDF2-HMAC-SHA256 600.000 vòng với salt ngẫu nhiên; chỉ hash được lưu vào database. Hash bcrypt hiện có cũng được hỗ trợ; plaintext không được chấp nhận.

Đổi mật khẩu, đánh dấu mã đã dùng và thu hồi tất cả phiên cùng một giao dịch. Có khóa hàng để chống hai yêu cầu đặt lại cùng thành công. Backend kiểm tra tài khoản và phiên trong database trên request được bảo vệ. Cookie thường là cookie phiên trình duyệt; chọn duy trì đăng nhập tạo cookie lưu lâu dài theo giới hạn của trình duyệt, đến khi đăng xuất, bị thu hồi hoặc đặt lại mật khẩu. Các token mới dùng `infinity` cho cột expires_at bắt buộc của schema; các phiên cũ vẫn tôn trọng expires_at đã lưu.

CSRF, CORS theo origin cấu hình, cookie HttpOnly, chống gửi lặp khi đang xử lý và kiểm tra quyền backend được giữ. Mật khẩu, mã và token không đưa vào URL hoặc log.

## Database, tài khoản và SMTP

Dùng các bảng hiện có `gcmb.user_account`, `gcmb.role`, `gcmb.auth_token`. Flyway áp dụng migration V2 tự động khi chạy backend. V2 vẫn giữ các cột/bảng giới hạn của phiên bản trước để bảo toàn checksum migration đã chạy trong database kiểm thử; bản hiện tại không sử dụng các giới hạn đó.

Đã chạy lệnh npm start mới và xác nhận migration V1/V2 thành công trên database thật `gcmb`. Database hiện chưa có tài khoản. Không tự chèn tài khoản/mật khẩu mặc định vào database người dùng. Cần tài khoản ACTIVE có username, email và password_hash hợp lệ để đăng nhập thật. Trường đăng nhập là username, không tự tìm theo email.

Các điểm vào theo vai trò: `/quan-ly-he-thong`, `/quan-ly-chi-nhanh`, `/le-tan`, `/ke-toan`. Các module nghiệp vụ chưa có trong repository nên trang hiện chỉ xác nhận người dùng, vai trò và cho đăng xuất.

Đăng nhập không yêu cầu SMTP. Khôi phục mật khẩu cần điền trong `backend/.env`: `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`; mặc định bật xác thực và STARTTLS. Khởi động lại npm sau khi đổi cấu hình. Chưa cấu hình hoặc gửi mail lỗi sẽ báo thất bại, không chuyển sang màn hình đặt lại.

## Cấu trúc package backend auth

Mã auth nằm trong `backend/src/main/java/com/gcmb/backend/administration/auth`:

```text
auth/
├── controller/      # Endpoint HTTP, cookie
├── service/         # Nghiệp vụ, giao dịch, gửi email
├── repository/      # Truy vấn SQL qua JdbcTemplate
├── entity/          # Dữ liệu tài khoản và mã khôi phục đọc từ database
├── dto/
│   ├── request/     # Dữ liệu đầu vào API
│   └── response/    # Dữ liệu trả về API
├── mapper/          # Chuyển ResultSet thành dữ liệu, ánh xạ vai trò
├── validator/       # Kiểm tra đầu vào
├── config/          # Cấu hình Spring Security và thuộc tính auth
├── security/        # Hash, sinh token, kiểm tra phiên
└── exception/       # Lỗi nghiệp vụ và phản hồi lỗi HTTP
```

`entity` dùng Java record cho dữ liệu JDBC, không phải JPA entity. `LoginResult` nằm trong `service` vì chứa token nội bộ; controller chỉ trả `Identity` và đặt token vào cookie HttpOnly. Transaction và khóa hàng được giữ khi chuyển SQL sang repository.

## Chạy kiểm thử

Frontend: `npm.cmd test -- --watchAll=false --runInBand` và `npm.cmd run build`.

Backend: `mvnw.cmd test`. Kiểm thử tích hợp chỉ chạy khi có `AUTH_TEST_DB_URL` trỏ tới database PostgreSQL riêng. Không dùng database chứa dữ liệu thật; bộ test thêm các tài khoản ngẫu nhiên và một test cố tình gây lỗi database để kiểm tra rollback.

Kết quả xác minh sau thay đổi: 14 test frontend và 17 test backend đạt; build frontend thành công. Lệnh npm start chạy được cả hai server; `/api/v1/auth/health` qua cổng 3000 trả 200, đăng nhập với tài khoản không tồn tại đi qua CSRF/proxy và trả đúng 401 tiếng Việt. Trình duyệt hiển thị nút Đăng nhập hoạt động, không còn gọi hoặc báo lỗi `/policy`.

Tài liệu kỹ thuật: [CRA API proxy](https://create-react-app.dev/docs/proxying-api-requests-in-development/), [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
