# GCMB Frontend

React frontend cho hệ thống GenZ Cinema & Music Box. Landing page được chuyển từ website tĩnh sang React component, giữ nguyên thiết kế, dữ liệu phòng, bảng giá, chi nhánh và các tương tác chính.

## Chạy dự án

```powershell
npm.cmd ci
npm.cmd start
```

Ứng dụng chạy tại `http://localhost:3000`.

Tạo `.env.local` nếu cần đổi backend:

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
