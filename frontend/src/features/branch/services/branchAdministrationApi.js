/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: API cơ sở và xác thực CSRF cho các thay đổi dữ liệu.
 */
import api from '../../../api/client';

const base = '/head-office/branches';

async function write(method, path, data) {
  const { data: csrf } = await api.get('/auth/csrf');
  return (
    await api.request({
      method,
      url: `${base}${path}`,
      data,
      headers: { [csrf.headerName]: csrf.token },
    })
  ).data;
}

export const branchAdministrationApi = {
  list: async () => (await api.get(base)).data,
  managers: async () => (await api.get(`${base}/managers`)).data,
  staff: async (id) => (await api.get(`${base}/${id}/staff`)).data,
  history: async (id) => (await api.get(`${base}/${id}/history`)).data,
  blockers: async (id) => (await api.get(`${base}/${id}/blockers`)).data,
  create: (data) => write('post', '', data),
  update: (id, data) => write('put', `/${id}`, data),
  assign: (id, userId) => write('put', `/${id}/manager`, { userId }),
  deactivate: (id, data) => write('post', `/${id}/deactivate`, data),
  reactivate: (id) => write('post', `/${id}/reactivate`),
};

export function branchError(error) {
  return (
    error.response?.data?.detail ||
    error.response?.data?.message ||
    'Không thể xử lý yêu cầu. Vui lòng kiểm tra kết nối và thử lại.'
  );
}
