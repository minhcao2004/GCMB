/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: API checkout và thanh toán; lấy CSRF trước thao tác ghi.
 */
import api from '../../../api/client';

async function post(path, data) {
  const { data: csrf } = await api.get('/auth/csrf');
  return (
    await api.post(`/reception${path}`, data, {
      headers: { [csrf.headerName]: csrf.token },
    })
  ).data;
}

export const checkoutApi = {
  list: async () => (await api.get('/reception/bills')).data,
  preview: async (id) => (await api.get(`/reception/bookings/${id}/checkout`)).data,
  issue: (id, data) => post(`/bookings/${id}/checkout`, data),
  bill: async (id) => (await api.get(`/reception/bills/${id}`)).data,
  plan: (id, data) => post(`/bills/${id}/payment-plan`, data),
  complete: (id) => post(`/bills/${id}/complete`, {}),
  renew: (id) => post(`/bills/${id}/renew-qr`, {}),
  changeMethod: (id) => post(`/bills/${id}/change-payment-method`, {}),
};
