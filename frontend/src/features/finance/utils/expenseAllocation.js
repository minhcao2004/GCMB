export const MIN_ALLOCATION_MONTHS = 2;
export const MAX_ALLOCATION_MONTHS = 60;

export function calculateExpenseAllocation(amountValue, monthsValue, startMonth) {
  const amountText = String(amountValue ?? '').trim();
  if (!/^\d+$/.test(amountText)) {
    return { lines: [], error: 'Số tiền phân bổ phải là VND nguyên, không có phần thập phân.' };
  }

  const total = window.BigInt(amountText);
  const months = Number(monthsValue);
  if (!Number.isInteger(months) || months < MIN_ALLOCATION_MONTHS || months > MAX_ALLOCATION_MONTHS) {
    return { lines: [], error: 'Số tháng phân bổ phải từ 2 đến 60.' };
  }
  if (total <= 0n) return { lines: [], error: 'Số tiền phân bổ phải lớn hơn 0đ.' };
  if (total < window.BigInt(months)) return { lines: [], error: 'Số tiền phải đủ để mỗi tháng được phân bổ ít nhất 1đ.' };
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(String(startMonth || ''))) {
    return { lines: [], error: 'Vui lòng chọn tháng bắt đầu phân bổ.' };
  }

  const base = total / window.BigInt(months);
  const remainder = total % window.BigInt(months);
  const [year, month] = startMonth.split('-').map(Number);
  const lines = Array.from({ length: months }, (_, index) => {
    const offset = month - 1 + index;
    const periodMonth = `${year + Math.floor(offset / 12)}-${String((offset % 12) + 1).padStart(2, '0')}`;
    return { sequence: index + 1, periodMonth, amount: base + (window.BigInt(index) < remainder ? 1n : 0n) };
  });
  return { lines, error: '' };
}

export function formatVndInteger(value) {
  return window.BigInt(value).toLocaleString('vi-VN');
}
