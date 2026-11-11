const branches = [
  { id: 'hbt', name: 'Hà Nội · Hai Bà Trưng', code: 'HN-HBT', rooms: 42, scale: 1.12, occupancy: 0.76, expenseRate: 0.39, status: 'ACTIVE', managers: 2, incidents: 1 },
  { id: 'caugiay', name: 'Hà Nội · Cầu Giấy', code: 'HN-CG', rooms: 36, scale: 0.94, occupancy: 0.72, expenseRate: 0.42, status: 'ACTIVE', managers: 1, incidents: 3 },
  { id: 'hoabang', name: 'Hà Nội · Hoa Bằng', code: 'HN-HB', rooms: 32, scale: 0.83, occupancy: 0.67, expenseRate: 0.44, status: 'ACTIVE', managers: 2, incidents: 0 },
  { id: 'danang', name: 'Đà Nẵng · Hải Châu', code: 'DN-HC', rooms: 38, scale: 1.01, occupancy: 0.79, expenseRate: 0.4, status: 'ACTIVE', managers: 3, incidents: 4 },
  { id: 'danang-sontra', name: 'Đà Nẵng · Sơn Trà', code: 'DN-ST', rooms: 28, scale: 0.76, occupancy: 0.64, expenseRate: 0.45, status: 'ACTIVE', managers: 1, incidents: 1 },
  { id: 'hcm-q1', name: 'TP.HCM · Quận 1', code: 'HCM-Q1', rooms: 46, scale: 1.26, occupancy: 0.82, expenseRate: 0.38, status: 'ACTIVE', managers: 2, incidents: 2 },
  { id: 'hcm-tb', name: 'TP.HCM · Tân Bình', code: 'HCM-TB', rooms: 34, scale: 0.89, occupancy: 0.7, expenseRate: 0.43, status: 'ACTIVE', managers: 2, incidents: 0 },
  { id: 'cantho', name: 'Cần Thơ · Ninh Kiều', code: 'CT-NK', rooms: 30, scale: 0.72, occupancy: 0.59, expenseRate: 0.46, status: 'SUSPENDED', managers: 0, incidents: 5 },
];

const approvals = [
  { id: 'EXP-261112-041', branchId: 'hbt', category: 'Sửa chữa hệ thống điều hòa', amount: 8_450_000, submittedDaysAgo: 0 },
  { id: 'EXP-261111-028', branchId: 'caugiay', category: 'Thay mới thiết bị âm thanh', amount: 6_200_000, submittedDaysAgo: 1 },
  { id: 'EXP-261110-019', branchId: 'danang', category: 'Bảo trì phòng chiếu', amount: 4_850_000, submittedDaysAgo: 2 },
  { id: 'EXP-261109-012', branchId: 'hcm-q1', category: 'Mua bổ sung vật tư vận hành', amount: 3_650_000, submittedDaysAgo: 3 },
];

