import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import CashClosingRegister from './CashClosingRegister';
import DrawerFundingPanel from './DrawerFundingPanel';
import RecurringExpensesTab from './RecurringExpensesTab';
import { financeApi } from '../services/financeApi';

jest.mock('react-router-dom', () => ({
  useLocation: () => ({ search: '?date=2026-11-26', hash: '' }),
  Link: ({ to, children }) => <a href={to}>{children}</a>,
}));
jest.mock('../../auth/services/authApi', () => ({ apiErrors: () => ({}) }));
jest.mock('../services/financeApi', () => ({
  today: () => '2026-11-27',
  vnd: (value) => `${value}đ`,
  financeApi: { get: jest.fn(), post: jest.fn(), listRecurringExpenses: jest.fn() },
}));

const branches = [{ id: 'a', name: 'Cơ sở A' }, { id: 'b', name: 'Cơ sở B' }];
beforeEach(() => {
  jest.clearAllMocks();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  Object.defineProperty(window, 'crypto', { configurable: true, value: { randomUUID: () => 'request-fixture' } });
});

test('renders day status and reconciliation amounts in one table and opens the saved report', async () => {
  const onOpen = jest.fn();
  financeApi.get.mockImplementation((path) => Promise.resolve(path.endsWith('/statuses') ? [
    { branchId: 'a', branchName: 'Cơ sở A', businessDate: '2026-11-25T17:00:00Z', closingId: 'closing-a',
      status: 'RECONCILED', actualClosing: 510000, expectedClosing: 500000, variance: 10000 },
    { branchId: 'b', branchName: 'Cơ sở B', businessDate: '2026-11-26', status: 'NOT_SUBMITTED' },
  ] : []));
  render(<CashClosingRegister onOpen={onOpen} refresh={0}/>);
  const table = await screen.findByRole('table');
  expect(screen.getAllByRole('table')).toHaveLength(1);
  expect(within(table).getByRole('columnheader', { name: 'Ngày' })).toBeInTheDocument();
  expect(screen.getByLabelText('Ngày')).toHaveValue('2026-11-26');
  expect(within(table).getByText('Đã đối soát')).toBeInTheDocument();
  expect(within(table).getAllByText('26/11/2026')).toHaveLength(2);
  expect(within(table).getByText('510000đ')).toBeInTheDocument();
  expect(within(table).getByText('Chưa gửi')).toBeInTheDocument();
  fireEvent.click(within(table).getByRole('button', { name: 'Mở báo cáo' }));
  expect(onOpen).toHaveBeenCalledWith('closing-a');
});

test('filters recurring templates by creation day in Vietnam and by branch', async () => {
  const items = [
    { id: 'one', branch_id: 'a', branch_name: 'Cơ sở A', beneficiary_name: 'Người nhận A', created_at: '2026-11-25T17:01:00Z' },
    { id: 'two', branch_id: 'a', branch_name: 'Cơ sở A', beneficiary_name: 'Người nhận B', created_at: '2026-11-25T16:59:00Z' },
    { id: 'three', branch_id: 'b', branch_name: 'Cơ sở B', beneficiary_name: 'Người nhận C', created_at: '2026-11-26T01:00:00Z' },
  ];
  financeApi.listRecurringExpenses.mockImplementation(({ branchId }) => Promise.resolve({
    recurringExpenses: items.filter((item) => !branchId || item.branch_id === branchId), periods: [],
  }));
  render(<RecurringExpensesTab branches={branches} categories={[]} revision={0}/>);
  fireEvent.click(screen.getByRole('button', { name: 'Mẫu chi định kỳ' }));
  await screen.findByText('Người nhận A');
  fireEvent.change(screen.getByLabelText('Ngày tạo từ'), { target: { value: '2026-11-26' } });
  fireEvent.change(screen.getByLabelText('Ngày tạo đến'), { target: { value: '2026-11-26' } });
  expect(screen.queryByText('Người nhận B')).not.toBeInTheDocument();
  expect(screen.getByText('Người nhận C')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Cơ sở'), { target: { value: 'a' } });
  await screen.findByText('Người nhận A');
  expect(screen.queryByText('Người nhận C')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Ngày tạo đến'), { target: { value: '2026-11-25' } });
  expect(screen.getByRole('alert')).toHaveTextContent('Ngày tạo từ phải bằng hoặc trước');
});

test('drawer funding shows a payment-only form and submits the existing payment command', async () => {
  financeApi.get.mockResolvedValue({ fundBalance: 2000000, branches, requests: [
    { id: 'funding-a', branchName: 'Cơ sở A', status: 'REQUESTED', amount: 500000,
      recipient_name: 'Người nhận A', recipient_bank_name: 'MB', recipient_bank_account: '123',
      recipient_bank_holder: 'NGUOI NHAN A' },
  ] });
  financeApi.post.mockResolvedValue({});
  render(<DrawerFundingPanel user={{ role: 'ACCOUNTANT' }}/>);
  fireEvent.click(await screen.findByRole('button', { name: 'Thanh toán cấp két' }));
  const drawer = within(screen.getByRole('dialog', { name: 'Ghi nhận thanh toán cấp két' }));
  expect(drawer.queryByRole('button', { name: 'Từ chối yêu cầu' })).not.toBeInTheDocument();
  fireEvent.change(drawer.getByLabelText('Mã giao dịch'), { target: { value: 'REF-123' } });
  fireEvent.click(drawer.getByRole('checkbox', { name: /Tôi xác nhận/ }));
  fireEvent.click(drawer.getByRole('button', { name: 'Xác nhận đã thanh toán' }));
  await waitFor(() => expect(financeApi.post).toHaveBeenCalledWith('/accounting/cash-closing/funding/funding-a/pay', {
    confirmed: true, reference: 'REF-123', note: '',
  }));
});
