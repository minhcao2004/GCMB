import logo from '../../assets/images/logo.png';
import './brand.css';

// Hiển thị tên thương hiệu dùng chung.
export default function BrandWordmark() {
  return <div className="brand-wordmark"><img src={logo} alt="" /><span className="brand-gradient">GenZ</span></div>;
}
