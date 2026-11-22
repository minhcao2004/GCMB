import { useEffect, useState } from 'react';
import api from '../../api/client';
import './imported-source-report.css';

const tabs = [
  ['cashflow', 'Thu chi'], ['revenue', 'Doanh thu'], ['cost', 'Chi phí'],
  ['method', 'Phương thức thanh toán'], ['branch', 'So sánh cơ sở'],
];
const money = (value) => `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(Number(value) || 0)} ₫`;
const dateLabel = (value) => value ? `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)}` : '—';
const reportNames = {
  cashflow: 'Báo cáo thu chi theo thời gian', revenue: 'Doanh thu theo thời gian',
  cost: 'Chi phí theo thời gian', method: 'Khoản thu theo phương thức thanh toán',
  branch: 'So sánh các cơ sở',
};

function paymentMethod(row) {
  if (row.paymentMethod) return row.paymentMethod;
  if (Number(row.cashAmount) && Number(row.bankAmount)) return 'Tiền mặt + chuyển khoản';
  if (Number(row.cashAmount)) return 'Tiền mặt';
  if (Number(row.bankAmount)) return 'Chuyển khoản';
  return 'Chưa ghi phương thức';
}

function SourceChart({ type, rows, financialReport }) {
  if (type === 'cost') {
    const data = (financialReport?.series || []).map((row) => ({ day: row.label, value: Number(row.expenses) || 0 }));
    if (!data.length) return <div className="isr-empty-chart">Chưa có khoản chi vận hành trong kỳ đã chọn.</div>;
    const width = 760; const height = 260; const left = 52; const right = 18; const top = 18; const bottom = 38;
    const innerWidth = width - left - right; const innerHeight = height - top - bottom;
    const max = Math.max(1, ...data.map((item) => item.value));
    const x = (index) => left + (data.length === 1 ? innerWidth / 2 : index * innerWidth / (data.length - 1));
    const y = (value) => top + innerHeight - value / max * innerHeight;
    const points = data.map((item, index) => `${x(index)},${y(item.value)}`).join(' ');
    const tickEvery = Math.max(1, Math.ceil(data.length / 8));
    return <div className="isr-line-chart-scroll"><svg viewBox={`0 0 ${width} ${height}`} className="isr-line-chart" role="img" aria-label="Chi phí vận hành theo thời gian">
      {[0, 1, 2, 3].map((tick) => { const value = max * (3 - tick) / 3; const lineY = top + innerHeight * tick / 3; return <g key={tick}><line x1={left} x2={width - right} y1={lineY} y2={lineY} /><text x={left - 8} y={lineY + 4} textAnchor="end">{new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 }).format(value)}</text></g>; })}
      <polyline points={points} />{data.map((item, index) => <g key={`${item.day}-${index}`}><circle cx={x(index)} cy={y(item.value)} r="4"><title>{item.day} · {money(item.value)}</title></circle>{(index % tickEvery === 0 || index === data.length - 1) && <text x={x(index)} y={height - 10} textAnchor="middle">{item.day}</text>}</g>)}
    </svg></div>;
  }
  if (type === 'method') {
    const methods = new Map();
    rows.filter((row) => row.recordType === 'BOOKING' || row.recordType === 'KIOT_INVOICE').forEach((row) => {
      const method = paymentMethod(row);
      methods.set(method, (methods.get(method) || 0) + Number(row.paidAmount || 0));
    });
    const data = [...methods.entries()].sort((a, b) => b[1] - a[1]);
    const max = Math.max(1, ...data.map((item) => item[1]));
    return data.length ? <div className="isr-method-chart" role="img" aria-label="Tổng thanh toán theo phương thức">
      {data.map(([name, value]) => <div className="isr-method-row" key={name}><span>{name}</span><div><i style={{ width: `${value / max * 100}%` }} /></div><strong>{money(value)}</strong></div>)}
    </div> : <div className="isr-empty-chart">Chưa có dữ liệu phương thức thanh toán trong kỳ.</div>;
  }
  if (type === 'branch') {
    const totals = new Map();
    rows.filter((row) => row.recordType === 'KIOT_INVOICE').forEach((row) => totals.set(row.branchName, (totals.get(row.branchName) || 0) + Number(row.amount || 0)));
    const data = [...totals.entries()].sort((a, b) => b[1] - a[1]);
    const max = Math.max(1, ...data.map((item) => item[1]));
    return data.length ? <div className="isr-method-chart" role="img" aria-label="Doanh thu hóa đơn theo cơ sở">
      {data.map(([name, value]) => <div className="isr-method-row" key={name}><span>{name}</span><div><i style={{ width: `${value / max * 100}%` }} /></div><strong>{money(value)}</strong></div>)}
    </div> : <div className="isr-empty-chart">Chưa có hóa đơn nguồn để so sánh cơ sở.</div>;
  }

  if (type === 'cashflow') {
    const daily = new Map();
    rows.filter((row) => row.recordType === 'KIOT_INVOICE').forEach((row) => {
      const label = `${row.businessDate.slice(8, 10)}/${row.businessDate.slice(5, 7)}`;
      const item = daily.get(label) || { income: 0, expense: 0 };
      item.income += Number(row.paidAmount || 0);
      daily.set(label, item);
    });
    (financialReport?.series || []).forEach((row) => {
      const item = daily.get(row.label) || { income: 0, expense: 0 };
      item.expense += Number(row.expenses || 0);
      daily.set(row.label, item);
    });
    const data = [...daily.entries()].sort((a, b) => a[0].split('/').reverse().join('-').localeCompare(b[0].split('/').reverse().join('-')));
    if (!data.length) return <div className="isr-empty-chart">Chưa có dữ liệu thu chi trong kỳ.</div>;
    const width = 760; const height = 260; const left = 52; const right = 18; const top = 18; const bottom = 38;
    const innerWidth = width - left - right; const innerHeight = height - top - bottom;
    const max = Math.max(1, ...data.flatMap((item) => [item[1].income, item[1].expense]));
    const x = (index) => left + (data.length === 1 ? innerWidth / 2 : index * innerWidth / (data.length - 1));
    const y = (value) => top + innerHeight - value / max * innerHeight;
    const points = (key) => data.map((item, index) => `${x(index)},${y(item[1][key])}`).join(' ');
    const tickEvery = Math.max(1, Math.ceil(data.length / 8));
    return <div className="isr-line-chart-scroll"><div className="isr-chart-legend"><span><i className="isr-income-dot" />Tiền thu</span><span><i className="isr-expense-dot" />Chi phí vận hành</span></div><svg viewBox={`0 0 ${width} ${height}`} className="isr-line-chart" role="img" aria-label="Tiền thu và chi phí vận hành theo ngày">
      {[0, 1, 2, 3].map((tick) => { const value = max * (3 - tick) / 3; const lineY = top + innerHeight * tick / 3; return <g key={tick}><line x1={left} x2={width - right} y1={lineY} y2={lineY} /><text x={left - 8} y={lineY + 4} textAnchor="end">{new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 }).format(value)}</text></g>; })}
      <polyline className="isr-line-income" points={points('income')} /><polyline className="isr-line-expense" points={points('expense')} />
      {data.map(([day, values], index) => <g key={day}><circle className="isr-point-income" cx={x(index)} cy={y(values.income)} r="3.5"><title>{day} · Thu {money(values.income)}</title></circle><circle className="isr-point-expense" cx={x(index)} cy={y(values.expense)} r="3.5"><title>{day} · Chi {money(values.expense)}</title></circle>{(index % tickEvery === 0 || index === data.length - 1) && <text x={x(index)} y={height - 10} textAnchor="middle">{day}</text>}</g>)}
    </svg></div>;
  }

  const invoiceRows = rows.filter((row) => row.recordType === 'KIOT_INVOICE');
  if (!invoiceRows.length) return <div className="isr-empty-chart">Chưa có hóa đơn nguồn trong khoảng thời gian đã chọn.</div>;
  const daily = new Map();
  invoiceRows.forEach((row) => {
    const day = row.businessDate;
    daily.set(day, (daily.get(day) || 0) + Number(type === 'revenue' ? row.amount : row.paidAmount));
  });
  const data = [...daily.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  const width = 760; const height = 260; const left = 52; const right = 18; const top = 18; const bottom = 38;
  const innerWidth = width - left - right; const innerHeight = height - top - bottom;
  const max = Math.max(1, ...data.map((item) => item[1]));
  const x = (index) => left + (data.length === 1 ? innerWidth / 2 : index * innerWidth / (data.length - 1));
  const y = (value) => top + innerHeight - value / max * innerHeight;
  const points = data.map((item, index) => `${x(index)},${y(item[1])}`).join(' ');
  const tickEvery = Math.max(1, Math.ceil(data.length / 8));
  return <div className="isr-line-chart-scroll"><svg viewBox={`0 0 ${width} ${height}`} className="isr-line-chart" role="img" aria-label={type === 'revenue' ? 'Doanh thu hóa đơn KiotViet theo ngày' : 'Tiền khách đã thanh toán theo ngày'}>
    {[0, 1, 2, 3].map((tick) => { const value = max * (3 - tick) / 3; const lineY = top + innerHeight * tick / 3; return <g key={tick}><line x1={left} x2={width - right} y1={lineY} y2={lineY} /><text x={left - 8} y={lineY + 4} textAnchor="end">{new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 }).format(value)}</text></g>; })}
    <polyline points={points} />
    {data.map(([day, value], index) => <g key={day}><circle cx={x(index)} cy={y(value)} r="4"><title>{dateLabel(day)} · {money(value)}</title></circle>{(index % tickEvery === 0 || index === data.length - 1) && <text x={x(index)} y={height - 10} textAnchor="middle">{day.slice(8, 10)}/{day.slice(5, 7)}</text>}</g>)}
  </svg><p className="isr-chart-caption">{type === 'revenue' ? 'Doanh thu sau giảm giá theo ngày ghi trên hóa đơn.' : 'Số khách đã trả theo ngày ghi trên hóa đơn.'}</p></div>;
}

