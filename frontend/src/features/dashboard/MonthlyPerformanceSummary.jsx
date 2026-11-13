import { useEffect, useState } from 'react';
import api from '../../api/client';
import './monthly-performance-summary.css';

const fmtMoney = (value) => `${new Intl.NumberFormat('vi-VN',{maximumFractionDigits:0}).format(Number(value)||0)} ₫`;
const fmtCount = (value) => new Intl.NumberFormat('vi-VN',{maximumFractionDigits:0}).format(Number(value)||0);
const fmtPercent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN',{maximumFractionDigits:2}).format(Number(value))}%`;
const fmtChange = (value) => value == null ? '—' : `${Number(value)>0?'+':''}${fmtPercent(value)}`;
const requestError = (error) => error.response?.data?.detail || error.response?.data?.message || (error.request
  ? 'Không thể kết nối đến GCMB. Vui lòng kiểm tra kết nối mạng và thử lại.'
  : 'Không tải được báo cáo. Vui lòng thử lại.');
const vnMonth = () => new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit'}).format(new Date());
const previousMonth = (value) => {
  const [year, month] = value.split('-').map(Number);
  return new Date(Date.UTC(year, month - 2, 1)).toISOString().slice(0, 7);
};
const sameMonthLastYear = (value) => `${Number(value.slice(0, 4)) - 1}${value.slice(4)}`;
const reportHasData = (report) => {
  const totals = report?.totals || {};
  return ['revenue','billRevenue','forfeitedDeposits','expenses','operatingExpense','salary','investment','netCashFlow','billCount','bookingCount']
    .some((key) => Number(totals[key]) !== 0);
};
const reportParams = (month, branchId) => ({month, ...(branchId ? {branchId} : {})});

// BR-103/104/105/107/108/211: compare operating income, paid operating costs, operating profit and margin; CAPEX remains separate.
const metrics = [
  {key:'revenue', label:'Thu nhập vận hành', kind:'money', direction:'good'},
  {key:'billRevenue', label:'Doanh thu hóa đơn hoàn tất', kind:'money', direction:'good'},
  {key:'forfeitedDeposits', label:'Cọc bị tịch thu', kind:'money', direction:'good'},
  {key:'expenses', label:'Chi phí vận hành (gồm lương/ứng lương)', kind:'money', direction:'cost'},
  {key:'operatingExpense', label:'Chi phí vận hành đã thanh toán', kind:'money', direction:'cost'},
  {key:'salary', label:'Lương và ứng lương', kind:'money', direction:'cost'},
  {key:'profit', label:'Lợi nhuận vận hành', kind:'money', direction:'good'},
  {key:'margin', label:'Biên lợi nhuận vận hành', kind:'margin', direction:'good'},
  {key:'investment', label:'Chi đầu tư (mua sắm, sửa chữa lớn)', kind:'money'},
  {key:'netCashFlow', label:'Dòng tiền thuần', kind:'money'},
];

function shown(value, kind) {
  if (value == null || !Number.isFinite(Number(value))) return '—';
  return kind === 'money' ? fmtMoney(value) : `${new Intl.NumberFormat('vi-VN',{maximumFractionDigits:2}).format(Number(value))}%`;
}

function delta(current, comparison, kind) {
  if (current == null || comparison == null || !Number.isFinite(Number(current)) || !Number.isFinite(Number(comparison))) return null;
  const value = Number(current) - Number(comparison);
  if (kind === 'margin') return {value, absolute:`${value > 0 ? '+' : ''}${new Intl.NumberFormat('vi-VN',{maximumFractionDigits:2}).format(value)} điểm %`};
  return {value, absolute:`${value > 0 ? '+' : ''}${fmtMoney(value)}`};
}

function changePercent(current, comparison) {
  if (current == null || comparison == null || !Number.isFinite(Number(current)) || !Number.isFinite(Number(comparison)) || Number(comparison) === 0) return null;
  const value = ((Number(current) - Number(comparison)) / Math.abs(Number(comparison))) * 100;
  return Number.isFinite(value) ? value : null;
}

function tone(metric, difference) {
  if (difference == null || difference === 0 || !metric.direction) return 'is-flat';
  const positive = metric.direction === 'cost' ? difference < 0 : difference > 0;
  return positive ? 'is-good' : 'is-bad';
}

function ColumnStatus({label, state, month, isMissingDefault}) {
  if (state.status === 'loading') return <span className="monthly-column-state" role="status">Đang tải tháng {month}…</span>;
  if (state.status === 'error') return <span className="monthly-column-state is-error" role="alert">{label}: {state.error}</span>;
  if (state.status === 'empty') return <span className="monthly-column-state">{isMissingDefault ? 'Tháng liền trước chưa có dữ liệu. Hãy chọn tháng B.' : month ? `Tháng ${month} chưa có dữ liệu.` : 'Chưa chọn tháng.'}</span>;
  if (!month) return <span className="monthly-column-state">Chưa chọn tháng.</span>;
  return null;
}

