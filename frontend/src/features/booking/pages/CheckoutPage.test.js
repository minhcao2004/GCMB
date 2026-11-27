import '@testing-library/jest-dom';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { authApi } from '../../auth/services/authApi';
import { checkoutApi } from '../services/checkoutApi';
import CheckoutPage from './CheckoutPage';

const mockNavigate = jest.fn();
const mockParams = jest.fn();
jest.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useParams: () => mockParams(),
}));
jest.mock('../../auth/services/authApi', () => ({ authApi: { me: jest.fn() } }));
jest.mock('../services/checkoutApi', () => ({ checkoutApi: { bill: jest.fn(), preview: jest.fn(), complete: jest.fn() } }));
jest.mock('./BookingManagementPage', () => ({ AppShell: ({ children }) => children }));

const bill = {
  id: 'bill-1',
  number: 'HD-DEMO',
  status: 'SETTLED',
  customer: 'Khách minh họa',
  room: '402 · Hongkong',
  branchName: 'GenZ Cinema Nhật Chiêu',
  branchPhone: '0838408881',
  branchTimezone: 'Asia/Ho_Chi_Minh',
  checkin: '2026-11-26T17:33:00Z',
  checkoutAt: '2026-11-26T23:50:00Z',
  grossAmount: 323000,
  discountAmount: 2500,
  amount: 320500,
  roundingAdjustment: 500,
  depositApplied: 20000,
  paid: 301000,
  remaining: 0,
  lines: [{ category: 'ROOM', description: 'Combo Premium Queen', quantity: 1, unitPrice: 323000, amount: 323000 }],
  payments: [{ method: 'CASH', amount: 100000 }, { method: 'BANK_TRANSFER', amount: 201000 }],
};

beforeEach(() => {
  authApi.me.mockResolvedValue({ role: 'RECEPTIONIST', branchName: 'Tên cơ sở trong phiên' });
  mockParams.mockReturnValue({ billId: 'bill-1' });
});

test('prints the settled bill with its own branch and recorded payment amounts', async () => {
  checkoutApi.bill.mockResolvedValue(bill);
  const print = jest.spyOn(window, 'print').mockImplementation(() => {});
  try {
    render(<CheckoutPage />);
    const button = await screen.findByRole('button', { name: 'In hóa đơn' });
    const receipt = within(screen.getByRole('article', { name: 'Phiếu tính tiền' }));

    expect(receipt.getByText(bill.branchName)).toBeInTheDocument();
    expect(receipt.queryByText('Tên cơ sở trong phiên')).toBeNull();
    expect(receipt.getByText(`Hotline ${bill.branchPhone}`)).toBeInTheDocument();
    expect(receipt.getByText('Giờ vào 00:33')).toBeInTheDocument();
    expect(receipt.getByText('Giờ ra 06:50')).toBeInTheDocument();
    expect(receipt.getByRole('columnheader', { name: 'Đơn giá' })).toBeInTheDocument();
    expect(receipt.getByText('Tổng cộng').parentElement).toHaveTextContent('321.000đ');
    expect(receipt.getByText('Tiền cọc đã áp dụng').parentElement).toHaveTextContent('20.000đ');
    expect(receipt.getByText('Tiền mặt').parentElement).toHaveTextContent('100.000đ');
    expect(receipt.getByText('Chuyển khoản').parentElement).toHaveTextContent('201.000đ');
    fireEvent.click(button);
    expect(print).toHaveBeenCalledTimes(1);
  } finally {
    print.mockRestore();
  }
});

test('shows the payment form before the bill is settled', async () => {
  checkoutApi.bill.mockResolvedValue({ ...bill, status: 'ISSUED', payments: [], remaining: 301000, paid: 0 });
  render(<CheckoutPage />);

  expect(await screen.findByRole('heading', { name: 'Phương thức thanh toán' })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'In hóa đơn' })).toBeNull();
  expect(screen.queryByRole('article', { name: 'Phiếu tính tiền' })).toBeNull();
});

test('allows issuing a bill when the deposit exceeds it', async () => {
  mockParams.mockReturnValue({ bookingId: 'booking-1' });
  checkoutApi.preview.mockResolvedValue({ amount: 100000, depositBalance: 120000, lines: bill.lines });
  render(<CheckoutPage />);

  fireEvent.click(await screen.findByRole('checkbox', { name: 'Đã kiểm tra phòng và xác nhận các khoản phí' }));
  expect(screen.getByRole('button', { name: 'Tiếp tục thanh toán' })).toBeEnabled();
  expect(screen.getByText(/Cọc dư 20\.000đ sẽ được gửi Kế toán/)).toBeInTheDocument();
});

test('completes a fully covered bill and shows the pending refund', async () => {
  const coveredBill = { ...bill, amount: 100000, depositApplied: 100000, depositRefundAmount: 20000,
    remaining: 0, paid: 0, payments: [], roundingAdjustment: 0 };
  checkoutApi.bill.mockResolvedValue({ ...coveredBill, status: 'ISSUED' });
  checkoutApi.complete.mockResolvedValue({ ...coveredBill, depositRefundStatus: 'REQUESTED' });
  render(<CheckoutPage />);

  fireEvent.click(await screen.findByRole('button', { name: 'Hoàn tất bill' }));
  expect(await screen.findByText('Chờ hoàn cọc · 20.000đ')).toBeInTheDocument();
  expect(checkoutApi.complete).toHaveBeenCalledWith('bill-1');
});
