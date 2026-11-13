# Triển khai GCMB trên một VPS

## Phạm vi

Một VPS Ubuntu 24.04 LTS, tối thiểu cấu hình khởi đầu 2 vCPU / 4 GB RAM. Compose chạy ba service: `web` phục vụ React bằng Caddy và chuyển nguyên đường dẫn `/api` đến `backend`; `backend` chạy Java 17 / Spring Boot profile `prod`; `db` chạy PostgreSQL 18. Chỉ web được xuất cổng; backend và database không mở ra Internet. Caddy được dùng để tự cấp/gia hạn HTTPS khi có tên miền.

Bản triển khai thực tế và kết quả kiểm tra: [VPS Deployment 2026-11-13](VPS%20Deployment%202026-11-13.md).

Đây là cấu hình cho một bản demo/test của nhóm, chưa phải cam kết chịu được 30 người thực hiện mọi thao tác cùng lúc. Cần đo tải trên VPS thực tế. Giới hạn RAM container: backend 1536 MB (Java heap tối đa 768 MB), database 768 MB, web 256 MB; phần còn lại dành cho hệ điều hành và khoảng dự phòng. Build có thể dùng thêm RAM ngoài các giới hạn chạy này: build tuần tự và nên thực hiện khi nhóm không test.

## Chạy thử trên laptop Windows trước khi thuê VPS

Cài và mở Docker Desktop, dùng Linux containers/WSL 2, đợi `Engine running`. Tạo `.env` từ `deploy/vps.env.example`, đặt mật khẩu database riêng, giữ `WEB_BIND_IP=127.0.0.1`, `SITE_ADDRESS=:80`, `AUTH_SECURE_COOKIE=false` và đổi `FRONTEND_ORIGIN=http://localhost`. Chạy `docker compose config --quiet`, build tuần tự backend rồi web, sau đó `docker compose up -d` như bước 3.

Mở `http://localhost` trên chính laptop, không cần SSH tunnel. Cổng 80 và 443 phải còn trống. Dữ liệu nằm trong volume Docker riêng, không tự lấy database hoặc mật khẩu đã đổi của bản chạy local bằng Maven/npm. Không chạy `down -v` nếu cần giữ dữ liệu test Docker. Bản này chỉ bind loopback, chưa đưa website lên Internet; khi có VPS cần tạo `.env` riêng trên VPS theo các bước bên dưới.

Lần build đầu cần tải Java, Node, Caddy, PostgreSQL và các dependency qua Internet nên có thể mất nhiều phút. Không đóng Docker Desktop trong lúc build/chạy. Lần sau các lớp image không đổi có thể được dùng lại từ cache.

## 1. Chuẩn bị máy chủ

