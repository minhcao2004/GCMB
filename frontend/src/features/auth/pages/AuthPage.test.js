import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import AuthPage from './AuthPage';
import { authApi } from '../services/authApi';

const mockNavigate = jest.fn();
jest.mock('../../../api/client', () => ({}));
jest.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useLocation: () => ({ state: null }),
  Link: ({ to, children, ...props }) => <a href={to} {...props}>{children}</a>,
  Navigate: ({ to }) => <div>Chuyển đến {to}</div>,
}));
jest.mock('../services/authApi', () => ({
  authApi: { login: jest.fn(), sendCode: jest.fn(), reset: jest.fn() },
  apiErrors: jest.requireActual('../services/authApi').apiErrors,
}));
const recovery = { email: 'staff@example.com', challenge: 'opaque-challenge' };
beforeEach(() => { jest.clearAllMocks(); });
const ready = async (text) => { await waitFor(() => expect(screen.getByRole('button', { name: text, exact: true })).toBeEnabled()); };

test('required login fields and show/hide password are accessible', async () => {
  render(<AuthPage mode="login" />);
  await ready('Đăng nhập');
  fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }));
  expect(screen.getByText('Vui lòng nhập tên đăng nhập')).toBeVisible();
  expect(screen.getByText('Vui lòng nhập mật khẩu')).toBeVisible();
  expect(authApi.login).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Hiện mật khẩu' }));
  expect(screen.getByLabelText('Mật khẩu', { exact: true })).toHaveAttribute('type', 'text');
  fireEvent.click(screen.getByRole('button', { name: 'Ẩn mật khẩu' }));
  expect(screen.getByLabelText('Mật khẩu', { exact: true })).toHaveAttribute('type', 'password');
});
test('forgot password stays on screen when mail fails and allows retry', async () => {
  authApi.sendCode.mockRejectedValue({ response: { data: { errors: { form: 'Gửi email thất bại. Vui lòng thử lại sau' } } } });
  const onRecovery = jest.fn();
  render(<AuthPage mode="forgot" onRecovery={onRecovery} />);
  await ready('Gửi Mail');
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: '  staff@example.com  ' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi Mail' }));
  await screen.findByText('Gửi email thất bại. Vui lòng thử lại sau');
  expect(authApi.sendCode).toHaveBeenCalledWith('staff@example.com');
  expect(onRecovery).not.toHaveBeenCalled();
  expect(mockNavigate).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: 'Gửi Mail' })).toBeEnabled();
});
test('successful mail advances only after completion and blocks duplicate submission', async () => {
  let finish;
  authApi.sendCode.mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
  const onRecovery = jest.fn();
  render(<AuthPage mode="forgot" onRecovery={onRecovery} />);
  await ready('Gửi Mail');
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'staff@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi Mail' }));
  expect(screen.getByRole('button', { name: 'Đang gửi…' })).toBeDisabled();
  fireEvent.click(screen.getByRole('button', { name: 'Đang gửi…' }));
  expect(authApi.sendCode).toHaveBeenCalledTimes(1);
  expect(mockNavigate).not.toHaveBeenCalled();
  await act(async () => finish(recovery));
  expect(onRecovery).toHaveBeenCalledWith(recovery);
  expect(mockNavigate).toHaveBeenCalledWith('/dat-lai-mat-khau');
});
test('reset preserves password and leading-zero code; no success on server failure', async () => {
  authApi.reset.mockRejectedValue({ response: { data: { errors: { code: 'Mã xác nhận không đúng' } } } });
  const onRecovery = jest.fn();
  render(<AuthPage mode="reset" recovery={recovery} onRecovery={onRecovery} />);
  await ready('Xác nhận');
  const password = '  mat khau moi  ';
  fireEvent.change(screen.getByLabelText('Mật khẩu mới', { exact: true }), { target: { value: password } });
  fireEvent.change(screen.getByLabelText('Xác nhận mật khẩu', { exact: true }), { target: { value: password } });
  fireEvent.change(screen.getByLabelText('Mã xác nhận', { exact: true }), { target: { value: '038291' } });
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận', exact: true }));
  await screen.findByText('Mã xác nhận không đúng');
  expect(authApi.reset).toHaveBeenCalledWith({ email: recovery.email, challenge: recovery.challenge, code: '038291', password, confirmation: password });
  expect(mockNavigate).not.toHaveBeenCalled();
  authApi.reset.mockResolvedValue({ message: 'Đặt lại mật khẩu thành công' });
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận', exact: true }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dang-nhap', { replace: true, state: { message: 'Đặt lại mật khẩu thành công' } }));
  expect(onRecovery).toHaveBeenCalledWith(null);
});
test('direct reset navigation without a recovery request redirects', async () => {
  await act(async () => render(<AuthPage mode="reset" recovery={null} />));
  expect(screen.getByText('Chuyển đến /dang-nhap')).toBeInTheDocument();
});
