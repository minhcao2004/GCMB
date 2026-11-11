
/** Author: HuyDV. UC-91..101: CSRF và phạm vi cơ sở cho API lương/chốt quỹ. */
import api from '../../../api/client';
import apiClient from '../../../api/client';
const legacyFinanceApi = {
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

const route = {
  HEAD_OFFICE_MANAGER: '/head-office/finance',
  ACCOUNTANT: '/accounting/finance',
  BRANCH_MANAGER: '/branch-management/finance',
};
const form = (values) => {
  const data = new FormData();
  Object.entries(values).forEach(([key, value]) => { if (value !== null && value !== undefined && value !== '') data.append(key, value); });
  return data;
};
async function mutate(method, url, body, multipart=false) {
  const { data: csrf } = await apiClient.get('/auth/csrf');
  const headers = { [csrf.headerName]: csrf.token };
  if (multipart) headers['Content-Type'] = undefined;
  return apiClient.request({ method, url, data: body, headers }).then(({ data }) => data);
}
export const financeApi = {
  ...legacyFinanceApi,
  load: (role) => apiClient.get(route[role]).then(({ data }) => data),
  sendFund: (values) => mutate('post','/head-office/finance/fund-transfers',values),
  uploadFundProof: (id, proof) => mutate('post',`/head-office/finance/fund-transfers/${id}/proof`,form({proof}),true),
  confirmFund: (id) => mutate('post',`/accounting/finance/fund-transfers/${id}/confirm`),
  createCategory: (value) => mutate('post','/accounting/finance/categories',value),
  updateCategory: (id, value) => mutate('put',`/accounting/finance/categories/${id}`,value),
  deleteCategory: (id) => mutate('delete',`/accounting/finance/categories/${id}`),
  createRequest: (values) => mutate('post','/branch-management/finance/requests',values),
  uploadRequestAttachment: (id, kind, file) => mutate('post',`/branch-management/finance/requests/${id}/attachments?kind=${kind}`,form({file}),true),
  review: (role, id, approve, note='') => mutate('post',`${route[role]}/requests/${id}/review`,{ approve, note }),
  pay: (id, reference, proof) => mutate('post',`/accounting/finance/requests/${id}/pay`,form({ reference, proof }),true),
};

