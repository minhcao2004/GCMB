/** Author: HuyDV. UC-91..101: CSRF và phạm vi cơ sở cho API lương/chốt quỹ. */
import api from '../../../api/client';
export const financeApi = {
  async get(path, params, branch) {
    return (await api.get(path, { params, headers: branch ? { 'X-GCMB-Branch-Id': branch } : {} }))
      .data;
  },
  async post(path, body, branch) {
    const { data: csrf } = await api.get('/auth/csrf');
    return (
      await api.post(path, body, {
        headers: {
          [csrf.headerName]: csrf.token,
          // HuyDV: để trình duyệt tạo multipart boundary khi gửi minh chứng chốt quỹ.
          ...(body instanceof FormData ? { 'Content-Type': null } : {}),
          ...(branch ? { 'X-GCMB-Branch-Id': branch } : {}),
        },
      })
    ).data;
  },
  async download(path, name, branch) {
    const response = await api.get(path, {
      responseType: 'blob',
      headers: branch ? { 'X-GCMB-Branch-Id': branch } : {},
    });
    const url = URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
export const vnd = (value) => `${Number(value || 0).toLocaleString('vi-VN')}đ`;
export const today = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
export const emptyMessage = 'Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.'; // MSG01.
