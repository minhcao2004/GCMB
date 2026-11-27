// Chèn khoảng trắng vào số điện thoại để dễ đọc trên giao diện.
export const formatPhone = (phone) => phone.replace(/(\d{4})(\d{3})(\d{3})/, '$1 $2 $3');
export function phoneFormatError(value) {
  const phone = value?.trim();
  if (!phone) return '';
  if (phone.length > 30) return 'Số điện thoại chỉ được chứa tối đa 30 ký tự.';
  return /^\+?[0-9]+$/.test(phone) ? '' : 'Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.';
}
// Đổi loại chi nhánh thành nhãn hiển thị trên trang giới thiệu.
export const branchTypeName = (type) => type === 'both' ? 'CINEMA & MUSIC' : type === 'music' ? 'MUSIC / GENZ BOX' : 'CINEMA';

