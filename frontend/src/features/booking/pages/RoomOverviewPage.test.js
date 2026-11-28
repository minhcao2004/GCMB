import { act, fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { ToastStack } from '../../../shared/components/Feedback';
import { authApi } from '../../auth/services/authApi';
import { bookingApi } from '../services/bookingApi';
import { checkoutApi } from '../services/checkoutApi';
import RoomOverviewPage from './RoomOverviewPage';
import WalkInDialog from './WalkInDialog';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  Link: ({ to, ...props }) => <a href={to} {...props} />,
  useNavigate: () => mockNavigate,
  useLocation: () => ({ state: null }),
  useParams: () => ({}),
}));
jest.mock('../../auth/services/authApi', () => ({
  authApi: { me: jest.fn(), logout: jest.fn() },
  apiErrors: () => ({ form: 'Không thể tải dữ liệu.' }),
}));
jest.mock('../services/bookingApi', () => ({
  bookingApi: { options: jest.fn(), rooms: jest.fn(), listAll: jest.fn(), availability: jest.fn(), detail: jest.fn(), openWalkIn: jest.fn() },
}));
jest.mock('../services/checkoutApi', () => ({ checkoutApi: { preview: jest.fn() } }));

test('dismisses shared notifications after seven seconds and waits while the close control is focused', () => {
  jest.useFakeTimers();
  try {
    const onClose = jest.fn();
    const first = render(<ToastStack notice="Đã lưu." onCloseNotice={onClose} />);
    act(() => jest.advanceTimersByTime(6999));
    expect(onClose).not.toHaveBeenCalled();
    act(() => jest.advanceTimersByTime(1));
    expect(onClose).toHaveBeenCalledTimes(1);
    first.unmount();

    onClose.mockClear();
    render(<ToastStack notice="Đã lưu." onCloseNotice={onClose} />);
    const closeButton = screen.getByRole('button', { name: 'Đóng thông báo' });
    closeButton.focus();
    act(() => jest.advanceTimersByTime(7000));
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.focusOut(closeButton, { relatedTarget: document.body });
    expect(onClose).toHaveBeenCalledTimes(1);
  } finally {
    jest.useRealTimers();
  }
});

test.each([
  [{ walkInBeforeSubmit: true }, /Chưa gửi yêu cầu mở phòng/, false],
  [{ response: { status: 503 } }, /Máy chủ báo lỗi \(503\)/, true],
  [{}, /Mất kết nối khi gửi yêu cầu/, true],
])('distinguishes walk-in request failures and preserves the uncertain warning', async (failure, message, disabled) => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  bookingApi.availability.mockResolvedValue({ rooms: [{ id: 'room-1' }], quote: { expectedAmount: 192000 } });
  bookingApi.openWalkIn.mockRejectedValue(failure);
  render(<WalkInDialog room={{ id: 'room-1', roomTypeId: 'type-1', code: 'Q02', name: 'Queen 02', capacity: 4 }}
    options={{ roomTypes: [{ id: 'type-1', combos: [] }] }} onClose={jest.fn()} onOpened={jest.fn()} />);
  const submit = await screen.findByRole('button', { name: 'Check-in & mở phòng' });
  await screen.findByText(/192\.000/);
  fireEvent.change(screen.getByLabelText(/Họ tên khách/), { target: { value: 'Khách' } });
  fireEvent.change(screen.getByLabelText(/Số điện thoại/), { target: { value: '0900000000' } });
  fireEvent.click(submit);
  expect(await screen.findByRole('alert')).toHaveTextContent(message);
  expect(submit.disabled).toBe(disabled);
  if (disabled) {
    fireEvent.change(screen.getByLabelText(/Họ tên khách/), { target: { value: 'Khách mới' } });
  }
  expect(screen.getByRole('alert')).toHaveTextContent(message);
});

test('renders the room page while data is loading and when no room is returned', async () => {
  authApi.me.mockResolvedValue({ username: 'reception', role: 'RECEPTIONIST' });
  bookingApi.options.mockResolvedValue({ branchName: 'Cơ sở Hải Phòng', timezone: 'Asia/Ho_Chi_Minh', roomTypes: [] });
  bookingApi.listAll.mockResolvedValue([]);
  bookingApi.rooms.mockResolvedValue([]);

  render(<RoomOverviewPage />);

  expect(screen.getByText('Đang kiểm tra phiên đăng nhập…')).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: 'Phòng', level: 1 })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'GenZ · Về Booking' })).toHaveAttribute('href', '/le-tan/bookings');
  expect(screen.queryByRole('button', { name: 'Tổng quan' })).not.toBeInTheDocument();
  expect(screen.queryByText('reception')).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Thông tin tài khoản' })).toBeEnabled();
  expect(screen.getByRole('button', { name: 'Đăng xuất' })).toBeEnabled();
  expect(await screen.findByText(/Không có phòng phù hợp với bộ lọc này/)).toBeInTheDocument();
});

test('keeps an in-house room occupied after its planned end', async () => {
  const booking = {
    id: 'booking-1', code: 'BK-01', status: 'IN_HOUSE', roomId: 'room-1',
    roomCode: 'DEMO-MB-01', roomName: 'Music Box', roomTypeName: 'Music Box demo',
    plannedStart: '2026-10-12T20:30:00+07:00',
    actualCheckin: '2026-10-12T20:39:00+07:00',
    plannedEnd: '2026-10-12T22:30:00+07:00',
  };
  authApi.me.mockResolvedValue({ username: 'reception', role: 'RECEPTIONIST' });
  bookingApi.options.mockResolvedValue({ branchName: 'Cơ sở Hải Phòng', timezone: 'Asia/Ho_Chi_Minh', roomTypes: [{ id: 'type-1', name: 'Music Box demo', capacity: 4 }] });
  bookingApi.listAll.mockImplementation((params) => Promise.resolve(params.status === 'IN_HOUSE' ? [booking] : []));
  bookingApi.availability.mockResolvedValue({ rooms: [{ id: 'room-1', code: 'DEMO-MB-01', name: 'Music Box', capacity: 4 }] });
  bookingApi.rooms.mockResolvedValue([{ id: 'room-1', code: 'DEMO-MB-01', name: 'Music Box', capacity: 4, roomTypeName: 'Music Box demo', operatingStatus: 'AVAILABLE' }]);
  bookingApi.detail.mockResolvedValue({ booking, charges: [{ status: 'ACTIVE', kind: 'COMBO', description: 'Gói 2 giờ Music Box' }], meter: null });
  checkoutApi.preview.mockResolvedValue({ amount: 5701740, depositBalance: 60000, payableAmount: 5642000 });

  render(<RoomOverviewPage />);

  const room = await screen.findByRole('button', { name: /DEMO-MB-01 Music Box/ });
  expect(room).toHaveTextContent('Đang sử dụng');
  expect(screen.getByRole('button', { name: /Đang sử dụng 1/ })).toBeInTheDocument();
  expect(await screen.findByText('01:51:00')).toBeInTheDocument();
  expect(await screen.findByText('Gói 2 giờ Music Box')).toBeInTheDocument();
  expect(screen.queryByText('BK-01')).not.toBeInTheDocument();
  expect(screen.getByText('Đã dừng tại giờ trả dự kiến')).toBeInTheDocument();
  expect(await screen.findByText('5.642.000đ')).toBeInTheDocument();
  expect(checkoutApi.preview).toHaveBeenCalledWith('booking-1');
});
