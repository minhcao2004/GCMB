import { fireEvent, render, screen, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import api from '../../../api/client';
import BranchOverviewDashboard from './BranchOverviewDashboard';

jest.mock('../../../api/client', () => ({ get: jest.fn() }));
jest.mock('../../dashboard/components/CustomerSourcesChart', () => () => null);
jest.mock('recharts', () => ({}));

const user = { branchId: 'branch-1', branchName: 'Cơ sở A' };

test('compares the three financial KPIs using the overview period', async () => {
  api.get.mockImplementation((path, { params }) => Promise.resolve({ data: {
    result: { id: user.branchId },
    scopePeriod: params.scopePeriod,
    scopeFrom: params.scopeFrom,
    scopeTo: params.scopeTo,
    reportSummary: { revenue: 1200, billCount: 5, fnbRevenue: 300 },
    reportComparison: { from: '2024-01-01', to: '2024-01-01', summary: { revenue: 1000, billCount: 10, fnbRevenue: 0 } },
  } }));
  render(<BranchOverviewDashboard user={user} />);

  const kpis = within(await screen.findByRole('group', { name: 'Các chỉ số vận hành chính' }));
  const revenue = within(kpis.getByText('Doanh thu').closest('article'));
  const transactions = within(kpis.getByText('Tổng giao dịch').closest('article'));
  const fnb = within(kpis.getByText('Doanh thu F&B').closest('article'));
  expect(revenue.getByText('+20%')).toBeInTheDocument();
  expect(transactions.getByText('−50%')).toBeInTheDocument();
  expect(fnb.getByText('Chưa có giá trị gốc để tính tỷ lệ so với tháng trước (01/01/2024)')).toBeInTheDocument();

  const scope = within(screen.getByRole('region', { name: 'Phạm vi báo cáo tổng quan' }));
  fireEvent.change(scope.getByLabelText('Xem theo'), { target: { value: 'DAY' } });
  expect(await revenue.findByText('ngày trước', { exact: true })).toBeInTheDocument();
  const day = scope.getByLabelText('Ngày').value;
  expect(api.get).toHaveBeenLastCalledWith('/branch-management/dashboard', {
    params: expect.objectContaining({ scopePeriod: 'DAY', scopeFrom: day, scopeTo: day, revenuePeriod: 'MONTH' }),
    headers: { 'X-GCMB-Branch-Id': user.branchId },
  });

  fireEvent.change(scope.getByLabelText('Xem theo'), { target: { value: 'YEAR' } });
  expect(await revenue.findByText('năm trước', { exact: true })).toBeInTheDocument();
  fireEvent.change(scope.getByLabelText('Xem theo'), { target: { value: 'RANGE' } });
  expect(await revenue.findByText('kỳ trước', { exact: true })).toBeInTheDocument();
});
