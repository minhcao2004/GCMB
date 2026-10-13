/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm thử đổi vai trò, lý do thu hồi và bảo vệ thao tác trên chính tài khoản.
 */
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import AccountAccessDialog from './AccountAccessDialog';
import { accountAccessApi } from './accountAccessApi';

jest.mock('./accountAccessApi', () => ({
  ...jest.requireActual('./accountAccessApi'),
  accountAccessApi: { permissions: jest.fn(), setRole: jest.fn(), deactivate: jest.fn() },
}));

const account = {
  id: 'target', role: 'RECEPTIONIST', branchId: 'branch',
  branchName: 'Hồ Tây', employeeName: 'Thu Hà', username: 'ha.rec', status: 'ACTIVE',
};
const options = {
  roles: [{ id: 'RECEPTIONIST' }, { id: 'ACCOUNTANT' }],
  branches: [{ id: 'branch', name: 'Hồ Tây' }],
};

beforeEach(() => {
  jest.clearAllMocks();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  accountAccessApi.permissions.mockResolvedValue([]);
});

test('sends a chain role with null branch and shows actual permission configuration', async () => {
  const onSaved = jest.fn();
  accountAccessApi.setRole.mockResolvedValue({});
  render(<AccountAccessDialog account={account} options={options} actorId="manager" onSaved={onSaved} onClose={jest.fn()} />);
  expect(await screen.findByText('Chưa cấu hình quyền chi tiết cho vai trò này.')).toBeVisible();
  fireEvent.change(screen.getByLabelText('Vai trò'), { target: { value: 'ACCOUNTANT' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu phân quyền' }));
  await waitFor(() => expect(accountAccessApi.setRole).toHaveBeenCalledWith('target', { role: 'ACCOUNTANT', branchId: null }));
  expect(onSaved).toHaveBeenCalled();
});

test('requires confirmation with reason and preserves input on server failure', async () => {
  accountAccessApi.deactivate.mockRejectedValue({ response: { data: { errors: { reason: 'Lý do không hợp lệ' } } } });
  render(<AccountAccessDialog account={account} options={options} actorId="manager" onSaved={jest.fn()} onClose={jest.fn()} />);
  await screen.findByText('Chưa cấu hình quyền chi tiết cho vai trò này.');
  fireEvent.click(screen.getByRole('button', { name: 'Thu hồi quyền truy cập ›' }));
  expect(accountAccessApi.deactivate).not.toHaveBeenCalled();
  const reason = screen.getByLabelText('Lý do');
  expect(reason).toBeRequired();
  fireEvent.change(reason, { target: { value: 'Nghỉ việc' } });
  fireEvent.click(screen.getByRole('button', { name: 'Thu hồi truy cập' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Lý do không hợp lệ');
  expect(reason).toHaveValue('Nghỉ việc');
  expect(accountAccessApi.deactivate).toHaveBeenCalledWith('target', 'Nghỉ việc');
});

test('disables editing the current user', async () => {
  render(<AccountAccessDialog account={account} options={options} actorId="target" onSaved={jest.fn()} onClose={jest.fn()} />);
  await screen.findByText('Chưa cấu hình quyền chi tiết cho vai trò này.');
  expect(screen.getByRole('button', { name: 'Lưu phân quyền' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Thu hồi quyền truy cập ›' })).toBeDisabled();
});
