import api from '../../../api/client';

async function mutate(method, path, body, headers = {}) {
  const { data: csrf } = await api.get('/auth/csrf');
  const { data } = await api.request({
    method,
    url: path,
    data: body,
    headers: { ...headers, [csrf.headerName]: csrf.token },
  });
  return data;
}

const mrPath = (mrCode, resource) => `/public/mr/${encodeURIComponent(mrCode)}/${resource}`;
const sessionHeader = (token) => ({ 'X-GCMB-QR-SESSION': token });

export const foodOrderApi = {
  openRoomSession: (mrCode) => mutate('post', mrPath(mrCode, 'session'), {}),
  roomMenu: async (mrCode, token) => (await api.get(mrPath(mrCode, 'menu'), { headers: sessionHeader(token) })).data,
  roomOrders: async (mrCode, token) => (await api.get(mrPath(mrCode, 'orders'), { headers: sessionHeader(token) })).data,
  submitRoomOrder: (mrCode, token, body) => mutate('post', mrPath(mrCode, 'orders'), body, sessionHeader(token)),
  staffOrders: async (status = 'ALL') => (await api.get('/reception/orders', { params: { status } })).data,
  activeBookings: async () => (await api.get('/reception/orders/active-bookings')).data,
  servingEmployees: async () => (await api.get('/reception/orders/serving-employees')).data,
  staffMenu: async () => (await api.get('/reception/orders/menu')).data,
  addStaffOrder: (body) => mutate('post', '/reception/orders', body),
  confirmRoomOrder: (orderId, body) => mutate('post', `/reception/orders/${orderId}/confirm`, body),
  rejectRoomOrder: (orderId) => mutate('post', `/reception/orders/${orderId}/reject`, {}),
  markServed: (orderId) => mutate('put', `/reception/orders/${orderId}/service-status`, { status: 'SERVED' }),
};
