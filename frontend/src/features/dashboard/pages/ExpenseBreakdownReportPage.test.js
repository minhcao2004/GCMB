import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import api from '../../../api/client';
import ExpenseBreakdownReportPage from './ExpenseBreakdownReportPage';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  Link: ({ to, children }) => <a href={to}>{children}</a>,
  useNavigate: () => mockNavigate,
  useSearchParams: () => require('react').useState(() => new URLSearchParams('month=2026-11&costType=OPEX')),
}));
jest.mock('../../../api/client', () => ({ get: jest.fn() }));
jest.mock('../../auth/services/authApi', () => ({ authApi: {} }));
jest.mock('../../booking/pages/BookingManagementPage', () => ({ AppShell: ({ children }) => children }));

const rows = [
  { costType: 'OPEX', categoryId: 'operations', categoryName: 'Vật tư', branchId: 'branch-1', branchName: 'Cơ sở A', amount: 340000, paymentCount: 2 },
  { costType: 'CAPEX', categoryId: 'investment', categoryName: 'Thiết bị', branchId: 'branch-1', branchName: 'Cơ sở A', amount: 150000, paymentCount: 1 },
];

beforeEach(() => {
  api.get.mockReset();
  api.get.mockImplementation((path, { params }) => Promise.resolve({ data: path.endsWith('/details')
    ? { rows: [] }
    : {
        branches: [{ id: 'branch-1', name: 'Cơ sở A' }],
        categories: rows.map((row) => ({ id: row.categoryId, name: row.categoryName })),
        rows: params.categoryId ? rows.filter((row) => row.categoryId === params.categoryId) : rows,
      } }));
});

test.each(['HEAD_OFFICE_MANAGER', 'ACCOUNTANT'])('shows the combined total before filters for %s', async (role) => {
  render(<ExpenseBreakdownReportPage initialUser={{ role }} />);

  const total = await screen.findByText('490.000 ₫');
  const filters = screen.getByRole('group', { name: 'Bộ lọc báo cáo chi phí' });
  expect(total.compareDocumentPosition(filters) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(screen.getByText('3 khoản chi')).toBeInTheDocument();
  expect(within(filters).queryByLabelText('Nhóm chi')).not.toBeInTheDocument();
  expect(screen.getByRole('columnheader', { name: 'Nhóm chi' })).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Quay lại báo cáo hiệu quả tháng' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Tải lại' })).toBeEnabled();
  expect(api.get).toHaveBeenCalledWith('/accounting/reports/expense-breakdown', expect.objectContaining({
    params: { month: '2026-11', basis: 'CASH' },
  }));

  fireEvent.change(within(filters).getByLabelText('Danh mục'), { target: { value: 'investment' } });
  await waitFor(() => expect(screen.getByText('Tổng chi phí').nextElementSibling).toHaveTextContent('150.000 ₫'));

  fireEvent.click(screen.getByRole('button', { name: 'Xem khoản chi' }));
  await waitFor(() => expect(api.get).toHaveBeenCalledWith('/accounting/reports/expense-breakdown/details', {
    params: { month: '2026-11', branchId: 'branch-1', categoryId: 'investment', costType: 'CAPEX', basis: 'CASH' },
  }));
  await screen.findByText('Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.');
});
