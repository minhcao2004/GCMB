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
  stayOptions: async (id) => (await api.get(`/reception/bookings/${id}/stay-options`)).data,
  roomChangeQuote: async (id, params) => (await api.get(`/reception/bookings/${id}/room-change-quote`, { params })).data,
  extensionQuote: (id, body) => mutate('post', `/reception/bookings/${id}/extension-quote`, body),
  availability: async (params) => (await api.get('/reception/availability', { params })).data,
  editOptions: async (id, params) => (await api.get(`/reception/bookings/${id}/edit-options`, { params })).data,
  create: (body) => mutate('post', '/reception/bookings', body),
  update: (id, body) => mutate('patch', `/reception/bookings/${id}`, body),
  checkIn: (id) => mutate('post', `/reception/bookings/${id}/check-in`, {}),
  changeRoom: (id, body) => mutate('post', `/reception/bookings/${id}/change-room`, body),
  extendStay: (id, body) => mutate('post', `/reception/bookings/${id}/extend`, body),
  startHourlyMeter: (id) => mutate('post', `/reception/bookings/${id}/hourly-meter/start`, {}),
  stopHourlyMeter: (id) => mutate('post', `/reception/bookings/${id}/hourly-meter/stop`, {}),
  cancel: (id, body) => mutate('post', `/reception/bookings/${id}/cancel`, body),
  markNoShow: (id, body) => mutate('post', `/reception/bookings/${id}/no-show`, body),
};
