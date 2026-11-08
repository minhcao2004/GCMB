/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Kiểm tra dữ liệu form đăng nhập, email khôi phục, mật khẩu xác nhận và mã xác nhận.
 */

const emailPattern = /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$/;
// BR-111, BR-113: kiểm tra email bắt buộc, định dạng và giới hạn độ dài.
export function emailError(value) {
  const email = value.trim();
  if (!email) return 'Vui lòng bổ sung email.';
  if (email.length > 254) return 'Email chỉ được chứa tối đa 254 ký tự.';
  if (!emailPattern.test(email) || email.indexOf('@') > 64 || email.startsWith('.') || email.includes('..') || email.includes('.@')) return 'Nhập địa chỉ email theo dạng ten@tenmien.com.';
  return '';
}
// BR-114; MSG29. Chỉ áp dụng chính sách này cho mật khẩu mới.
export function passwordError(value) {
  if (
    value.length < 8 ||
    value.length > 64 ||
    !/[A-Z]/.test(value) ||
    !/[a-z]/.test(value) ||
    !/[0-9]/.test(value) ||
    !/[^A-Za-z0-9\s]/.test(value) ||
    /[\s\uFEFF]/u.test(value)
  ) {
    return 'Mật khẩu phải có từ 8 đến 64 ký tự, gồm ít nhất một chữ hoa, một chữ thường, một chữ số và một ký tự đặc biệt; không được chứa khoảng trắng.';
  }
  return '';
}
// BR-111, BR-120: đồng bộ kiểm tra trường bắt buộc và xác nhận mật khẩu với MSG-02/MSG-06.
export function validate(mode, values) {
  const errors = {};
  if (mode === 'forgot') {
    const error = emailError(values.email);
    if (error) errors.email = error;
  } else {
    if (mode === 'login') {
      if (!values.username.trim()) errors.username = 'Vui lòng bổ sung tên đăng nhập.';
      else if (values.username.trim().length > 100) errors.username = 'Tên đăng nhập chỉ được chứa tối đa 100 ký tự.';
    }
    if (!values.password.trim()) errors.password = mode === 'login' ? 'Vui lòng bổ sung mật khẩu.' : 'Vui lòng bổ sung mật khẩu mới.';
    if (mode === 'reset') {
      if (values.password.trim()) {
        const policyError = passwordError(values.password);
        if (policyError) errors.password = policyError;
      }
      if (!values.confirmation.trim()) errors.confirmation = 'Vui lòng bổ sung xác nhận mật khẩu.';
      else if (values.confirmation !== values.password) errors.confirmation = 'Mật khẩu xác nhận không khớp với mật khẩu mới.';
      if (!values.code) errors.code = 'Vui lòng bổ sung mã xác minh.';
      else if (!/^[0-9]{6}$/.test(values.code)) errors.code = 'Mã xác nhận phải gồm đúng 6 chữ số từ 0–9';
    }
  }
  return errors;
}
