const makeSeries = (count, offset = 0) => Array.from({ length: count }, (_, index) => ({
  label: count === 7 ? ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'][index] : `${index + 1}`,
  value: Math.round((18 + ((index * 17 + offset * 11) % 31) + (index > count * 0.62 ? 12 : 0)) * 1000000),
}));

const base = {
  kpis: { todayRevenue: 18_450_000, bookings: 27, occupied: 32, roomTotal: 46, fnbRevenue: 4_280_000, lowStock: 3, staffPresent: 14, staffTotal: 17 },
  rooms: { vacant: 9, booked: 3, occupied: 32, maintenance: 2 },
  revenue: { 7: makeSeries(7), 30: makeSeries(30, 2) },
  peakHours: [
    [1, 1, 1, 2, 2, 2, 1], [1, 1, 1, 2, 2, 2, 1], [1, 1, 2, 2, 2, 2, 1],
    [1, 1, 2, 2, 3, 3, 2], [2, 2, 3, 3, 4, 4, 3], [2, 2, 3, 4, 4, 4, 3],
    [2, 3, 4, 4, 4, 4, 3], [2, 3, 4, 4, 4, 3, 3], [1, 2, 3, 3, 3, 3, 2],
    [1, 1, 2, 2, 2, 2, 1], [1, 1, 1, 2, 2, 2, 1], [1, 1, 1, 1, 2, 2, 1],
  ],
  topItems: [
    ['Trà đào cam sả', 42], ['Khoai tây chiên', 36], ['Cà phê sữa đá', 31], ['Gà viên chiên', 28], ['Mì trộn bò', 24],
    ['Nước suối', 22], ['Bắp rang bơ', 19], ['Trà tắc', 17], ['Xúc xích nướng', 14], ['Pepsi', 12],
  ],
  roomTypes: [
    ['Standard', 48, 21_600_000, 92], ['Deluxe', 36, 25_200_000, 118], ['VIP', 18, 19_800_000, 145], ['Couple', 22, 17_600_000, 106],
  ],
  inventory: [
    ['Ly giấy 500 ml', 18, 40, 'Cần nhập'], ['Trà đào đóng chai', 6, 12, 'Cần nhập'], ['Khăn giấy hộp', 9, 15, 'Cần nhập'],
  ],
  incidents: [
    ['EQ-024', 'Điều hòa', 'Phòng 203', 'Đang sửa', '12:10'], ['EQ-019', 'Máy chiếu', 'Phòng 108', 'Đang mở', '10:35'], ['EQ-031', 'Loa', 'Phòng 305', 'Đang sửa', '09:20'],
  ],
  shifts: [
    ['Hôm nay', '07:00–15:00', 'Nguyễn Minh Anh', 'Đã chấm công'], ['Hôm nay', '07:00–15:00', 'Trần Hoàng Nam', 'Chưa chấm công'],
    ['Hôm nay', '15:00–23:00', 'Lê Thu Hà', 'Chưa chấm công'], ['Ngày mai', '07:00–15:00', 'Phạm Gia Bảo', 'Chưa chấm công'],
    ['Ngày mai', '15:00–23:00', 'Võ Ngọc Linh', 'Chưa chấm công'],
  ],
  tasks: [
    ['Chi phí chờ gửi', '2 khoản · 3.450.000 ₫', 'Chi phí & đề nghị'], ['Chốt quỹ cuối ngày', 'Chưa thực hiện', 'Chốt quỹ'],
    ['Booking sắp đến', '3 booking trong 60 phút tới', 'Phòng'],
  ],
};

// Dữ liệu minh họa được tạo ổn định theo mã cơ sở, kể cả cơ sở mới được phân công.
export default function getBranchDashboardMock(branchId) {
  const seed = String(branchId || 'default').split('').reduce((value, char) => (value * 31 + char.charCodeAt(0)) >>> 0, 7);
  const offset = seed % 97 + 1;
  const occupied = Math.max(10, Math.min(base.kpis.roomTotal - 5, base.kpis.occupied + (offset % 9) - 4));
  const vacant = base.rooms.vacant - (occupied - base.rooms.occupied);
  return {
    ...base,
    kpis: { ...base.kpis, todayRevenue: base.kpis.todayRevenue + offset * 75_000, bookings: base.kpis.bookings + offset % 25, occupied, fnbRevenue: base.kpis.fnbRevenue + offset * 32_000, lowStock: 1 + offset % 5, staffPresent: base.kpis.staffPresent + offset % 3 },
    rooms: { ...base.rooms, occupied, vacant },
    revenue: { 7: makeSeries(7, offset), 30: makeSeries(30, offset + 2) },
    peakHours: base.peakHours.map((row, hour) => row.map((level, day) => 1 + ((level + offset + hour + day) % 4))),
    topItems: base.topItems.map(([name, count], index) => [name, count + ((offset + index) % 6)]),
    roomTypes: base.roomTypes.map(([name, count, revenue, minutes], index) => [name, count + offset % 5, revenue + offset * (index + 1) * 16_500, minutes + (offset % 3) * 5]),
    inventory: base.inventory.map(([name, stock, minimum, state], index) => [name, Math.max(1, stock - offset % (index + 3)), minimum, state]),
    incidents: base.incidents.map(([id, equipment, place, state, time], index) => [id, equipment, place.replace(/\d+/, String(120 + offset * 7 + index)), state, time]),
  };
}