export default function ImportedSourceReport({ month, range, branchId, reportTotals, financialReport, operationalTransactions = [] }) {
  const [reportType, setReportType] = useState('cashflow');
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  useEffect(() => { setPage(1); }, [month, range.from, range.to, branchId]);
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    api.get('/accounting/reports/source-imports', { params: { ...range, ...(branchId ? { branchId } : {}) } })
      .then(({ data: result }) => { if (active) setData(result); })
      .catch((cause) => { if (active) setError(cause.response?.data?.detail || cause.response?.data?.message || 'Không tải được dữ liệu nguồn.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [range, branchId]);

  const allRows = data?.rows || [];
  const invoices = allRows.filter((row) => row.recordType === 'KIOT_INVOICE');
  const receipts = invoices.reduce((sum, row) => sum + Number(row.paidAmount || 0), 0);
  const expenses = Number(financialReport?.totals?.expenses ?? reportTotals?.expenses ?? 0);
  const expenseAvailable = financialReport?.totals?.expenses != null || reportTotals?.expenses != null;
  const ledgerExpenses = operationalTransactions.filter((row) => Number(row.amount) < 0).map((row) => ({
    id: `ledger-${row.id}`, businessDate: String(row.bookingDate || '').slice(0, 10), sourceKey: row.id,
    branchName: row.branchName || 'Toàn chuỗi', recordType: 'OPERATING_EXPENSE', paymentMethod: row.method || '—',
    paidAmount: row.amount, amount: row.amount, sourceSystem: 'LEDGER', reason: row.reason,
  }));
  const rowsByType = reportType === 'revenue' || reportType === 'branch'
    ? allRows.filter((row) => row.recordType === 'KIOT_INVOICE')
    : reportType === 'method' ? allRows.filter((row) => row.recordType === 'BOOKING' || row.recordType === 'KIOT_INVOICE')
      : reportType === 'cost' ? ledgerExpenses : [...allRows, ...ledgerExpenses];
  const filteredRows = rowsByType.filter((row) => {
    const haystack = [row.sourceKey, row.invoiceNumber, row.branchName, row.sourceSheet, row.recordType, row.paymentMethod, row.room, row.reason]
      .filter(Boolean).join(' ').toLocaleLowerCase('vi');
    return row.businessDate >= range.from && row.businessDate <= range.to
      && haystack.includes(query.trim().toLocaleLowerCase('vi'));
  });
  const pageCount = Math.max(1, Math.ceil(filteredRows.length / pageSize));
  const safePage = Math.min(page, pageCount);
  const visibleRows = filteredRows.slice((safePage - 1) * pageSize, safePage * pageSize);
  const amountLabel = reportType === 'method' ? 'Tiền khách trả' : reportType === 'revenue' || reportType === 'branch' ? 'Doanh thu' : 'Số tiền';

  return <section className="imported-source-report" aria-labelledby="isr-title" aria-busy={loading}>
    <header className="isr-heading"><div><span className="isr-eyebrow">SỔ KẾ TOÁN</span><h2 id="isr-title">Báo cáo &amp; thống kê</h2></div></header>
    {allRows.some((row) => row.sourceSystem === 'SYNTHETIC_DEMO') && <p className="isr-data-notice" role="status"><strong>Báo cáo có dữ liệu mô phỏng.</strong> Các dòng mô phỏng được ghi nhãn trong bảng chi tiết.</p>}
    <nav className="isr-tabs" aria-label="Loại báo cáo">{tabs.map(([key, title]) => <button type="button" key={key} aria-pressed={reportType === key} onClick={() => { setReportType(key); setPage(1); }}>{title}</button>)}</nav>
    <div className="isr-kpis" aria-label="Tổng hợp báo cáo">
      <article><span>Thu khách hàng</span><strong>{money(receipts)}</strong><small>{invoices.length.toLocaleString('vi-VN')} hóa đơn</small></article>
      <article><span>Chi vận hành</span><strong>{expenseAvailable ? money(expenses) : '—'}</strong><small>Đã thanh toán</small></article>
      <article><span>Thuần vận hành</span><strong>{expenseAvailable ? money(receipts - expenses) : '—'}</strong><small>Thu − chi</small></article>
      <article><span>Giao dịch</span><strong>{(allRows.length + ledgerExpenses.length).toLocaleString('vi-VN')}</strong><small>{allRows.filter((row) => row.recordType === 'BOOKING').length.toLocaleString('vi-VN')} booking · {invoices.length.toLocaleString('vi-VN')} hóa đơn</small></article>
    </div>
    <article className="isr-card">
      <div className="isr-card-heading"><div><h3>{reportNames[reportType]}</h3><p>{reportType === 'cost' ? 'Chi phí vận hành theo kỳ đã chọn.' : 'Dữ liệu theo ngày và cơ sở đang chọn.'}</p></div><span>{reportType === 'cost' ? `${money(expenses)} chi phí` : `${filteredRows.length.toLocaleString('vi-VN')} giao dịch`}</span></div>
      {error ? <div className="isr-state isr-error" role="alert">{error}</div> : loading ? <div className="isr-state" role="status">Đang tải báo cáo…</div> : <SourceChart type={reportType} rows={allRows} financialReport={financialReport} />}
    </article>
    <article className="isr-card isr-table-card">
      <div className="isr-card-heading"><div><h3>Chi tiết giao dịch</h3></div><span>{filteredRows.length.toLocaleString('vi-VN')} dòng</span></div>
      <div className="isr-table-tools">
        <label className="isr-search-filter">Tìm kiếm<input type="search" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder="Mã giao dịch, cơ sở…" /></label>
        <label className="isr-page-size-filter">Số dòng<select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPage(1); }}><option value="10">10 dòng</option><option value="25">25 dòng</option><option value="50">50 dòng</option></select></label>
      </div>
      <div className="isr-table-scroll"><table><caption>{reportNames[reportType]} · {dateLabel(range.from)} – {dateLabel(range.to)}</caption><thead><tr><th scope="col">Ngày</th><th scope="col">Mã giao dịch</th><th scope="col">Cơ sở</th><th scope="col">Danh mục</th><th scope="col">Phương thức</th><th scope="col" className="isr-number">{amountLabel}</th><th scope="col">Nguồn</th></tr></thead><tbody>
        {visibleRows.map((row) => <tr key={row.id}><td>{dateLabel(row.businessDate)}</td><td>{row.invoiceNumber || row.sourceKey || `${row.sourceSheet || 'LEDGER'}-${row.id}`}</td><td>{row.branchName}</td><td>{row.recordType === 'KIOT_INVOICE' ? 'Hóa đơn doanh thu' : row.recordType === 'BOOKING' ? 'Booking' : 'Chi phí vận hành'}</td><td>{row.recordType === 'KIOT_INVOICE' ? (row.paymentMethod || 'Theo hóa đơn') : row.recordType === 'BOOKING' ? paymentMethod(row) : (row.paymentMethod || '—')}</td><td className="isr-number">{money(reportType === 'revenue' || reportType === 'branch' ? row.amount : row.paidAmount)}</td><td><span className={`isr-origin${row.sourceSystem === 'SYNTHETIC_DEMO' ? ' is-synthetic' : row.sourceSystem === 'LEDGER' ? ' is-ledger' : row.sourceSystem === 'OPERATIONAL' ? ' is-operational' : ''}`}>{row.sourceSystem === 'SYNTHETIC_DEMO' ? 'Mô phỏng' : row.sourceSystem === 'LEDGER' ? 'Sổ kế toán' : row.sourceSystem === 'OPERATIONAL' ? 'Hóa đơn vận hành' : 'File gốc'}</span></td></tr>)}
        {!loading && visibleRows.length === 0 && <tr><td colSpan="7" className="isr-no-rows">Không có dữ liệu trong bộ lọc này.</td></tr>}
      </tbody></table></div>
      <div className="isr-pager"><span>Trang {safePage} / {pageCount}</span><div><button type="button" disabled={safePage <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>Trước</button><button type="button" disabled={safePage >= pageCount} onClick={() => setPage((value) => Math.min(pageCount, value + 1))}>Tiếp</button></div></div>
    </article>
  </section>;
}
