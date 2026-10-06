import avengerImage from '../../../assets/images/avenger.jpg';
import barbieImage from '../../../assets/images/barbie.jpg';
import footballImage from '../../../assets/images/football.jpg';
import forestImage from '../../../assets/images/forest.jpg';
import gamingImage from '../../../assets/images/gaming.jpg';
import musicImage from '../../../assets/images/music.jpg';
import musicBannerImage from '../../../assets/images/music2.jpg';

export const images = { avenger: avengerImage, barbie: barbieImage, football: footballImage, forest: forestImage, gaming: gamingImage, music: musicImage, musicBanner: musicBannerImage };

export const rooms = [
  { name: 'Gaming Box', category: 'gaming', label: 'PLAY TOGETHER', image: gamingImage, caption: 'Vũ trụ gaming. Đồng đội là người thương.', description: 'Ánh sáng xanh tím, góc máy tính đôi và không gian xem phim trong cùng một căn phòng. Hẹn nhau một trận game, rồi cùng thả mình vào bộ phim yêu thích.', position: 'center' },
  { name: 'Barbie Room', category: 'cinema', label: 'PINK STATE OF MIND', image: barbieImage, caption: 'Một chút ngọt ngào cho buổi hẹn.', description: 'Tông hồng, ánh đèn neon và những chi tiết lấy cảm hứng từ Barbie tạo nên một góc hẹn thật riêng. Một lựa chọn cho những ai thích không gian ngọt ngào và những tấm ảnh nhiều màu sắc.', position: 'center 65%' },
  { name: 'Forest Box', category: 'cinema', label: 'SLOW DOWN', image: forestImage, caption: 'Đổi nhịp giữa một khoảng xanh.', description: 'Concept thiên nhiên mang đến một bối cảnh khác cho cuộc hẹn xem phim. Chọn một bộ phim nhẹ nhàng, ngồi xuống và dành thời gian cho người đi cùng.', position: 'center' },
  { name: 'Football Room', category: 'cinema', label: 'TEAM GOOD TIMES', image: footballImage, caption: 'Chất thể thao cho hội mê sân cỏ.', description: 'Những đường nét lấy cảm hứng từ sân bóng, sắc xanh và khu vực màn chiếu rộng tạo nên cá tính của Football Room. Một gợi ý cho buổi gặp của những người có chung niềm đam mê.', position: 'center 65%' },
  { name: 'Avenger Box', category: 'cinema', label: 'ENTER THE UNIVERSE', image: avengerImage, caption: 'Bước vào thế giới của những anh hùng.', description: 'Không gian theo chủ đề siêu anh hùng dành cho một cuộc hẹn nhiều cảm hứng. Tự chọn bộ phim hợp gu và để câu chuyện trên màn hình dẫn lối.', position: 'center' },
  { name: 'Music Box', category: 'music', label: 'SING YOUR HEART OUT', image: musicImage, caption: 'Gom hội bạn. Bật bài tủ.', description: 'Không gian âm nhạc với ghế sofa và những mảng trang trí nổi bật. Rủ hội bạn lên một buổi hẹn khác thường, cùng chọn playlist và hát những bài quen thuộc.', position: 'center 55%' },
];

export const branches = [
  { city: 'hn', type: 'cinema', name: 'Nguyễn Văn Lộc', address: 'LK13, Ngõ 2 Nguyễn Văn Lộc, P. Mộ Lao, Hà Đông, Hà Nội', phone: '0866521881' },
  { city: 'hn', type: 'cinema', name: 'Nguyễn Lương Bằng', address: 'Số 3, Ngõ 180 Nguyễn Lương Bằng, Quang Trung, Đống Đa, Hà Nội', phone: '0325186385' },
  { city: 'hn', type: 'cinema', name: 'Tân Khai', address: '130 Tân Khai, Vĩnh Hưng, Hai Bà Trưng, Hà Nội', phone: '0989838603' },
  { city: 'hn', type: 'cinema', name: 'Hoàng Ngân', address: '103 Hoàng Ngân, Nhân Chính, Thanh Xuân, Hà Nội', phone: '0823983881' },
  { city: 'hn', type: 'cinema', name: 'Hoa Bằng', address: '24 Hoa Bằng, Yên Hoà, Cầu Giấy, Hà Nội', phone: '0877155379' },
  { city: 'hn', type: 'cinema', name: 'Nhật Chiêu', address: '135 Nhật Chiêu, Nhật Tân, Tây Hồ, Hà Nội', phone: '0838408881' },
  { city: 'hn', type: 'both', name: 'Đống Đa', address: '86–88 Nguyễn Lương Bằng, Quang Trung, Hà Nội', phone: '0816018881' },
  { city: 'hn', type: 'cinema', name: 'Hoàng Công Chất', address: '462 Hoàng Công Chất, Cầu Diễn, Bắc Từ Liêm, Hà Nội', phone: '0846298881' },
  { city: 'hn', type: 'cinema', name: 'Triều Khúc', address: '06-N05 khu tái định cư Xóm Chùa, Triều Khúc, Thanh Liệt, Hà Nội', phone: '0823660705' },
  { city: 'hcm', type: 'cinema', name: 'Nguyễn Thái Bình', address: '419 Nguyễn Thái Bình, TP. Hồ Chí Minh', phone: '0932680419' },
  { city: 'hcm', type: 'cinema', name: 'Phan Xích Long', address: '320 Phan Xích Long, TP. Hồ Chí Minh', phone: '0903857320' },
  { city: 'hcm', type: 'both', name: 'Âu Cơ', address: '547 Âu Cơ, TP. Hồ Chí Minh', phone: '0793759597' },
  { city: 'hcm', type: 'cinema', name: 'Nguyễn Suý', address: '54 Nguyễn Suý, TP. Hồ Chí Minh', phone: '0796403054' },
  { city: 'hcm', type: 'both', name: 'Cách Mạng Tháng 8', address: '713 Cách Mạng Tháng Tám, TP. Hồ Chí Minh', phone: '0797397974' },
  { city: 'hn', type: 'music', name: 'Xuân Thủy', address: '51 Xuân Thủy, Cầu Giấy, Hà Nội', phone: '0823408881' },
  { city: 'hcm', type: 'music', name: 'Tân Bình', address: '412 Trường Chinh, Tân Bình, TP. Hồ Chí Minh', phone: '0982813121' },
  { city: 'hcm', type: 'music', name: 'Cửu Long', address: '50 Cửu Long, TP. Hồ Chí Minh', phone: '0964128112' },
  { city: 'hcm', type: 'music', name: 'Quận 10', address: '621 Cách Mạng Tháng Tám, TP. Hồ Chí Minh', phone: '0987359361' },
];

export const prices = {
  queen: { hour: 96, combos: [236, 276, 316], long: [296, 366, 416, 196, 296] },
  king: { hour: 106, combos: [246, 286, 336], long: [326, 396, 446, 246, 336] },
};

export const comboDetails = ['1 khô gà · 2 đồ uống tự chọn', '1 nem + 1 khoai · 2 đồ uống tự chọn', '1 khô gà + 1 khoai · 2 đồ uống tự chọn'];
export const longComboDetails = [
  ['COMBO 4 GIỜ', 'Tùy chọn giờ xem phim'], ['COMBO 5 GIỜ', 'Tùy chọn giờ xem phim'], ['COMBO 6 GIỜ', 'Tùy chọn giờ xem phim'],
  ['NGÀY · 7H–12H', '5 giờ xem phim + 1 nước pha chế'], ['ĐÊM · 23H–7H', '8 giờ xem phim + 1 nước pha chế'],
];

