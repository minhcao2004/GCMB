const emailPattern = /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$/;
export function emailError(value) {
  const email = value.trim();
  if (!email) return 'Vui lòng nhập email';
  if (email.length > 254) return 'Email không được vượt quá 254 ký tự';
  if (!emailPattern.test(email) || email.indexOf('@') > 64 || email.startsWith('.') || email.includes('..') || email.includes('.@')) return 'Email không đúng định dạng';
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
      else if (values.username.trim().length > 100) errors.username = 'Tên đăng nhập không được vượt quá 100 ký tự';
    }
    if (!values.password.trim()) errors.password = mode === 'login' ? 'Vui lòng nhập mật khẩu' : 'Vui lòng nhập mật khẩu mới';
    if (mode === 'reset') {
      if (!values.confirmation.trim()) errors.confirmation = 'Vui lòng nhập xác nhận mật khẩu';
      else if (values.confirmation !== values.password) errors.confirmation = 'Mật khẩu xác nhận không khớp';
      if (!values.code) errors.code = 'Vui lòng nhập mã xác nhận';
      else if (!/^[0-9]{6}$/.test(values.code)) errors.code = 'Mã xác nhận phải gồm đúng 6 chữ số từ 0–9';
    }
  }
  return errors;
}
