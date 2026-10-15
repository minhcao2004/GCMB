// Chèn khoảng trắng vào số điện thoại để dễ đọc trên giao diện.
export const formatPhone = (phone) => phone.replace(/(\d{4})(\d{3})(\d{3})/, '$1 $2 $3');
// Đổi loại chi nhánh thành nhãn hiển thị trên trang giới thiệu.
export const branchTypeName = (type) => type === 'both' ? 'CINEMA & MUSIC' : type === 'music' ? 'MUSIC / GENZ BOX' : 'CINEMA';

