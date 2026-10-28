/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: API UC-83–86; ca cố định theo BR-53 và ngày công tại Việt Nam.
 */
import api from '../../../api/client';
const base = (scope) => (scope ? '/branch-management/shifts' : '/reception/shifts');
const headers = (scope) => (scope ? { 'X-GCMB-Branch-Id': scope } : {});
async function write(path, body, scope, method = 'post') {
  const { data: csrf } = await api.get('/auth/csrf');
  return (
    await api[method](path, body, { headers: { ...headers(scope), [csrf.headerName]: csrf.token } })
  ).data;
}
export const shiftApi = {
  weekly: async (week, scope) =>
    (await api.get(base(scope), { params: { week }, headers: headers(scope) })).data,
  publish: (slots, scope) => write(`${base(scope)}/availability`, { slots }, scope, 'put'),
  register: (id) => write(`${base()}/${id}/register`, {}),
  replace: (id, body, scope) => write(`${base(scope)}/assignments/${id}/replacement`, body, scope),
};
export const addDays = (date, days) => {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
};
export const monday = (date) => {
  const weekday = new Date(`${date}T00:00:00Z`).getUTCDay();
  return addDays(date, -(weekday || 7) + 1);
};
export const today = () => {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Ho_Chi_Minh',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
      .formatToParts(new Date())
      .map((p) => [p.type, p.value])
  );
  return `${parts.year}-${parts.month}-${parts.day}`;
};
export const dateLabel = (date) => date?.split('-').reverse().join('/') || '—';
export const clock = (minutes) =>
  `${String(Math.floor(minutes / 60) % 24).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
export const shiftLabel = (template) =>
  `${clock(template.startMinute)}–${clock(template.endMinute)}${template.endMinute === 1440 ? ' (+1 ngày)' : ''}`;
export const slotKey = (date, templateId) => `${date}/${templateId}`;
