/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Gọi API phân quyền, thu hồi truy cập và tra cứu nhật ký hệ thống.
 */
import api from '../../../../api/client';

async function post(path, body, scope) {
  const { data: csrf } = await api.get('/auth/csrf');
  const { data } = await api.post(path, body, {
    headers: { ...(scope ? { 'X-GCMB-Branch-Id': scope } : {}), [csrf.headerName]: csrf.token },
  });
  return data;
}

export const accountAccessApi = {
  setRole(id, body) {
    return post(`/head-office/accounts/${id}/role`, body);
  },
  deactivate(id, reason, scope) {
    return post(
      `${scope ? '/branch-management' : '/head-office'}/accounts/${id}/deactivate`,
      { reason },
      scope
    );
  },
  reactivate(id, scope) {
    return post(
      `${scope ? '/branch-management' : '/head-office'}/accounts/${id}/reactivate`,
      {},
      scope
    );
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
  // HuyDV: tên hiển thị dùng chung cho danh sách, bộ lọc và chi tiết nhật ký.
  ASSIGN_EMPTY_SHIFT: 'Phân công nhân viên vào ca',
  CANCEL_SHIFT_AFTER_ATTENDANCE_CLOSING: 'Hủy ca sau khi chốt công nhân viên',
  CLOSE_EMPLOYEE_MONTHLY_ATTENDANCE: 'Chốt công tháng cho nhân viên',
  SET_BRANCH_HOURLY_PAY: 'Thiết lập đơn giá giờ của nhân viên',
  SUBMIT_BRANCH_PAYROLL: 'Chốt lương và gửi kế toán',
  PAY_SALARY_ADVANCE: 'Ghi nhận ứng lương',
  RECORD_SALARY_PAYMENT: 'Ghi nhận kết quả trả lương',
  SHIFT_CASH_DISPATCHED: 'Giao tiền mặt chuyển ca',
  SHIFT_CASH_RECEIVED: 'Nhận tiền mặt chuyển ca',
  SHIFT_CASH_RESOLVED: 'Xử lý chênh lệch bàn giao ca',
  SUBMIT_DAILY_TILL_REPORT: 'Gửi báo cáo chốt quỹ cuối ngày',
  RESUBMIT_DAILY_TILL_REPORT: 'Gửi lại báo cáo chốt quỹ',
  RECONCILE_DAILY_TILL_REPORT: 'Hoàn tất đối soát chốt quỹ',
  RETURN_DAILY_TILL_REPORT: 'Yêu cầu bổ sung báo cáo chốt quỹ',
  RECORD_DRAWER_REMITTANCE: 'Ghi nhận nộp tiền khỏi két',
  REQUEST_DRAWER_FUNDING: 'Tạo yêu cầu cấp tiền két',
  COMPLETE_DRAWER_FUNDING_RECIPIENT: 'Bổ sung thông tin người nhận tiền cấp két',
  PAY_DRAWER_FUNDING: 'Xác nhận thanh toán cấp tiền két',
  REJECT_DRAWER_FUNDING: 'Từ chối yêu cầu cấp tiền két',
  CREATE_ACCOUNTANT_DIRECT_EXPENSE: 'Ghi nhận chi trực tiếp',
  CREATE_FINANCE_EXPENSE_CATEGORY: 'Tạo hạng mục chi phí',
  UPDATE_FINANCE_EXPENSE_CATEGORY: 'Sửa hạng mục chi phí',
  DELETE_FINANCE_EXPENSE_CATEGORY: 'Xóa hạng mục chi phí',
  DEACTIVATE_FINANCE_EXPENSE_CATEGORY: 'Ngừng sử dụng hạng mục chi phí',
  REACTIVATE_FINANCE_EXPENSE_CATEGORY: 'Mở lại hạng mục chi phí',
  CREATE_FINANCE_RECURRING_EXPENSE: 'Tạo khoản chi định kỳ',
  UPDATE_FINANCE_RECURRING_EXPENSE: 'Sửa khoản chi định kỳ',
  TERMINATE_FINANCE_RECURRING_EXPENSE: 'Kết thúc khoản chi định kỳ',
  SKIP_FINANCE_RECURRING_EXPENSE_PERIOD: 'Bỏ qua kỳ chi định kỳ',
  PAY_FINANCE_RECURRING_EXPENSE_PERIOD: 'Ghi nhận thanh toán kỳ chi định kỳ',
  ADJUST_FINANCIAL_TRANSACTION: 'Điều chỉnh giao dịch tài chính',
  CLOSE_FINANCIAL_PERIOD: 'Khóa kỳ tài chính',
  RESOLVE_SEPAY_EXCEPTION: 'Xử lý giao dịch SePay ngoại lệ',
  EXPORT_ACCOUNTING_MONTHLY_WORKBOOK: 'Xuất sổ kế toán tháng',
  EXPORT_FINANCIAL_OVERVIEW: 'Xuất báo cáo tài chính',
  EXPORT_MONTHLY_PERFORMANCE: 'Xuất báo cáo hiệu quả kinh doanh tháng',
  CONFIRM_MANUAL_DEPOSIT: 'Xác nhận đã nhận tiền cọc',
  REQUEST_DEPOSIT_REFUND: 'Tạo yêu cầu hoàn tiền cọc',
  WALK_IN_CHECKED_IN: 'Tạo booking và nhận phòng khách vãng lai',
  ADD_FB_ORDER_ITEMS: 'Thêm món vào đơn phục vụ phòng',
  CONFIRM_FB_ORDER: 'Xác nhận đơn phục vụ phòng',
  REJECT_FB_ORDER: 'Từ chối đơn phục vụ phòng',
  UPDATE_FB_ORDER_SERVICE_STATUS: 'Cập nhật trạng thái phục vụ đơn',
  CREATE_MENU_ITEM: 'Thêm món vào thực đơn',
  UPDATE_MENU_ITEM: 'Sửa món trong thực đơn',
  DELETE_MENU_ITEM: 'Xóa món khỏi thực đơn',
  CHANGE_MENU_ITEM_STATUS: 'Đổi trạng thái món trong thực đơn',
  CREATE_INVENTORY_ITEM: 'Tạo mặt hàng trong kho',
  UPDATE_INVENTORY_ITEM: 'Sửa mặt hàng trong kho',
  DELETE_INVENTORY_ITEM: 'Xóa mặt hàng trong kho',
  CREATE_EQUIPMENT_CATEGORY: 'Tạo nhóm thiết bị',
  UPDATE_EQUIPMENT_CATEGORY: 'Sửa nhóm thiết bị',
  DELETE_EQUIPMENT_CATEGORY: 'Xóa nhóm thiết bị',
  CHANGE_EQUIPMENT_CATEGORY_STATUS: 'Đổi trạng thái nhóm thiết bị',
  CREATE_EQUIPMENT_ASSET: 'Tạo hồ sơ thiết bị',
  UPDATE_EQUIPMENT_ASSET: 'Sửa hồ sơ thiết bị',
  DELETE_EQUIPMENT_ASSET: 'Xóa hồ sơ thiết bị',
  CHANGE_EQUIPMENT_ASSET_STATUS: 'Đổi trạng thái thiết bị',
  UPLOAD_EQUIPMENT_ASSET_IMAGE: 'Tải ảnh hoặc chứng từ thiết bị',
  CREATE_EQUIPMENT_INCIDENT: 'Báo sự cố thiết bị',
  ASSESS_EQUIPMENT_INCIDENT: 'Đánh giá sự cố thiết bị',
  APPROVE_EQUIPMENT_INCIDENT: 'Duyệt xử lý sự cố thiết bị',
  REJECT_EQUIPMENT_INCIDENT: 'Từ chối xử lý sự cố thiết bị',
  SUGGEST_EQUIPMENT_REPAIR: 'Đề xuất sửa chữa thiết bị',
  RECORD_EQUIPMENT_REPAIR_OUTCOME: 'Ghi nhận kết quả sửa chữa thiết bị',
  UPDATE_EQUIPMENT_INCIDENT_PROGRESS: 'Cập nhật tiến độ xử lý sự cố',
  CLOSE_EQUIPMENT_INCIDENT: 'Đóng sự cố thiết bị',
  CREATE_EQUIPMENT_TRANSFER: 'Tạo yêu cầu điều chuyển thiết bị',
  CONFIRM_EQUIPMENT_HANDOVER: 'Xác nhận giao thiết bị điều chuyển',
  CONFIRM_EQUIPMENT_RECEIPT: 'Xác nhận nhận thiết bị điều chuyển',
  RECORD_DAILY_ATTENDANCE: 'Ghi nhận / sửa chấm công',
  CLOSE_MONTHLY_ATTENDANCE: 'Chốt bảng công tháng',
  SET_WEEKLY_SHIFT_AVAILABILITY: 'Công bố / chỉnh ca tuần',
  REGISTER_FOR_SHIFT: 'Đăng ký ca làm',
  REPLACE_SHIFT_EMPLOYEE: 'Thay nhân viên trong ca',
  CREATE_TAX_INVOICE_REQUEST: 'Ghi nhận yêu cầu hóa đơn thuế',
  UPDATE_TAX_INVOICE_REQUEST: 'Cập nhật kết quả giao hóa đơn thuế',
  CREATE_STAFF: 'Tạo hồ sơ nhân viên',
  UPDATE_STAFF: 'Sửa hồ sơ nhân viên',
  DEACTIVATE_STAFF: 'Ngừng hồ sơ nhân viên',
  REACTIVATE_STAFF: 'Kích hoạt hồ sơ nhân viên',
  SET_EMPLOYEE_BRANCH: 'Gán cơ sở nhân viên',
  TRANSFER_EMPLOYEE: 'Điều chuyển nhân viên',
  CHANGE_PASSWORD: 'Đổi mật khẩu',
  CREATE_ROOM: 'Thêm phòng',
  UPDATE_ROOM: 'Sửa phòng',
  DELETE_ROOM: 'Xóa phòng',
  UPDATE_ROOM_STATUS: 'Đổi trạng thái phòng',
  CHANGE_BILL_PAYMENT_METHOD: 'Đổi phương thức thanh toán bill',
  PLAN_DEPOSIT_PAYMENT: 'Lập yêu cầu thanh toán tiền cọc',
  CONFIRM_SEPAY_DEPOSIT: 'SePay xác nhận tiền cọc',
  RENEW_BILL_QR: 'Gia hạn mã QR bill',
  ISSUE_BILL: 'Lập bill',
  PLAN_BILL_PAYMENT: 'Ghi nhận thanh toán bill',
  CONFIRM_SEPAY_PAYMENT: 'SePay xác nhận thanh toán',
  CHECKOUT_BOOKING: 'Hoàn tất checkout',
  CREATE_BRANCH: 'Thêm cơ sở',
  UPDATE_BRANCH: 'Sửa cơ sở',
  DEACTIVATE_BRANCH: 'Ngừng hoạt động cơ sở',
  REACTIVATE_BRANCH: 'Mở lại cơ sở',
  CREATE_ROOM_TYPE: 'Thêm hạng phòng',
  UPDATE_ROOM_TYPE: 'Sửa hạng phòng',
  DELETE_ROOM_TYPE: 'Xóa hạng phòng',
  DEACTIVATE_ROOM_TYPE: 'Ngừng sử dụng hạng phòng',
  CREATE_PRICE_RULE: 'Thêm giá / quy tắc giá',
  UPDATE_PRICE_RULE: 'Sửa giá / quy tắc giá',
  DELETE_PRICE_RULE: 'Xóa quy tắc giá',
  CREATE_COMBO: 'Thêm combo',
  UPDATE_COMBO: 'Sửa combo',
  DELETE_COMBO: 'Xóa combo',
  ASSIGN_BRANCH_MANAGER: 'Gán / chuyển quản lý cơ sở',
  BOOKING_CREATED: 'Tạo booking',
  BOOKING_UPDATED: 'Sửa booking',
  BOOKING_CANCELLED: 'Hủy booking',
  BOOKING_CHECKED_IN: 'Check-in',
  BOOKING_NO_SHOW: 'Đánh dấu không đến',
  BOOKING_ROOM_CHANGED: 'Đổi phòng',
  BOOKING_EXTENDED: 'Gia hạn',
  HOURLY_METER_STARTED: 'Bắt đầu tính giờ lẻ',
  HOURLY_METER_STOPPED: 'Chốt giờ lẻ',
  ROOM_CLEANING_COMPLETED: 'Hoàn tất dọn phòng',
  UPDATE_ACCOUNT_INFORMATION: 'Cập nhật thông tin tài khoản',
  CREATE_USER_ACCOUNT: 'Tạo tài khoản',
  SET_ACCOUNT_ROLE: 'Thay đổi vai trò / phạm vi',
  DEACTIVATE_USER_ACCOUNT: 'Vô hiệu hóa tài khoản',
  REACTIVATE_USER_ACCOUNT: 'Kích hoạt tài khoản',
};
