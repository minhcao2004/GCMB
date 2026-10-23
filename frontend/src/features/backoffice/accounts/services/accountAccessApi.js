/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Gọi API phân quyền, thu hồi truy cập và tra cứu nhật ký hệ thống.
 */
import api from '../../../../api/client';

async function post(path, body) {
  const { data: csrf } = await api.get('/auth/csrf');
  const { data } = await api.post(path, body, {
    headers: { [csrf.headerName]: csrf.token },
  });
  return data;
}

export const accountAccessApi = {
  setRole(id, body) {
    return post(`/head-office/accounts/${id}/role`, body);
  },
  deactivate(id, reason) {
    return post(`/head-office/accounts/${id}/deactivate`, { reason });
  },
  async permissions(role) {
    const { data } = await api.get('/head-office/accounts/permissions', {
      params: { role },
    });
    return data;
  },
  async audit(params) {
    const { data } = await api.get('/head-office/audit-logs', { params });
    return data;
  },
  async actions() {
    const { data } = await api.get('/head-office/audit-logs/actions');
    return data;
  },
  async detail(id) {
    const { data } = await api.get(`/head-office/audit-logs/${id}`);
    return data;
  },
};

export const roleNames = {
  HEAD_OFFICE_MANAGER: 'Quản lý cấp cao (HOM)',
  BRANCH_MANAGER: 'Quản lý cơ sở (BM)',
  RECEPTIONIST: 'Lễ tân (REC)',
  ACCOUNTANT: 'Kế toán (ACC)',
};

export const actionNames = {
  CHANGE_PASSWORD: 'Đổi mật khẩu',
  CREATE_ROOM: 'Thêm phòng', UPDATE_ROOM: 'Sửa phòng', DELETE_ROOM: 'Xóa phòng', UPDATE_ROOM_STATUS: 'Đổi trạng thái phòng',
  CHANGE_BILL_PAYMENT_METHOD: 'Đổi phương thức thanh toán bill',
  RENEW_BILL_QR: 'Gia hạn mã QR bill', ISSUE_BILL: 'Lập bill', PLAN_BILL_PAYMENT: 'Ghi nhận thanh toán bill', CONFIRM_SEPAY_PAYMENT: 'SePay xác nhận thanh toán', CHECKOUT_BOOKING: 'Hoàn tất checkout',
  CREATE_BRANCH: 'Thêm cơ sở', UPDATE_BRANCH: 'Sửa cơ sở', DEACTIVATE_BRANCH: 'Ngừng hoạt động cơ sở',
  REACTIVATE_BRANCH: 'Mở lại cơ sở',
  CREATE_ROOM_TYPE: 'Thêm hạng phòng', UPDATE_ROOM_TYPE: 'Sửa hạng phòng', DELETE_ROOM_TYPE: 'Xóa hạng phòng',
  DEACTIVATE_ROOM_TYPE: 'Ngừng sử dụng hạng phòng',
  CREATE_PRICE_RULE: 'Thêm giá / quy tắc giá', UPDATE_PRICE_RULE: 'Sửa giá / quy tắc giá', DELETE_PRICE_RULE: 'Xóa quy tắc giá',
  CREATE_COMBO: 'Thêm combo', UPDATE_COMBO: 'Sửa combo', DELETE_COMBO: 'Xóa combo',
  ASSIGN_BRANCH_MANAGER: 'Gán / chuyển quản lý cơ sở',
  BOOKING_CREATED: 'Tạo booking', BOOKING_UPDATED: 'Sửa booking', BOOKING_CANCELLED: 'Hủy booking',
  BOOKING_CHECKED_IN: 'Check-in', BOOKING_NO_SHOW: 'Đánh dấu không đến', BOOKING_ROOM_CHANGED: 'Đổi phòng',
  BOOKING_EXTENDED: 'Gia hạn', HOURLY_METER_STARTED: 'Bắt đầu tính giờ lẻ', HOURLY_METER_STOPPED: 'Chốt giờ lẻ',
  ROOM_CLEANING_COMPLETED: 'Hoàn tất dọn phòng',
  UPDATE_ACCOUNT_INFORMATION: 'Cập nhật thông tin tài khoản',
  CREATE_USER_ACCOUNT: 'Tạo tài khoản',
  SET_ACCOUNT_ROLE: 'Thay đổi vai trò / phạm vi',
  DEACTIVATE_USER_ACCOUNT: 'Thu hồi truy cập',
};
