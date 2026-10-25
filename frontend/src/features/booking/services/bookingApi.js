import api from '../../../api/client';

// Lấy CSRF mới trước mỗi yêu cầu ghi và gửi cookie phiên kèm theo.
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

// Lấy đủ các trang khi cần đối chiếu mọi phòng đang được sử dụng.
async function listAll(params = {}) {
  const pageSize = 100;
  const first = (await api.get('/reception/bookings', { params: { ...params, page: 0, size: pageSize } })).data;
  if (first.totalPages <= 1) return first.items || [];
  const pages = await Promise.all(Array.from({ length: first.totalPages - 1 }, (_, index) =>
    api.get('/reception/bookings', { params: { ...params, page: index + 1, size: pageSize } })));
  return [...(first.items || []), ...pages.flatMap((response) => response.data.items || [])];
}

// Tập hợp lời gọi API booking; quyết định nghiệp vụ và giá vẫn nằm ở backend.
export const bookingApi = {
  // Dữ liệu đầu vào và danh sách booking của chi nhánh hiện tại.
  options: async () => (await api.get('/reception/booking-options')).data,
  rooms: async () => (await api.get('/reception/rooms')).data,
  list: async (params) => (await api.get('/reception/bookings', { params })).data,
  listAll,
  detail: async (id) => (await api.get(`/reception/bookings/${id}`)).data,
  // Thao tác trong thời gian khách đang ở phòng.
  stayOptions: async (id) => (await api.get(`/reception/bookings/${id}/stay-options`)).data,
  roomChangeQuote: async (id, params) => (await api.get(`/reception/bookings/${id}/room-change-quote`, { params })).data,
  extensionQuote: (id, body) => mutate('post', `/reception/bookings/${id}/extension-quote`, body),
  // Báo giá và phòng có thể chọn cho lúc tạo/sửa booking.
  availability: async (params) => (await api.get('/reception/availability', { params })).data,
  editOptions: async (id, params) => (await api.get(`/reception/bookings/${id}/edit-options`, { params })).data,
  // Mọi lệnh ghi đều đi qua mutate để gắn token CSRF mới.
  create: (body) => mutate('post', '/reception/bookings', body),
  planDeposit: (id) => mutate('post', `/reception/bookings/${id}/deposit-payment`, {}),
  depositPayment: async (id) => (await api.get(`/reception/bookings/${id}/deposit-payment`)).data,
  update: (id, body) => mutate('patch', `/reception/bookings/${id}`, body),
  checkIn: (id) => mutate('post', `/reception/bookings/${id}/check-in`, {}),
  changeRoom: (id, body) => mutate('post', `/reception/bookings/${id}/change-room`, body),
  extendStay: (id, body) => mutate('post', `/reception/bookings/${id}/extend`, body),
  startHourlyMeter: (id) => mutate('post', `/reception/bookings/${id}/hourly-meter/start`, {}),
  stopHourlyMeter: (id) => mutate('post', `/reception/bookings/${id}/hourly-meter/stop`, {}),
  cancel: (id, body) => mutate('post', `/reception/bookings/${id}/cancel`, body),
  markNoShow: (id, body) => mutate('post', `/reception/bookings/${id}/no-show`, body),
};
