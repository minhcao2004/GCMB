# GCMB — GenZ Cinema & Music Box Management System

 is a centralized web application for managing the GenZ Cinema & Music Box chain. It connects room bookings, F&B orders, inventory, equipment, staff, payroll, and financial operations across branches.

The project aims to reduce duplicate data entry and manual reconciliation caused by operational data being spread across KiotViet, spreadsheets, and manual records.

**FPT University Capstone Project · SEP490-G12**  
**Architecture:** Modular Monolith + Feature-based Structure  
**Stack:** React (JavaScript/JSX + plain CSS), Java 17, Spring Boot, PostgreSQL

> This README describes the agreed project structure and development setup. Feature descriptions represent the planned scope, not a completion checklist. The source repository was not supplied for verification; configuration examples below must match the actual `pom.xml`, `package.json`, and API implementation.

## Table of Contents

- [About the Project](#about-the-project)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Usage](#usage)
- [Testing](#testing)
- [Contributing](#contributing)
- [Documentation](#documentation)
- [Team](#team)
- [License](#license)

## About the Project

GCMB brings branch operations and financial records into one system. Its goals are to prevent conflicting room assignments, standardize operational workflows, maintain transaction history, and provide consistent branch and chain-level reporting.

Access is controlled by both business role and branch scope.

| User | Main responsibilities |
| --- | --- |
| Head Office Manager | Manage branches, shared configuration, access rights, major approvals, and chain reports. |
| Branch Manager | Manage branch rooms, stock, equipment, shifts, attendance, expenses, and daily cash closing. |
| Receptionist | Handle bookings, check-in/out, deposits, payments, F&B orders, and personal shift information. |
| Accountant | Process refunds, funds, expenses, reimbursements, reconciliation, payroll, and financial reports. |
| Customer | View the F&B menu and submit orders using an in-room QR code. |

Customer interaction is limited to in-room F&B ordering. Room reservations received through social channels are entered by staff.

## Features

| ID | Feature | Planned capabilities |
| --- | --- | --- |
| FE-01 | System Administration and Configuration | Accounts, role and branch access, password recovery, audit logs, branches, room categories, rooms, time-based pricing, and combo packages. |
| FE-02 | Booking and Room Operations | Walk-in and advance bookings, availability checks, cancellation, check-in/out, room changes, extensions, no-shows, deposits, refunds, billing, and payments. |
| FE-03 | F&B, Inventory and Equipment Management | Menu and room-linked orders, QR ordering, stock receipts and adjustments, equipment assets, incident reports, repairs, and equipment transfers. |
| FE-04 | Workforce and Payroll Management | Staff records and branch assignments, shifts, attendance, monthly attendance closing, payroll, salary advances, Excel export, and salary payment records. |
| FE-05 | Financial Management and Reporting | Cash closing and reconciliation, funds, expenses, approvals, reimbursements, financial-period closing, dashboards, reports, and tax-accounting data export. |

The current scope excludes a customer booking portal, hardware barcode-scanner integration, statutory tax report generation, recipe/BOM-based ingredient management, content licensing management, and regulatory or safety compliance enforcement.

## Tech Stack

| Area | Technology |
| --- | --- |
| Frontend | React with Create React App, JavaScript, JSX, plain CSS |
| Backend | Java 17, Spring Boot, Maven Wrapper |
| Database | PostgreSQL with version-controlled SQL migration scripts |
| Communication | REST API with JSON |
| External integrations | SePay, email service |
| Version control | Git and GitHub |
| Testing | JUnit and API testing tools such as Postman |
| Deployment target | Docker on VPS/Cloud |

Dependency versions are defined in `backend/pom.xml`, `frontend/package.json`, and the frontend lockfile. Use the versions committed by the team.

## Architecture

GCMB uses a Web Client–Server architecture. The React frontend calls the centralized Spring Boot REST API, and the backend accesses PostgreSQL and external services.


The backend is deployed as one application. Its ten peer feature packages are `auth`, `accounts`, `audit`, `branch`, `pricing`, `booking`, `inventory`, `workforce`, `dashboard`, and `finance`. Each feature keeps its own technical layers. The five feature areas above describe business scope, not a required Java package hierarchy. Frontend organization is maintained separately.


- Keep business rules, authorization, and branch-scope checks in backend services.
- Use DTOs for API requests and responses.
- Expose explicit service operations when modules need to collaborate.
- Keep business-specific code and supporting technical classes within their owning feature. The current backend does not introduce `shared/` or `infrastructure/` packages.

## Project Structure


The tree below reflects the current repository structure. Dashboard reporting, financial transaction adjustments, financial-period closing, cash closing, payroll, funds, expenses, approvals, and reimbursements are active capabilities.

```text
GCMB/
├── backend/
│   ├── src/main/java/com/gcmb/backend/
│   │   ├── auth/
│   │   ├── accounts/
│   │   ├── audit/
│   │   ├── branch/
│   │   ├── pricing/
│   │   ├── booking/
│   │   ├── dashboard/
│   │   ├── inventory/
│   │   ├── workforce/
│   │   ├── finance/
│   │   └── BackendApplication.java
│   ├── src/main/resources/
│   │   ├── application.yaml
│   │   ├── application-dev.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/
│   ├── src/test/
│   ├── .mvn/wrapper/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── features/
│   │   │   ├── auth/
│   │   │   ├── backoffice/
│   │   │   ├── booking/
│   │   │   ├── branch/
│   │   │   ├── dashboard/
│   │   │   ├── finance/
│   │   │   ├── inventory/
│   │   │   ├── workforce/
│   │   │   ├── landing/
│   │   │   └── pricing/
│   │   ├── shared/
│   │   │   ├── components/
│   │   │   └── utils/
│   │   ├── layouts/
│   │   ├── routes/
│   │   ├── api/
│   │   ├── assets/
│   │   │   ├── images/
│   │   │   ├── icons/
│   │   │   └── styles/
│   │   ├── App.js
│   │   ├── App.css
│   │   ├── index.js
│   │   └── index.css
│   ├── package.json
│   └── package-lock.json
└── README.md
```

| Module | Ownership |
| --- | --- |
| `auth` | Login/logout, sessions, password recovery/change, security configuration, recovery email, and head-office dashboard navigation restored from the URL. |
| `accounts` | User accounts, personal profiles, roles, and access management. |
| `audit` | Mutation audit recording and audit-history lookup. |
| `branch` | Branches, manager assignments, rooms, room operating status, and branch-manager dashboard navigation restored from the URL. The dashboard renders one selected feature at a time and keeps scrolling inside the content area without a visible vertical scrollbar. |
| `pricing` | Room types, room prices, pricing rules, and combo packages. |
| `booking` | Reservations, walk-in check-in, room sessions, deposits, checkout bills, customer payments, and tax invoice request tracking. |
| `inventory` | F&B catalog, guest QR menu and orders, reception order handling and service assignment, inventory items, stock receipts, adjustments, movement history, equipment assets, incidents, and transfers. |
| `workforce` | Staff records, branch assignments and transfers, shift schedules, attendance, and payroll. |
| `dashboard` | Head-office chain overview with period filters, KPIs, interactive Recharts, branch ranking, pending approvals and operational alerts; monthly performance summary on the head-office Business Reports tab; branch performance summaries with bill and forfeited-deposit revenue; receptionist room and booking status charts; accountant financial reports and financial-period operations; accountant Excel export with UC-123 performance summary and UC-125 recorded-ledger detail. |
| `finance` (frontend) | Accountant fund and expense workflows, cash closing, payroll, financial-period closing, transaction adjustments, and tax-accounting export screens. |
| `finance` (backend) | Chain-wide funds, expenses, approvals, reimbursements, cash reconciliation, financial transactions, and period closing. |

F&B charges are linked to an active booking and included in its checkout bill. Order processing and inventory updates belong to `inventory`; bill calculation belongs to `booking`. Accountant responsibilities span `booking`, `workforce`, and `finance`.

Within a backend feature, create `controller/`, `service/`, `repository/`, `entity/`, and `dto/` as needed. Within a frontend feature, use `pages/`, `components/`, `services/`, `hooks/`, and `styles/` as needed.

Backend package reorganization (24/10/2026): the former `backoffice/` directory and `com.gcmb.backend.administration.*` declarations are replaced by the six peer packages above. All existing classes are retained. `branch`, `pricing`, and `booking` keep their existing responsibilities; this change does not extract a `rooms` module or change API routes, database schema, or business behavior. Tests follow the same package hierarchy.

`auth` has six direct subpackages: `controller`, `dto`, `repository`, `service`, `security`, and `exception`. Request/response records and `LoginResult` live in `dto`; internal JDBC models `Account` and `Reset` live in `repository/model`; `AuthMapper` lives in `repository`; `AuthValidation` lives in `service`; `AuthSecurity` and `AuthSettings` live in `security`. Internal models and `LoginResult` are not automatically exposed as HTTP responses by their package location.

Frontend account management lives in `frontend/src/features/backoffice/accounts/`: `pages/` contains `AccountsPage` and `AuditLogPage`; `components/` contains account dialogs and the password form; `services/` contains API clients; `styles/` contains CSS. `CreateAccountDrawer` remains inside `pages/AccountsPage.js`. The parent dashboard remains at `frontend/src/features/auth/components/HeadOfficeDashboard.js`.

## Getting Started

### Prerequisites

- JDK 17; `JAVA_HOME` must point to the JDK.
- Node.js and npm compatible with the committed frontend dependencies.
- PostgreSQL running locally.
- Git.

Maven Wrapper is used, so a separate Maven installation is optional. Docker is optional for local development and is used for the planned container deployment.

### 1. Clone the repository

Replace `YOUR_REPOSITORY_URL` with the team's Git repository URL. The final argument sets the local folder name.

```bash
git clone YOUR_REPOSITORY_URL GCMB
cd GCMB
```

### 2. Create a development database

Run the following in pgAdmin Query Tool or `psql` using a PostgreSQL administrator account. Replace the example password before executing.

```sql
CREATE USER GCMB_dev WITH PASSWORD 'REPLACE_WITH_LOCAL_PASSWORD';
CREATE DATABASE GCMB OWNER GCMB_dev;
```

PostgreSQL is separate from SQL Server. GCMB uses the PostgreSQL database and connection settings below.

### 3. Configure and run the backend

Ensure `application-dev.yml` uses the [configuration example](#configuration) below, or adjust the environment variables to match the project's existing configuration.

**Windows PowerShell — from the repository root:**

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:postgresql://localhost:5432/GCMB"
$env:DB_USERNAME = "GCMB_dev"
$env:DB_PASSWORD = "REPLACE_WITH_LOCAL_PASSWORD"
.\mvnw.cmd spring-boot:run
```

**macOS/Linux — from the repository root:**

```bash
cd backend
export SPRING_PROFILES_ACTIVE=dev
export DB_URL='jdbc:postgresql://localhost:5432/GCMB'
export DB_USERNAME='GCMB_dev'
export DB_PASSWORD='REPLACE_WITH_LOCAL_PASSWORD'
./mvnw spring-boot:run
```

The example configuration runs the backend at **http://localhost:8080**. Keep this terminal open.

### 4. Configure and run the frontend

Create `frontend/.env.local`:

```dotenv
REACT_APP_API_BASE_URL=http://localhost:8080/api/v1
```

`/api/v1` is the proposed API base path. Match it to the backend routes, and ensure the shared API client in `src/api/` reads `process.env.REACT_APP_API_BASE_URL`.

Open a second terminal at the repository root.

**Windows PowerShell:**

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

**macOS/Linux:**

```bash
cd frontend
npm ci
npm start
```

`npm ci` requires a committed `package-lock.json` matching `package.json`. If the project has no lockfile yet, run `npm install` once and commit the generated lockfile.

Open **http://localhost:3000**. Create React App uses `npm start`. Restart the frontend after changing `.env.local`.

### Troubleshooting

Backend entry point: `com.gcmb.backend.BackendApplication`, tại `backend/src/main/java/com/gcmb/backend/BackendApplication.java`. Maven chỉ định lớp này trong `pom.xml`; cấu hình Run của IDE cũng cần dùng đúng tên package. Nếu IDE còn chọn `com.gcmb.BackendApplication`, xóa cấu hình Run cũ và chọn lại lớp hiện tại.

| Problem | Check or action |
| --- | --- |
| PowerShell blocks `npm.ps1` | Use `npm.cmd` as shown above. |
| Backend cannot connect to PostgreSQL | Check the database service, port, database name, username, and password. |
| Backend reports a missing table | Confirm the database migrations have been configured and applied. |
| Flyway reports more than one migration with the same version after a migration was renamed or renumbered | Preserve migration versions that have already been applied, then run `cd backend; .\\mvnw.cmd clean spring-boot:run` on Windows (or `./mvnw clean spring-boot:run` on macOS/Linux) to remove stale migration files from `target/classes` and rebuild. If the duplicate is still reported, check `src/main/resources/db/migration/` for duplicate version prefixes. |
| Frontend cannot call the API | Check the backend is running, the API base path is correct, and backend CORS allows `http://localhost:3000`. |
| Java version mismatch | Check `java -version`, `JAVA_HOME`, and the IDE project SDK; use JDK 17. |
| `Unresolved compilation problem` at `BackendApplication.main` while Maven says `Nothing to compile` | Stop the development servers, run `.\mvnw.cmd clean compile` from `backend/`, then run `npm.cmd start` from `frontend/`. This rebuilds generated classes after an IDE compilation or entry-point package change. Ensure the IDE uses `com.gcmb.backend.BackendApplication`. |

## Configuration

| File | Purpose |
| --- | --- |
| `application.yml` | Common backend settings. |
| `application-dev.yml` | Local development settings. |
| `application-prod.yml` | Production settings using environment-supplied secrets. |
| `frontend/.env.local` | Local frontend configuration; exclude from Git. |

Example `backend/src/main/resources/application-dev.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/GCMB}
    username: ${DB_USERNAME:GCMB_dev}
    password: ${DB_PASSWORD}
```

Set the active profile through `SPRING_PROFILES_ACTIVE`. Backend `.env` files are not loaded automatically by Spring Boot; supply variables through the terminal, IDE run configuration, or deployment environment.

Keep database passwords and SePay, email, and cloud-storage credentials outside source control. Frontend `REACT_APP_*` variables are exposed in the browser bundle and must contain only public configuration.

### Database migrations

Store schema changes under `backend/src/main/resources/db/migration/`. For Flyway, use names such as `V1__create_initial_schema.sql` and `V2__add_booking_indexes.sql`.

Migration ownership: V21 and V22 are the F&B menu migrations; V30 and V31 are the equipment category and asset migrations. V30/V31 use idempotent DDL because some development databases already contain those schema changes from the earlier V21/V22 numbering. For those existing databases, reconcile Flyway history before startup; do not reset the schema or change domain data. The `npm start` launcher removes only the generated `backend/target/classes/db/migration/` copy before Maven starts, so renamed source migrations cannot remain as stale duplicate files.

The directory alone does not run migrations. If the team selects Flyway, configure its compatible dependencies in `pom.xml`, including PostgreSQL support where required. With Flyway enabled, migrations can run at backend startup. Keep schema generation under migration control; use Hibernate `ddl-auto: validate` when the migrated schema is ready.

Add a new migration for changes to an already-shared schema. Preserve applied migrations and test changes against a development database.

Khôi phục tính tương thích Flyway (11/11/2026): giữ nội dung đã phát hành tại các phiên bản V32–V40; các migration payroll mới được đặt tiếp theo ở V41–V43. Không đổi SQL của các migration V32–V40 đã áp dụng. V3–V5 được khôi phục theo checksum của database phát triển đang dùng: V3 `1759023455`, V4 `2097460310`, V5 `-262747397`; phần khác biệt trước đó chỉ là chú thích đầu file. Không chạy `repair` để đánh dấu migration mới là đã áp dụng. Nếu một database khác có checksum V3–V5 khác, so sánh lịch sử của database và nội dung SQL trước khi sửa checksum; không xóa schema hoặc dữ liệu.

Đối chiếu database local sau merge (11/11/2026): lịch sử thực tế còn lưu payroll ở V32–V33, combo/thiết bị ở V34–V41 và payroll parts ở V42. Đã so sánh đủ 42 migration: SQL của các file chuyển phiên bản không đổi, V3–V5 chỉ khác chú thích copyright so với commit `d143a7b`. Đã sao lưu lịch sử vào `backend/target/flyway-history-before-20261111.json`, rồi chuyển metadata sang đúng phiên bản/file hiện tại bằng [script đối chiếu có điều kiện](backend/scripts/reconcile_legacy_flyway_20261111.sql). Bản sao lưu chỉ nằm trên máy local; cần lưu ra nơi riêng trước khi xóa `target`.

- Script chỉ chấp nhận đúng lịch sử cũ gồm V1–V42 thành công và 14 bộ phiên bản/tên file/checksum đã xác minh; chạy trên lịch sử khác hoặc chạy lại sẽ bị từ chối. Dừng backend, sao lưu lịch sử và đối chiếu toàn bộ checksum/SQL với source trước khi dùng cho môi trường khác. Chạy bằng `psql -X -v ON_ERROR_STOP=1 -f backend/scripts/reconcile_legacy_flyway_20261111.sql` với cấu hình kết nối của môi trường đó; không truyền mật khẩu qua tham số dòng lệnh.
- Script không chạy SQL nghiệp vụ và không đánh dấu V40 finance là đã áp dụng. Vì lịch sử sau đối chiếu đã có V43, phải chạy Flyway một lần với `outOfOrder=true` để áp dụng riêng V40 còn thiếu; kiểm tra chỉ V40 ở trạng thái pending trước khi chạy. Sau đó khởi động lại với `outOfOrder=false` như cấu hình mặc định. Database local đã hoàn tất cả hai bước, không cần chạy lại script hay bật tùy chọn này lâu dài.
- `FinancialController` dùng `AS "month"` cho alias tháng ở cả danh sách kỳ và phản hồi chốt kỳ, giữ nguyên khóa JSON `month`; sửa lỗi PostgreSQL `syntax error at or near "month"`. Không đổi API hay quy tắc chốt kỳ.
- Kiểm tra: backend biên dịch thành công; khởi động với cấu hình Flyway mặc định xác thực đủ 43 migration; gọi danh sách/chốt kỳ qua controller trên PostgreSQL thật thành công, toàn bộ dữ liệu thử đã rollback. Bộ unit test tổng: 180 test, 11 failure, 5 error, 1 skipped ở các test tài khoản/auth ngoài phạm vi sửa lỗi này; chưa sửa các lỗi test đó.

## Usage

Cơ sở tạm ngừng có thể mở lại bằng **Mở lại cơ sở** và xác nhận; hệ thống ghi nhật ký `REACTIVATE_BRANCH`, chuyển về ACTIVE và xóa lý do trạng thái hiện tại, giữ lịch sử tạm ngừng. API: `POST /api/v1/head-office/branches/{id}/reactivate` dành cho quản lý cấp cao, chỉ nhận cơ sở SUSPENDED (gọi lại với ACTIVE không đổi dữ liệu). Màn cơ sở không còn nút **Đóng cơ sở**; bản ghi CLOSED cũ vẫn được giữ để tra cứu. Lịch sử sửa thông tin không dùng lại lý do tạm ngừng; ghi chú vận hành và lý do trạng thái là hai thông tin riêng. Không cần migration.

### Checkout, bill và SePay (14/10/2026)

Lễ tân chọn phòng đang sử dụng → **Checkout & lập bill**. Kiểm tra tiền phòng theo giá đã xác nhận, F&B đã SERVED (không tính lại món combo giá 0), phụ phí có lý do và xác nhận đã kiểm tra phòng. **Tiếp tục thanh toán** chốt bill và thời điểm ngừng tính sử dụng; sau đó phí/order/đổi phòng/gia hạn bị khóa. Khách trả sớm vẫn giữ các khoản booking đã xác nhận; không tự tính lại bằng bảng giá mới. Nếu đã quá giờ mà chưa có phiên giờ lẻ, cần xác nhận gia hạn trước; đồng hồ giờ lẻ đang chạy được dừng bằng service hiện hành khi chốt bill.

Trong thanh toán chọn **Tiền mặt**, **SePay** hoặc **Kết hợp**. Phần tiền mặt nhập khi tạo QR được lưu thành phân bổ tạm trên bill, chưa tạo payment và không hiển thị là đã thu. **Đổi phương thức** hủy QR cũ, xóa toàn bộ phân bổ tạm và đưa form về tổng bill ban đầu. Bill 170.000đ nhập tạm 50.000đ tiền mặt + QR 120.000đ: sau đặt lại vẫn cần thanh toán 170.000đ, không giữ 50.000đ. Khi SePay đã xác nhận chuyển khoản hoặc có khoản thu thực tế, không cho đặt lại. Webhook của mã đã hủy được lưu EXCEPTION, không cộng vào bill; hủy QR không hủy giao dịch tại ngân hàng. Lễ tân bấm **Hoàn tất thanh toán** mới ghi tiền mặt, tiền khách đưa và tiền thừa trong cùng giao dịch kết thúc bill. Trường readyToComplete cho biết tổng khoản thu thực tế + tiền mặt dự kiến bằng đúng net_amount; tổng paid vẫn chỉ gồm payment thật. QR có hạn 30 phút, có thể gia hạn cùng mã sau hết hạn. Hoàn tất chuyển bill SETTLED, booking COMPLETED, allocation FINISHED và phòng AVAILABLE (Trống); gửi lặp không thu hai lần. Menu **Thanh toán** tra cứu 100 bill gần nhất của cơ sở; có in bill qua trình duyệt.


Dữ liệu hoàn cọc local (15/10/2026), tài khoản test.rec.ha tại TEST_CS_B: phòng TEST_REFUND_01; BK-20261015-E6426634 cọc 100.000đ và BK-20261015-53FBBC28 cọc 150.000đ đã hủy sớm, có yêu cầu REQUESTED; BK-20261015-D79299DC cọc 80.000đ đã hủy đủ điều kiện, chưa gửi yêu cầu. Thông tin nhận hoàn dùng TEST BANK / 0000000000, hoàn toàn giả lập. Đây là dữ liệu thêm theo yêu cầu trên database local, không phải migration và chưa ghi nhận khoản hoàn thực tế.

### Ghép tiền cọc vào checkout (15/10/2026)

Đã ghép màn Tiền cọc của Thanh vào AppShell chung, giữ cả menu Tiền cọc và Thanh toán. Màn Tiền cọc chờ xác thực trước khi dựng AppShell; thanh tài khoản dùng chung xử lý trường hợp chưa có user và khóa thao tác tài khoản trong thời gian đó. AuthErrors xử lý cả ResponseStatusException và MethodArgumentTypeMismatchException. Các endpoint danh sách/chi tiết/yêu cầu hoàn cọc được giữ cùng MutationAudit của booking.

Khi lập bill, GCMB khóa booking và ví cọc, kiểm tra yêu cầu hoàn đang REQUESTED/APPROVED/PROCESSING, rồi ghi deposit_entry.kind=APPLY cho số dư cọc đã nhận. Không ghi thêm payment cho cọc, không sửa sổ cọc gốc; gọi lặp không trừ lần hai. Tổng bill vẫn giữ nguyên; depositApplied là cọc đã trừ, paid là khoản thu thêm, remaining = amount - depositApplied - paid. Tiền mặt/QR chỉ thu phần remaining. Ví dụ bill 170.000đ, cọc 50.000đ: cần thu thêm 120.000đ. Đổi phương thức chỉ hủy phân bổ tiền mặt/QR tạm, không hủy cọc đã áp dụng. Hoàn tất khi depositApplied + paid + plannedCash bằng tổng bill. Nếu cọc bằng bill thì hoàn tất không cần thu thêm.

API preview checkout trả depositBalance và hasPendingRefund; API bill trả depositApplied. Màn Tiền cọc có trạng thái APPLIED (Đã trừ vào bill) và lịch sử APPLY, không làm mất hồ sơ sau khi dùng hết cọc. Chưa triển khai hoàn cọc dư khi checkout: dữ liệu cọc âm/vượt tổng bill bị từ chối để kiểm tra; luồng yêu cầu hoàn của Thanh vẫn áp dụng cho booking đã hủy. Không có migration mới, dùng sổ cọc hiện hữu V1/V8/V9 và bill V12/V13.

Customers scan the in-room QR code to view the F&B menu and submit an order for their active room session.

Obtain development accounts through the team's agreed seed or account-provisioning process. No demo credentials are specified here.

V14 seed correction (24/10/2026): the six starter rooms per seeded branch are initial data, not a business limit. Branches can retain existing rooms and add more through room management. V14 no longer requires exactly six rooms or all existing rooms to be AVAILABLE; it retains the service-mode consistency check. Room inserts still use `ON CONFLICT (branch_id, code) DO NOTHING`, preserving existing room details and status. This correction targets databases where V14 has not succeeded. If V14 has already been applied on another environment, coordinate migration history before distributing the edited script; do not reset that database or rewrite its Flyway checksum automatically.

## Testing

Lịch ca lễ tân (08/11/2026): màn Ca làm việc có vùng cuộn dọc cho cả Lịch ca / đăng ký và Công cá nhân trên desktop; bảng lịch cuộn ngang khi màn hình hẹp. Lễ tân dùng chung bố cục, kích thước ô, màu bốn khung ca, thanh chọn tuần, nhãn trạng thái và chú giải với lịch ca quản lý cơ sở trong `ShiftSchedulePage.js`, không dùng bộ CSS riêng. Nội dung và thao tác vẫn theo vai trò: lễ tân đăng ký/xem ca cá nhân; quản lý mở ca, phân công và chấm công. Không thay API hoặc quyền truy cập. File ảnh hưởng: `ShiftSchedulePage.js`, `shifts.css`.

Run commands from the corresponding project directory.

Thông báo lịch ca (08/11/2026): mở chi tiết/phân công, mở chấm công, mở chỉnh lịch hoặc đổi tuần/cơ sở sẽ xóa thông báo thành công của thao tác trước. Đóng hộp thoại không hiển thị thành công và không gọi API phân công. Thông báo mới chỉ được đặt sau khi API lưu thành công. File ảnh hưởng: `ShiftSchedulePage.js`. Không thêm file test theo yêu cầu; bốn kiểm tra hồi quy đã chạy đạt trước khi xóa file test tạm.

| Check | Windows PowerShell | macOS/Linux |
| --- | --- | --- |
| Backend tests (`backend/`) | `.\mvnw.cmd test` | `./mvnw test` |
| Backend verification (`backend/`) | `.\mvnw.cmd verify` | `./mvnw verify` |
| Frontend tests (`frontend/`) | `npm.cmd test -- --watchAll=false` | `npm test -- --watchAll=false` |
| Frontend production build (`frontend/`) | `npm.cmd run build` | `npm run build` |

These commands assume the standard Maven and Create React App scripts are present. Database-dependent tests must use a separate test database and the configuration defined by the test suite.

Prioritize tests for booking conflicts, role and branch access, bill/deposit calculations, stock movements, payroll calculations, and financial reconciliation. API/system tests and UAT should cover complete workflows and their failure cases. Do not use production customer or financial data for testing.

## Contributing

Work on a separate branch and submit a pull request to the team's integration branch. Agree on the integration branch name before adopting a `develop` workflow.

Use descriptive branch names such as `feature/booking-create` or `fix/booking-conflict`. Use concise commit messages such as `feat: add booking creation`, `fix: validate overlapping bookings`, or `docs: update local setup`.

Before opening a pull request:

- Synchronize with the integration branch and resolve conflicts.
- Run checks relevant to the change and report the results.
- Describe the resulting behavior and any required configuration or migration.
- Update documentation when setup, APIs, or behavior changes.
- Check that no secrets or local generated files are included.

Review and test changes before merging. Keep `node_modules/`, `build/`, `target/`, and local environment files out of Git; preserve examples such as `.env.example` and Maven Wrapper files.

## Documentation

The detailed project references are:

- Report 1 — Project Introduction.
- Report 2 — Project Management Plan.
- GenZ Use Case List.
- GenZ Cinema AS-IS / TO-BE workflows.

- [Room Pricing and Room Management Validation](docs/Room%20Pricing%20and%20Room%20Management%20Validation.md) — backend validation behavior for room types, pricing rules, combo packages, and rooms.
- [Finance Fund and Expense Workflow](docs/Finance%20Fund%20and%20Expense%20Workflow.md) — fund receipt confirmation, category maintenance, branch expense requests, approval thresholds, payment, reimbursement, and evidence access.


Keep detailed requirements, design, business rules, and test documentation outside this README. Add repository-relative links when approved documents are placed in `docs/`, or link to their authorized shared location.

README structure reference: [How to Write a Good README File — freeCodeCamp](https://www.freecodecamp.org/news/how-to-write-a-good-readme-file/).

Technical setup references: [Create React App environment variables](https://create-react-app.dev/docs/adding-custom-environment-variables/), [Spring Boot profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html), and [Flyway documentation](https://documentation.red-gate.com/flyway).

## Team

| Name | Project role |
| --- | --- |
| Nguyen Minh Cao | Team Leader |
| Dao Van Huy | Member |
| Cu Thi Huyen Trang | Member |
| Nguyen Van Trong | Member; business stakeholder as Branch Manager |
| Nguyen Tuan Thanh | Member |

**Supervisor:** Nguyen Thi Hanh.

## License

GCMB is developed as an FPT University capstone project. A software license has not been specified in the supplied project materials. The team should agree on usage and distribution terms and add a `LICENSE` file before granting reuse rights.


Reception merge (15/10/2026): màn Phòng giữ bộ lọc ngày và lấy giờ hiện tại cộng 5 phút, không có ô chọn giờ riêng. AppShell dùng giao diện chung và ẩn ô tìm kiếm trên topbar khi màn Phòng đã có ô tìm kiếm. Giữ luồng đổi phòng/gia hạn và nối Checkout & lập bill tới các route thanh toán.

Checkout và thu gọn giao diện (24/10/2026): hoàn tất thanh toán/bill trả phòng trực tiếp về AVAILABLE (Trống), không còn bước Đang dọn hoặc nút Dọn xong. Bỏ API POST /api/v1/reception/rooms/{roomId}/finish-cleaning và không cho chọn CLEANING tại API/giao diện quản lý phòng. V16__retire_room_cleaning_status.sql chuyển phòng CLEANING cũ về AVAILABLE, xóa giờ dự kiến sẵn sàng và thêm ràng buộc không tạo trạng thái CLEANING mới; giữ nguyên booking, allocation và lịch sử trạng thái cũ. Lịch đặt tiếp theo và các kiểm tra trùng phòng vẫn áp dụng. Menu bên trái ở khung lễ tân/kế toán và bảng Phòng đang chọn bên phải có nút thu gọn/mở lại, dùng được bằng bàn phím; thu gọn không mất lựa chọn phòng hay bộ lọc. Trên màn hình nhỏ, menu thu gọn được ẩn thay vì chiếm một cột.

Checkout sớm và đặt lại phòng (24/10/2026): V17__release_finished_room_allocations.sql đồng bộ ràng buộc `no_room_overlap` với kiểm tra phòng trống/tạo booking: chỉ allocation RESERVED hoặc OCCUPIED giữ lịch phòng; FINISHED và RELEASED không chặn booking mới. Sau checkout đã hoàn tất, có thể đặt lại phòng dù giờ trả dự kiến của lượt trước chưa đến. Giữ nguyên toàn bộ mốc giờ và lịch sử booking/allocation; không thay đổi ràng buộc `no_booking_parallel_rooms`. Booking còn CONFIRMED/IN_HOUSE vẫn phải vượt qua kiểm tra trùng lịch hiện hành.

### Cọc QR 30% và nhiều quản lý cơ sở (25/10/2026)

- Khi bật thu cọc, số tiền bằng 30% giá booking do backend báo (combo hoặc tính giờ), làm tròn HALF_UP đến đồng. Đây là quy tắc mới thay cho mức cọc cấu hình riêng của combo trong luồng tạo booking. Không sửa các khoản cọc lịch sử. Tiền mặt chỉ ghi nhận khi lễ tân đã nhận tiền; chuyển khoản không còn ghi nhận thu thủ công lúc tạo booking.
- Chọn Chuyển khoản rồi bấm **Tạo booking & lấy QR cọc**: tạo booking giữ phòng, sau đó hiển thị QR có số tiền và mã tham chiếu riêng. Lưu mã booking trong bản nháp phiên để tải lại trang không tạo booking lần hai. Rời QR vẫn giữ booking; có thể mở **QR cọc 30%** trong chi tiết booking CONFIRMED chưa nhận cọc.
- API mới: `POST /api/v1/reception/bookings/{id}/deposit-payment` tạo/dùng lại yêu cầu cọc; `GET` cùng đường dẫn đọc trạng thái. Giới hạn booking thuộc cơ sở của lễ tân. Phản hồi gồm `status`, `bookingId`, `bookingCode`, `amount`, `receivedAmount`; yêu cầu chưa thu còn có `reference`, `expiresAt`, `bank`, `account`, `holder`, `qrUrl` khi còn hiệu lực. QR có hạn 30 phút; lấy lại QR sẽ hủy yêu cầu cũ đã hết hạn hoặc lệch giá. Không nhận cọc lần hai.
- Dùng cấu hình SePay hiện có: `SEPAY_BANK`, `SEPAY_ACCOUNT_NUMBER`, `SEPAY_ACCOUNT_HOLDER`, `SEPAY_WEBHOOK_KEY` trong `backend/.env`. Webhook `/api/v1/payments/sepay/webhook` xác thực và đối chiếu mã, tài khoản, số tiền, thời gian; chỉ sau đó tạo payment DEPOSIT và bút toán RECEIVE. Gửi lại cùng giao dịch không ghi trùng. Giao dịch sai số tiền, QR cũ, booking đã hủy/check-in hoặc giá đã đổi được lưu EXCEPTION để đối chiếu, không tự ghi nhận cọc. Không nhập khóa webhook trên frontend. Nhật ký mới: PLAN_DEPOSIT_PAYMENT, CONFIRM_SEPAY_DEPOSIT.
- Mỗi cơ sở có tối đa **2 quản lý**, một quản lý có thể phụ trách nhiều cơ sở. Gán thêm giữ nguyên cả các quản lý hiện tại lẫn những cơ sở người đó đang phụ trách. Áp dụng chung cho màn Cơ sở và phân quyền/tạo tài khoản. API cơ sở trả các tên/mã/tài khoản quản lý nối bằng dấu phẩy và `managerCount`, thay trường `managerId` đơn. Thu hồi/chuyển quản lý qua phân quyền tài khoản như trước.
- Migration mới `V18__allow_three_branch_managers.sql` đổi khóa chính phân công thành `(branch_id,user_id)`, giữ UNIQUE user_id và thêm trigger giới hạn 3. Không thay V1–V17 đã chạy; Flyway áp dụng V18 khi backend khởi động. Không tạo thêm file test.

### Quản lý nhiều cơ sở và chọn cơ sở trên dashboard (25/10/2026)

- Đây là cập nhật yêu cầu sau Report 2/3: quan hệ quản lý–cơ sở là nhiều–nhiều; mỗi cơ sở tối đa 2 quản lý. V19__managers_with_multiple_branches.sql bỏ UNIQUE user_id của phân công và bổ sung auth_token.selected_branch_id. Giữ nguyên V18 đã áp dụng và các phân công hiện hữu. Backend cần khởi động lại để Flyway chạy V19 trước khi dùng code mới.
- Màn Tài khoản → Vai trò & phạm vi truy cập: Quản lý cơ sở có thể tích nhiều cơ sở; danh sách gửi trong branchIds của API đổi quyền. Danh sách này thay thế toàn bộ phân công của tài khoản trong một transaction; bỏ chọn là thu hồi quyền tại cơ sở đó. Nếu phân công mới khiến một cơ sở vượt 2 quản lý, từ chối toàn bộ thay đổi. Người dùng cần đăng nhập lại sau khi đổi quyền như trước. branchId vẫn là cơ sở mặc định để tương thích dữ liệu/tài khoản cũ. AccountView bổ sung managedBranchIds và hiển thị tên tất cả cơ sở; lọc tài khoản theo cơ sở xét mọi phân công quản lý.
- Màn Cơ sở → Thêm quản lý chỉ thêm phân công, không chuyển người khỏi cơ sở khác; gọi lại phân công đã tồn tại không tạo trùng. Danh sách ứng viên không bị lặp khi người đó có nhiều cơ sở.
- Dashboard quản lý: dropdown **Cơ sở đang quản lý** cùng hàng với Thông tin tài khoản chỉ liệt kê các cơ sở ACTIVE được phân công. Đổi lựa chọn cập nhật phiên, tiêu đề cơ sở và tải lại dữ liệu, xóa bộ lọc/biểu mẫu phòng cũ. Các menu chưa triển khai vẫn là màn chờ theo phạm vi cơ sở đang chọn.
- API mới cho BRANCH_MANAGER: GET /api/v1/auth/branches trả [{id,name}], POST /api/v1/auth/branch với {branchId} trả Identity của cơ sở đã chọn. Có CSRF và kiểm tra phân công/cơ sở ACTIVE tại server; không thể chọn cơ sở ngoài quyền bằng cách sửa request. Lựa chọn được nhớ theo phiên đăng nhập. Nếu cơ sở bị thu hồi hoặc tạm ngừng, danh tính chọn lại một cơ sở còn hợp lệ; không còn cơ sở nào thì không tiếp tục sử dụng phiên quản lý.
- API phòng quản lý gửi X-GCMB-Branch-Id theo màn hình đang mở. SessionFilter xác minh lại phân công trước khi đặt phạm vi Identity, giúp các tab đang xem cơ sở khác nhau không ghi nhầm dữ liệu khi phiên đổi lựa chọn. Khách API không gửi header dùng cơ sở đang chọn của phiên. Header không tự cấp thêm quyền.
- File ảnh hưởng: branch/BranchAdministrationService, auth/{AuthRepository,AuthService,AuthController,SessionFilter,AuthSecurity}, accounts/{DTO,AccountRepository,AccountAccessService}; frontend BranchManagerDashboard, DashboardTopbar, RoleHomePage, AccountAccessDialog, BranchManagementPage, BranchRoomsPage và các API/styles liên quan. Không thêm file test.

### Giới hạn hai quản lý và bố trí dropdown (25/10/2026)

- Yêu cầu hiện hành: mỗi cơ sở tối đa 2 quản lý; một quản lý vẫn có thể quản lý nhiều cơ sở. BranchAdministrationService chặn người thứ ba, kể cả khi gán từ màn tài khoản; giao diện phân quyền và bộ đếm cơ sở hiển thị giới hạn 2.
- Dropdown cơ sở nằm cùng hàng với tên đăng nhập, Thông tin tài khoản và Đăng xuất trên desktop; màn hình nhỏ được xuống hàng để không tràn. Thay đổi CSS tại frontend/src/features/branch/pages/branch-rooms.css.
- V20__limit_two_branch_managers.sql thay hàm trigger giới hạn 3 thành 2, không sửa V18/V19 đã phát hành. Migration kiểm tra phân công hiện hữu và báo cơ sở có quá 2 người để quản lý chủ động chọn người cần thu hồi; không tự xóa phân công. Khởi động lại backend để Flyway áp dụng các migration còn thiếu.
- Theo lựa chọn trực tiếp của người dùng, đã bỏ phân công qlcs.nguyenluongbang tại HN_HOA_BANG trên database local, ghi UNASSIGN_BRANCH_MANAGER. Giữ nguyên tài khoản và phân công tại cơ sở khác. Các môi trường khác cần tự kiểm tra dữ liệu trước khi chạy V20. Không thêm file test.

Hợp nhất giao diện (25/10/2026): `BookingManagementPage` giữ khung chi tiết booking có nút đóng và chế độ hộp thoại trên màn hình nhỏ, đồng thời hỗ trợ QR cọc 30%; rời QR tải lại danh sách và chi tiết để cập nhật tiền cọc. `DashboardTopbar` nhận đồng thời các thuộc tính thu gọn menu (`onToggleSidebar`, `sidebarExpanded`) và vùng chọn cơ sở (`accountHeader`).

### Checkout booking quá giờ (25/10/2026)

- Checkout tự báo phần quá giờ chưa thu từ `plannedEnd` hiện hành (đã gồm thời lượng gia hạn) đến lúc chốt bill, không yêu cầu bật đồng hồ trước. Đơn giá lấy từ cấu hình giá giờ của hạng phòng/cơ sở tại giờ bắt đầu quá hạn; làm tròn thời lượng lên từng phút và làm tròn tiền HALF_UP đến đồng.
- Nếu bật đồng hồ muộn, bổ sung đoạn từ `plannedEnd` đến `billedFrom` theo giá tại `plannedEnd`. Đoạn đồng hồ RUNNING dùng đơn giá đã lưu và tự dừng khi lập bill. Đồng hồ STOPPED giữ nguyên khoản đã ghi và thời điểm dừng; checkout chỉ bổ sung đoạn trước đồng hồ nếu thiếu, không thu lại thời gian đã chốt. Các đoạn được làm tròn phút riêng.
- API preview checkout bổ sung `overtime: [{from,to,minutes,hourlyPrice,amount,priceRuleId}]` và `overtimeAmount`; `lines` và `amount` đã bao gồm các khoản này. Preview chỉ đọc, chưa ghi phí. Request lập bill bổ sung `overtimeConfirmed` và `overtimeAmount`. Backend tính lại bằng giờ máy chủ trong giao dịch có khóa booking; thiếu xác nhận hoặc số tiền thay đổi trả 409, không ghi phí/bill. Gửi lại booking đã có bill trả bill hiện hữu, không thu trùng.
- Màn Checkout hiển thị từng khoảng giờ, đơn giá, tiền phát sinh và ô xác nhận bắt buộc; có nút **Cập nhật tiền phòng**. Khi lập bill gặp 409, tải lại preview và bỏ chọn xác nhận để lễ tân kiểm tra lại. Cần triển khai frontend/backend cùng nhau. Không thêm migration; không tính lại hóa đơn đã lập.
- File ảnh hưởng: `CheckoutDtos.java`, `BookingService.java`, `CheckoutService.java`, `CheckoutPage.js`. Các file test tạo trong đợt sửa này đã được xóa theo yêu cầu; giữ nguyên các test có sẵn.

### Tạo tài khoản và thông báo theo Expected UT (02/11/2026)

- UC-04 tạo tài khoản từ các trường được quản lý xác nhận: `employeeId`, `username`, `email`, `password`, `role`, `branchId`. Form có email chỉnh sửa được và dropdown vai trò/cơ sở; chọn nhân viên chỉ gợi ý giá trị ban đầu, backend không ghi đè request bằng hồ sơ. Vai trò toàn chuỗi không nhận `branchId`; vai trò tại cơ sở bắt buộc chọn cơ sở ACTIVE. Giữ kiểm tra nhân viên ACTIVE chưa có tài khoản, email/username duy nhất và chính sách mật khẩu. Không thay dữ liệu tài khoản đã tạo hay thêm migration.
- Quản lý cơ sở chỉ tạo tài khoản RECEPTIONIST cho nhân viên và cơ sở trong phạm vi đang quản lý; bộ lọc, dropdown hoặc sửa request không cấp thêm quyền. Các lỗi quyền tài khoản dùng MSG24 theo thao tác tạo/xem/phân quyền/đổi trạng thái. Lỗi CSRF giữ cách xử lý hiện có, không được coi là lỗi quyền nghiệp vụ.
- Đồng bộ MSG35 cho email trùng, MSG36 cho username ngoài 3–100 ký tự/định dạng, MSG40 có tên cơ sở cho phân công vào cơ sở không hoạt động. Áp dụng cho DEF-UT-055/058/059/063/095/097–099/104–105/107–108 và luồng tạo tài khoản DEF-UT-068–075/088–092.
- File ảnh hưởng: `AccountService`, `AccountAccessService`, `AccountProfileService`, `AccountRepository`, `AuthSecurity` và `AccountsPage.js`. Không thêm hoặc sửa file test; kiểm tra bổ sung trong bộ nhớ. Giữ nguyên DEF-UT-007/010/030/373 để thống nhất sau; không cập nhật sheet UT trong đợt này.
