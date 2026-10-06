import { useState } from 'react';
import { images } from '../data/siteData';

const heroSlides = [
  { name: 'Gaming Box', image: images.gaming, position: 'center 54%' },
  { name: 'Barbie Room', image: images.barbie, position: 'center 68%' },
  { name: 'Music Box', image: images.musicBanner, position: 'center 57%' },
];

export default function Hero() {
  const [activeSlide, setActiveSlide] = useState(0);
  const slide = heroSlides[activeSlide];
  return <><section className="hero" aria-labelledby="hero-title"><div className="hero-photo" id="hero-photo" style={{ backgroundImage: `url(${slide.image})`, backgroundPosition: slide.position }}></div><div className="hero-shade"></div><div className="hero-topline"><span><i className="live-dot"></i> YOUR PRIVATE CINEMA CLUB</span><span>HÀ NỘI & TP. HỒ CHÍ MINH</span></div><div className="hero-content"><p className="eyebrow">Tắt deadline. Bật thế giới của bạn.</p><h1 id="hero-title"><span>BẬT MOOD.</span><span className="pink-text">CHẠM CHẤT RIÊNG<span className="title-dot">.</span></span></h1><div className="hero-description"><p>Một bộ phim hay. Một người hợp cạ.<br />Một không gian chỉ dành cho bạn.</p><a href="#spaces" className="round-link"><span className="round-arrow">↗</span> Khám phá không gian</a></div></div><div className="hero-bottom"><a href="#about" className="scroll-cue"><span>↓</span> CUỘN ĐỂ CẢM NHẬN</a><div className="hero-selector" aria-label="Ảnh không gian nổi bật">{heroSlides.map((item, index) => <button key={item.name} className={index === activeSlide ? 'active' : ''} onClick={() => setActiveSlide(index)} aria-label={`Xem ${item.name}`} aria-pressed={index === activeSlide}>0{index + 1} <span>{item.name}</span></button>)}</div><a className="hero-ticket" href="#pricing"><span>HẸN NHAU TỪ</span><strong>96K<small>/giờ</small></strong><span>Khám phá combo ↗</span></a></div></section><div className="marquee" aria-hidden="true"><div>YOUR SPACE. YOUR VIBE. <span>✳</span> CINEMA · GAMING · MUSIC <span>✳</span> YOUR SPACE. YOUR VIBE. <span>✳</span> CINEMA · GAMING · MUSIC <span>✳</span></div></div></>;
}

