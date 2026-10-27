/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 26/10/2026
 * Updated: 27/10/2026
 * Description: Danh sách, chi tiết và lịch sử phân công nhân viên.
 */

import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { staffApi, positions, staffStatuses, dateLabel, timeLabel } from '../services/staffApi';
import StaffDialog from '../components/StaffDialog';
import '../styles/staff.css';

const initialFilters = { q: '', branchId: '', position: '', status: '', page: 0 };
const statusBadge = status => <span className={`staff-badge ${status === 'ACTIVE' ? 'active' : ''}`}>{staffStatuses[status] || status}</span>;
const info = (name, value) => <div><dt>{name}</dt><dd>{value || '—'}</dd></div>;

export default function StaffPage({ user, branchScope, readOnly = false, onAccounts, onBranches }) {
  const [filters, setFilters] = useState(initialFilters);
  const [data, setData] = useState({ items: [], total: 0 });
  const [branches, setBranches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  const [employee, setEmployee] = useState(null);
  const [tab, setTab] = useState('info');
  const [dialog, setDialog] = useState(null);
  const detailRequest = useRef(0);
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    const timer = setTimeout(() => {
      const params = { ...filters, branchId: filters.branchId && filters.branchId !== 'NONE' ? filters.branchId : undefined, unassigned: filters.branchId === 'NONE', size: 10 };
      Promise.all([staffApi.list(params, branchScope), staffApi.options(branchScope)])
        .then(([page, options]) => { if (active) { setData(page); setBranches(options.branches); } })
        .catch(e => { if (active) setError(Object.values(apiErrors(e)).join(' ')); })
        .finally(() => { if (active) setLoading(false); });
    }, 180);
    return () => { active = false; clearTimeout(timer); };
  }, [filters, branchScope, revision]);
  useEffect(() => () => { detailRequest.current++; }, []);
  function filter(key, value) { setFilters(old => ({ ...old, [key]: value, page: 0 })); }
  async function open(id) {
    const request = ++detailRequest.current;
    setDetailLoading(true); setError(''); setNotice('');
    try { const record = await staffApi.detail(id, branchScope); if (request === detailRequest.current) { setEmployee(record); setTab('info'); } }
    catch (e) { if (request === detailRequest.current) setError(Object.values(apiErrors(e)).join(' ')); }
    finally { if (request === detailRequest.current) setDetailLoading(false); }
  }
  const accountLink = employee?.userId && onAccounts && (!branchScope || employee.accountRole === 'RECEPTIONIST');
  const canChangeStatus = !readOnly && employee?.userId !== user.id
    && (!branchScope || !employee?.userId || employee.accountRole === 'RECEPTIONIST' || employee.status !== 'ACTIVE');
  function saved(record, message) { setEmployee(record); setNotice(message); setRevision(v => v + 1); }
  return <section className="staff-page" aria-labelledby="staff-page-title">
    {notice && <p className="staff-banner success" role="status">{notice}</p>}
    {error && <div className="staff-banner error" role="alert">{error} <button type="button" className="staff-link" onClick={() => employee ? open(employee.id) : setRevision(v => v + 1)}>Thử lại</button></div>}
    {employee ? <>
      <button type="button" className="staff-link staff-back" onClick={() => { detailRequest.current++; setEmployee(null); setNotice(''); setError(''); setDetailLoading(false); }}>Nhân sự / Danh sách hồ sơ</button>
      <header className="staff-heading"><div><h1 id="staff-page-title">{employee.fullName}</h1><p>{employee.code} · {positions[employee.position]}</p></div><button type="button" className="staff-primary" disabled={readOnly || detailLoading} onClick={() => setDialog('edit')}>Sửa hồ sơ</button></header>
      <nav className="staff-tabs" aria-label="Chi tiết hồ sơ"><button type="button" aria-current={tab === 'info' ? 'page' : undefined} onClick={() => setTab('info')}>Thông tin</button><button type="button" aria-current={tab === 'history' ? 'page' : undefined} onClick={() => setTab('history')}>Lịch sử cơ sở</button></nav>
      {detailLoading ? <p role="status">Đang tải hồ sơ…</p> : tab === 'info' ? <>
        <div className="staff-detail-grid"><section className="staff-card"><h2>Thông tin nhân viên</h2><dl className="staff-facts">
          {info('Họ tên', employee.fullName)}{info('Mã / vị trí', `${employee.code} · ${positions[employee.position]}`)}
          {info('Liên hệ', employee.phone)}{info('Cơ sở làm việc chính', employee.branchName || 'Chưa gán')}
          {info('Trạng thái hồ sơ', staffStatuses[employee.status])}{info('Định danh', employee.identityNumber ? '•••• •••• ' + employee.identityNumber.slice(-4) : '—')}
          {info('Email liên hệ', employee.contactEmail)}{info('Ngày vào làm', dateLabel(employee.hiredOn))}
          {employee.resignedOn && info('Ngày nghỉ việc', dateLabel(employee.resignedOn))}{employee.address && info('Địa chỉ', employee.address)}
          {employee.notes && <div className="full"><dt>Ghi chú</dt><dd>{employee.notes}</dd></div>}
        </dl></section><section className="staff-card staff-account-card"><h2>Tài khoản truy cập</h2>
          {employee.userId ? <><span className={`staff-badge ${employee.accountStatus === 'ACTIVE' ? 'active' : ''}`}>{employee.accountStatus === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa / thu hồi'}</span><h3>{employee.username}</h3><p>{positions[employee.accountRole]}</p>
            {accountLink && <div className="staff-account-actions"><button type="button" className="staff-primary" onClick={() => onAccounts(employee.username)}>Xem tài khoản</button><button type="button" onClick={() => onAccounts(employee.username)}>{branchScope ? 'Trạng thái tài khoản' : 'Phân quyền tài khoản'}</button></div>}</>
            : <><p className="staff-muted">Chưa liên kết tài khoản đăng nhập.</p>{onAccounts && <button type="button" className="staff-outline" onClick={() => onAccounts('')}>Quản lý tài khoản</button>}</>}
        </section></div>
        <section className="staff-card"><h2>Phân công và trạng thái hồ sơ</h2><div className="staff-actions">
          {!branchScope && <button type="button" disabled={readOnly || employee.status !== 'ACTIVE'} onClick={() => setDialog(employee.branchId ? 'transfer' : 'assign')}>{employee.branchId ? 'Điều chuyển nhân viên' : 'Gán cơ sở làm việc'}</button>}
          <button type="button" disabled={!canChangeStatus} onClick={() => setDialog('status')}>{employee.status === 'ACTIVE' ? 'Ngừng hồ sơ' : 'Kích hoạt lại hồ sơ'}</button>
        </div><div className="staff-banner"><strong>Lịch sử được lưu theo từng lần phân công</strong><p>Thông tin cơ sở trước đó vẫn được giữ khi nhân viên được điều chuyển.</p></div></section>
      </> : <section className="staff-card staff-history"><h2>Lịch sử cơ sở · {employee.fullName}</h2><div className="staff-table-wrap"><table><thead><tr><th>Thời điểm</th><th>Hình thức</th><th>Cơ sở trước</th><th>Cơ sở sau</th><th>Người thực hiện</th></tr></thead><tbody>{employee.history.map(h => <tr key={h.id}><td>{timeLabel(h.createdAt)}</td><td>{h.previousBranchName ? 'Điều chuyển' : 'Gán ban đầu'}{h.reason && <small>{h.reason}</small>}</td><td>{h.previousBranchName || '—'}</td><td>{h.branchName}</td><td>{h.actor}</td></tr>)}</tbody></table></div>
        {!employee.history.length && <p className="staff-empty">Nhân viên chưa có lần phân công cơ sở.</p>}<div className="staff-banner success"><strong>Phân công mới nhất: {employee.branchName || 'Chưa gán'}</strong><p>Thông tin các lần phân công trước được giữ để tra cứu.</p></div></section>}
    </> : <>
      <header className="staff-heading"><div><h1 id="staff-page-title">{branchScope ? 'Nhân sự cơ sở' : 'Nhân sự toàn chuỗi'}</h1><p>Hồ sơ nhân viên và lịch sử phân công cơ sở</p></div><button type="button" className="staff-primary" disabled={readOnly || loading || !!error} onClick={() => setDialog('create')}>Thêm nhân viên</button></header>
      <div className="staff-filters"><div className="staff-search"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></svg><input aria-label="Tìm nhân viên" placeholder="Tìm tên / mã / điện thoại" value={filters.q} maxLength={200} onChange={e => filter('q', e.target.value)} /></div>
        <select aria-label="Lọc cơ sở" value={filters.branchId} disabled={!!branchScope} onChange={e => filter('branchId', e.target.value)}><option value="">{branchScope ? user.branchName : 'Cơ sở: Tất cả'}</option>{branches.map(b => <option value={b.id} key={b.id}>{b.name}</option>)}{!branchScope && <option value="NONE">Chưa gán</option>}</select>
        <select aria-label="Lọc vị trí" value={filters.position} onChange={e => filter('position', e.target.value)}><option value="">Vị trí: Tất cả</option>{Object.entries(positions).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select>
        <select aria-label="Lọc hồ sơ" value={filters.status} onChange={e => filter('status', e.target.value)}><option value="">Hồ sơ: Tất cả</option>{Object.entries(staffStatuses).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select>
      </div>
      <div className="staff-card staff-list" aria-busy={loading || detailLoading}>{loading ? <p className="staff-empty" role="status">Đang tải hồ sơ nhân viên…</p> : <>
        <div className="staff-table-wrap"><table><thead><tr><th>Mã / họ tên</th><th>Vị trí</th><th>Cơ sở chính</th><th>Điện thoại</th><th>Hồ sơ</th><th>Thao tác</th></tr></thead><tbody>{data.items.map((r, index) => <tr key={r.id} className={index === 0 ? 'staff-highlight' : ''}><td><strong>{r.code}</strong><small>{r.fullName}</small></td><td>{positions[r.position]}</td><td>{r.branchName || 'Chưa gán'}</td><td>{r.phone || '—'}</td><td>{statusBadge(r.status)}</td><td><button type="button" className="staff-link" disabled={detailLoading} onClick={() => open(r.id)} aria-label={`Chi tiết ${r.fullName}`}>Chi tiết</button></td></tr>)}</tbody></table></div>
        {!data.items.length && <p className="staff-empty">Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.</p>}
        <footer className="staff-pagination"><span>Hiển thị {data.items.length ? filters.page * 10 + 1 : 0}–{data.items.length ? filters.page * 10 + data.items.length : 0} / {data.total}</span><span>10 dòng / trang</span><nav aria-label="Phân trang nhân viên"><button type="button" aria-label="Trang trước" disabled={!filters.page} onClick={() => setFilters(old => ({ ...old, page: old.page - 1 }))}>‹</button><span>{filters.page + 1}</span><button type="button" aria-label="Trang sau" disabled={(filters.page + 1) * 10 >= data.total} onClick={() => setFilters(old => ({ ...old, page: old.page + 1 }))}>›</button></nav></footer>
      </>}{detailLoading && <p role="status" className="staff-help">Đang mở chi tiết…</p>}</div>
    </>}
    {dialog && <StaffDialog mode={dialog} employee={employee} branches={branches} user={user} branchScope={branchScope} onClose={() => setDialog(null)} onSaved={saved} onHistory={() => setTab(['assign', 'transfer'].includes(dialog) ? 'history' : 'info')} onAccounts={onAccounts} onBranches={onBranches} />}
  </section>;
}
