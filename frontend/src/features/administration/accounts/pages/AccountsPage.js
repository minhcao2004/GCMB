/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Updated: 12/10/2026
 * Description: Hiển thị danh sách tài khoản, bộ lọc, phân trang và form tạo tài khoản liên kết hồ sơ nhân viên.
 */

import { useEffect, useRef, useState } from 'react';
import { accountApi } from '../services/accountApi';
import { apiErrors } from '../../../auth/services/authApi';
import '../styles/accounts.css';
import AccountAccessDialog from '../components/AccountAccessDialog';

const roles = { HEAD_OFFICE_MANAGER: ['HOM', 'Quản lý cấp cao'], BRANCH_MANAGER: ['BM', 'Quản lý cơ sở'], RECEPTIONIST: ['REC', 'Lễ tân'], ACCOUNTANT: ['ACC', 'Kế toán'] };
const statuses = { ACTIVE: 'Hoạt động', LOCKED: 'Đã khóa', REVOKED: 'Đã thu hồi' };
const emptyFilters = { q: '', role: '', branchId: '', status: '', page: 0 };

export default function AccountsPage({ actorId, onAudit }) {
  const [filters, setFilters] = useState(emptyFilters);
  const [data, setData] = useState({ items: [], total: 0 });
  const [options, setOptions] = useState({ employees: [], roles: [], branches: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [selectedAccount, setSelectedAccount] = useState(null);
  const [notice, setNotice] = useState('');
  const [drawer, setDrawer] = useState(false);
  const [highlight, setHighlight] = useState(null);
  const createButton = useRef(null);
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    const timer = setTimeout(() => {
      Promise.all([accountApi.list({ ...filters, branchId: filters.branchId || undefined, size: 10 }), accountApi.options()])
        .then(([page, choices]) => { if (active) { setData(page); setOptions(choices); } })
        .catch((e) => { if (active) setError(Object.values(apiErrors(e)).join(' ')); })
        .finally(() => { if (active) setLoading(false); });
    }, 200);
    return () => { active = false; clearTimeout(timer); };
  }, [filters, revision]);
  const filter = (key, value) => setFilters((old) => ({ ...old, [key]: value, page: 0 }));
  const close = () => { setDrawer(false); createButton.current?.focus(); };
  const viewCreated = (account) => { setHighlight(account.id); setFilters({ ...emptyFilters, q: account.username }); close(); };
  return <section className="accounts-page" aria-labelledby="accounts-title">
    <div className="accounts-heading"><div><h1 id="accounts-title">Tài khoản</h1><p>Quản lý vai trò và phạm vi truy cập của nhân viên</p></div><button className="account-primary" ref={createButton} onClick={() => setDrawer(true)} disabled={loading || !!error}>Tạo tài khoản</button></div>
    {notice && <p className="account-success-message" role="status">{notice}</p>}
    <div className="accounts-filters">
      <div className="account-search"><span aria-hidden="true">⌕</span><input aria-label="Tìm tài khoản" placeholder="Tìm tên, tài khoản hoặc email" maxLength={200} value={filters.q} onChange={(e) => filter('q', e.target.value)} /></div>
      <select aria-label="Lọc vai trò" value={filters.role} onChange={(e) => filter('role', e.target.value)}><option value="">Vai trò: Tất cả</option>{Object.entries(roles).map(([id, names]) => <option key={id} value={id}>{names[1]}</option>)}</select>
      <select aria-label="Lọc cơ sở" value={filters.branchId} onChange={(e) => filter('branchId', e.target.value)}><option value="">Cơ sở: Tất cả</option>{options.branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}</select>
      <select aria-label="Lọc truy cập" value={filters.status} onChange={(e) => filter('status', e.target.value)}><option value="">Truy cập: Tất cả</option>{Object.entries(statuses).map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select>
    </div>
    <div className="accounts-card" aria-busy={loading}>
      {error ? <div className="accounts-empty" role="alert">{error}<button onClick={() => setRevision((n) => n + 1)}>Thử lại</button></div> : loading ? <p className="accounts-empty" role="status">Đang tải tài khoản…</p> : <>
        <div className="accounts-table-scroll"><table><thead><tr><th>Nhân viên / tài khoản</th><th>Email</th><th>Vai trò</th><th>Phạm vi</th><th>Truy cập</th><th>Thao tác</th></tr></thead><tbody>
          {data.items.map((account) => <tr key={account.id} className={highlight === account.id ? 'account-highlight' : ''}><td><strong>{account.employeeName}</strong><small>{account.username}</small></td><td>{account.email}</td><td><abbr title={roles[account.role]?.[1]}>{roles[account.role]?.[0] || account.role}</abbr></td><td>{account.branchName}</td><td><span className={`account-status ${account.status.toLowerCase()}`}>{statuses[account.status] || account.status}</span></td>
            <td>
              <button className="account-link" onClick={() => setSelectedAccount(account)}>Phân quyền</button>
            </td>
          </tr>)}
        </tbody></table></div>
        {!data.items.length && <div className="accounts-empty"><p>{data.total ? 'Trang này không còn tài khoản.' : 'Không tìm thấy tài khoản phù hợp.'}</p><button onClick={() => setFilters(emptyFilters)}>Đặt lại bộ lọc</button></div>}
        <div className="accounts-pagination"><span>Hiển thị {data.items.length ? filters.page * 10 + 1 : 0}–{data.items.length ? filters.page * 10 + data.items.length : 0} / {data.total}</span><span>10 dòng / trang</span><nav aria-label="Phân trang tài khoản"><button aria-label="Trang trước" disabled={filters.page === 0} onClick={() => setFilters((old) => ({ ...old, page: old.page - 1 }))}>‹</button><span aria-current="page">{filters.page + 1}</span><button aria-label="Trang sau" disabled={(filters.page + 1) * 10 >= data.total} onClick={() => setFilters((old) => ({ ...old, page: old.page + 1 }))}>›</button></nav></div>
      </>}
    </div>
    <div className="accounts-bottom-actions">
      <button onClick={onAudit}>Xem nhật ký hệ thống</button>
    </div>
    {selectedAccount && (
      <AccountAccessDialog
        account={selectedAccount}
        options={options}
        actorId={actorId}
        onClose={() => setSelectedAccount(null)}
        onSaved={(message) => {
          setSelectedAccount(null);
          setNotice(message);
          setRevision((old) => old + 1);
        }}
      />
    )}
    {drawer && <CreateAccountDrawer options={options} onClose={close} onCreated={() => setRevision((n) => n + 1)} onView={viewCreated} />}
  </section>;
}

function CreateAccountDrawer({ options, onClose, onCreated, onView }) {
  const dialog = useRef(null);
  const [form, setForm] = useState({ employeeId: '', branchId: '', username: '', password: '', email: '', role: 'RECEPTIONIST' });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [visible, setVisible] = useState(false);
  const [created, setCreated] = useState(null);
  const chain = ['HEAD_OFFICE_MANAGER', 'ACCOUNTANT'].includes(form.role);
  useEffect(() => { dialog.current.showModal(); const previous = document.body.style.overflow; document.body.style.overflow = 'hidden'; return () => { document.body.style.overflow = previous; }; }, []);
  const change = (key, value) => { setForm((old) => ({ ...old, [key]: value })); setErrors((old) => ({ ...old, [key]: '', form: '' })); };
  const submit = async (event) => {
    event.preventDefault();
    if (busy) return;
    setBusy(true); setErrors({});
    try {
      const account = await accountApi.create({ ...form, branchId: chain ? null : form.branchId || null });
      setForm((old) => ({ ...old, password: '' })); setCreated(account); onCreated();
    } catch (e) { setErrors(apiErrors(e)); }
    finally { setBusy(false); }
  };
  const field = (name, label, control) => <div className="account-field"><label htmlFor={`account-${name}`}>{label}</label>{control}{errors[name] && <small id={`error-${name}`} className="account-error" role="alert">{errors[name]}</small>}</div>;
  const attrs = (name) => ({ id: `account-${name}`, value: form[name], onChange: (e) => change(name, e.target.value), required: true, 'aria-invalid': !!errors[name], 'aria-describedby': errors[name] ? `error-${name}` : undefined });
  return <dialog ref={dialog} className="account-drawer" aria-labelledby="create-account-title" onCancel={(e) => { e.preventDefault(); if (!busy) onClose(); }}>
    <div className="account-drawer-heading"><div><h2 id="create-account-title">Tạo tài khoản nhân viên</h2><p>Liên kết với hồ sơ nhân sự đã có</p></div><button aria-label="Đóng bảng tạo tài khoản" disabled={busy} onClick={onClose}>×</button></div>
    {created ? <><div className="account-success" role="status"><div className="account-check" aria-hidden="true">✓</div><h2>Tài khoản đã được tạo</h2><dl><dt>Nhân viên</dt><dd>{created.employeeName} · {created.employeeCode}</dd><dt>Tên đăng nhập</dt><dd>{created.username}</dd><dt>Vai trò / cơ sở</dt><dd>{roles[created.role]?.[1]} · {created.branchName}</dd></dl></div><div className="account-drawer-footer"><button onClick={onClose}>Đóng</button><button className="account-primary" onClick={() => onView(created)}>Xem tài khoản</button></div></> :
      <form onSubmit={submit} className="account-create-form"><fieldset disabled={busy} className="account-form-body">
        {errors.form && <p className="account-error" role="alert">{errors.form}</p>}
        {field('employeeId', 'Hồ sơ nhân viên', <select {...attrs('employeeId')}><option value="">Chọn hồ sơ nhân viên</option>{(options.employees || []).map((item) => <option key={item.id} value={item.id}>{item.name} · {item.code}</option>)}</select>)}
        {!options.employees?.length && <p className="account-note">Không có nhân viên đang hoạt động chưa được cấp tài khoản.</p>}
        {field('username', 'Tên đăng nhập', <input {...attrs('username')} autoComplete="off" minLength={3} maxLength={100} pattern="[A-Za-z0-9._\-]{3,100}" title="3–100 chữ, số hoặc dấu . _ -" />)}
        {field('password', 'Mật khẩu', <div className="account-password"><input {...attrs('password')} type={visible ? 'text' : 'password'} autoComplete="new-password" maxLength={128} /><button type="button" aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} aria-pressed={visible} onClick={() => setVisible((old) => !old)}>{visible ? 'Ẩn' : 'Hiện'}</button></div>)}
        {field('email', 'Email', <input {...attrs('email')} type="email" maxLength={254} autoComplete="off" />)}
        {field('role', 'Vai trò ban đầu', <select {...attrs('role')}>{options.roles.map((item) => <option key={item.id} value={item.id}>{roles[item.id]?.[1] || item.name} ({roles[item.id]?.[0] || item.id})</option>)}</select>)}
        {field('branchId', 'Phạm vi cơ sở', chain ? <input id="account-branchId" readOnly value="Toàn chuỗi" /> : <select {...attrs('branchId')}><option value="">Chọn cơ sở</option>{options.branches.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select>)}
        <div className="account-access-note"><strong>Thông tin truy cập</strong><p>Nhập mật khẩu của bạn. Phạm vi truy cập theo vai trò và cơ sở đã chọn.</p></div>
      </fieldset><div className="account-drawer-footer"><button type="button" disabled={busy} onClick={onClose}>Hủy</button><button className="account-primary" disabled={busy} type="submit">{busy ? 'Đang tạo…' : 'Tạo tài khoản'}</button></div></form>}
  </dialog>;
}
