/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm thử xem, hủy sửa, lưu và xử lý lỗi thông tin cá nhân.
 */
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import AccountProfileDialog from './AccountProfileDialog';
import { accountProfileApi } from './accountProfileApi';

jest.mock('./accountProfileApi', () => ({
  accountProfileApi: { view: jest.fn(), update: jest.fn() },
}));

const profile = {
  account: {
    username: 'ha.rec', employeeCode: 'NV-01', email: 'ha@example.com',
    role: 'RECEPTIONIST', branchName: 'Hồ Tây', status: 'ACTIVE',
  },
  fullName: 'Thu Hà', phone: null, linkedEmployee: true,
};

beforeEach(() => {
  jest.resetAllMocks();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  accountProfileApi.view.mockResolvedValue(profile);
});

async function edit() {
  render(<AccountProfileDialog onClose={jest.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Chỉnh sửa thông tin' }));
}

test('views scope and cancels unsaved changes', async () => {
  await edit();
  fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: 'Tên mới' } });
  fireEvent.click(screen.getByRole('button', { name: 'Hủy chỉnh sửa' }));
  expect(screen.getByText('Thu Hà')).toBeVisible();
  expect(screen.getByText('Hồ Tây')).toBeVisible();
  expect(accountProfileApi.update).not.toHaveBeenCalled();
});

test('saves only personal fields and displays returned profile', async () => {
  accountProfileApi.update.mockResolvedValue({ ...profile, fullName: 'Tên mới' });
  await edit();
  fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: 'Tên mới' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
  expect(await screen.findByText('Đã cập nhật thông tin tài khoản.')).toBeVisible();
  expect(screen.getByText('Tên mới')).toBeVisible();
  expect(accountProfileApi.update).toHaveBeenCalledWith({
    fullName: 'Tên mới', phone: '', email: 'ha@example.com', currentPassword: null,
  });
});

test('email change requires password and preserves other input on failure', async () => {
  accountProfileApi.update.mockRejectedValue({ response: { data: { errors: { email: 'Email đã tồn tại' } } } });
  await edit();
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'new@example.com' } });
  const password = screen.getByLabelText('Mật khẩu hiện tại');
  expect(password).toBeRequired();
  fireEvent.change(password, { target: { value: '123' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Email đã tồn tại');
  expect(screen.getByLabelText('Email')).toHaveValue('new@example.com');
  expect(password).toHaveValue('');
});

test('unlinked account disables employee fields and can retry loading', async () => {
  accountProfileApi.view.mockRejectedValueOnce({ response: { data: { message: 'Lỗi tải' } } })
    .mockResolvedValueOnce({ ...profile, linkedEmployee: false, fullName: null });
  render(<AccountProfileDialog onClose={jest.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Chỉnh sửa thông tin' }));
  expect(screen.getByLabelText('Họ và tên')).toBeDisabled();
  expect(screen.getByLabelText('Số điện thoại')).toBeDisabled();
  await waitFor(() => expect(accountProfileApi.view).toHaveBeenCalledTimes(2));
});
