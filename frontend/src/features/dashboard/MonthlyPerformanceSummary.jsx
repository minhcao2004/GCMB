import { useState } from 'react';
import api from '../../api/client';
import './monthly-performance-summary.css';

const moneyKeys = ['billRevenue','forfeitedDeposits','revenue','roomRevenue','fnbRevenue','overtimeRevenue','otherRevenue','operatingExpense','salary','investment','profit','netCashFlow'];
const labels = { billRevenue:'Doanh thu từ hóa đơn', forfeitedDeposits:'Thu từ cọc bị tịch thu', revenue:'Tổng doanh thu', roomRevenue:'Tiền phòng', fnbRevenue:'Thực phẩm và đồ uống', overtimeRevenue:'Phụ phí quá giờ', otherRevenue:'Khác (phụ phí/giảm giá)', operatingExpense:'Chi phí vận hành', salary:'Lương và ứng lương', investment:'Đầu tư', profit:'Lợi nhuận vận hành', margin:'Biên lợi nhuận vận hành', netCashFlow:'Dòng tiền thuần', bookingCount:'Số lượt đặt phòng', completedBookings:'Đã hoàn tất (hóa đơn đã thanh toán)', cancelledNoShowBookings:'Hủy hoặc khách không đến', occupancyRate:'Tỷ lệ lấp đầy tháng', billCount:'Hóa đơn đã thanh toán' };
const fmtMoney = (value) => `${new Intl.NumberFormat('vi-VN',{maximumFractionDigits:0}).format(Number(value)||0)} ₫`;
const fmtCount = (value) => new Intl.NumberFormat('vi-VN',{maximumFractionDigits:0}).format(Number(value)||0);
const fmtPercent = (value) => value == null ? '—' : `${new Intl.NumberFormat('vi-VN',{maximumFractionDigits:2}).format(Number(value))}%`;
const fmtChange = (value) => value == null ? '—' : `${Number(value)>0?'+':''}${fmtPercent(value)}`;

export default function MonthlyPerformanceSummary({ report, month, branchId = '' }) {
  const [busy,setBusy]=useState(''); const [error,setError]=useState('');
  const totals=report?.totals||{}; const previous=report?.previousTotals||{}; const changes=report?.changes||{};
  const metrics=[...moneyKeys,'margin','billCount','bookingCount','completedBookings','cancelledNoShowBookings','occupancyRate'];
  async function download(format) {
    setBusy(format); setError('');
    try {
      const {data}=await api.get('/accounting/reports/monthly-performance/export',{params:{month,...(branchId?{branchId}:{}),format},responseType:'blob'});
      const url=URL.createObjectURL(data); const a=document.createElement('a'); a.href=url; a.download=`gcmb-monthly-performance-${month}.${format}`; a.click(); window.setTimeout(()=>URL.revokeObjectURL(url),1000);
    } catch(e) { setError(e.response?.data?.detail||'Không thể xuất báo cáo. Vui lòng thử lại.'); }
    finally { setBusy(''); }
  }
  return <section className="monthly-performance" aria-label="Báo cáo hiệu quả kinh doanh">
    <header><div><span className={`monthly-period-badge ${report?.periodStatus==='CLOSED'?'is-closed':'is-open'}`}>Kỳ {report?.periodStatus==='CLOSED'?'Đã khóa':'Đang mở'}</span><h2>Hiệu quả kinh doanh · {month}</h2></div><div className="monthly-export-actions"><button type="button" disabled={!!busy} onClick={()=>download('xlsx')}>{busy==='xlsx'?'Đang xuất…':'Xuất Excel'}</button><button type="button" disabled={!!busy} onClick={()=>download('csv')}>{busy==='csv'?'Đang xuất…':'Xuất tệp CSV'}</button></div></header>
    {error&&<p role="alert" className="monthly-error">{error}</p>}
    <p className="monthly-note">Tổng doanh thu gồm doanh thu từ hóa đơn đã thanh toán và tiền cọc bị tịch thu, ghi nhận theo ngày ghi nhận và múi giờ của cơ sở. Không tính cọc đã áp dụng vào hóa đơn, cọc chưa sử dụng hoặc cọc đang chờ hoàn; khoản hoàn cọc đã chi không phải chi phí vận hành. Lợi nhuận vận hành = tổng doanh thu − chi phí vận hành đã thanh toán; biên lợi nhuận = lợi nhuận vận hành ÷ tổng doanh thu. Dòng tiền thuần lấy từ sổ tiền; điều chỉnh chỉ ảnh hưởng dòng tiền.</p>
    <div className="monthly-table-wrap"><table><caption>So sánh với tháng trước · số tiền là đồng</caption><thead><tr><th>Chỉ số</th><th>Tháng này</th><th>Tháng trước</th><th>Thay đổi</th></tr></thead><tbody>{metrics.map((key)=>{
      const current=totals[key]; const prior=previous[key]; const isMoney=moneyKeys.includes(key); const isCount=['billCount','bookingCount','completedBookings','cancelledNoShowBookings'].includes(key);
      const show=(v)=>v==null?'—':isMoney?fmtMoney(v):isCount?fmtCount(v):fmtPercent(v);
      return <tr key={key}><th scope="row">{labels[key]}</th><td>{show(current)}</td><td>{show(prior)}</td><td>{fmtChange(changes[key])}</td></tr>;
    })}</tbody></table></div>
    <p className="monthly-estimate">Tỷ lệ lấp đầy tháng là ước tính: khung giờ 08:00–20:00, phòng hiện ngừng hoạt động/bảo trì bị loại khỏi mẫu số cả tháng vì chưa lưu lịch sử trạng thái phòng.</p>
    <div className="monthly-groups"><section><h3>Cơ cấu doanh thu</h3>{(report?.revenueGroups||[]).length?<ul>{report.revenueGroups.map((item,i)=><li key={`${item.branchName}-${item.label}-${i}`}><span>{item.branchName} · {item.label}</span><strong>{fmtMoney(item.amount)}</strong></li>)}</ul>:<p>Chưa có dữ liệu doanh thu trong kỳ.</p>}</section><section><h3>Chi phí theo danh mục</h3>{(report?.expenseGroups||[]).length?<ul>{report.expenseGroups.map((item)=><li key={item.category}><span>{item.category} · {fmtCount(item.count)} khoản</span><strong>{fmtMoney(item.amount)}</strong></li>)}</ul>:<p>Chưa có chi phí đã thanh toán trong kỳ.</p>}</section></div>
  </section>;
}
