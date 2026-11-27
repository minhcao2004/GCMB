const vietnamDate = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric',
});

export function formatDateLabel(value, fallback = '—') {
  if (!value) return fallback;
  const text = String(value);
  // Calendar dates carry no timezone. Timestamp values represent an instant.
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    const date = new Date(text + 'T00:00:00Z');
    if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== text) return fallback;
    return text.split('-').reverse().join('/');
  }
  if (!/^\d{4}-\d{2}-\d{2}T/.test(text)) return fallback;
  const date = new Date(text);
  return Number.isFinite(date.getTime()) ? vietnamDate.format(date) : fallback;
}
