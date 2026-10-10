import logo from '../../assets/images/logo.png';
import './brand.css';

export default function Brand() {
  return <a className="brand" href="#main" aria-label="GenZ Cinema — Trang chủ"><img src={logo} alt="Logo GenZ Cinema" width="78" height="58" /><span className="brand-gradient">GenZ<span className="brand-small">CINEMA & MUSIC</span></span></a>;
}

