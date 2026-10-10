import api from '../../../api/client';

// Keep secrets out of URLs, browser storage and logs. Cookies are HttpOnly.
async function post(path, body) {
  const { data: csrf } = await api.get('/auth/csrf');
  const { data } = await api.post(`/auth/${path}`, body, { headers: { [csrf.headerName]: csrf.token } });
  return data;
}
export const authApi = {
  me: async () => (await api.get('/auth/me')).data,
  login: (body) => post('login', body),
  sendCode: (email) => post('forgot-password', { email }),
  reset: (body) => post('reset-password', body),
  logout: () => post('logout', {}),
};
export function apiErrors(error) {
  if (!error.response) return { form: 'Không thể kết nối máy chủ hoặc yêu cầu đã quá thời gian chờ. Vui lòng kiểm tra mạng và thử lại.' };
  return error.response.data?.errors || { form: error.response.data?.message || 'Máy chủ chưa thể hoàn tất thao tác. Vui lòng thử lại.' };
}
