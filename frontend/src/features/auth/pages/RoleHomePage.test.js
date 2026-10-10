import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import RoleHomePage from './RoleHomePage';
import { authApi } from '../services/authApi';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({ useNavigate: () => mockNavigate }));
jest.mock('../services/authApi', () => ({
  authApi: { me: jest.fn(), logout: jest.fn() },
  apiErrors: () => ({ form: 'Không thể kết nối máy chủ' }),
}));
beforeEach(() => jest.resetAllMocks());

test.each([
  ['HEAD_OFFICE_MANAGER', 'Quản lý cấp cao'],
  ['BRANCH_MANAGER', 'Quản lý chi nhánh'],
  ['RECEPTIONIST', 'Lễ tân'],
  ['ACCOUNTANT', 'Kế toán'],
])('shows the dashboard for %s after checking the session', async (role, name) => {
  authApi.me.mockResolvedValue({ username: 'staff', role });
  render(<RoleHomePage role={role} />);
  expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: role === 'HEAD_OFFICE_MANAGER' ? 'Xin chào quản lý cấp cao.' : `Dashboard — ${name}` })).toBeVisible();
  expect(screen.getByText('staff')).toBeVisible();
  expect(mockNavigate).not.toHaveBeenCalled();
});

test('head office navigation changes selection while keeping the content empty except for the greeting', async () => {
  authApi.me.mockResolvedValue({ username: 'manager', role: 'HEAD_OFFICE_MANAGER' });
  render(<RoleHomePage role="HEAD_OFFICE_MANAGER" />);
  const overview = await screen.findByRole('button', { name: 'Tổng quan' });
  expect(overview).toHaveAttribute('aria-current', 'page');
  fireEvent.click(screen.getByRole('button', { name: 'Nhân sự' }));
  expect(screen.getByRole('button', { name: 'Nhân sự' })).toHaveAttribute('aria-current', 'page');
  expect(overview).not.toHaveAttribute('aria-current');
  expect(screen.getByRole('region', { name: 'Xin chào quản lý cấp cao.' })).toHaveTextContent(/^Xin chào quản lý cấp cao\.$/);
  expect(mockNavigate).not.toHaveBeenCalled();
});

test('redirects a user visiting another role dashboard', async () => {
  authApi.me.mockResolvedValue({ username: 'staff', role: 'RECEPTIONIST', destination: '/le-tan' });
  render(<RoleHomePage role="ACCOUNTANT" />);
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/le-tan', { replace: true }));
  expect(screen.queryByRole('heading')).not.toBeInTheDocument();
});

test('redirects an expired session to login', async () => {
  authApi.me.mockRejectedValue({ response: { status: 401 } });
  render(<RoleHomePage role="ACCOUNTANT" />);
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dang-nhap', expect.objectContaining({ replace: true })));
  expect(screen.queryByRole('heading')).not.toBeInTheDocument();
});

test('logs out and returns to login', async () => {
  authApi.me.mockResolvedValue({ username: 'staff', role: 'ACCOUNTANT' });
  authApi.logout.mockResolvedValue({});
  render(<RoleHomePage role="ACCOUNTANT" />);
  fireEvent.click(await screen.findByRole('button', { name: 'Đăng xuất' }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dang-nhap', { replace: true }));
  expect(authApi.logout).toHaveBeenCalledTimes(1);
});
