/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 26/10/2026
 * Updated: 27/10/2026
 * Description: API nhân sự và kiểm tra dữ liệu theo BR/MSG.
 */

import api from '../../../api/client';

const path = scope => scope ? '/branch-management/staff' : '/head-office/staff';
const headers = scope => scope ? { 'X-GCMB-Branch-Id': scope } : {};
async function write(scope, suffix, body, method = 'post') {
  const { data: csrf } = await api.get('/auth/csrf');
  return (await api[method](`${path(scope)}${suffix}`, body, { headers: { ...headers(scope), [csrf.headerName]: csrf.token } })).data;
}
export const staffApi = {
  list: async (params, scope) => (await api.get(path(scope), { params, headers: headers(scope) })).data,
  options: async scope => (await api.get(`${path(scope)}/options`, { headers: headers(scope) })).data,
  detail: async (id, scope) => (await api.get(`${path(scope)}/${id}`, { headers: headers(scope) })).data,
  create: (body, scope) => write(scope, '', body),
  update: (id, body, scope) => write(scope, `/${id}`, body, 'put'),
  status: (id, body, scope) => write(scope, `/${id}/status`, body),
  assign: (id, body, scope) => write(scope, `/${id}/branch`, body),
  transfer: (id, body, scope) => write(scope, `/${id}/transfers`, body),
};
export const positions = { HEAD_OFFICE_MANAGER: 'Quản lý cấp cao', BRANCH_MANAGER: 'Quản lý cơ sở', RECEPTIONIST: 'Lễ tân', ACCOUNTANT: 'Kế toán', OTHER: 'Khác' };
export const staffStatuses = { ACTIVE: 'Đang làm việc', INACTIVE: 'Đã ngừng', RESIGNED: 'Đã nghỉ việc' };
export const dateLabel = value => value ? value.split('-').reverse().join('/') : '—';
export const timeLabel = value => value ? new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : '—';
// BR-111, BR-119, BR-199; MSG02, MSG03, MSG04, MSG05, MSG88. The backend repeats these checks.
export function staffErrors(form) {
  const errors = {};
  for (const [key, label, max] of [['code', 'Mã nhân viên', 60], ['fullName', 'Họ tên', 200], ['position', 'Vị trí', 60]]) {
    if (!form[key]?.trim()) errors[key] = `Vui lòng bổ sung ${label.toLocaleLowerCase('vi')}.`;
    else if (form[key].trim().length > max) errors[key] = `${label} chỉ được chứa tối đa ${max} ký tự.`;
  }
  for (const [key, label, max] of [['phone', 'Số điện thoại', 30], ['identityNumber', 'Số định danh', 30], ['contactEmail', 'Email liên hệ', 254], ['address', 'Địa chỉ', 2000], ['notes', 'Ghi chú', 4000]]) {
    if ((form[key] || '').trim().length > max) errors[key] = `${label} chỉ được chứa tối đa ${max} ký tự.`;
  }
  if (form.phone?.trim() && !/^\+?[0-9]+$/.test(form.phone.trim())) errors.phone = 'Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.';
  if (form.contactEmail?.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.contactEmail.trim())) errors.contactEmail = 'Nhập địa chỉ email theo dạng ten@tenmien.com.';
  if (!form.hiredOn) errors.hiredOn = 'Vui lòng bổ sung ngày vào làm.';
  if (form.resignedOn && form.hiredOn && form.resignedOn < form.hiredOn) errors.resignedOn = 'Ngày nghỉ việc phải bằng hoặc sau ngày vào làm.';
  return errors;
}
