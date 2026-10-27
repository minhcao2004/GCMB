/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Gọi API thông tin cá nhân bằng phiên đăng nhập và mã CSRF.
 */
import api from '../../../../api/client';

const path = '/account/profile';

export const accountProfileApi = {
  async view() {
    const { data } = await api.get(path);
    return data;
  },
  async update(body) {
    const { data: csrf } = await api.get('/auth/csrf');
    const { data } = await api.post(path, body, {
      headers: { [csrf.headerName]: csrf.token },
    });
    return data;
  },
};
