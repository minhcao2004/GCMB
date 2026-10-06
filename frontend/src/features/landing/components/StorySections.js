import { images } from '../data/siteData';

export function IntroSection() {
  return <section id="about" className="section intro"><div className="intro-label reveal"><p className="eyebrow">01 / HELLO, GENZ!</p><span className="asterisk">✳</span></div><div className="intro-copy reveal"><h2>Ra ngoài một chút.<br /><span className="muted">Vui theo cách</span><br /><span className="underlined">của riêng mình.</span></h2><div className="intro-bottom"><p>Không cần một kế hoạch quá cầu kỳ. Rủ người thương xem phim, hẹn hội bạn chiến game hay thả mình vào một bài hát yêu thích. Ở GenZ, mỗi cuộc hẹn đều có một không gian để bắt đầu.</p><a href="#spaces" className="text-link">Tìm chiếc phòng hợp gu <span>↗</span></a></div></div></section>;
}

export function ExperienceSection({ onBook }) {
  const items = [['Hẹn hò có gu', 'Một góc riêng cho câu chuyện của hai người.'], ['Hội bạn lên kèo', 'Chọn phòng, chọn mood, còn lại cứ vui thôi.'], ['Hát hết mình', 'Đổi gió với không gian Music đầy màu sắc.']];
  return <section className="experience"><div className="experience-image"><img src={images.barbie} alt="Phòng Barbie với ánh đèn hồng, giường và góc máy tính đôi" loading="lazy" /><span className="photo-caption">BARBIE ROOM / A LITTLE PINK, A LOT OF LOVE</span></div><div className="experience-copy reveal"><p className="eyebrow">KHÔNG CHỈ LÀ XEM PHIM</p><h2>Phim có thể hết.<br /><span>Kỷ niệm thì còn.</span></h2><p>Để điện thoại xuống một chút. Cười cùng một phân cảnh, chia nhau món ăn vặt, hay đơn giản là ở cạnh nhau. Những điều bé xíu cũng đủ làm một ngày trở nên đáng nhớ.</p><div className="experience-items">{items.map(([title, description], index) => <div key={title}><span>0{index + 1}</span><p><strong>{title}</strong>{description}</p></div>)}</div><button className="text-link" onClick={onBook}>Lên lịch cho cuộc hẹn <span>↗</span></button></div></section>;
}

export function FaqSection() {
  const faqs = [
    ['Mình đặt phòng như thế nào?', 'Chọn “Đặt phòng”, chọn chi nhánh rồi gọi trực tiếp cho cơ sở hoặc nhắn fanpage GenZ Cinema. Nhân viên sẽ xác nhận giờ, concept còn trống và giá áp dụng trước khi chốt lịch.'],
    ['Queen, King và concept phòng khác nhau thế nào?', 'Queen và King là hai hạng trong menu giá. Barbie, Forest, Gaming, Football… là các concept không gian. Concept và thiết bị không giống nhau ở mọi cơ sở; hãy hỏi chi nhánh về hạng phòng tương ứng và tiện ích bạn muốn sử dụng.'],
    ['Combo đã có đồ ăn và nước uống chưa?', 'Combo 1, 2, 3 gồm 2 giờ xem phim, món ăn theo từng combo và 2 đồ uống tự chọn. Combo ngày và đêm gồm 1 nước pha chế theo menu. Các gói 4–6 giờ không ghi kèm đồ ăn, thức uống; bạn có thể hỏi thêm khi đặt.'],
    ['Có thể chọn phòng Music trong bảng giá Cinema không?', 'Music là mô hình riêng. Bạn hãy lọc “Music / GenZ Box” ở phần chi nhánh và gọi trực tiếp để được tư vấn phòng, thời lượng và giá phù hợp với nhóm.'],
  ];
  return <section className="section faq"><p className="eyebrow">05 / BẠN HỎI, GENZ TRẢ LỜI</p><h2>Một chút trước khi <span className="pink-text">lên kèo.</span></h2><div className="faq-list">{faqs.map(([question, answer]) => <details key={question}><summary>{question}<span>+</span></summary><p>{answer}</p></details>)}</div></section>;
}

export function FinalCta({ onBook }) {
  return <section className="final-cta"><p className="eyebrow">HẸN NHAU Ở GENZ.</p><h2>YOUR SPACE.<br /><span>YOUR VIBE.</span><span className="cta-star" aria-hidden="true">✳</span></h2><button className="button dark" onClick={onBook}>Lên kèo ngay <span>↗</span></button></section>;
}

