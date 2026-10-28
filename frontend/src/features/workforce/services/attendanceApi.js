/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-87..89, API công thực tế và định dạng ngày giờ theo múi giờ cơ sở.
 */
import api from '../../../api/client';

const base = '/branch-management/attendance';
const config = (scope) => ({ headers: { 'X-GCMB-Branch-Id': scope } });

async function write(path, body, scope) {
  const { data: csrf } = await api.get('/auth/csrf');
  return (
    await api.post(path, body, {
      headers: { ...config(scope).headers, [csrf.headerName]: csrf.token },
    })
  ).data;
}

export const attendanceApi = {
  daily: async (date, scope) => (await api.get(base, { ...config(scope), params: { date } })).data,
  monthly: async (month, scope) =>
    (await api.get(`${base}/month`, { ...config(scope), params: { month } })).data,
  record: (id, body, scope) => write(`${base}/${id}/record`, body, scope),
  close: (month, body, scope) => write(`${base}/month/${month}/close`, body, scope),
};

export const hoursLabel = (minutes = 0) => `${Math.floor(minutes / 60)} giờ ${minutes % 60} phút`;

export function localInput(value, timezone = 'Asia/Ho_Chi_Minh') {
  if (!value) return '';
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', {
      timeZone: timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date(value))
      .map((p) => [p.type, p.value])
  );
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

export function dateTimeLabel(value, timezone) {
  const local = localInput(value, timezone);
  if (!local) return '—';
  const [date, time] = local.split('T');
  return `${time} · ${date.split('-').reverse().join('/')}`;
}

// UC-87: bản ghi mới gợi ý giờ ca; bản ghi cũ giữ giờ thực tế đã lưu, kể cả giá trị còn thiếu.
export function initialAttendanceTime(row, field, timezone) {
  if (!row) return '';
  const planned = field === 'actualIn' ? row.startsAt : row.endsAt;
  return localInput(row.attendanceId ? row[field] : planned, timezone);
}

// BR-193/204: ô nhập chỉ có HH:mm nhưng payload vẫn chứa đủ ngày giờ.
// Chọn ngày gần mốc gốc nhất để 00:00/23:xx qua đêm không bị tính thêm một ngày công.
export function attendanceClockTime(row, field, clock, timezone) {
  if (!row || !/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(clock)) return '';
  const planned = field === 'actualIn' ? row.startsAt : row.endsAt;
  const anchor = localInput(row[field] || planned, timezone);
  if (!anchor) return '';
  const reference = Date.parse(`${anchor}Z`);
  const sameDay = Date.parse(`${anchor.slice(0, 10)}T${clock}Z`);
  let closest = sameDay;
  for (const offset of [-86400000, 86400000]) {
    const candidate = sameDay + offset;
    if (Math.abs(candidate - reference) < Math.abs(closest - reference)) closest = candidate;
  }
  return new Date(closest).toISOString().slice(0, 16);
}

// BR-193: chỉ là bản xem trước, backend tự tính lại bằng múi giờ của cơ sở.
export function previewMinutes(status, actualIn, actualOut) {
  if (status === 'ABSENT') return 0;
  if (!actualIn || !actualOut) return null;
  const minutes = (Date.parse(`${actualOut}Z`) - Date.parse(`${actualIn}Z`)) / 60000;
  return Number.isInteger(minutes) && minutes > 0 ? minutes : null;
}
