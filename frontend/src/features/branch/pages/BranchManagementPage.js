/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Danh sách, chi tiết, thêm/sửa, gán quản lý và ngừng hoạt động cơ sở.
 */
import { useEffect, useRef, useState } from 'react';
import { branchAdministrationApi as api, branchError } from '../services/branchAdministrationApi';
import { actionNames } from '../../administration/accounts/accountAccessApi';
import './branch-administration.css';

const statuses = { ACTIVE: 'Đang hoạt động', SUSPENDED: 'Tạm ngừng', CLOSED: 'Đã đóng' };
const emptyForm = { code: '', name: '', address: '', phone: '', notes: '' };
const time = (value) => value ? new Date(value).toLocaleString('vi-VN') : '—';

export default function BranchManagementPage({ onAccounts }) {
  const [branches, setBranches] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(0);
  const [tab, setTab] = useState('Thông tin');
  const [related, setRelated] = useState([]);
  const [relatedLoading, setRelatedLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [modalError, setModalError] = useState('');
  const [managers, setManagers] = useState(null);
  const [managerQuery, setManagerQuery] = useState('');
  const [managerId, setManagerId] = useState('');
  const [reason, setReason] = useState('');
  const [blockers, setBlockers] = useState(null);
  const [showChecks, setShowChecks] = useState(false);
  const selected = branches.find((branch) => branch.branchId === selectedId);
  const selectedManager = managers?.find((manager) => manager.userId === managerId);
  const isStatus = modal === 'SUSPENDED' || modal === 'CLOSED';
  const phoneError = form.phone.trim() && !/^0[0-9]{6,14}$/.test(form.phone.trim())
    ? 'Số điện thoại phải bắt đầu bằng 0 và gồm 7–15 chữ số, không chứa dấu hoặc khoảng trắng.' : '';

  async function refresh() {
    setLoading(true);
    try {
      setBranches(await api.list());
      setError('');
    } catch (e) {
      setError(branchError(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { refresh(); }, []);
  useEffect(() => { setPage(0); }, [query, status]);

  // Hủy cập nhật kết quả cũ nếu người dùng chuyển cơ sở hoặc đóng hộp thoại.
  useEffect(() => {
    let active = true;
    if (!selectedId || tab === 'Thông tin') return undefined;
    setRelatedLoading(true);
    setRelated([]);
    const request = tab === 'Nhân sự' ? api.staff(selectedId) : api.history(selectedId);
    request.then((data) => { if (active) setRelated(data); })
      .catch((e) => { if (active) setError(branchError(e)); })
      .finally(() => { if (active) setRelatedLoading(false); });
    return () => { active = false; };
  }, [selectedId, tab, branches]);

  useEffect(() => {
    let active = true;
    const request = modal === 'manager' ? api.managers()
      : isStatus && selectedId ? api.blockers(selectedId) : null;
    if (!request) return undefined;
    request.then((data) => {
      if (!active) return;
      if (modal === 'manager') setManagers(data);
      else setBlockers(data);
    }).catch((e) => { if (active) setModalError(branchError(e)); });
    return () => { active = false; };
  }, [modal, selectedId, isStatus]);

  function open(kind) {
    setModalError('');
    setNotice('');
    setManagers(null);
    setManagerQuery('');
    setManagerId(selected?.managerId || '');
    setBlockers(null);
    setShowChecks(false);
    setReason('');
    setForm(kind === 'edit' ? {
      code: selected.code,
      name: selected.branchName,
      address: selected.address,
      phone: selected.phone || '',
      notes: selected.notes || '',
    } : { ...emptyForm });
    setModal(kind);
  }

  async function save(event) {
    event.preventDefault();
    if (busy) return;
    if ((modal === 'create' || modal === 'edit') && phoneError) {
      setModalError('');
      event.currentTarget.querySelector('input[type="tel"]')?.focus();
      return;
    }
    setBusy(true);
    setModalError('');
    try {
      let result;
      if (modal === 'create') result = await api.create(form);
      else if (modal === 'edit') result = await api.update(selectedId, form);
      else if (modal === 'manager') result = await api.assign(selectedId, managerId);
      else result = await api.deactivate(selectedId, { status: modal, reason });
      setSelectedId(result.branchId);
      setModal(null);
      setNotice(isStatus ? 'Đã ngừng hoạt động cơ sở. Lịch sử được giữ nguyên.' : 'Đã lưu thông tin cơ sở.');
      await refresh();
    } catch (e) {
      setModalError(branchError(e));
      // Backend kiểm tra lại tại thời điểm xác nhận, vì có thể vừa phát sinh booking mới.
      if (isStatus) {
        try { setBlockers(await api.blockers(selectedId)); }
        catch { setBlockers(null); }
      }
    } finally {
      setBusy(false);
    }
  }

  const visible = branches.filter((branch) =>
    (status === 'ALL' || branch.status === status)
    && `${branch.code} ${branch.branchName} ${branch.address}`.toLocaleLowerCase('vi')
      .includes(query.trim().toLocaleLowerCase('vi')));
  const managerOptions = (managers || []).filter((manager) =>
    `${manager.fullName} ${manager.employeeCode || ''} ${manager.username}`
      .toLocaleLowerCase('vi').includes(managerQuery.trim().toLocaleLowerCase('vi')));

  return (
    <section className="branch-admin">
      {selected && <button className="ba-back" onClick={() => { setSelectedId(null); setError(''); }}>‹ Danh sách cơ sở</button>}
      <header className="ba-heading">
        <div>
          <h1>{selected ? selected.branchName : 'Cơ sở'}</h1>
          <p>{selected ? `${selected.code} · Thông tin cơ sở và quản lý phụ trách` : 'Quản lý thông tin và hoạt động của các cơ sở trong chuỗi'}</p>
        </div>
        {selected ? <Status value={selected.status} /> : <button className="ba-primary" onClick={() => open('create')}>Thêm cơ sở</button>}
      </header>
      {error && <div className="ba-error" role="alert">{error} <button onClick={refresh}>Thử lại</button></div>}
      {notice && <div className="ba-success" role="status">{notice}</div>}
      {!selected ? <>
        <div className="ba-filters">
          <input aria-label="Tìm cơ sở" placeholder="Tìm mã, tên hoặc địa chỉ cơ sở" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select aria-label="Trạng thái cơ sở" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="ALL">Trạng thái: Tất cả</option>
            {Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </div>
        <div className="ba-card ba-table-scroll">
          <table>
            <thead><tr><th>Mã / tên cơ sở</th><th>Địa chỉ</th><th>Quản lý phụ trách</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
            <tbody>
              {loading ? <tr><td colSpan="5">Đang tải cơ sở…</td></tr> : visible.slice(page * 10, page * 10 + 10).map((branch) => (
                <tr key={branch.branchId}>
                  <td><strong>{branch.branchName}</strong><small>{branch.code}</small></td>
                  <td>{branch.address}</td><td>{branch.managerName || 'Chưa gán quản lý'}</td>
                  <td><Status value={branch.status} /></td>
                  <td><button className="ba-link" onClick={() => { setSelectedId(branch.branchId); setTab('Thông tin'); }}>Chi tiết</button></td>
                </tr>
              ))}
              {!loading && !visible.length && <tr><td colSpan="5">Không tìm thấy cơ sở phù hợp.</td></tr>}
            </tbody>
          </table>
          <footer className="ba-pagination">
            <span>{visible.length} cơ sở · 10 dòng / trang</span>
            <div><button disabled={!page} onClick={() => setPage(page - 1)}>Trước</button> {page + 1} <button disabled={(page + 1) * 10 >= visible.length} onClick={() => setPage(page + 1)}>Sau</button></div>
          </footer>
        </div>
      </> : <>
        <nav className="ba-tabs" aria-label="Chi tiết cơ sở">
          {['Thông tin', 'Nhân sự', 'Lịch sử thay đổi'].map((name) => <button key={name} aria-current={tab === name ? 'page' : undefined} onClick={() => { setTab(name); setError(''); }}>{name}</button>)}
        </nav>
        {tab === 'Thông tin' ? <>
          <div className="ba-detail-grid">
            <section className="ba-card">
              <h2>Thông tin cơ sở</h2>
              <dl className="ba-info-grid">
                <Info label="Tên cơ sở" value={selected.branchName} /><Info label="Mã cơ sở" value={selected.code} />
                <Info label="Địa chỉ" value={selected.address} /><Info label="Ngày tạo" value={time(selected.createdAt)} />
                <Info label="Điện thoại" value={selected.phone} /><Info label="Cập nhật gần nhất" value={time(selected.updatedAt)} />
                <Info label="Ghi chú vận hành" value={selected.notes} /><Info label="Lý do trạng thái" value={selected.statusReason} />
              </dl>
            </section>
            <section className="ba-card ba-manager-card">
              <h2>Quản lý phụ trách</h2>
              <div className="ba-manager-identity"><span className="ba-avatar">{(selected.managerName || '?').slice(0, 1)}</span><div><strong>{selected.managerName || 'Chưa gán quản lý'}</strong><small>{selected.managerCode || selected.managerUsername || 'Chọn quản lý sau khi tạo cơ sở'}</small></div></div>
              <button className="ba-primary" disabled={selected.status !== 'ACTIVE'} onClick={() => open('manager')}>Gán / thay quản lý</button>
              {selected.status !== 'ACTIVE' && <p>Chỉ gán quản lý khi cơ sở đang hoạt động.</p>}
            </section>
          </div>
          <section className="ba-card">
            <h2>Thao tác quản lý</h2>
            <div className="ba-actions">
              <button onClick={() => open('edit')}>Sửa thông tin</button>
              <button disabled={selected.status !== 'ACTIVE'} onClick={() => open('SUSPENDED')}>Tạm ngừng cơ sở</button>
              <button disabled={selected.status === 'CLOSED'} onClick={() => open('CLOSED')}>Đóng cơ sở</button>
              <button onClick={() => setTab('Lịch sử thay đổi')}>Xem lịch sử</button>
            </div>
            <aside className="ba-info"><strong>Thông tin lịch sử được giữ lại</strong><span>Khi tạm ngừng hoặc đóng cơ sở, dữ liệu đã phát sinh vẫn được lưu để tra cứu.</span></aside>
          </section>
        </> : <section className="ba-card ba-table-scroll">
          <h2>{tab}</h2>
          {relatedLoading ? <p>Đang tải…</p> : !related.length ? <p>Chưa có dữ liệu.</p> : <table>
            <thead><tr>{(tab === 'Nhân sự' ? ['Mã nhân viên', 'Họ tên', 'Trạng thái'] : ['Thời điểm', 'Người thực hiện', 'Thay đổi', 'Lý do']).map((label) => <th key={label}>{label}</th>)}</tr></thead>
            <tbody>{related.map((row) => <tr key={row.id}>{tab === 'Nhân sự' ? <><td>{row.code}</td><td>{row.fullName}</td><td>{row.status === 'ACTIVE' ? 'Đang làm việc' : 'Đã ngừng làm việc'}</td></> : <><td>{time(row.createdAt)}</td><td>{row.actor || 'Hệ thống'}</td><td>{actionNames[row.action] || row.action}</td><td>{row.reason || '—'}</td></>}</tr>)}</tbody>
          </table>}
        </section>}
      </>}

      {modal && <Dialog drawer={!isStatus} busy={busy} onClose={() => setModal(null)} title={modal === 'create' ? 'Thêm cơ sở' : modal === 'edit' ? 'Sửa thông tin cơ sở' : modal === 'manager' ? 'Gán / thay quản lý' : modal === 'CLOSED' ? 'Đóng cơ sở' : 'Tạm ngừng cơ sở'}>
        <form onSubmit={save}>
          <div className="ba-modal-body">
            <p className="ba-subtitle">{selected && modal !== 'create' ? `${selected.code} · ${selected.branchName}` : 'Thông tin cơ sở được quản lý tập trung'}</p>
            {(modal === 'create' || modal === 'edit') && <div className="ba-fields">
              <label>Mã cơ sở<input required pattern="[A-Za-z0-9_\-]{2,60}" title="Mã không chứa khoảng trắng, gồm 2–60 chữ cái, số, gạch ngang hoặc gạch dưới" maxLength="60" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })} /></label>
              <label>Tên cơ sở<input required maxLength="200" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
              <label>Địa chỉ<input required maxLength="2000" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></label>
              <label>Điện thoại liên hệ<input type="tel" inputMode="numeric" maxLength="30" value={form.phone} aria-invalid={!!phoneError} aria-describedby={phoneError ? 'branch-phone-error' : undefined} onChange={(e) => { setForm({ ...form, phone: e.target.value }); setModalError(''); }} /></label>
              {phoneError && <span id="branch-phone-error" role="alert" style={{ color: '#c52e4a' }}>{phoneError}</span>}
              <label>Ghi chú vận hành<textarea rows="4" maxLength="4000" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} /></label>
              <aside className="ba-info"><strong>Quản lý phụ trách</strong><span>Có thể gán quản lý tại trang chi tiết sau khi lưu cơ sở.</span></aside>
            </div>}
            {modal === 'manager' && <>
              <aside className="ba-info"><strong>Hiện tại: {selected?.managerName || 'Chưa gán quản lý'}</strong><span>{selected?.managerCode || selected?.managerUsername}</span></aside>
              <label className="ba-search-label">Tìm quản lý<input placeholder="Tên, mã nhân viên hoặc tài khoản" value={managerQuery} onChange={(e) => setManagerQuery(e.target.value)} /></label>
              {managers === null ? <p>Đang tải quản lý…</p> : !managerOptions.length ? <p>Không có quản lý phù hợp.</p> : managerOptions.map((manager) => <label key={manager.userId} className={`ba-manager-option ${managerId === manager.userId ? 'selected' : ''}`}>
                <input type="radio" name="manager" required value={manager.userId} checked={managerId === manager.userId} onChange={() => setManagerId(manager.userId)} />
                <div><span className="ba-badge ACTIVE">Tài khoản đang hoạt động</span><strong>{manager.fullName} · {manager.employeeCode || manager.username}</strong><small>Đang phụ trách: {manager.branchName || 'Chưa có cơ sở'}</small></div>
              </label>)}
              {selectedManager && <aside className="ba-success"><strong>Sau khi lưu: {selectedManager.fullName}</strong><p>Quản lý được giao phụ trách {selected.branchName}.</p>{selectedManager.branchId && selectedManager.branchId !== selectedId && <p>Cơ sở {selectedManager.branchName} sẽ không còn quản lý này. Người quản lý cũ tại cơ sở đích sẽ được thay thế.</p>}</aside>}
              {onAccounts && <button className="ba-link" type="button" onClick={onAccounts}>Không có tài khoản? Mở quản lý tài khoản ›</button>}
            </>}
            {isStatus && <>
              <div className="ba-status-transition"><Status value={selected.status} /><strong>→ {statuses[modal]}</strong></div>
              <label className="ba-search-label">Lý do<textarea required maxLength="2000" rows="3" value={reason} onChange={(e) => setReason(e.target.value)} /></label>
              <aside className="ba-warning"><strong>{blockers?.length ? 'Còn nghiệp vụ cần kiểm tra' : 'Dữ liệu lịch sử vẫn được giữ'}</strong><span>{blockers === null ? 'Đang kiểm tra điều kiện ngừng hoạt động…' : blockers.length ? 'Có booking, phiên sử dụng hoặc hóa đơn chưa xử lý. Vui lòng kiểm tra trước khi đổi trạng thái.' : 'Cơ sở sẽ ngừng phát sinh đặt phòng và giao dịch vận hành mới.'}</span></aside>
              {showChecks && <div className="ba-checks"><h3>Nghiệp vụ cần xử lý</h3><ul>{blockers?.map((item) => <li key={`${item.kind}-${item.id}`}><strong>{item.code}</strong> · {item.kind === 'BOOKING' ? 'Đặt phòng' : item.kind === 'BILL' ? 'Hóa đơn' : 'Phiên sử dụng'} · {item.status}</li>)}</ul><p>Nhân viên cơ sở cần hoàn tất hoặc hủy các nghiệp vụ trên trước khi xác nhận lại.</p></div>}
            </>}
            {modalError && <div className="ba-error" role="alert">{modalError}</div>}
          </div>
          <footer className="ba-modal-footer">
            <button type="button" disabled={busy} onClick={() => setModal(null)}>Hủy</button>
            {isStatus && !!blockers?.length ? <button className="ba-primary" type="button" onClick={() => setShowChecks(true)}>Kiểm tra cơ sở</button> : <button className={modal === 'CLOSED' ? 'ba-danger' : 'ba-primary'} disabled={busy || (isStatus && blockers === null) || (modal === 'manager' && !selectedManager)}>{busy ? 'Đang lưu…' : modal === 'manager' ? 'Xác nhận gán quản lý' : modal === 'CLOSED' ? 'Xác nhận đóng' : modal === 'SUSPENDED' ? 'Xác nhận tạm ngừng' : 'Lưu cơ sở'}</button>}
          </footer>
        </form>
      </Dialog>}
    </section>
  );
}

function Status({ value }) {
  return <span className={`ba-badge ${value}`}>{statuses[value] || value}</span>;
}

function Info({ label, value }) {
  return <div><dt>{label}</dt><dd>{value || '—'}</dd></div>;
}

// Focus nằm trong hộp thoại, Escape và lớp phủ chỉ đóng khi chưa gửi yêu cầu.
function Dialog({ title, drawer, busy, onClose, children }) {
  const ref = useRef(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    const previous = document.activeElement;
    ref.current?.focus();
    return () => previous?.focus();
  }, []);
  function keyDown(event) {
    if (event.key === 'Escape' && !busy) closeRef.current();
    if (event.key !== 'Tab') return;
    const items = [...ref.current.querySelectorAll('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled)')];
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && (document.activeElement === first || document.activeElement === ref.current)) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }
  return <div className={`ba-overlay ${drawer ? 'drawer' : ''}`} onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose(); }}>
    <section className="ba-modal" ref={ref} tabIndex="-1" role="dialog" aria-modal="true" aria-labelledby="branch-modal-title" onKeyDown={keyDown}>
      <header><h2 id="branch-modal-title">{title}</h2><button type="button" aria-label="Đóng" disabled={busy} onClick={onClose}>×</button></header>
      {children}
    </section>
  </div>;
}
