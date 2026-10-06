import { useState } from 'react';
import { comboDetails, longComboDetails, prices } from '../data/siteData';

export default function PricingSection({ onBook }) {
  const [tier, setTier] = useState('queen');
  const price = prices[tier];
  return (
    <section id="pricing" className="section pricing">
      <div className="section-heading reveal"><div><p className="eyebrow">03 / GOOD TIMES, GREAT COMBOS</p><h2>Chill hết nấc.<br /><span className="muted">Giá vừa túi.</span></h2></div><div className="price-switch" role="group" aria-label="Chọn hạng phòng">{['queen', 'king'].map((value) => <button key={value} className={tier === value ? 'active' : ''} onClick={() => setTier(value)} aria-pressed={tier === value}>{value.toUpperCase()}</button>)}</div></div>
      <div className="price-layout"><div className="hour-ticket reveal"><span className="eyebrow">PHÒNG <span>{tier.toUpperCase()}</span></span><p className="big-price"><span>{price.hour}</span><span>K</span></p><span className="per-hour">/ giờ xem phim</span><div className="ticket-perf"></div><p>Một chiếc phòng riêng.<br />Một cuộc hẹn đúng ý.</p><button className="button dark" onClick={onBook}>Chọn chi nhánh <span>↗</span></button></div><div className="combos">{price.combos.map((value, index) => <div className="combo" key={value}><div><h3>Combo 0{index + 1}{index === 1 && <span className="combo-label">CHILL CÙNG NHAU</span>}</h3><p>2 giờ xem phim<br />{comboDetails[index]}</p></div><span className="combo-price">{value}K<small>/ 2 giờ</small></span></div>)}</div></div>
      <div className="long-combos">{price.long.map((value, index) => <div className="long-combo" key={longComboDetails[index][0]}><h3>{longComboDetails[index][0]}</h3><p>{longComboDetails[index][1]}</p><strong>{value}K</strong></div>)}</div>
      <p className="price-note">Giá tham khảo theo menu GenZ Cinema bạn đang xem, đơn vị nghìn đồng. Giá và điều kiện áp dụng có thể khác theo chi nhánh, loại phòng và thời điểm. Liên hệ cơ sở để xác nhận trước khi đặt. Bảng giá này không áp dụng mặc định cho Music Box.</p>
    </section>
  );
}
