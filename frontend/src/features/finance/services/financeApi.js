import apiClient from '../../../api/client';

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
