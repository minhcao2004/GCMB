# GCMB trên VPS ngày 13/11/2026

## Địa chỉ và bố trí

- Website: https://gcmb.103.78.1.178.sslip.io
- Máy chủ: `103.78.1.178`, Ubuntu 24.04 LTS, RAM 4 GB / ổ 30 GB.
- Thư mục triển khai: `/opt/gcmb`; Compose project `gcmb`.
- Source gốc tại commit `052d31c` cộng bộ cấu hình deploy và sửa lockfile chưa commit trong workspace của tác vụ. Không tạo commit hoặc push Git trong lần triển khai này.
- Service: `web` (Caddy/React), `backend` (Java 17, profile `prod`), `db` (PostgreSQL 18). Image được build từ source trên VPS; restart policy `unless-stopped`; Docker được bật cùng máy chủ.
- Chứng chỉ HTTPS công khai do Let's Encrypt cấp qua Caddy, được lưu trong volume và tự gia hạn. HTTP chuyển sang HTTPS. Hostname tạm dùng DNS công cộng sslip.io, không yêu cầu mua tên miền; phụ thuộc IP VPS/DNS này.
- Chỉ TCP 22, 80, 443 đang lắng nghe công khai. Backend 8080, database 5432 và cổng quản trị Caddy không publish ra host.

## Dữ liệu đã chuyển theo lựa chọn người dùng

Chụp database PostgreSQL local hiện tại bằng `pg_dump -Fc`, kiểm tra archive rồi chuyển qua SSH. Nguồn local được đọc, không reset hoặc thay đổi. PostgreSQL trên VPS được kiểm tra schema nghiệp vụ còn trống trước restore, sau đó restore trong một transaction, giữ lịch sử Flyway hiện có.

- 18 cơ sở, 108 phòng, 6 tài khoản, 58 migration thành công; số lượng được kiểm tra lại sau restore và sau restart backend.
- Đã đối chiếu hash mật khẩu của cả 6 tài khoản giữa local và VPS: giống nhau. Không in hoặc lưu các hash khi kiểm tra. Người dùng đăng nhập bằng tài khoản/mật khẩu GCMB local; mật khẩu SSH/root là thông tin khác.
- Không chuyển dữ liệu `gcmb.auth_token`, nên phiên đăng nhập và mã khôi phục cũ không dùng trên VPS. Cần đăng nhập mới. Không thay đổi phiên trong database nguồn.
- Hai file ảnh local (304.287 byte) được chuyển vào volume `gcmb_attachments`; thư mục thuộc UID/GID `10001` để backend ghi được.
- Cấu hình SMTP, SePay, Cloudinary đang dùng ở backend local được chuyển riêng qua SSH vào `.env` VPS, có đối chiếu 15 biến liên quan, không in bí mật. Mật khẩu database VPS được tạo mới, không dùng lại mật khẩu PostgreSQL local.
- `.env` trên VPS có quyền 600, thư mục `private` quyền 700. Source archive không bao gồm `.env`, khóa SSH, `backend/secrets`, `node_modules` hoặc `target`. File private trên máy cá nhân được Git bỏ qua và không nằm trong Docker build context.

Flyway trên database restored cần `spring.flyway.default-schema: public` trong `application-prod.yml`. Đây là sửa cấu hình tìm đúng `public.flyway_schema_history`, không phải `baseline`, `repair` hoặc thay checksum. Schema nghiệp vụ vẫn là `gcmb`.

## Kiểm tra đã đạt

- Build backend/frontend trực tiếp trên VPS; `db` và `backend` healthy; Caddy phục vụ web.
- Từ bên ngoài VPS: trang gốc, đăng nhập và đường dẫn sâu `/le-tan/bookings/new` trả React HTML 200 qua HTTPS; `/api/v1/auth/health` trả 200/UP.
- Xác thực TLS thành công bằng trust store bình thường, không bỏ qua kiểm tra chứng chỉ; HTTP tự chuyển sang HTTPS.
- CSRF cookie có `Secure`; gửi token/cookie qua proxy hoạt động, login với tài khoản không tồn tại trả 401; truy cập API quản lý chưa đăng nhập trả 401; POST không CSRF trả 403.
- Restart backend thành công, không lặp lỗi Flyway; web/API hoạt động lại và số dữ liệu nền giữ nguyên.
- Backup PostgreSQL sau deploy được tạo và `pg_restore --list` kiểm tra được. Dung lượng ổ còn khoảng 22 GB ở thời điểm kiểm tra.

