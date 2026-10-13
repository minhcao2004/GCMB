/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm thử bộ lọc ngày, phân trang và chi tiết nhật ký.
 */
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import AuditLogPage from './AuditLogPage';
import { accountApi } from './accountApi';
import { accountAccessApi } from './accountAccessApi';

jest.mock('./accountApi', () => ({ accountApi: { options: jest.fn() } }));
jest.mock('./accountAccessApi', () => ({
  ...jest.requireActual('./accountAccessApi'),
  accountAccessApi: { audit: jest.fn(), actions: jest.fn(), detail: jest.fn() },
}));

const entry = {
  id: 'audit-1', createdAt: '2026-10-13T01:00:00Z', actorName: 'Quản lý',
  action: 'DEACTIVATE_USER_ACCOUNT', entityName: 'ha.rec', entityType: 'USER_ACCOUNT',
  branchName: 'Hồ Tây', reason: 'Nghỉ việc',
};

beforeEach(() => {
  jest.clearAllMocks();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  accountApi.options.mockResolvedValue({ branches: [{ id: 'branch', name: 'Hồ Tây' }] });
  accountAccessApi.actions.mockResolvedValue(['DEACTIVATE_USER_ACCOUNT']);
  accountAccessApi.audit.mockResolvedValue({ items: [entry], total: 11 });
  accountAccessApi.detail.mockResolvedValue({ event: entry, before: { status: 'ACTIVE' }, after: { status: 'REVOKED' } });
});

test('filters and pages audit events and displays before/after detail', async () => {
  render(<AuditLogPage onAccounts={jest.fn()} />);
  await screen.findByText('ha.rec');
  fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }));
  await waitFor(() => expect(accountAccessApi.audit).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })));
  fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '2026-10-12' } });
  fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-10-13' } });
  fireEvent.change(screen.getByLabelText('Tìm nhật ký'), { target: { value: 'ha.rec' } });
  fireEvent.change(screen.getByLabelText('Lọc hành động'), { target: { value: 'DEACTIVATE_USER_ACCOUNT' } });
  await waitFor(() => expect(accountAccessApi.audit).toHaveBeenLastCalledWith(expect.objectContaining({
    page: 0, from: '2026-10-12', to: '2026-10-13', q: 'ha.rec', action: 'DEACTIVATE_USER_ACCOUNT',
  })));
  fireEvent.click(await screen.findByRole('button', { name: 'Chi tiết' }));
  expect(await screen.findByText('Nghỉ việc')).toBeVisible();
  expect(screen.getByText('Đã thu hồi')).toBeVisible();
});

test('invalid date range is explained without submitting it', async () => {
  render(<AuditLogPage onAccounts={jest.fn()} />);
  await screen.findByText('ha.rec');
  accountAccessApi.audit.mockClear();
  fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '2026-10-13' } });
  fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-10-12' } });
  expect(await screen.findByRole('alert')).toHaveTextContent('Ngày bắt đầu không được sau ngày kết thúc');
  expect(accountAccessApi.audit).not.toHaveBeenCalled();
});
