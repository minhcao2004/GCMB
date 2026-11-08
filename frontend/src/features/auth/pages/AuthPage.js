/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Hiển thị và xử lý form đăng nhập, quên mật khẩu và đặt lại mật khẩu, gồm kiểm tra dữ liệu và gọi API.
 */

import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import AuthShell from '../components/AuthShell';
import AuthField from '../components/AuthField';
import { authApi, apiErrors } from '../services/authApi';
import { validate } from '../services/validation';

const empty = {
  username: '',
  password: '',
  email: '',
  confirmation: '',
  code: '',
  remember: false,
};
// Dùng chung biểu mẫu đăng nhập, quên mật khẩu và đặt lại mật khẩu.
export default function AuthPage({ mode, recovery, onRecovery }) {
  const [values, setValues] = useState(empty);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState('');
  const [notice, setNotice] = useState('');
  const submitting = useRef(false);
  const navigate = useNavigate();
  const location = useLocation();
  const login = mode === 'login';
  const forgot = mode === 'forgot';
  useEffect(() => {
    document.title = `${mode === 'login' ? 'Đăng nhập' : mode === 'forgot' ? 'Quên mật khẩu' : 'Đặt lại mật khẩu'} | GenZ Cinema & Music Box`;
  }, [mode]);
  // Cập nhật trường nhập và xóa lỗi cũ của trường đó.
  const change = (event) => {
    const { name, value, checked, type } = event.target;
    setValues((old) => ({ ...old, [name]: type === 'checkbox' ? checked : value }));
    setErrors((old) => ({ ...old, [name]: '', form: '' }));
    setNotice('');
  };
  // Chỉ hiện lỗi khi người dùng đã nhập hoặc trường đang có lỗi.
  const blur = (name) => {
    // Không làm xê dịch liên kết điều hướng khi trường trống chưa sửa mất focus.
    if (!values[name] && !errors[name]) return;
    const next = validate(mode, values);
    setErrors((old) => ({ ...old, [name]: next[name] || '' }));
  };
  // Ghép thuộc tính chung và trạng thái lỗi cho một trường xác thực.
  const field = (name) => ({
    name,
    value: values[name],
    onChange: change,
    onBlur: () => blur(name),
    error: errors[name],
    disabled: Boolean(busy),
    required: true,
  });
  // Kiểm tra dữ liệu rồi gọi API tương ứng với chế độ của trang.
  const submit = async (event) => {
    event.preventDefault();
    if (submitting.current) return;
    const next = validate(mode, values);
    setErrors(next);
    setNotice('');
    if (Object.keys(next).length) {
      document.getElementById(`auth-${Object.keys(next)[0]}`)?.focus();
      return;
    }
    submitting.current = true;
    setBusy('submit');
    try {
      if (login) {
        const user = await authApi.login({
          username: values.username.trim(),
          password: values.password,
          remember: values.remember,
        });
        navigate(user.destination, { replace: true });
      } else if (forgot) {
        const result = await authApi.sendCode(values.email.trim());
        onRecovery(result);
        navigate('/dat-lai-mat-khau');
      } else {
        await authApi.reset({
          email: recovery.email,
          challenge: recovery.challenge,
          code: values.code,
          password: values.password,
          confirmation: values.confirmation,
        });
        onRecovery(null);
        navigate('/dang-nhap', { replace: true, state: { message: 'Đã cập nhật mật khẩu.' } });
      }
    } catch (error) {
      setErrors(apiErrors(error));
    } finally {
      submitting.current = false;
      setBusy('');
    }
  };
  // Gửi mã mới và xóa mã cũ trên biểu mẫu sau khi API thành công.
  const resend = async () => {
    if (submitting.current) return;
    submitting.current = true;
    setBusy('resend');
    setErrors({});
    setNotice('');
    try {
      const result = await authApi.sendCode(recovery.email);
      onRecovery(result);
      setValues((old) => ({ ...old, code: '' }));
      setNotice(
        `Đã gửi mã xác minh đến ${recovery.email}. Mã có hiệu lực trong 10 phút kể từ khi được gửi.`
      );
    } catch (error) {
      setErrors(apiErrors(error));
    } finally {
      submitting.current = false;
      setBusy('');
    }
  };
  if (mode === 'reset' && !recovery) return <Navigate to="/dang-nhap" replace />;
  return (
    <AuthShell title={login ? 'Đăng nhập' : forgot ? 'Quên mật khẩu' : 'Đặt lại mật khẩu'}>
      <form noValidate onSubmit={submit} aria-busy={Boolean(busy)}>
        <div className="auth-form-body">
          {location.state?.message && (
            <p className="auth-success" role="status">
              {location.state.message}
            </p>
          )}
          {mode === 'reset' && (
            <p className="auth-intro">
              Mã xác nhận đã được gửi đến <strong>{recovery.email}</strong>. Vui lòng kiểm tra cả
              thư rác.
            </p>
          )}
          {login && (
            <AuthField
              {...field('username')}
              label="Tên đăng nhập"
              placeholder="Tên đăng nhập"
              autoComplete="username"
              maxLength={100}
              autoFocus
            />
          )}
          {forgot && (
            <AuthField
              {...field('email')}
              label="Email"
              type="email"
              placeholder="Email"
              autoComplete="email"
              maxLength={254}
              autoFocus
            />
          )}
          {!forgot && (
            <AuthField
              {...field('password')}
              label={login ? 'Mật khẩu' : 'Mật khẩu mới'}
              type="password"
              placeholder={login ? 'Mật khẩu' : 'Mật khẩu mới'}
              autoComplete={login ? 'current-password' : 'new-password'}
            />
          )}
          {mode === 'reset' && (
            <>
              <AuthField
                {...field('confirmation')}
                label="Xác nhận mật khẩu"
                type="password"
                placeholder="Xác nhận mật khẩu"
                autoComplete="new-password"
              />
              <AuthField
                {...field('code')}
                label="Mã xác nhận"
                placeholder="6 chữ số"
                inputMode="numeric"
                autoComplete="one-time-code"
              />
              <div className="auth-resend">
                <button type="button" disabled={Boolean(busy)} onClick={resend}>
                  {busy === 'resend' ? 'Đang gửi…' : 'Gửi lại mã'}
                </button>
                <Link
                  to="/quen-mat-khau"
                  aria-disabled={Boolean(busy)}
                  onClick={(e) => {
                    if (busy) e.preventDefault();
                  }}
                >
                  Đổi email
                </Link>
              </div>
            </>
          )}
          {login && (
            <div className="auth-options">
              <label>
                <input
                  type="checkbox"
                  name="remember"
                  checked={values.remember}
                  onChange={change}
                  disabled={Boolean(busy)}
                />{' '}
                Duy trì đăng nhập
              </label>
              <Link
                to="/quen-mat-khau"
                onClick={(e) => {
                  if (busy) e.preventDefault();
                }}
              >
                Quên mật khẩu?
              </Link>
            </div>
          )}
          {notice && (
            <p className="auth-success" role="status">
              {notice}
            </p>
          )}
          {errors.form && (
            <p className="auth-error auth-form-error" role="alert">
              {errors.form}
            </p>
          )}
        </div>
        <div className={`auth-actions${login ? ' auth-actions-single' : ''}`}>
          <button type="submit" disabled={Boolean(busy)}>
            {busy === 'submit'
              ? forgot
                ? 'Đang gửi…'
                : 'Đang xử lý…'
              : login
                ? 'Đăng nhập'
                : forgot
                  ? 'Gửi Mail'
                  : 'Xác nhận'}
          </button>
          {!login && (
            <button
              type="button"
              disabled={Boolean(busy)}
              onClick={() => navigate(forgot ? '/dang-nhap' : '/quen-mat-khau')}
            >
              Quay lại
            </button>
          )}
        </div>
      </form>
    </AuthShell>
  );
}
