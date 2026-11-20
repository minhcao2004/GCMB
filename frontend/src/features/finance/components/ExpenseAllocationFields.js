import { useMemo } from 'react';
import { calculateExpenseAllocation, formatVndInteger } from '../utils/expenseAllocation';

const monthLabel = (value) => {
  const [year, month] = value.split('-');
  return `${month}/${year}`;
};

export default function ExpenseAllocationFields({ amount, months, startMonth, onMonthsChange, onStartMonthChange, disabled = false }) {
  const preview = useMemo(() => calculateExpenseAllocation(amount, months, startMonth), [amount, months, startMonth]);
  const readyToPreview = String(amount || '').length > 0 && String(months || '').length > 0 && String(startMonth || '').length > 0;

  return <div className="finance-allocation-fields">
    <label>Số tháng phân bổ *<input required type="number" min="2" max="60" step="1" value={months ?? ''} disabled={disabled} onChange={(event) => onMonthsChange(event.target.value)} /></label>
    <label>Tháng bắt đầu *<input required type="month" value={startMonth || ''} disabled={disabled} onChange={(event) => onStartMonthChange(event.target.value)} /></label>
    {readyToPreview && preview.error && <p className="finance-allocation-error" role="alert">{preview.error}</p>}
    {readyToPreview && !preview.error && <div className="finance-allocation-preview">
      <strong>Lịch phân bổ xem trước</strong>
      <div className="finance-allocation-preview-scroll"><table><thead><tr><th>Tháng</th><th>Số tiền ghi nhận</th></tr></thead>
        <tbody>{preview.lines.map((line) => <tr key={line.sequence}><td>{monthLabel(line.periodMonth)}</td><td>{formatVndInteger(line.amount)}đ</td></tr>)}</tbody>
      </table></div>
      <small>Backend sẽ tính lại lịch phân bổ khi ghi nhận thanh toán.</small>
    </div>}
  </div>;
}
