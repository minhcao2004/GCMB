/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Tra cứu nhật ký theo khoảng ngày, người thực hiện, hành động và cơ sở.
 */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { accountApi } from './accountApi';
import { accountAccessApi, actionNames, roleNames } from './accountAccessApi';
import './accounts.css';
import './accountAccess.css';

const initialFilters = { q: '', action: '', branchId: '', from: '', to: '', page: 0 };
const statusNames = { ACTIVE: 'Hoạt động', LOCKED: 'Đã khóa', REVOKED: 'Đã thu hồi' };
const entityNames = { USER_ACCOUNT: 'Tài khoản', ROOM: 'Phòng', ROOM_TYPE: 'Hạng phòng', PRICE_RULE: 'Quy tắc giá', COMBO: 'Combo', BOOKING: 'Booking', BRANCH: 'Cơ sở' };
const fieldNames = {
  address: 'Địa chỉ', notes: 'Ghi chú vận hành', updated_at: 'Cập nhật lúc', closed_on: 'Ngày đóng',
  username: 'Tên đăng nhập', role: 'Vai trò', branchName: 'Cơ sở', status: 'Trạng thái', fullName: 'Họ tên', email: 'Email', phone: 'Số điện thoại',
  id: 'ID', code: 'Mã', name: 'Tên', branch_id: 'ID cơ sở', room_type_id: 'ID hạng phòng', mode: 'Loại dịch vụ', capacity: 'Sức chứa',
  description: 'Mô tả', floor_number: 'Tầng', operating_status: 'Trạng thái phòng', status_reason: 'Lý do trạng thái', expected_ready_at: 'Dự kiến sẵn sàng',
  active: 'Đang áp dụng', version: 'Phiên bản', valid_from: 'Hiệu lực từ', valid_to: 'Hiệu lực đến', minute_from: 'Phút bắt đầu', minute_to: 'Phút kết thúc',
  weekdays: 'Ngày trong tuần', day_kind: 'Loại ngày', specific_date: 'Ngày cụ thể', hourly_price: 'Giá giờ', overtime_hourly_price: 'Giá vượt giờ',
  billing_step_minutes: 'Bước tính phút', priority: 'Ưu tiên', price: 'Giá', duration_minutes: 'Thời lượng (phút)', deposit_required: 'Cọc yêu cầu',
  day_group: 'Nhóm ngày', min_guests: 'Số khách tối thiểu', included_description: 'Dịch vụ bao gồm', contact_name: 'Tên khách', contact_phone: 'Điện thoại khách',
  guest_count: 'Số khách', planned_start: 'Giờ hẹn', planned_end: 'Giờ kết thúc', actual_checkin: 'Check-in thực tế', actual_checkout: 'Check-out thực tế',
  note: 'Ghi chú', channel: 'Kênh đặt', pricing_mode: 'Cách tính giá', managerId: 'ID quản lý', event: 'Chi tiết nghiệp vụ',
};

function time(value) {
  return new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}