const dayKey = (date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const addDays = (date, amount) => {
  const next = new Date(date);
  next.setDate(next.getDate() + amount);
  return next;
};
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

function dailyRecords(now) {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Array.from({ length: 70 }, (_, index) => {
    const date = addDays(today, index - 69);
    const dayNumber = date.getDate();
    const weekdayFactor = [0.94, 0.88, 0.91, 0.98, 1.08, 1.24, 1.18][date.getDay()];
    const monthFactor = 0.94 + ((dayNumber * 7 + date.getMonth() * 3) % 13) / 100;
    const branchesForDate = branches.map((branch, branchIndex) => {
      const variation = 0.88 + ((dayNumber * 11 + branchIndex * 17) % 25) / 100;
      const base = 4_700_000 * branch.scale * weekdayFactor * monthFactor * variation;
      const roomRevenue = Math.round(base * 0.72 / 1000) * 1000;
      const fnbRevenue = Math.round(base * 0.22 / 1000) * 1000;
      const overtimeRevenue = Math.round(base * 0.06 / 1000) * 1000;
      const revenue = roomRevenue + fnbRevenue + overtimeRevenue;
      const bookings = Math.max(2, Math.round(11 * branch.scale * weekdayFactor * variation));
      const occupancy = clamp(branch.occupancy + (variation - 1) * 0.38 + (weekdayFactor - 1) * 0.14, 0.31, 0.97);
      const cost = Math.round(revenue * branch.expenseRate / 1000) * 1000;
      return { ...branch, roomRevenue, fnbRevenue, overtimeRevenue, revenue, cost, bookings, occupancy };
    });
    return { date: dayKey(date), branches: branchesForDate };
  });
}

function rangeFor(period, now) {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (period === 'today') return { from: today, to: today, previousFrom: addDays(today, -1), previousTo: addDays(today, -1) };
  if (period === 'week') return { from: addDays(today, -6), to: today, previousFrom: addDays(today, -13), previousTo: addDays(today, -7) };
  const from = new Date(today.getFullYear(), today.getMonth(), 1);
  const previousTo = addDays(from, -1);
  return { from, to: today, previousFrom: new Date(previousTo.getFullYear(), previousTo.getMonth(), 1), previousTo };
}

const inRange = (record, from, to) => record.date >= dayKey(from) && record.date <= dayKey(to);

function summarize(records, from, to) {
  const selected = records.filter((record) => inRange(record, from, to));
  const byBranch = branches.map((branch) => {
    const values = selected.flatMap((record) => record.branches.filter((row) => row.id === branch.id));
    const revenue = values.reduce((sum, row) => sum + row.revenue, 0);
    const roomRevenue = values.reduce((sum, row) => sum + row.roomRevenue, 0);
    const fnbRevenue = values.reduce((sum, row) => sum + row.fnbRevenue, 0);
    const overtimeRevenue = values.reduce((sum, row) => sum + row.overtimeRevenue, 0);
    const cost = values.reduce((sum, row) => sum + row.cost, 0);
    const bookings = values.reduce((sum, row) => sum + row.bookings, 0);
    const occupancy = values.length ? values.reduce((sum, row) => sum + row.occupancy, 0) / values.length : 0;
    return { ...branch, revenue, roomRevenue, fnbRevenue, overtimeRevenue, cost, bookings, occupancy, profit: revenue - cost };
  });
  const revenue = byBranch.reduce((sum, row) => sum + row.revenue, 0);
  const cost = byBranch.reduce((sum, row) => sum + row.cost, 0);
  const rooms = byBranch.reduce((sum, row) => sum + row.rooms, 0);
  const occupancy = rooms ? byBranch.reduce((sum, row) => sum + row.occupancy * row.rooms, 0) / rooms : 0;
  return {
    branches: byBranch,
    revenue,
    cost,
    bookings: byBranch.reduce((sum, row) => sum + row.bookings, 0),
    occupancy,
    activeBranches: branches.filter((branch) => branch.status === 'ACTIVE').length,
    suspendedBranches: branches.filter((branch) => branch.status === 'SUSPENDED').length,
    roomRevenue: byBranch.reduce((sum, row) => sum + row.roomRevenue, 0),
    fnbRevenue: byBranch.reduce((sum, row) => sum + row.fnbRevenue, 0),
    overtimeRevenue: byBranch.reduce((sum, row) => sum + row.overtimeRevenue, 0),
  };
}

export function getExecutiveDashboardData(period, now = new Date()) {
  const records = dailyRecords(now);
  const range = rangeFor(period, now);
  const current = summarize(records, range.from, range.to);
  const previous = summarize(records, range.previousFrom, range.previousTo);
  const days = records.filter((record) => inRange(record, range.from, range.to));
  const revenueByDay = days.map((record) => ({
    date: record.date,
    label: new Intl.DateTimeFormat('vi-VN', { day: 'numeric', month: 'numeric' }).format(new Date(`${record.date}T12:00:00`)),
    roomRevenue: record.branches.reduce((sum, row) => sum + row.roomRevenue, 0),
    fnbRevenue: record.branches.reduce((sum, row) => sum + row.fnbRevenue, 0),
  }));
  const branchBars = current.branches.map((branch) => ({ ...branch, shortName: branch.code }));
  const warningItems = [
    ...branches.filter((branch) => branch.managers < 2).map((branch) => ({ id: `manager-${branch.id}`, branch: branch.name, kind: 'Thiếu quản lý', detail: branch.managers === 1 ? 'Mới có 1/2 quản lý được phân công.' : 'Chưa có quản lý được phân công.', level: branch.status === 'SUSPENDED' ? 'critical' : 'warning' })),
    ...branches.filter((branch) => branch.status === 'SUSPENDED').map((branch) => ({ id: `status-${branch.id}`, branch: branch.name, kind: 'Cơ sở tạm ngừng', detail: 'Cần kiểm tra trạng thái hoạt động của cơ sở.', level: 'critical' })),
    ...branches.filter((branch) => branch.incidents >= 3).map((branch) => ({ id: `equipment-${branch.id}`, branch: branch.name, kind: 'Sự cố thiết bị', detail: `${branch.incidents} sự cố thiết bị đang cần theo dõi.`, level: 'warning' })),
  ];

  return {
    kpis: {
      revenue: current.revenue,
      revenueChange: previous.revenue ? (current.revenue - previous.revenue) / previous.revenue * 100 : 0,
      bookings: current.bookings,
      occupancy: current.occupancy,
      cost: current.cost,
      grossProfit: current.revenue - current.cost,
      activeBranches: current.activeBranches,
      suspendedBranches: current.suspendedBranches,
      branchTotal: branches.length,
    },
    revenueByBranch: branchBars,
    revenueByDay,
    revenueMix: [
      { name: 'Tiền phòng', value: current.roomRevenue, color: '#6a3ee8' },
      { name: 'F&B', value: current.fnbRevenue, color: '#168b87' },
      { name: 'Phụ phí quá giờ', value: current.overtimeRevenue, color: '#d28b2d' },
    ],
    ranking: [...current.branches].sort((a, b) => b.revenue - a.revenue),
    approvals: approvals.map((item) => ({ ...item, branch: branches.find((branch) => branch.id === item.branchId)?.name || '' })),
    warnings: warningItems,
  };
}

export const executivePeriods = [
  { id: 'today', label: 'Hôm nay' },
  { id: 'week', label: '7 ngày' },
  { id: 'month', label: 'Tháng này' },
];
