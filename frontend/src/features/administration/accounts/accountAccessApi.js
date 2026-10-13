/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Gọi API phân quyền, thu hồi truy cập và tra cứu nhật ký hệ thống.
 */
import api from '../../../api/client';

async function post(path, body) {
  const { data: csrf } = await api.get('/auth/csrf');
  const { data } = await api.post(path, body, {
    headers: { [csrf.headerName]: csrf.token },
  });
  return data;
}

export const accountAccessApi = {
  setRole(id, body) {
    return post(`/head-office/accounts/${id}/role`, body);
  },
  deactivate(id, reason) {
    return post(`/head-office/accounts/${id}/deactivate`, { reason });
  },
  async permissions(role) {
    const { data } = await api.get('/head-office/accounts/permissions', {
      params: { role },
    });
    return data;
  },
  async audit(params) {
    const { data } = await api.get('/head-office/audit-logs', { params });
    return data;
  },
  async actions() {
    const { data } = await api.get('/head-office/audit-logs/actions');
    return data;
  },
  async detail(id) {
    const { data } = await api.get(`/head-office/audit-logs/${id}`);
    return data;
  },
};

export const roleNames = {
  HEAD_OFFICE_MANAGER: 'Quản lý cấp cao (HOM)',
  BRANCH_MANAGER: 'Quản lý cơ sở (BM)',
  RECEPTIONIST: 'Lễ tân (REC)',
  ACCOUNTANT: 'Kế toán (ACC)',
};

export const actionNames = {
  UPDATE_ACCOUNT_INFORMATION: 'Cập nhật thông tin tài khoản',
  CREATE_USER_ACCOUNT: 'Tạo tài khoản',
  SET_ACCOUNT_ROLE: 'Thay đổi vai trò / phạm vi',
  DEACTIVATE_USER_ACCOUNT: 'Thu hồi truy cập',
};
