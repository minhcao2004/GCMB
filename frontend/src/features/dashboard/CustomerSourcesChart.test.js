import '@testing-library/jest-dom';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import api from '../../api/client';
import CustomerSourcesChart from './CustomerSourcesChart';

jest.mock('../../api/client', () => ({ get: jest.fn() }));

beforeEach(() => api.get.mockReset());

test('head office can combine the chart date and its separate branch filter', async () => {
  api.get.mockImplementation(async (path, options) => {
    if (path === '/head-office/branches') return { data: [{ branchId: 'hoa-bang', branchName: 'Hoa Bằng' }] };
    return { data: { sources: [
      { channel: 'FACEBOOK', count: options.params.branchId ? 3 : 12 },
      { channel: 'ZALO', count: 0 },
      { channel: 'TIKTOK', count: 0 },
      { channel: 'INSTAGRAM', count: 0 },
    ] } };
  });

  render(<CustomerSourcesChart headOffice />);
  const chart = within(screen.getByRole('region', { name: 'Nguồn khách' }));
  expect(await chart.findByRole('status', { name: '' })).toBeInTheDocument();
  await waitFor(() => expect(chart.getByRole('status')).toHaveTextContent('Tổng 12 lượt đặt'));
  await chart.findByRole('option', { name: 'Hoa Bằng' });
  fireEvent.change(chart.getByLabelText('Cơ sở nguồn khách'), { target: { value: 'hoa-bang' } });
  fireEvent.change(chart.getByLabelText('Loại kỳ nguồn khách'), { target: { value: 'DAY' } });
  fireEvent.change(chart.getByLabelText('Ngày nguồn khách'), { target: { value: '2026-10-15' } });

  await waitFor(() => {
    expect(api.get).toHaveBeenLastCalledWith('/head-office/dashboard/customer-sources', expect.objectContaining({
      params: { period: 'DAY', date: '2026-10-15', branchId: 'hoa-bang' },
    }));
    expect(chart.getByRole('status')).toHaveTextContent('Tổng 3 lượt đặt');
  });
  expect(chart.getAllByRole('listitem')).toHaveLength(4);
  expect(chart.getByText('Instagram').closest('li')).toHaveTextContent('0');
});

test('branch manager changes the chart period within the selected branch', async () => {
  api.get.mockResolvedValue({ data: { sources: [] } });
  render(<CustomerSourcesChart branchId="hoa-bang" />);

  expect(screen.queryByLabelText('Cơ sở nguồn khách')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Loại kỳ nguồn khách'), { target: { value: 'YEAR' } });
  const yearSelect = screen.getByLabelText('Năm nguồn khách');
  const previousYear = yearSelect.options[1].value;
  fireEvent.change(yearSelect, { target: { value: previousYear } });

  await waitFor(() => {
    expect(api.get).toHaveBeenLastCalledWith('/branch-management/dashboard/customer-sources', expect.objectContaining({
      params: { period: 'YEAR', date: previousYear },
      headers: { 'X-GCMB-Branch-Id': 'hoa-bang' },
    }));
    expect(screen.getByRole('status')).toHaveTextContent('Tổng 0 lượt đặt');
  });
});
