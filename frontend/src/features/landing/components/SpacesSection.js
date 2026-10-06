import { useMemo, useRef, useState } from 'react';
import { rooms } from '../data/siteData';

const filters = [['all', 'Tất cả'], ['cinema', 'Cinema'], ['gaming', 'Gaming'], ['music', 'Music']];

export default function SpacesSection({ onSelectRoom }) {
  const [filter, setFilter] = useState('all');
  const trackRef = useRef(null);
  const filteredRooms = useMemo(() => rooms.filter((room) => filter === 'all' || room.category === filter), [filter]);
  const changeFilter = (value) => { setFilter(value); trackRef.current?.scrollTo({ left: 0, behavior: 'smooth' }); };
  const moveRooms = (direction) => { const card = trackRef.current?.querySelector('.room-card'); if (card) trackRef.current.scrollBy({ left: direction * (card.getBoundingClientRect().width + 26), behavior: 'smooth' }); };
  return (
    <section id="spaces" className="section spaces">
      <div className="section-heading reveal"><div><p className="eyebrow">02 / PICK YOUR VIBE</p><h2>Mỗi căn phòng.<br /><span className="pink-text">Một cá tính.</span></h2></div><p>Từ ngọt ngào đến cực cháy.<br />Hôm nay bạn muốn là phiên bản nào?</p></div>
      <div className="filter-row"><div className="filters" role="group" aria-label="Lọc không gian">{filters.map(([value, label]) => <button key={value} className={filter === value ? 'active' : ''} onClick={() => changeFilter(value)} aria-pressed={filter === value}>{label}{value === 'all' && <small>06</small>}</button>)}</div><span className="room-counter">{String(filteredRooms.length).padStart(2, '0')} không gian · Vuốt để khám phá ↔</span></div>
      <div className="room-track" ref={trackRef} tabIndex="0" aria-label="Danh sách phòng" onKeyDown={(event) => { if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') { event.preventDefault(); moveRooms(event.key === 'ArrowRight' ? 1 : -1); } }}>
        {filteredRooms.map((room) => { const originalIndex = rooms.indexOf(room); return <article className="room-card" data-category={room.category} key={room.name}><button className="room-image-button" onClick={() => onSelectRoom(room)} aria-label={`Khám phá ${room.name}`}><img src={room.image} alt={`Không gian ${room.name} tại GenZ`} style={{ objectPosition: room.position }} loading="lazy" /><span className="room-badge">{room.label}</span><span className="room-arrow" aria-hidden="true">↗</span></button><div className="room-heading"><h3>{room.name}</h3><span>0{originalIndex + 1} / 06</span></div><p>{room.caption}</p></article>; })}
      </div>
      <div className="room-footer"><span>Concept & tiện ích thay đổi theo từng chi nhánh.</span><div className="slider-actions"><button className="circle-button" aria-label="Xem phòng trước" onClick={() => moveRooms(-1)}>←</button><button className="circle-button" aria-label="Xem phòng tiếp theo" onClick={() => moveRooms(1)}>→</button></div></div>
    </section>
  );
}

