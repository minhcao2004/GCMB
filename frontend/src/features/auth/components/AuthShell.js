/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Tạo khung trang xác thực dùng chung với logo, tiêu đề, nội dung được truyền vào và chân trang.
 */

import BrandWordmark from '../../../shared/components/BrandWordmark';
import '../styles/auth.css';

// Khung giao diện chung cho ba màn hình xác thực.
export default function AuthShell({ children, title, wide = false }) {
  return <main className="auth-page">
    <section className={`auth-card${wide ? ' auth-card-wide' : ''}`} aria-labelledby="auth-title">
      <div className="auth-brand"><BrandWordmark /></div>
      <h1 id="auth-title">{title}</h1>
      {children}
    </section>
    <footer className="auth-footer">Hệ thống quản lý GenZ Cinema &amp; Music Box</footer>
  </main>;
}
