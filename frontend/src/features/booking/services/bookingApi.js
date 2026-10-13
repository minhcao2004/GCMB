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

// Tập hợp lời gọi API booking; quyết định nghiệp vụ và giá vẫn nằm ở backend.
export const bookingApi = {
  // Dữ liệu đầu vào và danh sách booking của chi nhánh hiện tại.
  options: async () => (await api.get('/reception/booking-options')).data,
  list: async (params) => (await api.get('/reception/bookings', { params })).data,
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
  update: (id, body) => mutate('patch', `/reception/bookings/${id}`, body),
  checkIn: (id) => mutate('post', `/reception/bookings/${id}/check-in`, {}),
  changeRoom: (id, body) => mutate('post', `/reception/bookings/${id}/change-room`, body),
  extendStay: (id, body) => mutate('post', `/reception/bookings/${id}/extend`, body),
  startHourlyMeter: (id) => mutate('post', `/reception/bookings/${id}/hourly-meter/start`, {}),
  stopHourlyMeter: (id) => mutate('post', `/reception/bookings/${id}/hourly-meter/stop`, {}),
  cancel: (id, body) => mutate('post', `/reception/bookings/${id}/cancel`, body),
  markNoShow: (id, body) => mutate('post', `/reception/bookings/${id}/no-show`, body),
};
