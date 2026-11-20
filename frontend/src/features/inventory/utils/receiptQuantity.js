/* global BigInt */
export const RECEIVING_UNITS = ['Thùng', 'Lốc', 'Hộp', 'Túi', 'Gói', 'Bao', 'Khay'];

export const newReceiptLine = (id) => ({
  id, inventoryItemId: '', quantity: '', unitCost: '', receivingUnit: '', customUnit: '', unitsPerPack: '',
});

export const receivingUnitName = (line, item) => line.receivingUnit === 'CUSTOM'
  ? line.customUnit.trim() : line.receivingUnit && line.receivingUnit !== 'BASE' ? line.receivingUnit : item?.unitName || 'đơn vị';

const scaledQuantity = (value) => {
  const text = String(value).trim();
  if (!/^\d+(?:\.\d{1,3})?$/.test(text)) return null;
  const [whole, fraction = ''] = text.split('.');
  return BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0'));
};

// BR-31: số lượng nhận phải dương. Quy đổi chính xác về đơn vị tồn kho, tối đa 3 số thập phân.
export const receiptQuantity = (line) => {
  const isPack = Boolean(line.receivingUnit && line.receivingUnit !== 'BASE');
  const received = scaledQuantity(line.quantity);
  if (received === null || received <= 0n) {
    return { error: 'Số lượng nhận phải lớn hơn 0 và tối đa 3 chữ số thập phân.' };
  }
  if (isPack && received % 1000n !== 0n) {
    // MSG44: áp dụng số nguyên cho số lượng kiện hàng; đơn vị gốc vẫn hỗ trợ hàng cân/đong.
    return { error: 'Số lượng nhận phải là số nguyên lớn hơn 0.' };
  }
  if (line.receivingUnit === 'CUSTOM' && !line.customUnit.trim()) {
    // BR-111, MSG02: tên đơn vị khác không được để trống hoặc chỉ có khoảng trắng.
    return { error: 'Vui lòng bổ sung tên đơn vị nhận.' };
  }
  const factor = isPack ? scaledQuantity(line.unitsPerPack) : 1000n;
  if (factor === null || factor <= 0n) {
    return { error: 'Quy cách phải lớn hơn 0 và tối đa 3 chữ số thập phân.' };
  }
  const quantity = received * factor / 1000n;
  if (quantity > 999999999999999999n) {
    return { error: 'Số lượng quy đổi vượt giới hạn tồn kho. Vui lòng giảm số lượng nhận hoặc quy cách.' };
  }
  const fraction = String(quantity % 1000n).padStart(3, '0').replace(/0+$/, '');
  return { value: `${quantity / 1000n}${fraction ? `.${fraction}` : ''}` };
};

// BR-33: làm tròn từng dòng đến đồng theo HALF_UP như backend, không dùng số thực nhị phân.
export const receiptAmount = (quantity, unitCost) => {
  const scaled = scaledQuantity(quantity);
  const cost = String(unitCost).trim();
  if (scaled === null || !/^\d+$/.test(cost) || BigInt(cost) > 999999999999999999n) return null;
  return (scaled * BigInt(cost) + 500n) / 1000n;
};