Chưa kiểm tra đăng nhập thành công bằng mật khẩu người dùng, gửi email thật, giao dịch SePay thật, mọi nghiệp vụ trên giao diện hoặc tải 20–30 người đồng thời. Bộ unit test đã ghi trong [hướng dẫn chung](VPS%20Deployment.md) vẫn còn 16 ca lỗi từ trước; tác vụ này không sửa các lỗi nghiệp vụ/test đó.

### Kiểm tra tiếp Cloudinary và SePay từ VPS

- Đối chiếu lại 15 biến tích hợp với cấu hình backend local: khớp, không in bí mật.
- Cloudinary: authenticated ping thành công; upload ảnh PNG riêng tư 1 pixel, tải lại bằng cách ký URL SHA-256 giống GCMB và xóa ảnh thử đều thành công. Không thêm chứng từ hoặc ảnh vào dữ liệu nghiệp vụ GCMB. Kiểm tra này xác nhận quyền truy cập Cloudinary từ VPS, chưa thay thế test upload qua giao diện và kiểm tra phân quyền GCMB.
- SePay: tạo ảnh QR thử từ cấu hình ngân hàng hiện có thành công. Không tạo payment request trong GCMB và không chuyển tiền.
- Endpoint webhook HTTPS công khai: payload rỗng không có khóa trả 401; cùng payload với khóa cấu hình trả 400 do thiếu mã giao dịch. Xác nhận route và chứng thực hoạt động; payload bị từ chối trước thao tác ghi giao dịch.
- Chưa có quyền truy cập trang quản trị SePay để xác minh hoặc đổi URL webhook phía SePay. Cần cấu hình URL mới ở mục công việc bên dưới và test một giao dịch theo kế hoạch của nhóm trước khi xác nhận toàn bộ luồng thanh toán đã hoạt động.

## Quản lý và cập nhật

Đăng nhập SSH bằng phương thức đang có, rồi:

```bash
cd /opt/gcmb
docker compose ps
docker compose logs --tail=100 backend web
```

Khi cập nhật source, sao lưu trước, build tuần tự rồi recreate theo [hướng dẫn chung](VPS%20Deployment.md). Không dùng `down -v`, không đổi PostgreSQL major trên volume hiện có. Cần giữ cấu hình Flyway schema `public` cả khi build lại.

Backup khởi tạo ở `/opt/gcmb/private/gcmb-current.dump`; backup sau deploy ở `/opt/gcmb/private/post-deploy.dump`. File upload gốc có archive riêng trong thư mục này. Bản dump và khóa SSH cũng nằm trong thư mục local `backend/secrets/deploy/`, được Git bỏ qua. Không gửi hoặc commit các file đó. Chưa cài lịch backup tự động; cần sao lưu định kỳ theo hướng dẫn và lưu bản ngoài VPS.

Các tên volume: `gcmb_postgres_data`, `gcmb_attachments`, `gcmb_caddy_data`, `gcmb_caddy_config`. Không sửa trực tiếp dữ liệu PostgreSQL trong volume.

## Việc nhóm cần làm tiếp

- Mở website và đăng nhập bằng các tài khoản hiện có để test đầy đủ quyền/nghiệp vụ.
- Cập nhật endpoint webhook trong tài khoản SePay sang `https://gcmb.103.78.1.178.sslip.io/api/v1/payments/sepay/webhook`, giữ khóa xác thực đã cấu hình ở backend. Lần deploy này chưa thay cấu hình trên trang quản trị SePay.
- QR/liên kết từng in hoặc tạo bằng URL local cần tạo lại bằng URL web mới.
- Test email, cloud attachment và thanh toán theo kế hoạch của nhóm; đổi tên miền thì cập nhật lại webhook/QR.
- Dữ liệu trên VPS và local độc lập sau snapshot, không tự đồng bộ hai chiều.

## Ngoài tài liệu hiện có

Đã có bản deploy công khai với dữ liệu local và HTTPS tạm, bổ sung cấu hình Flyway schema `public` và biên bản vận hành này. Không đổi API, business rule hoặc migration. Cần cập nhật webhook SePay ở phía dịch vụ và kiểm thử các luồng thật trước khi bảo vệ.
