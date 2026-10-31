import api from '../../../api/client';

export const depositApi = {
  list: async (params) => (await api.get('/reception/deposits', { params })).data,
  detail: async (bookingId) => (await api.get(`/reception/deposits/${bookingId}`)).data,
  refundRequests: async () => (await api.get('/accounting/deposits/refund-requests')).data,
  completeRefundRequest: async (requestId, externalReference, image) => {
    const { data: csrf } = await api.get('/auth/csrf');
    const form = new FormData();
    if (externalReference?.trim()) form.append('externalReference', externalReference.trim());
    if (image) form.append('image', image);
    return (await api.post(`/accounting/deposits/refund-requests/${requestId}/complete`, form, {
      headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'multipart/form-data' },
    })).data;
  },
  refundEvidence: async (requestId) => (await api.get(
    `/accounting/deposits/refund-requests/${requestId}/evidence`, { responseType: 'blob' },
  )).data,
  receptionRefundEvidence: async (requestId) => (await api.get(
    `/reception/deposits/refund-requests/${requestId}/evidence`, { responseType: 'blob' },
  )).data,
};