export default function MonthlyPerformanceSummary({ report, month, branchId = '' }) {
  const [busy,setBusy]=useState(''); const [error,setError]=useState('');
  const [monthA,setMonthA]=useState(month || vnMonth());
  const [monthB,setMonthB]=useState(() => previousMonth(month || vnMonth()));
  const [autoMissingB,setAutoMissingB]=useState(true);
  const [aState,setAState]=useState({status:'loading',data:null,error:''});
  const [bState,setBState]=useState({status:'loading',data:null,error:''});
  const [revision,setRevision]=useState(0);

  useEffect(() => {
    if (!month) return;
    setMonthA(month);
    setMonthB(previousMonth(month));
    setAutoMissingB(true);
  }, [month]);

  useEffect(() => {
    let active = true;
    const maxMonth = vnMonth();
    const invalidA = Boolean(monthA && monthA > maxMonth);
    const invalidB = Boolean(monthB && monthB > maxMonth);
    const missingDefault = autoMissingB && !monthB;
    setAState({status:invalidA?'error':monthA?'loading':'idle',data:null,error:invalidA?'Không thể chọn tháng tương lai.':''});
    setBState({status:invalidB?'error':missingDefault?'empty':monthB?'loading':'idle',data:null,error:invalidB?'Không thể chọn tháng tương lai.':''});
    if (invalidA || invalidB || !monthA) return () => { active = false; };

    const load = async () => {
      const aRequest = api.get('/accounting/reports/monthly', {params:reportParams(monthA, branchId)});
      if (monthA === monthB) {
        const aResult = await Promise.allSettled([aRequest]);
        if (!active) return;
        const result = aResult[0];
        if (result.status === 'fulfilled') {
          const value = result.value.data;
          const state = {status:reportHasData(value)?'ready':'empty',data:value,error:''};
          setAState(state); setBState(state);
        } else {
          const message = requestError(result.reason);
          setAState({status:'error',data:null,error:message}); setBState({status:'error',data:null,error:message});
        }
        return;
      }
      const requests = [aRequest, monthB ? api.get('/accounting/reports/monthly', {params:reportParams(monthB, branchId)}) : Promise.resolve(null)];
      const [aResult,bResult] = await Promise.allSettled(requests);
      if (!active) return;
      if (aResult.status === 'fulfilled') {
        const value = aResult.value.data;
        setAState({status:reportHasData(value)?'ready':'empty',data:value,error:''});
      } else {
        setAState({status:'error',data:null,error:requestError(aResult.reason)});
      }
      if (!monthB) {
        setBState({status:missingDefault?'empty':'idle',data:null,error:''});
      } else if (bResult.status === 'fulfilled') {
        const value = bResult.value?.data;
        const hasData = reportHasData(value);
        setBState({status:hasData?'ready':'empty',data:value,error:''});
        if (autoMissingB && !hasData) { setMonthB(''); setAutoMissingB(true); }
      } else {
        setBState({status:'error',data:null,error:requestError(bResult.reason)});
      }
    };
    load();
    return () => { active = false; };
  }, [monthA, monthB, branchId, revision, autoMissingB]);

  async function download(format) {
    setBusy(format); setError('');
    try {
      const {data}=await api.get('/accounting/reports/monthly-performance/export',{params:{month,...(branchId?{branchId}:{}),format},responseType:'blob'});
      const url=URL.createObjectURL(data); const a=document.createElement('a'); a.href=url; a.download=`gcmb-monthly-performance-${month}.${format}`; a.click(); window.setTimeout(()=>URL.revokeObjectURL(url),1000);
    } catch(e) { setError(requestError(e)); }
    finally { setBusy(''); }
  }

  function setSelectedMonth(side, value) {
    if (value && value > vnMonth()) {
      const state = {status:'error',data:null,error:'Không thể chọn tháng tương lai.'};
      if (side === 'A') setAState(state); else setBState(state);
      return;
    }
    if (side === 'A') setMonthA(value);
    else { setMonthB(value); setAutoMissingB(false); }
  }

  function swapMonths() {
    setMonthA(monthB); setMonthB(monthA); setAutoMissingB(false);
  }

  const sameMonth = Boolean(monthA && monthB && monthA === monthB);
  const aTotals = aState.data?.totals || {};
  const bTotals = bState.data?.totals || {};
  const hasCompareMonth = Boolean(monthB);

  return <section className="monthly-performance" aria-label="Bảng so sánh hiệu quả kinh doanh">
    <header><div><span className={`monthly-period-badge ${report?.periodStatus==='CLOSED'?'is-closed':'is-open'}`}>Tháng {month} theo bộ lọc · {report?.periodStatus==='CLOSED'?'Đã khóa':'Đang mở'}</span><h2>So sánh hiệu quả kinh doanh</h2></div><div className="monthly-export-actions"><button type="button" disabled={!!busy} onClick={()=>download('xlsx')}>{busy==='xlsx'?'Đang xuất…':'Xuất Excel (tháng bộ lọc)'}</button><button type="button" disabled={!!busy} onClick={()=>download('csv')}>{busy==='csv'?'Đang xuất…':'Xuất CSV (tháng bộ lọc)'}</button></div></header>
    {error&&<p role="alert" className="monthly-error">{error}</p>}
    <p className="monthly-note">Thu nhập vận hành gồm doanh thu hóa đơn hoàn tất và cọc bị tịch thu; cọc đang giữ không được tính. Chi phí vận hành chỉ gồm khoản đã thanh toán, không gồm chuyển quỹ nội bộ. Chi đầu tư được trình bày riêng, không trừ vào lợi nhuận vận hành.</p>
    <div className="monthly-compare-controls">
      <label>Tháng A<input type="month" value={monthA} max={vnMonth()} onChange={(event)=>setSelectedMonth('A',event.target.value)} aria-label="Chọn tháng A" /></label>
      <label>Tháng B<input type="month" value={monthB} max={vnMonth()} onChange={(event)=>setSelectedMonth('B',event.target.value)} aria-label="Chọn tháng B" /></label>
      <button type="button" onClick={swapMonths} disabled={!monthB || !monthA}>Đổi chỗ A ↔ B</button>
      <button type="button" onClick={()=>{setMonthB(sameMonthLastYear(monthA));setAutoMissingB(false);}} disabled={!monthA}>Cùng tháng năm trước</button>
      <button type="button" onClick={()=>setRevision((value)=>value+1)} disabled={aState.status==='loading'||bState.status==='loading'}>Thử tải lại</button>
    </div>
    <p className="monthly-compare-hint">Tệp xuất là báo cáo của tháng {month} theo bộ lọc phía trên; chưa gồm bảng so sánh Tháng A/B.</p>
    {sameMonth&&<p className="monthly-compare-hint" role="status">Hai tháng đang giống nhau; báo cáo chỉ được tải một lần.</p>}
    <div className="monthly-compare-statuses">
      <ColumnStatus label="Tháng A" month={monthA} state={aState} />
      <ColumnStatus label="Tháng B" month={monthB} state={bState} isMissingDefault={autoMissingB} />
    </div>
    <div className="monthly-table-wrap"><table><caption>Chênh lệch tính theo Tháng A − Tháng B</caption><thead><tr><th>Chỉ tiêu</th><th>Tháng A · {monthA || '—'}</th><th>Tháng B · {monthB || '—'}</th><th>Chênh lệch</th><th>% thay đổi</th></tr></thead><tbody>{metrics.map((metric)=>{
      const a=aState.status==='ready'?aTotals[metric.key]:null;
      const b=bState.status==='ready'?bTotals[metric.key]:null;
      const change=hasCompareMonth&&a!=null&&b!=null?delta(a,b,metric.kind):null;
      const pct=hasCompareMonth&&a!=null&&b!=null?changePercent(a,b):null;
      return <tr key={metric.key}><th scope="row">{metric.label}</th><td>{aState.status==='loading'?'Đang tải…':shown(a,metric.kind)}</td><td>{bState.status==='loading'?'Đang tải…':shown(b,metric.kind)}</td><td className={tone(metric,change?.value)}>{change?change.absolute:'—'}</td><td className={tone(metric,pct)}>{fmtChange(pct)}</td></tr>;
    })}</tbody></table></div>
    <p className="monthly-compare-footnote">Biên lợi nhuận: chênh lệch hiển thị theo điểm phần trăm; % thay đổi dùng giá trị Tháng B làm mẫu số. Khi Tháng B bằng 0, % thay đổi hiển thị “—”.</p>
    <div className="monthly-groups"><section><h3>Cơ cấu doanh thu · tháng A {monthA || '—'}</h3>{aState.status==='loading'?<p>Đang tải doanh thu…</p>:aState.status==='error'?<p>Không tải được dữ liệu tháng A.</p>:(aState.data?.revenueGroups||[]).length?<ul>{aState.data.revenueGroups.map((item,i)=><li key={`${item.branchName}-${item.label}-${i}`}><span>{item.branchName} · {item.label}</span><strong>{fmtMoney(item.amount)}</strong></li>)}</ul>:<p>Chưa có dữ liệu doanh thu trong tháng A.</p>}</section><section><h3>Chi phí theo danh mục · tháng A {monthA || '—'}</h3>{aState.status==='loading'?<p>Đang tải chi phí…</p>:aState.status==='error'?<p>Không tải được dữ liệu tháng A.</p>:(aState.data?.expenseGroups||[]).length?<ul>{aState.data.expenseGroups.map((item)=><li key={item.category}><span>{item.category} · {fmtCount(item.count)} khoản</span><strong>{fmtMoney(item.amount)}</strong></li>)}</ul>:<p>Chưa có chi phí đã thanh toán trong tháng A.</p>}</section></div>
  </section>;
}
