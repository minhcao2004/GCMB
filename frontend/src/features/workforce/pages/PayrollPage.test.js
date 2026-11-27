import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import PayrollPage from './PayrollPage';
import { financeApi } from '../../finance/services/financeApi';

jest.mock('react-router-dom', () => ({ useSearchParams: () => [new URLSearchParams('month=2026-11')] }));
jest.mock('../../auth/services/authApi', () => ({ apiErrors: () => ({ form: 'Lỗi tải dữ liệu' }) }));
jest.mock('../../finance/services/financeApi', () => ({
  financeApi: { get: jest.fn(), post: jest.fn(), downloadPost: jest.fn() },
  vnd: (value) => `${value}đ`,
  today: () => '2026-12-05',
}));

const rows = Array.from({ length: 20 }, (_, index) => ({
  id: `entry-${index + 1}`, employee_id: `employee-${index + 1}`, code: `NV${index + 1}`, name: `Nhân viên ${index + 1}`,
  submitted_branch_id: 'branch-1', submitted_branch_name: 'Cơ sở A',
  gross_amount: 490000, deduction_amount: 0, advances: 0, net_amount: 490000, paid: false,
  bank_name_snapshot: 'MB', bank_account_snapshot: '00123456', bank_holder_snapshot: 'NHAN VIEN',
}));

beforeEach(() => {
  jest.clearAllMocks();
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  Object.defineProperty(window, 'crypto', { configurable: true, value: { randomUUID: () => 'payroll-request-001' } });
  financeApi.get.mockImplementation((path) => {
    if (path.endsWith('/options')) return Promise.resolve({ employees: [], branches: [{ id: 'branch-1', name: 'Cơ sở A' }] });
    if (path.endsWith('/payment-history')) return Promise.resolve([]);
    return Promise.resolve({ status: 'CONFIRMED', version: 1, advances: [], rows });
  });
  financeApi.post.mockResolvedValue({});
  financeApi.downloadPost.mockResolvedValue(undefined);
});

test('shows the salary metrics before filters and applies search to those figures', async () => {
  render(<PayrollPage />);
  const summary = screen.getByText('Còn phải trả').parentElement;
  await within(summary).findByText('9800000đ');
  const month = screen.getByLabelText('Tháng lương');
  expect(summary.compareDocumentPosition(month) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

  fireEvent.change(screen.getByLabelText('Tìm nhân viên'), { target: { value: 'Không khớp' } });
  expect(within(summary).getByText('0đ')).toBeInTheDocument();
});

test('selects all twenty employees across pages, exports the same recipients and records a bank batch once', async () => {
  render(<PayrollPage />);
  await screen.findByRole('option', { name: 'Cơ sở A' });
  fireEvent.change(screen.getByLabelText('Cơ sở'), { target: { value: 'branch-1' } });

  const register = within(screen.getByRole('region', { name: 'Bảng lương chi tiết' }));
  expect(register.getAllByRole('checkbox')).toHaveLength(10);
  fireEvent.click(screen.getByRole('button', { name: 'Chọn tất cả 20 nhân viên' }));
  fireEvent.click(screen.getByRole('button', { name: /Phân trang lương nhân viên: trang sau/ }));
  expect(register.getByRole('checkbox', { name: 'Chọn Nhân viên 20' })).toBeChecked();
  expect(screen.getByText('Đã chọn 20 nhân viên · 9800000đ')).toBeInTheDocument();
  fireEvent.click(register.getByRole('checkbox', { name: 'Chọn Nhân viên 20' }));
  expect(screen.getByText('Đã chọn 19 nhân viên · 9310000đ')).toBeInTheDocument();
  fireEvent.click(register.getByRole('checkbox', { name: 'Chọn Nhân viên 20' }));

  const selection = { version: 1, branchId: 'branch-1', employeeIds: rows.map((row) => row.employee_id) };
  fireEvent.click(screen.getByRole('button', { name: 'Lập lô chuyển khoản' }));
  const dialog = within(screen.getByRole('dialog', { name: 'Lô chuyển khoản lương' }));
  fireEvent.click(dialog.getByRole('button', { name: 'Tải Excel người nhận' }));
  await waitFor(() => expect(financeApi.downloadPost).toHaveBeenCalledWith(
    '/accounting/payroll/2026-11/payment-batches/export', selection, 'Danh-sach-chuyen-luong-2026-11.xlsx'));

  fireEvent.change(dialog.getByLabelText('Ngân hàng chuyển'), { target: { value: 'MB' } });
  fireEvent.change(dialog.getByLabelText('Mã giao dịch lô'), { target: { value: 'BANK-BATCH-001' } });
  expect(dialog.getByRole('button', { name: 'Ghi nhận 20 nhân viên' })).toBeDisabled();
  fireEvent.click(dialog.getByRole('checkbox', { name: /Tôi đã đối chiếu/ }));
  fireEvent.click(dialog.getByRole('button', { name: 'Ghi nhận 20 nhân viên' }));

  await waitFor(() => expect(financeApi.post).toHaveBeenCalledWith('/accounting/payroll/2026-11/payment-batches', {
    requestId: 'payroll-request-001', selection, paymentDate: '2026-12-05', bankName: 'MB', reference: 'BANK-BATCH-001',
  }));
  await screen.findByRole('heading', { name: 'Lịch sử thanh toán lương' });
  expect(screen.getByLabelText('Tháng thanh toán')).toHaveValue('2026-12');
  expect(financeApi.get).toHaveBeenCalledWith('/accounting/payroll/payment-history', { month: '2026-12', branchId: 'branch-1' });
  expect(financeApi.post).toHaveBeenCalledTimes(1);
});

test('groups batch history while keeping legacy results visible in the payment month', async () => {
  const batch = {
    batch_id: 'batch-1', payment_date: '2026-12-04', payroll_month: '2026-11', branch_name: 'Cơ sở A',
    external_reference: 'LOT-01', bank_name: 'MB', status: 'PAID', recorded_by: 'ketoan',
  };
  financeApi.get.mockImplementation((path) => path.endsWith('/payment-history') ? Promise.resolve([
    { ...batch, id: 'p1', employee_name: 'Nhân viên 1', amount: 490000 },
    { ...batch, id: 'p2', employee_name: 'Nhân viên 2', amount: 490000 },
    { ...batch, id: 'p3', batch_id: null, external_reference: 'LEGACY', status: 'FAILED', amount: 80000 },
  ]) : Promise.resolve({ rows: [], branches: [] }));

  render(<PayrollPage />);
  fireEvent.click(screen.getByRole('button', { name: 'Lịch sử thanh toán' }));
  const history = within(screen.getByRole('region', { name: 'Lịch sử thanh toán lương' }));
  await history.findByText('LOT-01');
  expect(history.getByText('LEGACY')).toBeInTheDocument();
  expect(history.getByText('MB · 2 người')).toBeInTheDocument();
  expect(history.getAllByRole('row')).toHaveLength(3);
  fireEvent.click(within(history.getByText('LOT-01').closest('tr')).getByRole('button', { name: 'Chi tiết' }));
  const detail = within(screen.getByRole('dialog', { name: 'Chi tiết lô chuyển khoản' }));
  expect(detail.getByText('Nhân viên 2')).toBeInTheDocument();
});
