/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm thử bộ lọc, phân trang, tạo tài khoản liên kết nhân viên với mật khẩu ngắn và hiển thị lỗi trùng tài khoản.
 */

import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import AccountsPage from './AccountsPage';
import { accountApi } from './accountApi';

jest.mock('./accountApi', () => ({ accountApi: { list: jest.fn(), options: jest.fn(), create: jest.fn() } }));
const account = { id: 'user-1', employeeName: 'Phạm Thu Hà', employeeCode: 'NV-0031', username: 'thuha.rec', email: 'thuha@example.com', role: 'RECEPTIONIST', branchId: 'branch-1', branchName: 'Nguyễn Văn Lộc', status: 'ACTIVE' };
beforeEach(() => {
  jest.resetAllMocks();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  accountApi.list.mockResolvedValue({ items: [account], total: 11 });
  accountApi.options.mockResolvedValue({ employees: [{ id: 'employee-1', name: 'Phạm Thu Hà', code: 'NV-0031' }], roles: [{ id: 'RECEPTIONIST', name: 'Lễ tân' }], branches: [{ id: 'branch-1', name: 'Nguyễn Văn Lộc' }] });
});

test('loads accounts and sends search, role, branch, status and page filters', async () => {
  render(<AccountsPage />);
  expect(await screen.findByText('Phạm Thu Hà')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }));
  await waitFor(() => expect(accountApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })));
  fireEvent.change(screen.getByLabelText('Tìm tài khoản'), { target: { value: 'thuha' } });
  fireEvent.change(screen.getByLabelText('Lọc vai trò'), { target: { value: 'RECEPTIONIST' } });
  fireEvent.change(screen.getByLabelText('Lọc cơ sở'), { target: { value: 'branch-1' } });
  fireEvent.change(screen.getByLabelText('Lọc truy cập'), { target: { value: 'ACTIVE' } });
  await waitFor(() => expect(accountApi.list).toHaveBeenLastCalledWith({ q: 'thuha', role: 'RECEPTIONIST', branchId: 'branch-1', status: 'ACTIVE', page: 0, size: 10 }));
});

async function openForm() {
  render(<AccountsPage />);
  await screen.findByText('Phạm Thu Hà');
  fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
  const dialog = screen.getByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('Hồ sơ nhân viên'), { target: { value: 'employee-1' } });
  fireEvent.change(within(dialog).getByLabelText('Phạm vi cơ sở'), { target: { value: 'branch-1' } });
  for (const [name, value] of [['Tên đăng nhập', 'thuha.rec'], ['Mật khẩu', '123'], ['Email', 'thuha@example.com']]) fireEvent.change(within(dialog).getByLabelText(name), { target: { value } });
  return dialog;
}

test('creates an account linked to an employee and with a short password then shows and locates the saved account without displaying the password', async () => {
  accountApi.create.mockResolvedValue(account);
  const dialog = await openForm();
  expect(within(dialog).getByLabelText('Mật khẩu')).toHaveAttribute('type', 'password');
  fireEvent.click(within(dialog).getByRole('button', { name: 'Tạo tài khoản' }));
  expect(await screen.findByText('Tài khoản đã được tạo')).toBeVisible();
  expect(accountApi.create).toHaveBeenCalledWith({ employeeId: 'employee-1', username: 'thuha.rec', password: '123', email: 'thuha@example.com', role: 'RECEPTIONIST', branchId: 'branch-1' });
  expect(within(dialog).queryByLabelText('Mật khẩu')).not.toBeInTheDocument();
  fireEvent.click(within(dialog).getByRole('button', { name: 'Xem tài khoản' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  await waitFor(() => expect(accountApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'thuha.rec', page: 0 })));
});

test('keeps form input and displays duplicate errors instead of success', async () => {
  accountApi.create.mockRejectedValue({ response: { data: { errors: { username: 'Tên đăng nhập đã tồn tại' } } } });
  const dialog = await openForm();
  fireEvent.click(within(dialog).getByRole('button', { name: 'Tạo tài khoản' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Tên đăng nhập đã tồn tại');
  expect(within(dialog).getByLabelText('Email')).toHaveValue('thuha@example.com');
  expect(screen.queryByText('Tài khoản đã được tạo')).not.toBeInTheDocument();
});
