# GCMB Frontend

React frontend cho hệ thống GenZ Cinema & Music Box. Landing page được chuyển từ website tĩnh sang React component, giữ nguyên thiết kế, dữ liệu phòng, bảng giá, chi nhánh và các tương tác chính.

## Chạy dự án

```powershell
npm.cmd ci
npm.cmd start
```

`npm start` tự chạy cả backend Spring Boot và frontend React. PostgreSQL cần đang chạy; cấu hình riêng trong `backend/.env`, xem mẫu `backend/.env.example`. Không cần bấm thêm BackendApplication trong IntelliJ. Nhấn Ctrl+C để dừng cả hai.

Mở `http://localhost:3000/dang-nhap`. Frontend gọi `/api/v1` qua proxy tới backend cổng 8080. Lệnh báo lỗi nếu cổng 3000/8080 đang bị dùng, không tự chuyển sang 3001.

Nếu muốn chạy backend riêng trong IntelliJ, dùng `npm.cmd run start:frontend` cho phần React.

Khi chạy riêng bằng `start:frontend`, tạo `.env.local` nếu cần đổi backend. Lệnh chạy chung `npm start` luôn dùng proxy `/api/v1`:

```dotenv
REACT_APP_API_BASE_URL=http://localhost:8080/api/v1
```

## Kiểm tra

```powershell
npm.cmd test -- --watchAll=false
npm.cmd run build
```

## Cấu trúc

```text
src/
├── api/                  # Axios client và API services
├── assets/
│   ├── images/           # Ảnh giao diện
│   └── styles/           # CSS dùng chung
├── features/landing/
│   ├── components/       # Các section và dialog
│   ├── data/             # Phòng, chi nhánh, bảng giá
│   ├── hooks/            # Hiệu ứng trang
│   └── pages/            # Trang chủ
├── layouts/              # Bố cục public
├── routes/               # React Router
├── shared/               # Component và tiện ích dùng lại
├── App.js
└── index.js
```

Không đặt secret trong biến `REACT_APP_*` vì các biến này được đưa vào bundle trình duyệt.
