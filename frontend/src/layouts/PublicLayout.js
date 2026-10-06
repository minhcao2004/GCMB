import { useState } from 'react';
import Brand from '../shared/components/Brand';

function Header({ onBook }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = () => setMenuOpen(false);
  return (
    <header id="header">
      <Brand />
      <nav aria-label="Điều hướng chính" id="navigation" className={menuOpen ? 'open' : ''}>
        <a href="#about" onClick={closeMenu}>Chất GenZ</a><a href="#spaces" onClick={closeMenu}>Không gian</a><a href="#pricing" onClick={closeMenu}>Bảng giá</a><a href="#locations" onClick={closeMenu}>Chi nhánh</a>
      </nav>
      <div className="header-actions">
        <button className="button small pink" onClick={onBook}>Đặt phòng <span>↗</span></button>
        <button id="menu-toggle" className="menu-toggle" aria-label={menuOpen ? 'Đóng menu' : 'Mở menu'} aria-expanded={menuOpen} aria-controls="navigation" onClick={() => setMenuOpen((value) => !value)}><span></span><span></span></button>
      </div>
    </header>
  );
}

function Footer() {
  return (
    <footer>
      <div className="footer-top"><Brand /><p>Một chiếc phòng.<br />Cả thế giới của bạn.</p><div><a href="https://www.facebook.com/genzcinema/" target="_blank" rel="noopener noreferrer">Facebook ↗</a><a href="https://zalo.me/0876583333" target="_blank" rel="noopener noreferrer">Zalo ↗</a><a href="mailto:genzcinemavn@gmail.com">Email ↗</a></div></div>
      <div className="footer-bottom"><span>© {new Date().getFullYear()} GenZ Cinema</span><a href="https://genzcinema.vn/" target="_blank" rel="noopener noreferrer">Khám phá website GenZ Cinema ↗</a><a href="#main">Về đầu trang ↑</a></div>
    </footer>
  );
}

function SocialDock() {
  return (
    <aside className="social-dock" aria-label="Liên hệ GenZ Cinema">
      <a className="social-facebook" href="https://www.facebook.com/genzcinema/" target="_blank" rel="noopener noreferrer" aria-label="Liên hệ Facebook GenZ Cinema"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 21v-8h3l.5-4H14V7c0-1.1.4-2 2-2h2V1.5A24 24 0 0 0 15 1c-3 0-5 1.8-5 5v3H7v4h3v8z" /></svg><span>Facebook</span></a>
      <a className="social-zalo" href="https://zalo.me/0876583333" target="_blank" rel="noopener noreferrer" aria-label="Liên hệ Zalo GenZ Cinema"><svg viewBox="0 0 48 48" aria-hidden="true"><path fill="white" d="M24 5C12 5 4 12 4 22c0 5 2 9 6 12L8 42l10-4c2 1 4 1 6 1 12 0 20-7 20-17S36 5 24 5z" /><text x="24" y="27" textAnchor="middle" fill="#0878ed" fontFamily="Arial,sans-serif" fontSize="14" fontWeight="bold">Zalo</text></svg><span>Zalo</span></a>
    </aside>
  );
}

export default function PublicLayout({ children, onBook }) {
  return <><a className="skip-link" href="#main">Đến nội dung chính</a><Header onBook={onBook} />{children}<Footer /><SocialDock /></>;
}

