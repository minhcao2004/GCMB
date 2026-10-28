/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Gọi API danh sách tài khoản, dữ liệu lựa chọn và tạo tài khoản với mã bảo vệ CSRF.
 */

import api from '../../../../api/client';

// UC-04/05: phạm vi vẫn được kiểm tra lại tại server.
const path = (scope) => (scope ? '/branch-management/accounts' : '/head-office/accounts');
const headers = (scope) => (scope ? { 'X-GCMB-Branch-Id': scope } : {});
export const accountApi = {
  list: async (params, scope) =>
    (await api.get(path(scope), { params, headers: headers(scope) })).data,
  options: async (scope) =>
    (await api.get(`${path(scope)}/options`, { headers: headers(scope) })).data,
  create: async (body, scope) => {
    const { data: csrf } = await api.get('/auth/csrf');
    return (
      await api.post(path(scope), body, {
        headers: { ...headers(scope), [csrf.headerName]: csrf.token },
      })
    ).data;
  },
};
