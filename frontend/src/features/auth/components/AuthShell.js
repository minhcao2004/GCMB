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
