import { fireEvent, render, screen, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import { financeApi } from '../services/financeApi';
import FinancePage from './FinancePage';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  useLocation: () => ({ pathname: '/ke-toan/chi-phi', search: '?section=plus&tab=requests' }),
  useNavigate: () => mockNavigate,
}));
jest.mock('../../../api/client', () => ({}));
jest.mock('../services/financeApi', () => ({ financeApi: { load: jest.fn() } }));

test('filters pending expenses by their expense date, branch and status', async () => {
  const rows = [
    ['Chi tháng trước', '2026-10-31', 'branch-a', 'WAITING_ACCOUNTANT'],
    ['Chi đầu tháng', '2026-11-01', 'branch-a', 'WAITING_ACCOUNTANT'],
    ['Chi cơ sở khác', '2026-11-01', 'branch-b', 'WAITING_ACCOUNTANT'],
    ['Chi chờ cấp cao', '2026-11-01', 'branch-a', 'WAITING_HEAD_OFFICE'],
    ['Chi đã xong', '2026-11-01', 'branch-a', 'PAID'],
    ['Chi cuối tháng', '2026-11-30', 'branch-a', 'WAITING_ACCOUNTANT'],
  ];
  financeApi.load.mockResolvedValue({
    branches: [{ id: 'branch-a', name: 'Cơ sở A' }, { id: 'branch-b', name: 'Cơ sở B' }],
    requests: rows.map(([description, expense_on, branch_id, status]) => ({
      id: description, description, expense_on, branch_id, status, amount: 60000,
    })),
  });
  render(<FinancePage user={{ role: 'ACCOUNTANT' }} />);
  const table = within(screen.getByRole('region', { name: 'Đề nghị chờ xử lý của kế toán' }));
  await table.findByText('Chi tháng trước');

  fireEvent.change(screen.getByLabelText('Từ ngày'), { target: { value: '2026-11-01' } });
  fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-11-01' } });
  fireEvent.change(screen.getByLabelText('Cơ sở'), { target: { value: 'branch-a' } });
  fireEvent.change(screen.getByLabelText('Trạng thái'), { target: { value: 'WAITING_ACCOUNTANT' } });

  expect(table.getAllByRole('row')).toHaveLength(2);
  expect(table.getByText('Chi đầu tháng')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Đến ngày'), { target: { value: '2026-10-31' } });
  expect(screen.getByRole('alert')).toHaveTextContent('Ngày bắt đầu lọc phải bằng hoặc trước ngày kết thúc lọc.');
  expect(table.queryByText('Chi đầu tháng')).not.toBeInTheDocument();
});