export default function AuditLogPage({ onAccounts }) {
  const [filters, setFilters] = useState(initialFilters);
  const [page, setPage] = useState({ items: [], total: 0 });
  const [branches, setBranches] = useState([]);
  const [actions, setActions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [selected, setSelected] = useState(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    if (filters.from && filters.to && filters.from > filters.to) {
      setLoading(false);
      setError('Ngày bắt đầu không được sau ngày kết thúc.');
      return undefined;
    }
    const timer = setTimeout(async () => {
      try {
        const [result, options, availableActions] = await Promise.all([
          accountAccessApi.audit({
            ...filters,
            branchId: filters.branchId || undefined,
            from: filters.from || undefined,
            to: filters.to || undefined,
            size: 10,
          }),
          accountApi.options(),
          accountAccessApi.actions(),
        ]);
        if (active) {
          setPage(result);
          setBranches(options.branches);
          setActions(availableActions);
        }
      } catch (failure) {
        if (active) setError(Object.values(apiErrors(failure)).join(' '));
      } finally {
        if (active) setLoading(false);
      }
    }, 200);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [filters, retry]);

  function filter(key, value) {
    setFilters((old) => ({ ...old, [key]: value, page: 0 }));
  }

  return (
    <section className="accounts-page" aria-labelledby="audit-title">
      <div className="accounts-heading">
        <div>
          <h1 id="audit-title">Nhật ký hệ thống</h1>
          <p>Tra cứu người thực hiện và các thay đổi quan trọng</p>
        </div>
        <button onClick={onAccounts}>Về tài khoản</button>
      </div>
      <div className="accounts-filters audit-filters">
        <div className="audit-date">
          <label htmlFor="audit-from">Từ ngày</label>
          <input id="audit-from" type="date" value={filters.from} onChange={(event) => filter('from', event.target.value)} />
        </div>
        <div className="audit-date">
          <label htmlFor="audit-to">Đến ngày</label>
          <input id="audit-to" type="date" value={filters.to} onChange={(event) => filter('to', event.target.value)} />
        </div>
        <input
          aria-label="Tìm nhật ký"
          placeholder="Người / mã đối tượng"
          maxLength={200}
          value={filters.q}
          onChange={(event) => filter('q', event.target.value)}
        />
        <select aria-label="Lọc hành động" value={filters.action} onChange={(event) => filter('action', event.target.value)}>
          <option value="">Hành động: Tất cả</option>
          {actions.map((action) => <option key={action} value={action}>{actionNames[action] || action}</option>)}
        </select>
        <select aria-label="Lọc cơ sở" value={filters.branchId} onChange={(event) => filter('branchId', event.target.value)}>
          <option value="">Cơ sở: Tất cả</option>
          {branches.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
        </select>
      </div>
      <div className="accounts-card" aria-busy={loading}>
        {loading ? <p className="accounts-empty" role="status">Đang tải nhật ký…</p> : error ? (
          <div className="accounts-empty" role="alert">
            {error}
            <button onClick={() => setRetry((old) => old + 1)}>Thử lại</button>
          </div>
        ) : (
          <>
            <div className="accounts-table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Thời điểm</th><th>Người thực hiện</th><th>Hành động</th>
                    <th>Đối tượng</th><th>Phạm vi</th><th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((entry) => (
                    <tr key={entry.id}>
                      <td>{time(entry.createdAt)}</td>
                      <td><strong>{entry.actorName}</strong><small>{entry.actorUsername}</small></td>
                      <td>{actionNames[entry.action] || entry.action}</td>
                      <td><strong>{entry.entityName}</strong><small>{entityNames[entry.entityType] || entry.entityType}</small></td>
                      <td>{entry.branchName}</td>
                      <td><button className="account-link" onClick={() => setSelected(entry.id)}>Chi tiết</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!page.items.length && <p className="accounts-empty">Không có nhật ký phù hợp.</p>}
            <div className="accounts-pagination">
              <span>Hiển thị {page.items.length ? filters.page * 10 + 1 : 0}–{page.items.length ? filters.page * 10 + page.items.length : 0} / {page.total}</span>
              <span>10 dòng / trang</span>
              <nav aria-label="Phân trang nhật ký">
                <button aria-label="Trang trước" disabled={!filters.page} onClick={() => setFilters((old) => ({ ...old, page: old.page - 1 }))}>‹</button>
                <span aria-current="page">{filters.page + 1}</span>
                <button aria-label="Trang sau" disabled={(filters.page + 1) * 10 >= page.total} onClick={() => setFilters((old) => ({ ...old, page: old.page + 1 }))}>›</button>
              </nav>
            </div>
          </>
        )}
      </div>
      <div className="account-access-note audit-readonly">
        <strong>Nhật ký chỉ đọc</strong>
        <p>Các thay đổi được giữ lại phục vụ tra cứu và kiểm tra. Thời gian hiển thị theo giờ Việt Nam.</p>
      </div>
      {selected && <AuditDetail id={selected} onClose={() => setSelected(null)} />}
    </section>
  );
}

function AuditDetail({ id, onClose }) {
  const dialog = useRef(null);
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    const element = dialog.current;
    const previousFocus = document.activeElement;
    element.showModal();
    accountAccessApi.detail(id)
      .then((data) => { if (active) setDetail(data); })
      .catch((failure) => { if (active) setError(Object.values(apiErrors(failure)).join(' ')); });
    return () => {
      active = false;
      element.close();
      previousFocus?.focus();
    };
  }, [id]);

  const legacyFields = [
    ['username', 'Tên đăng nhập'],
    ...(detail?.event.action === 'UPDATE_ACCOUNT_INFORMATION' ? [
      ['fullName', 'Họ và tên'],
      ['email', 'Email'],
      ['phone', 'Số điện thoại'],
    ] : [
    ['role', 'Vai trò'],
    ['branchName', 'Phạm vi'],
    ['status', 'Truy cập'],
    ]),
  ];
  const before = detail?.beforeValues || detail?.before;
  const after = detail?.afterValues || detail?.after;
  const keys = [...new Set([...Object.keys(before || {}), ...Object.keys(after || {})])];
  const fields = keys.length ? keys.map(key => [key, fieldNames[key] || key]) : legacyFields;
  function value(snapshot, field) {
    const text = snapshot?.[field];
    if (field === 'role') return roleNames[text] || text || '—';
    if (field === 'status') return statusNames[text] || text || '—';
    if (text == null) return '—';
    if (typeof text === 'boolean') return text ? 'Có' : 'Không';
    if (typeof text === 'object') return JSON.stringify(text, null, 2);
    return String(text);
  }

  return (
    <dialog ref={dialog} className="account-drawer" aria-labelledby="audit-detail-title" onCancel={onClose}>
      <div className="account-drawer-heading">
        <h2 id="audit-detail-title">Chi tiết nhật ký</h2>
        <button onClick={onClose} aria-label="Đóng">×</button>
      </div>
      <div className="audit-detail-body">
        {error ? <p role="alert" className="account-error">{error}</p> : !detail ? <p role="status">Đang tải chi tiết…</p> : (
          <>
            <dl>
              <dt>Thời điểm</dt><dd>{time(detail.event.createdAt)}</dd>
              <dt>Người thực hiện</dt><dd>{detail.event.actorName}</dd>
              <dt>Vai trò</dt><dd>{roleNames[detail.actorRole] || detail.actorRole || 'Không xác định'}</dd>
              <dt>Hành động</dt><dd>{actionNames[detail.event.action] || detail.event.action}</dd>
              <dt>Đối tượng</dt><dd>{detail.event.entityName}</dd>
              <dt>Mã đối tượng</dt><dd>{detail.event.entityId || '—'}</dd>
              <dt>Lý do</dt><dd>{detail.event.reason || 'Không có'}</dd>
            </dl>
            <table className="access-permission-table">
              <thead><tr><th>Thông tin</th><th>Trước</th><th>Sau</th></tr></thead>
              <tbody>
                {fields.map(([field, label]) => (
                  <tr key={field}><td>{label}</td><td style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{value(before, field)}</td><td style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{value(after, field)}</td></tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>
      <div className="account-drawer-footer"><button onClick={onClose}>Đóng</button></div>
    </dialog>
  );
}
