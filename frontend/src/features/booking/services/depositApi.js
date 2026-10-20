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

export const depositApi = {
  list: async (params) => (await api.get('/reception/deposits', { params })).data,
  detail: async (bookingId) => (await api.get(`/reception/deposits/${bookingId}`)).data,
  eligibleRefundBookings: async () => (await api.get('/reception/deposits/eligible-refund-bookings')).data,
  createRefundRequest: (body) => mutate('post', '/reception/deposits/refund-requests', body),
  refundRequests: async () => (await api.get('/accounting/deposits/refund-requests')).data,
  completeRefundRequest: (requestId, body) => mutate('post', `/accounting/deposits/refund-requests/${requestId}/complete`, body),
};
