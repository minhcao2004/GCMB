import { fireEvent, render, screen, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import { authApi } from '../../auth/services/authApi';
import { depositApi } from '../services/depositApi';
import AccountantRefundPage from './AccountantRefundPage';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({ useNavigate: () => mockNavigate }));
jest.mock('./BookingManagementPage', () => ({ AppShell: ({ children }) => children }));
jest.mock('../../auth/services/authApi', () => ({ authApi: { me: jest.fn() }, apiErrors: () => ({}) }));
jest.mock('../services/depositApi', () => ({ depositApi: { refundRequests: jest.fn() } }));

test('paginates the queue and combines branch and inclusive Vietnamese request dates', async () => {
  authApi.me.mockResolvedValue({ role: 'ACCOUNTANT' });
  depositApi.refundRequests.mockResolvedValue({
    branchName: 'Tất cả cơ sở',
    items: Array.from({ length: 12 }, (_, index) => ({
      id: `refund-${index + 1}`, customerName: `Khách ${index + 1}`, amount: 60000, status: 'REQUESTED',
      branchId: index === 10 ? 'branch-b' : 'branch-a', branchName: index === 10 ? 'Cơ sở B' : 'Cơ sở A',
      requestedAt: index >= 10 ? '2026-11-20T18:00:00Z' : '2026-11-20T16:59:00Z',
    })),
  });
  render(<AccountantRefundPage />);
  const table = within(screen.getByRole('table'));
  const pager = await screen.findByRole('navigation', { name: 'Phân trang hoàn tiền cọc' });
  expect(table.getAllByRole('row')).toHaveLength(11);
  fireEvent.click(within(pager).getByRole('button', { name: /trang sau/ }));
  expect(table.getAllByRole('row')).toHaveLength(3);
  expect(within(screen.getByRole('complementary', { name: 'Chi tiết yêu cầu hoàn cọc' })).getByText('Khách 11')).toBeInTheDocument();

  fireEvent.change(screen.getByLabelText('Ngày gửi từ'), { target: { value: '2026-11-21' } });
  fireEvent.change(screen.getByLabelText('Ngày gửi đến'), { target: { value: '2026-11-21' } });
  expect(table.getByText('Khách 11')).toBeInTheDocument();
  expect(table.getByText('Khách 12')).toBeInTheDocument();
  expect(table.queryByText('Khách 10')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Cơ sở'), { target: { value: 'branch-a' } });
  expect(table.getByText('Khách 12')).toBeInTheDocument();
  expect(table.queryByText('Khách 11')).not.toBeInTheDocument();

  fireEvent.change(screen.getByLabelText('Ngày gửi từ'), { target: { value: '2026-11-22' } });
  expect(screen.getByRole('alert')).toHaveTextContent('Ngày bắt đầu lọc phải bằng hoặc trước ngày kết thúc lọc.');
  expect(table.queryByText('Khách 12')).not.toBeInTheDocument();

  fireEvent.change(screen.getByLabelText('Cơ sở'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('Ngày gửi từ'), { target: { value: '' } });
  fireEvent.change(screen.getByLabelText('Ngày gửi đến'), { target: { value: '' } });
  expect(table.getByText('Khách 1')).toBeInTheDocument();
  expect(table.queryByText('Khách 11')).not.toBeInTheDocument();
});