- Thuê theo tháng, chọn Ubuntu 24.04 LTS và có SSH. Chưa cần mua control panel.
- Cài Docker Engine và Compose plugin theo [hướng dẫn Ubuntu chính thức](https://docs.docker.com/engine/install/ubuntu/). Các lệnh dưới đây chạy bằng tài khoản có quyền Docker (hoặc thêm `sudo`).
- Đưa đúng bản source đã kiểm tra lên VPS bằng Git/SSH. Không chuyển `backend/.env`, `node_modules`, `target`, file upload và bản dump riêng bằng Git. Chạy các lệnh tiếp theo ở thư mục gốc repo.
- Ban đầu chỉ cho phép SSH ở firewall nhà cung cấp. Compose mặc định bind web vào loopback. Không mở 5432 hoặc 8080. Docker có thể bỏ qua quy tắc UFW đối với cổng publish; không dùng UFW làm lớp bảo vệ duy nhất.

## 2. Tạo cấu hình riêng trên VPS

```bash
cp deploy/vps.env.example .env
chmod 600 .env
nano .env
```

Đặt `DB_PASSWORD` bằng mật khẩu ngẫu nhiên riêng cho VPS. Giữ các giá trị bootstrap để chưa công khai tài khoản seed:

```dotenv
WEB_BIND_IP=127.0.0.1
SITE_ADDRESS=:80
FRONTEND_ORIGIN=http://localhost:8088
AUTH_SECURE_COOKIE=false
```

`.env` gốc được Compose đọc và truyền cho backend; file này đã được Git bỏ qua và bị loại khỏi Docker build context. Không dùng `.env.example` cũ ở gốc cho triển khai này. Không in `docker compose config` đầy đủ hoặc gửi `.env` vào chat vì có thể lộ bí mật.

`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` chỉ khởi tạo PostgreSQL khi volume còn trống. Đổi `.env` sau đó không tự đổi mật khẩu/role/database trong volume có sẵn.

## 3. Build và khởi tạo kín

```bash
docker compose config --quiet
docker compose build backend
docker compose build web
docker compose up -d
docker compose ps
docker compose logs --tail=100 backend
```

Backend tự chạy các migration Flyway trên database mới; không thay đổi database local của người phát triển. Không dùng `repair`, không sửa migration đã chạy, không chạy `down -v` để xử lý lỗi. Nếu build hết RAM, dừng build và chuyển sang build image trên máy khác rồi chuyển image lên; không tăng heap đến mức hết RAM máy chủ.

Profile `prod` chỉ định `spring.flyway.default-schema: public` để luôn dùng `public.flyway_schema_history`. Khi role database tên `gcmb` và schema `gcmb` đã tồn tại, PostgreSQL có thể ưu tiên schema cùng tên role trong `search_path`; bỏ cấu hình này có thể làm lần khởi động sau/restore tìm sai lịch sử migration. Schema nghiệp vụ và cấu hình JPA vẫn là `gcmb`; không di chuyển hoặc viết lại lịch sử migration.

Trên máy cá nhân mở kết nối SSH (thay `USER` và `VPS_IP`):

```bash
ssh -L 8088:127.0.0.1:80 USER@VPS_IP
```

Giữ SSH mở và truy cập `http://localhost:8088`. HTTP chỉ dùng trong tunnel SSH để khởi tạo. Kiểm tra health tại `http://localhost:8088/api/v1/auth/health` và tải lại trực tiếp một trang con của React.

### Tài khoản và dữ liệu ban đầu

Database mới lấy tài khoản/mật khẩu từ migration seed, không lấy các mật khẩu đã thay trên database local. Danh sách tài khoản nền và khác biệt local được ghi trong [Local Database Reset 2026-11-13](Local%20Database%20Reset%202026-11-13.md). Người phụ trách seed phải xác nhận thông tin đăng nhập riêng; không ghi mật khẩu vào tài liệu/Git.

Trong tunnel, đổi mật khẩu của **mọi tài khoản seed** trước khi mở web công khai. Đăng nhập lại kiểm tra đúng vai trò và cơ sở. Nếu chưa biết mật khẩu seed, giải quyết bước này với người phụ trách dữ liệu trước; không công khai backend để thử mật khẩu. Không import bản dump trước reset vào VPS mới một cách tự động. Việc chuyển dữ liệu local cần chọn rõ bản dump, bảo vệ file và có kế hoạch restore riêng.

## 4. Mở website HTTPS

Chuẩn bị tên miền/subdomain và trỏ bản ghi A về IPv4 VPS. Chỉ thêm AAAA khi IPv6 thật sự được cấu hình. Chỉnh `.env`, thay tên miền ví dụ:

```dotenv
WEB_BIND_IP=0.0.0.0
SITE_ADDRESS=gcmb.example.com
FRONTEND_ORIGIN=https://gcmb.example.com
AUTH_SECURE_COOKIE=true
```

`SITE_ADDRESS` là hostname; `FRONTEND_ORIGIN` có `https://` và không có dấu `/` cuối. Mở TCP 80/443 tại firewall nhà cung cấp, sau đó:

```bash
docker compose up -d
docker compose logs --tail=100 web
```

Caddy tự xin chứng chỉ nếu DNS và cổng đã đúng. Đăng nhập lại từ `https://gcmb.example.com`. Không dùng HTTP với cookie `Secure`; không để `AUTH_SECURE_COOKIE=false` trên web công khai. Nếu chưa có tên miền, giữ chế độ SSH tunnel ở bước 3 để nhóm khởi tạo trước; bộ cấu hình này không tự cấp chứng chỉ công khai cho IP.

Cho demo chưa mua tên miền, có thể dùng hostname nhúng IP từ [sslip.io](https://sslip.io/), ví dụ `gcmb.103.78.1.178.sslip.io`, rồi cấu hình Caddy như một hostname thông thường. Đây là DNS công cộng của bên thứ ba, website vẫn chạy trên VPS; địa chỉ phụ thuộc IP VPS và dịch vụ DNS này. Cần cổng 80/443 công khai để cấp chứng chỉ. Khi có tên miền riêng, đổi `SITE_ADDRESS`/`FRONTEND_ORIGIN`, recreate backend/web và cập nhật URL QR/webhook đang sử dụng.

## 5. Tích hợp để test đầy đủ

- SMTP: điền `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`; xác nhận VPS kết nối được SMTP. Thiếu SMTP vẫn đăng nhập được, nhưng không gửi mã quên mật khẩu.
- SePay: điền các biến `SEPAY_*`, đăng ký webhook `https://TEN_MIEN/api/v1/payments/sepay/webhook` và cùng khóa `Apikey`. Thiếu cấu hình thì không có luồng chuyển khoản QR đầy đủ. Chỉ test giao dịch theo kế hoạch của nhóm; không coi tiền test là giao dịch giả lập tự động.
- Ảnh thiết bị: lưu trong volume `attachments`, tiếp tục tồn tại khi recreate container.
- Chứng từ hoàn cọc/tài chính: code hiện hành sử dụng Cloudinary hoặc S3 cho tệp mới. Cấu hình tài khoản/bucket riêng qua các biến ví dụ nếu cần test các luồng đó; volume local không thay thế dịch vụ cloud này. Khi chuyển dữ liệu cũ, cần cả file hoặc cloud storage gốc tương ứng.
- `prod` giữ quy tắc chốt kỳ thông thường (`GCMB_ALLOW_EARLY_PERIOD_CLOSE=false`); nếu nhóm cần test chốt sớm trên dữ liệu demo, thêm biến này bằng `true` rồi tắt lại khi kết thúc.

Sau khi đổi `.env`, chạy `docker compose up -d`. Không chỉ `restart`: container phải được recreate để nhận biến mới.

Kiểm tra trước buổi bảo vệ: đăng nhập/đăng xuất và quyền của từng vai trò; tạo booking trùng lịch; nhận cọc/checkout; QR gọi món; upload/xem ảnh và chứng từ; báo cáo/xuất Excel; F5 tại trang con. Dùng dữ liệu riêng cho đồ án. Test tải với nhà cung cấp theo chính sách của họ, đo bằng `docker stats` và `df -h`, rồi mới quyết định nâng RAM.

## 6. Sao lưu và cập nhật

Database, ảnh local và chứng chỉ có named volume. `docker compose down` giữ volume; **`docker compose down -v` sẽ xóa dữ liệu**. Backup nhà cung cấp không thay thế bản sao lưu đã tải về máy riêng.

Ví dụ backup thủ công trên VPS Linux, dừng ghi để database và ảnh local nhất quán:

```bash
umask 077
backup_dir=".backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$backup_dir"
docker compose stop backend web
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup_dir/gcmb.dump"
docker compose cp backend:/app/data/attachments "$backup_dir/attachments"
docker compose exec -T db pg_restore --list < "$backup_dir/gcmb.dump" > "$backup_dir/archive-list.txt"
docker compose start backend web
```

Nếu lệnh backup lỗi, không coi file dump là hợp lệ; kiểm tra lỗi và khởi động lại các service đã dừng. Tải bản sao lưu về nơi riêng có bảo vệ. Cloudinary/S3 cần chính sách sao lưu riêng. Diễn tập restore sang database tách biệt trước khi tin cậy quy trình; không restore đè môi trường đang test. Các lệnh trên tạo backup thủ công, chưa có lịch backup tự động.

Trước mỗi cập nhật: ghi lại commit/image đang chạy và backup dữ liệu. Đưa source mới đã kiểm tra lên VPS rồi:

```bash
docker compose build backend
docker compose build web
docker compose up -d
docker compose ps
docker compose logs --tail=100 backend
```

Không pull source chưa commit từ máy cá nhân hoặc chỉ cập nhật frontend/backend một nửa. Nếu migration đã thay schema, quay lại image cũ chưa chắc tương thích; cần đánh giá migration hoặc phục hồi từ backup trong thời gian bảo trì. Không tự hạ major PostgreSQL trên volume hiện có. Theo dõi dung lượng SSD vì image build, database và ảnh đều dùng chung ổ.

## Kết quả kiểm tra khi chuẩn bị cấu hình (13/11/2026)

- Frontend `npm run build` với API tương đối `/api/v1`: thành công; còn cảnh báo hook có sẵn tại `IncomingRoomOrderDialog.js` về dependency `order`.
- Backend `mvnw -DskipTests clean package`: thành công. Build sạch loại bỏ migration cũ còn sót trong `target`; không thay migration nguồn.
- Chạy JAR bằng profile `prod` trên PostgreSQL 18 tạm, tách biệt database local: áp dụng thành công 58 migration; health trả 200; endpoint CSRF trả cookie `Secure`. Backend và PostgreSQL tạm đã được dừng sau kiểm tra.
- Kiểm tra YAML, cấu hình cổng nội bộ, thứ tự healthcheck, đường dẫn volume PostgreSQL 18, quoting lệnh `pg_isready`, route API và fallback React: đạt kiểm tra tĩnh.
- Bộ unit test hiện hành `mvnw -Dtest=*Test verify`: 179 ca, 11 failure và 5 error ở các lớp test AccountProfileService, AccountService, AuditService, AuthService. Chưa sửa các lỗi này trong tác vụ cấu hình deploy. Context test cần database riêng (`BackendApplicationTests`) chưa chạy. Build image đang bỏ qua thực thi test, vì vậy build thành công không có nghĩa bộ test đã đạt.
- Kiểm tra tiếp trên Docker Desktop/WSL 2: build thành công backend Java 17 và frontend Node 22/Caddy; `db` và `backend` healthy; Caddy xác nhận cấu hình hợp lệ; PostgreSQL Docker đã áp dụng đủ 58 migration. Backend ghi được volume ảnh bằng user không phải root.
- `npm ci` trong image phát hiện lockfile thiếu optional peer `yaml@2.9.1` của Tailwind. Đã đồng bộ `frontend/package-lock.json` bằng npm 10 trong Node 22, chỉ bổ sung một entry này (17 dòng); không thay dependency trực tiếp trong `package.json`. Sau đó clean install và frontend production build trong Docker thành công.
- HTTP qua Caddy tại `http://localhost`: `/`, `/dang-nhap`, `/le-tan/bookings/new` trả React HTML 200; `/api/v1/auth/health` trả 200; cookie/token CSRF dùng được khi gửi login không hợp lệ (401), API bảo vệ chặn anonymous (401), POST không có CSRF bị chặn (403). Chưa kiểm tra đăng nhập thành công bằng tài khoản seed hoặc toàn bộ luồng nghiệp vụ trên giao diện.
- Root `.env` local được tạo riêng, Git bỏ qua, dùng mật khẩu database Docker ngẫu nhiên và origin `http://localhost`; không chuyển dữ liệu/mật khẩu từ PostgreSQL local. Ba container được giữ chạy để người dùng truy cập; chỉ web publish vào loopback.
- Chưa kiểm tra cấp HTTPS công khai trên VPS, diễn tập backup/restore, tích hợp SMTP/SePay/cloud storage hoặc đo tải 20–30 người dùng. Các biến tích hợp trong `.env` Docker local vẫn để trống; cần cấu hình trước khi test đầy đủ các luồng đó.

## Ngoài tài liệu hiện có

- Thêm Dockerfile và `.dockerignore` cho từng ứng dụng, `compose.yaml`, `deploy/Caddyfile`, `deploy/vps.env.example` và profile `application-prod.yml`. README liên kết đến hướng dẫn này.
- Deployment có cùng origin cho React/API, hỗ trợ F5 các đường dẫn React, HTTPS theo tên miền, volume database/ảnh và giới hạn RAM/log cho VPS 4 GB. Không sửa API, nghiệp vụ hoặc migration.
- Đồng bộ `frontend/package-lock.json` để cài dependency từ đầu trong Docker; bổ sung hướng dẫn chạy thử trên Windows. Bản Docker local đã chạy tại `http://localhost`, dùng database riêng; chưa thay dữ liệu local hiện hữu.
- Đã triển khai VPS theo biên bản liên kết ở đầu tài liệu: HTTPS công khai, khôi phục dữ liệu local, xác thực mật khẩu bằng đối chiếu hash không in/lưu hash, kiểm tra CSRF và restart. `prod` cố định Flyway history ở schema `public`. Chưa đo tải đồng thời hoặc test toàn bộ nghiệp vụ/tích hợp.
