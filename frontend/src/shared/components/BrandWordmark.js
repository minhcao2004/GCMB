import logo from '../../assets/images/logo.png';
import './brand.css';

export default function BrandWordmark() {
  return <div className="brand-wordmark"><img src={logo} alt="" /><span className="brand-gradient">GenZ</span></div>;
}
