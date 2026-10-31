/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-127/BR-125, API theo dõi yêu cầu hóa đơn ngoài GCMB.
 */
import api from '../../../api/client';
const base = (accounting) => `/${accounting ? 'accounting' : 'reception'}/tax-invoices`;
async function write(path, body, method = 'post') {
  const { data: csrf } = await api.get('/auth/csrf');
  return (await api[method](path, body, { headers: { [csrf.headerName]: csrf.token } })).data;
}
export const taxInvoiceApi = {
  list: async (accounting) => (await api.get(base(accounting))).data,
  detail: async (id, accounting) => (await api.get(`${base(accounting)}/${id}`)).data,
  create: (id, body) => write(`${base(false)}/${id}`, body),
  update: (id, body) => write(`${base(true)}/${id}`, body, 'put'),
  unread: async () => (await api.get(`${base(true)}/unread`)).data,
  read: (id) => write(`${base(true)}/${id}/read`, {}),
};
export const invoiceStatuses = {
  REQUESTED: 'Đã gửi kế toán',
  PREPARING: 'Đang chuẩn bị',
  ISSUED: 'Đã phát hành bên ngoài',
  DELIVERED: 'Đã giao khách',
  DELIVERY_FAILED: 'Giao chưa thành công',
};
export const accountantInvoiceStatuses = { ...invoiceStatuses, REQUESTED: 'Chờ tiếp nhận' };
export const deliveryResults = {
  DELIVERED: 'Đã giao thành công',
  EMAIL: 'Đã gửi qua email',
  DIRECT: 'Đã giao trực tiếp cho khách',
  MESSAGE: 'Đã gửi qua Zalo / kênh liên hệ của khách',
  INVALID_CONTACT: 'Thông tin liên hệ không đúng',
  UNREACHABLE: 'Chưa liên hệ được với khách',
  SEND_FAILED: 'Gửi hóa đơn không thành công',
};
export const deliveryStatus = (value) =>
  ['DELIVERED', 'EMAIL', 'DIRECT', 'MESSAGE'].includes(value)
    ? 'DELIVERED'
    : ['INVALID_CONTACT', 'UNREACHABLE', 'SEND_FAILED'].includes(value)
      ? 'DELIVERY_FAILED'
      : null;
// Bản ghi nhập tự do trước đây vẫn đọc được; lựa chọn mới được kiểm tra ở backend.
export const deliveryLabel = (value) =>
  deliveryResults[value] || value || 'Chưa ghi nhận kết quả giao';
