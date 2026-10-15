import { fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { bookingApi } from '../services/bookingApi';
import BookingCreatePage from './BookingCreatePage';
import { AppShell } from './BookingManagementPage';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({ useNavigate: () => mockNavigate }));
jest.mock('../services/bookingApi', () => ({
  bookingApi: { availability: jest.fn(), listAll: jest.fn() },
}));

beforeEach(() => {
  window.sessionStorage.clear();
  bookingApi.availability.mockReset();
  bookingApi.listAll.mockReset();
  mockNavigate.mockReset();
});

test('does not offer a room whose guest has not checked out', async () => {
  bookingApi.availability.mockResolvedValue({
    rooms: [{ id: 'room-1', code: 'DEMO-MB-01', name: 'Music Box', capacity: 4 }],
    quote: { expectedAmount: 100000, depositRequired: 0 },
  });
  bookingApi.listAll.mockResolvedValue([{ id: 'booking-1', roomId: 'room-1', status: 'IN_HOUSE' }]);

  render(<BookingCreatePage
    options={{ roomTypes: [{ id: 'type-1', name: 'Music Box demo', combos: [{ id: 'combo-1', name: '2 giờ', durationMinutes: 120, price: 100000 }] }] }}
    optionsLoading={false} optionsError="" onBack={() => {}} onCreate={() => {}}
  />);

  expect(await screen.findByText(/Còn 0 phòng Music Box demo phù hợp/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /DEMO-MB-01/ })).not.toBeInTheDocument();
});

test('restores an unfinished booking after visiting the room screen and clears it when cancelled', async () => {
  bookingApi.availability.mockResolvedValue({
    rooms: [{ id: 'room-1', code: 'DEMO-MB-01', name: 'Music Box', capacity: 4 }],
    quote: { expectedAmount: 100000, depositRequired: 0 },
  });
  bookingApi.listAll.mockResolvedValue([]);
  const props = {
    user: { id: 'receptionist-1' },
    options: { roomTypes: [{ id: 'type-1', name: 'Music Box demo', combos: [{ id: 'combo-1', name: '2 giờ', durationMinutes: 120, price: 100000 }] }] },
    optionsLoading: false, optionsError: '', onBack: jest.fn(), onCreate: jest.fn(),
  };

  const { unmount } = render(<BookingCreatePage {...props} />);
  fireEvent.change(screen.getByPlaceholderText('Nhập họ tên khách'), { target: { value: 'Nguyễn An' } });
  fireEvent.change(screen.getByPlaceholderText('Nhập số điện thoại'), { target: { value: '0901234567' } });
  fireEvent.change(screen.getByPlaceholderText('Thông tin khách cần lưu ý'), { target: { value: 'Sinh nhật' } });
  fireEvent.click(await screen.findByRole('button', { name: /DEMO-MB-01/ }));
  unmount();

  render(<BookingCreatePage {...props} />);
  expect(screen.getByPlaceholderText('Nhập họ tên khách')).toHaveValue('Nguyễn An');
  expect(screen.getByPlaceholderText('Nhập số điện thoại')).toHaveValue('0901234567');
  expect(screen.getByPlaceholderText('Thông tin khách cần lưu ý')).toHaveValue('Sinh nhật');
  expect(await screen.findByRole('button', { name: /DEMO-MB-01/ })).toHaveClass('is-selected');

  fireEvent.click(screen.getByRole('button', { name: 'Hủy' }));
  expect(props.onBack).toHaveBeenCalledTimes(1);
  expect(window.sessionStorage.getItem('gcmb.reception.booking-create.v1:receptionist-1')).toBeNull();
});

test('Booking navigation returns to an unfinished form from the room screen', () => {
  window.sessionStorage.setItem('gcmb.reception.booking-create.v1:receptionist-1', JSON.stringify({ form: { contactName: 'Nguyễn An' } }));
  render(<AppShell user={{ id: 'receptionist-1' }} breadcrumb="Phòng" search="" onSearchChange={() => {}} onLogout={() => {}}><div>Room screen</div></AppShell>);

  fireEvent.click(screen.getByRole('button', { name: 'Booking' }));
  expect(mockNavigate).toHaveBeenCalledWith('/le-tan/bookings/new');
});

test('does not silently replace a draft room that is no longer available', async () => {
  window.sessionStorage.setItem('gcmb.reception.booking-create.v1:receptionist-1', JSON.stringify({ form: { roomId: 'room-1' } }));
  bookingApi.availability.mockResolvedValue({
    rooms: [{ id: 'room-2', code: 'DEMO-MB-02', name: 'Music Box', capacity: 4 }],
    quote: { expectedAmount: 100000, depositRequired: 0 },
  });
  bookingApi.listAll.mockResolvedValue([]);

  render(<BookingCreatePage
    user={{ id: 'receptionist-1' }}
    options={{ roomTypes: [{ id: 'type-1', name: 'Music Box demo', combos: [{ id: 'combo-1', name: '2 giờ', durationMinutes: 120, price: 100000 }] }] }}
    optionsLoading={false} optionsError="" onBack={() => {}} onCreate={() => {}}
  />);

  const replacement = await screen.findByRole('button', { name: /DEMO-MB-02/ });
  expect(await screen.findByRole('alert')).toHaveTextContent('Phòng đã chọn không còn trống');
  expect(replacement).not.toHaveClass('is-selected');
  expect(screen.getByText('Chưa chọn')).toBeInTheDocument();
});
