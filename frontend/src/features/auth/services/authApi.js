/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Gọi API xác thực với mã CSRF và chuyển lỗi API thành thông báo cho form.
 */

import api from '../../../api/client';

// Không đưa bí mật vào URL, bộ nhớ trình duyệt hoặc log; token phiên ở cookie HttpOnly.
// Lấy token CSRF trước mỗi thao tác xác thực làm thay đổi dữ liệu.
async function post(path, body) {
  const { data: csrf } = await api.get('/auth/csrf');
  const { data } = await api.post(`/auth/${path}`, body, { headers: { [csrf.headerName]: csrf.token } });
  return data;
}
// Các lời gọi xác thực dùng cookie HttpOnly thay vì lưu token trong JavaScript.
export const authApi = {
  // Trả danh tính từ cookie phiên hiện tại.
  me: async () => (await api.get('/auth/me')).data,
  // Các thao tác ghi đều lấy CSRF mới qua post.
  login: (body) => post('login', body),
  sendCode: (email) => post('forgot-password', { email }),
  reset: (body) => post('reset-password', body),
  changePassword: (body) => post('change-password', body),
  logout: () => post('logout', {}),
};
// Đổi lỗi HTTP hoặc lỗi mạng thành thông báo trường biểu mẫu.
export function apiErrors(error) {
  if (!error.response) return { form: 'Không thể kết nối máy chủ hoặc yêu cầu đã quá thời gian chờ. Vui lòng kiểm tra mạng và thử lại.' };
  return error.response.data?.errors || { form: error.response.data?.message || 'Máy chủ chưa thể hoàn tất thao tác. Vui lòng thử lại.' };
}
