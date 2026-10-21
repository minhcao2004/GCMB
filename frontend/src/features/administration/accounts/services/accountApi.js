/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Gọi API danh sách tài khoản, dữ liệu lựa chọn và tạo tài khoản với mã bảo vệ CSRF.
 */

import api from '../../../../api/client';

const path = '/head-office/accounts';
export const accountApi = {
  list: async (params) => (await api.get(path, { params })).data,
  options: async () => (await api.get(`${path}/options`)).data,
  create: async (body) => {
    const { data: csrf } = await api.get('/auth/csrf');
    return (await api.post(path, body, { headers: { [csrf.headerName]: csrf.token } })).data;
  },
};
