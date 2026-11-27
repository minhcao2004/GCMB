import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import api from '../../../api/client';
import { authApi } from '../../auth/services/authApi';
import AccountantDashboard from './AccountantDashboard';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({ useNavigate: () => mockNavigate, Link: ({ to, children }) => <a href={to}>{children}</a> }));
jest.mock('../../../api/client', () => ({ get: jest.fn() }));
jest.mock('../../auth/services/authApi', () => ({ authApi: { me: jest.fn() } }));
jest.mock('../../booking/pages/BookingManagementPage', () => ({ AppShell: ({ children }) => children }));

test('compares all three cash figures and reloads the selected calendar scope', async () => {
  authApi.me.mockResolvedValue({ role: 'ACCOUNTANT' });
  api.get.mockImplementation((path) => Promise.resolve({ data: path.endsWith('/cash-flow') ? {
    current: { income: 1200, expense: 400, net: 800 }, previous: { income: 1000, expense: 500, net: 500 },
    comparisonFrom: '2026-10-01', comparisonTo: '2026-10-26',
    branches: [{ id: 'a', name: 'Cơ sở A' }],
    rows: [{ id: 'expense-1', cashDate: '2026-11-26', kind: 'SALARY', amount: -400 }],
  } : path.endsWith('/unreconciled-count') ? { count: 2 } : { items: [] } }));
  render(<AccountantDashboard/>);
  const metrics = within(screen.getByRole('region', { name: 'Chỉ số tài chính' }));
  await metrics.findByText('+20%');
  expect(metrics.getByText('−20%')).toBeInTheDocument();
  expect(metrics.getByText('+60%')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Cơ cấu khoản chi' })).toBeInTheDocument();
  expect(screen.queryByRole('heading', { name: 'Thu theo phương thức' })).not.toBeInTheDocument();
  expect(screen.getByText('Lương và ứng lương')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Loại kỳ tổng quan tài chính'), { target: { value: 'DAY' } });
  fireEvent.change(screen.getByLabelText('Ngày tổng quan tài chính'), { target: { value: '2026-11-26' } });
  fireEvent.change(screen.getByLabelText('Cơ sở'), { target: { value: 'a' } });
  await waitFor(() => expect(api.get).toHaveBeenCalledWith('/accounting/reports/cash-flow', {
    params: { from: '2026-11-26', to: '2026-11-26', branchId: 'a', compare: 'previous-day' },
  }));
});
