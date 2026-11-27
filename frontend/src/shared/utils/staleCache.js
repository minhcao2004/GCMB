/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 27/11/2026
 * Description: Giữ kết quả dashboard gần nhất trong bộ nhớ tab để hiển thị ngay khi mở lại trang, rồi tải mới ở nền.
 */

// Chỉ lưu trong bộ nhớ JavaScript (mất khi tải lại trang); không ghi số liệu tài chính vào localStorage/sessionStorage.
const entries = new Map();
const MAX_ENTRIES = 50;

export function staleKey(...parts) {
  return JSON.stringify(parts);
}

export function readStale(key) {
  return entries.get(key);
}

export function writeStale(key, value) {
  if (!entries.has(key) && entries.size >= MAX_ENTRIES) entries.delete(entries.keys().next().value);
  entries.set(key, value);
}

// Gọi khi đăng nhập, đăng xuất hoặc đổi cơ sở để không hiển thị số liệu của phiên/phạm vi khác.
export function clearStale() {
  entries.clear();
}
