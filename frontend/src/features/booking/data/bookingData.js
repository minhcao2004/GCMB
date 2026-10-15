import { images, prices, comboDetails } from '../../landing/data/siteData';

export const bookingRooms = [
  { id: 'P.501', tier: 'queen', name: 'Queen · P.501', theme: 'Forest Box', capacity: 4, image: images.forest, operational: true },
  { id: 'P.502', tier: 'queen', name: 'Queen · P.502', theme: 'Barbie Room', capacity: 4, image: images.barbie, operational: true },
  { id: 'P.301', tier: 'king', name: 'King · P.301', theme: 'Avenger Box', capacity: 6, image: images.avenger, operational: true },
  { id: 'P.402', tier: 'king', name: 'King · P.402', theme: 'Gaming Box', capacity: 6, image: images.gaming, operational: true },
  { id: 'P.403', tier: 'king', name: 'King · P.403', theme: 'Đang bảo trì', capacity: 6, image: images.music, operational: false },
];

export const bookingStatuses = {
  pending: 'Chờ check-in',
  checkedIn: 'Đã check-in',
  cancelled: 'Đã hủy',
  noShow: 'No-show',
};

export const tierLabels = { queen: 'Queen', king: 'King' };

// Định dạng ngày cho input và bộ lọc trên giao diện.
export function toLocalDateInput(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// Chỉ chấp nhận chuỗi ngày lịch hợp lệ trước khi gửi bộ lọc lên API.
export function isLocalDateInput(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00`);
  return !Number.isNaN(parsed.getTime()) && toLocalDateInput(parsed) === value;
}

// Ghép ngày và giờ nhập thành Date theo múi giờ máy.
export function fromDateAndTime(date, time) {
  if (!date || !time) return null;
  const value = new Date(`${date}T${time}:00`);
  return Number.isNaN(value.getTime()) ? null : value;
}

// Định dạng số tiền theo đồng Việt Nam.
export function formatVnd(value) {
  return `${new Intl.NumberFormat('vi-VN').format(Number(value) || 0)}đ`;
}

export function formatMoneyInput(value) {
  const digits = String(value ?? '').replace(/\D/g, '');
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

// Định dạng ngày cho giao diện.
export function formatDate(value) {
  if (!value) return '—';
  const [year, month, day] = value.split('-');
  return `${day}/${month}/${year}`;
}

// Tìm phòng tương ứng trong dữ liệu minh họa.
export function roomForBooking(booking) {
  return bookingRooms.find((room) => room.id === booking?.roomId) || null;
}

// Ước tính giá trên dữ liệu minh họa; giá chốt của API do backend tính.
export function calculateBookingPrice({ tier, pricingMode, comboIndex, startDate, startTime, endDate, endTime }) {
  const tierPrices = prices[tier];
  if (!tierPrices) return 0;
  if (pricingMode === 'combo') return Number(tierPrices.combos[Number(comboIndex)] || 0) * 1000;

  const start = fromDateAndTime(startDate, startTime);
  const end = fromDateAndTime(endDate, endTime);
  if (!start || !end || end <= start) return 0;
  const hours = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / (60 * 60 * 1000)));
  return hours * Number(tierPrices.hour) * 1000;
}

// Kiểm tra giao nhau giữa các lượt đặt trong dữ liệu minh họa.
export function hasRoomConflict(bookings, candidate, excludeId) {
  const candidateStart = fromDateAndTime(candidate.startDate, candidate.startTime);
  const candidateEnd = fromDateAndTime(candidate.endDate, candidate.endTime);
  if (!candidateStart || !candidateEnd || candidateEnd <= candidateStart || !candidate.roomId) return false;

  return bookings.some((booking) => {
    if (booking.id === excludeId || booking.roomId !== candidate.roomId) return false;
    if (!['pending', 'checkedIn'].includes(booking.status)) return false;
    const existingStart = fromDateAndTime(booking.startDate, booking.startTime);
    const existingEnd = fromDateAndTime(booking.endDate, booking.endTime);
    if (!existingStart || !existingEnd) return false;
    return candidateStart < existingEnd && existingStart < candidateEnd;
  });
}

// Tạo một booking mẫu quanh ngày hiện tại cho giao diện minh họa.
function bookingAt({ id, offsetMinutes, durationMinutes, roomId, customerName, phone, guestCount, status, depositAmount, source, pricingMode = 'hourly', comboIndex = 0, priceAmount, depositOutcome = '' }) {
  const start = new Date(Date.now() + offsetMinutes * 60 * 1000);
  const end = new Date(start.getTime() + durationMinutes * 60 * 1000);
  const startTime = `${String(start.getHours()).padStart(2, '0')}:${String(start.getMinutes()).padStart(2, '0')}`;
  const endTime = `${String(end.getHours()).padStart(2, '0')}:${String(end.getMinutes()).padStart(2, '0')}`;
  const room = bookingRooms.find((item) => item.id === roomId);
  const computedPrice = calculateBookingPrice({
    tier: room?.tier,
    pricingMode,
    comboIndex,
    startDate: toLocalDateInput(start),
    startTime,
    endDate: toLocalDateInput(end),
    endTime,
  });

  return {
    id,
    customerName,
    phone,
    roomId,
    guestCount,
    startDate: toLocalDateInput(start),
    startTime,
    endDate: toLocalDateInput(end),
    endTime,
    pricingMode,
    comboIndex,
    priceAmount: priceAmount ?? computedPrice,
    priceLabel: pricingMode === 'combo' ? `Combo ${room?.tier === 'king' ? 'King' : 'Queen'} ${Number(comboIndex) + 1}` : 'Theo giờ',
    status,
    depositAmount,
    depositStatus: depositAmount > 0 ? 'paid' : 'unpaid',
    depositOutcome,
    source,
    cancellationReason: '',
  };
}

// Sinh danh sách booking mẫu; không ghi vào database thật.
export function createDemoBookings() {
  return [
    bookingAt({ id: 'BK-26001', offsetMinutes: 75, durationMinutes: 120, roomId: 'P.501', customerName: 'Nguyễn Minh Anh', phone: '0912 345 678', guestCount: 4, status: 'pending', depositAmount: 150000, source: 'Đặt trước', pricingMode: 'combo', comboIndex: 0 }),
    bookingAt({ id: 'BK-26002', offsetMinutes: 210, durationMinutes: 180, roomId: 'P.301', customerName: 'Trần Hoàng Nam', phone: '0903 456 789', guestCount: 5, status: 'pending', depositAmount: 0, source: 'Đặt trước' }),
    bookingAt({ id: 'BK-26003', offsetMinutes: -45, durationMinutes: 150, roomId: 'P.502', customerName: 'Lê Thu Trang', phone: '0987 654 321', guestCount: 2, status: 'checkedIn', depositAmount: 120000, source: 'Khách đến trực tiếp' }),
    bookingAt({ id: 'BK-26004', offsetMinutes: 300, durationMinutes: 120, roomId: 'P.402', customerName: 'Phạm Gia Bảo', phone: '0866 521 881', guestCount: 3, status: 'cancelled', depositAmount: 100000, source: 'Đặt trước', depositOutcome: 'eligible_refund' }),
    bookingAt({ id: 'BK-26005', offsetMinutes: -180, durationMinutes: 120, roomId: 'P.301', customerName: 'Vũ Khánh Linh', phone: '0325 186 385', guestCount: 4, status: 'noShow', depositAmount: 100000, source: 'Đặt trước', depositOutcome: 'forfeited' }),
    bookingAt({ id: 'BK-26006', offsetMinutes: 1500, durationMinutes: 240, roomId: 'P.502', customerName: 'Đỗ Hải Yến', phone: '0823 983 881', guestCount: 3, status: 'pending', depositAmount: 0, source: 'Đặt trước', pricingMode: 'combo', comboIndex: 1 }),
  ];
}

export const comboLabels = comboDetails;
