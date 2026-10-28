/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Kiểm tra dữ liệu form đăng nhập, email khôi phục, mật khẩu xác nhận và mã xác nhận.
 */

const emailPattern =
  /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$/;
// Kiểm tra email đã trim và giới hạn độ dài trước khi gọi API.
export function emailError(value) {
  const email = value.trim();
  if (!email) return 'Vui lòng nhập email';
  if (email.length > 254) return 'Email không được vượt quá 254 ký tự';
  if (
    !emailPattern.test(email) ||
    email.indexOf('@') > 64 ||
    email.startsWith('.') ||
    email.includes('..') ||
    email.includes('.@')
  )
    return 'Nhập địa chỉ email theo dạng ten@tenmien.com.';
  return '';
}
// Kiểm tra trường bắt buộc theo từng bước đăng nhập hoặc khôi phục.
// BR-114; MSG29. Chỉ áp dụng mật khẩu MỚI, không chặn đăng nhập bằng mật khẩu cũ.
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

export function validate(mode, values) {
  const errors = {};
  if (mode === 'forgot') {
    const error = emailError(values.email);
    if (error) errors.email = error;
  } else {
    if (mode === 'login') {
      if (!values.username.trim()) errors.username = 'Vui lòng nhập tên đăng nhập';
      else if (values.username.trim().length > 100)
        errors.username = 'Tên đăng nhập không được vượt quá 100 ký tự';
    }
    if (!values.password.trim())
      errors.password = mode === 'login' ? 'Vui lòng nhập mật khẩu' : 'Vui lòng nhập mật khẩu mới';
    if (mode === 'reset') {
      if (values.password.trim()) {
        const policyError = passwordError(values.password);
        if (policyError) errors.password = policyError;
      }
      if (!values.confirmation.trim()) errors.confirmation = 'Vui lòng nhập xác nhận mật khẩu';
      else if (values.confirmation !== values.password)
        errors.confirmation = 'Mật khẩu xác nhận không khớp với mật khẩu mới.';
      if (!values.code) errors.code = 'Vui lòng nhập mã xác nhận';
      else if (!/^[0-9]{6}$/.test(values.code))
        errors.code = 'Mã xác nhận phải gồm đúng 6 chữ số từ 0–9';
    }
  }
  return errors;
}
