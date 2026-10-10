import api from '../../../api/client';

async function mutate(method, path, body) {
  const { data: csrf } = await api.get('/auth/csrf');
  const response = await api.request({
    method,
    url: path,
    data: body,
    headers: { [csrf.headerName]: csrf.token },
  });
  return response.data;
}

export const bookingApi = {
  options: async () => (await api.get('/reception/booking-options')).data,
  list: async (params) => (await api.get('/reception/bookings', { params })).data,
  detail: async (id) => (await api.get(`/reception/bookings/${id}`)).data,
  availability: async (params) => (await api.get('/reception/availability', { params })).data,
  create: (body) => mutate('post', '/reception/bookings', body),
  update: (id, body) => mutate('patch', `/reception/bookings/${id}`, body),
  cancel: (id, body) => mutate('post', `/reception/bookings/${id}/cancel`, body),
};
