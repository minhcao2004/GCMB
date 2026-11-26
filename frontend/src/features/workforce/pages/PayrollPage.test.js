import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import PayrollPage from './PayrollPage';
import { financeApi } from '../../finance/services/financeApi';

jest.mock('react-router-dom', () => ({ useSearchParams: () => [new URLSearchParams('month=2026-11')] }));
jest.mock('../../auth/services/authApi', () => ({ apiErrors: () => ({ form: 'Lỗi tải dữ liệu' }) }));
jest.mock('../../finance/services/financeApi', () => ({
  financeApi: { get: jest.fn(), post: jest.fn() },
  vnd: (value) => `${value}đ`,
  today: () => '2026-11-26',
  emptyMessage: 'Không có dữ liệu',
}));

beforeEach(() => {
  jest.clearAllMocks();
  Object.defineProperty(window, 'crypto', { configurable: true, value: { randomUUID: () => 'payroll-request-001' } });
  financeApi.get.mockImplementation((path) => Promise.resolve(path.endsWith('/options')
    ? { employees: [], branches: [], funds: [] }
    : {
        status: 'CONFIRMED', version: 1, advances: [],
        rows: [{ id: 'entry-1', employee_id: 'employee-1', code: 'NV01', name: 'Nhân viên A',
          gross_amount: 490000, deduction_amount: 0, advances: 0, net_amount: 490000, paid: false,
          bank_name_snapshot: 'MB', bank_account_snapshot: '123456', bank_holder_snapshot: 'NHAN VIEN A' }],
      }));
  financeApi.post.mockResolvedValue({});
});

test('shows salary summary before filters and updates it when searching', async () => {
  render(<PayrollPage />);
  const summary = await screen.findByText('Còn phải trả: 490000đ');
  const month = screen.getByLabelText('Tháng lương');
  expect(summary.compareDocumentPosition(month) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();

  fireEvent.change(screen.getByLabelText('Tìm nhân viên'), { target: { value: 'Không khớp' } });
  expect(screen.getByText('Còn phải trả: 0đ')).toBeInTheDocument();
});

test('submits salary results without asking for an accountant fund', async () => {
  render(<PayrollPage />);
  fireEvent.click(await screen.findByRole('checkbox', { name: 'Chọn Nhân viên A' }));
  fireEvent.click(screen.getByRole('button', { name: 'Ghi nhận trả lương (1)' }));
  expect(screen.queryByText('Quỹ kế toán chi tiền *')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Kết quả thực tế'), { target: { value: 'PAID' } });
  fireEvent.change(screen.getByLabelText(/Mã chứng từ/), { target: { value: 'PAYROLL-REFERENCE' } });
  fireEvent.click(screen.getByRole('checkbox', { name: /Tôi xác nhận/ }));
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận ghi nhận kết quả' }));

  await waitFor(() => expect(financeApi.post).toHaveBeenCalledWith('/accounting/payroll/2026-11/payments', {
    version: 1,
    payments: [{ employeeId: 'employee-1', result: 'PAID', reference: 'PAYROLL-REFERENCE', requestId: 'payroll-request-001' }],
  }));
});
