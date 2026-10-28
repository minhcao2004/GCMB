/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Tra cứu nhật ký theo khoảng ngày, người thực hiện, hành động và cơ sở.
 */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../../auth/services/authApi';
import { accountApi } from '../services/accountApi';
import { accountAccessApi, actionNames, roleNames } from '../services/accountAccessApi';
import '../styles/accounts.css';
import '../styles/accountAccess.css';

const initialFilters = { q: '', action: '', branchId: '', from: '', to: '', page: 0 };
const statusNames = {
  ACTIVE: 'Hoạt động',
  LOCKED: 'Đã khóa',
  REVOKED: 'Đã thu hồi',
  INACTIVE: 'Ngừng hoạt động',
  SUSPENDED: 'Tạm ngừng',
  CLOSED: 'Đã đóng',
  CONFIRMED: 'Đã xác nhận',
  IN_HOUSE: 'Đang sử dụng',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy',
  NO_SHOW: 'Không đến',
  AVAILABLE: 'Sẵn sàng',
  OCCUPIED: 'Đang sử dụng',
  CLEANING: 'Đang dọn',
  MAINTENANCE: 'Bảo trì',
  OUT_OF_SERVICE: 'Ngừng sử dụng',
  PHONE: 'Điện thoại',
  WALK_IN: 'Khách trực tiếp',
  FACEBOOK: 'Facebook',
  ZALO: 'Zalo',
  OTHER: 'Khác',
  HOURLY: 'Theo giờ',
  COMBO: 'Theo combo',
  CINEMA: 'Xem phim',
  MUSIC: 'Âm nhạc',
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
  SEPAY: 'SePay',
  SYSTEM: 'Hệ thống',
  WEEKDAY: 'Ngày thường',
  WEEKEND: 'Cuối tuần',
  HOLIDAY: 'Ngày lễ',
};
const entityNames = {
  USER_ACCOUNT: 'Tài khoản',
  ROOM: 'Phòng',
  ROOM_TYPE: 'Hạng phòng',
  PRICE_RULE: 'Quy tắc giá',
  COMBO: 'Combo',
  BOOKING: 'Đặt phòng',
  BRANCH: 'Cơ sở',
};
const fieldNames = {
  managerNames: 'Các quản lý phụ trách',
  depositRequested: 'Cọc yêu cầu',
  cashAmount: 'Tiền mặt',
  bankAmount: 'Chuyển khoản',
  address: 'Địa chỉ',
  notes: 'Ghi chú vận hành',
  closed_on: 'Ngày đóng',
  username: 'Tên đăng nhập',
  role: 'Vai trò',
  branchName: 'Cơ sở',
  status: 'Trạng thái',
  fullName: 'Họ tên',
  email: 'Email',
  phone: 'Số điện thoại',
  name: 'Tên',
  mode: 'Loại dịch vụ',
  capacity: 'Sức chứa',
  description: 'Mô tả',
  floor_number: 'Tầng',
  operating_status: 'Trạng thái phòng',
  status_reason: 'Lý do trạng thái',
  expected_ready_at: 'Dự kiến sẵn sàng',
  active: 'Đang áp dụng',
  valid_from: 'Hiệu lực từ',
  valid_to: 'Hiệu lực đến',
  minute_from: 'Phút bắt đầu',
  minute_to: 'Phút kết thúc',
  weekdays: 'Ngày trong tuần',
  day_kind: 'Loại ngày',
  specific_date: 'Ngày cụ thể',
  hourly_price: 'Giá giờ',
  overtime_hourly_price: 'Giá vượt giờ',
  billing_step_minutes: 'Bước tính phút',
  priority: 'Ưu tiên',
  price: 'Giá',
  duration_minutes: 'Thời lượng (phút)',
  deposit_required: 'Cọc yêu cầu',
  day_group: 'Nhóm ngày',
  min_guests: 'Số khách tối thiểu',
  included_description: 'Dịch vụ bao gồm',
  contact_name: 'Tên khách',
  contact_phone: 'Điện thoại khách',
  guest_count: 'Số khách',
  planned_start: 'Giờ hẹn',
  planned_end: 'Giờ kết thúc',
  actual_checkin: 'Check-in thực tế',
  actual_checkout: 'Check-out thực tế',
  note: 'Ghi chú',
  channel: 'Kênh đặt',
  pricing_mode: 'Cách tính giá',
  roomCode: 'Phòng',
  plannedEnd: 'Giờ kết thúc',
  startsAt: 'Giờ bắt đầu',
  endsAt: 'Giờ kết thúc',
  updatedAmount: 'Tiền phòng',
  addedAmount: 'Tiền gia hạn',
  amount: 'Số tiền',
  adjustmentAmount: 'Tiền điều chỉnh',
  depositBalance: 'Số dư cọc',
  depositReceived: 'Cọc đã nhận',
  depositAmount: 'Tiền cọc',
  depositMethod: 'Hình thức nhận cọc',
  earlyCheckIn: 'Nhận phòng sớm',
  actualCheckin: 'Giờ nhận phòng',
  hours: 'Số giờ',
  billedMinutes: 'Số phút tính phí',
  hourlyPrice: 'Giá giờ',
  startedAt: 'Bắt đầu tính giờ',
  stoppedAt: 'Dừng tính giờ',
  combos: 'Combo',
  priceRules: 'Quy tắc giá',
};

const moneyFields = new Set([
  'cashAmount',
  'bankAmount',
  'hourly_price',
  'overtime_hourly_price',
  'price',
  'deposit_required',
  'updatedAmount',
  'addedAmount',
  'amount',
  'adjustmentAmount',
  'depositBalance',
  'depositReceived',
  'depositAmount',
  'hourlyPrice',
]);
const dateFields = new Set([
  'expected_ready_at',
  'valid_from',
  'valid_to',
  'specific_date',
  'closed_on',
  'planned_start',
  'planned_end',
  'actual_checkin',
  'actual_checkout',
  'plannedEnd',
  'startsAt',
  'endsAt',
  'actualCheckin',
  'startedAt',
  'stoppedAt',
]);

export function auditValue(value, field) {
  if (value == null || value === '') return '—';
  if (typeof value === 'boolean') return value ? 'Có' : 'Không';
  if (field === 'role') return roleNames[value] || 'Không xác định';
  if (moneyFields.has(field) && Number.isFinite(Number(value)))
    return `${Number(value).toLocaleString('vi-VN')} ₫`;
  if (dateFields.has(field) && !Number.isNaN(Date.parse(value))) {
    return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value.split('-').reverse().join('/') : time(value);
  }
  if (Array.isArray(value)) {
    if (field === 'weekdays')
      return value
        .map(
          (day) =>
            ({
              1: 'Thứ hai',
              2: 'Thứ ba',
              3: 'Thứ tư',
              4: 'Thứ năm',
              5: 'Thứ sáu',
              6: 'Thứ bảy',
              7: 'Chủ nhật',
            })[day] || '—'
        )
        .join(', ');
    return (
      value
        .map((item) =>
          typeof item === 'object' && item
            ? [
                item.name,
                item.price != null
                  ? auditValue(item.price, 'price')
                  : item.hourly_price != null
                    ? auditValue(item.hourly_price, 'hourly_price')
                    : null,
              ]
                .filter(Boolean)
                .join(' · ')
            : ''
        )
        .filter(Boolean)
        .join(', ') || '—'
    );
  }
  if (
    typeof value === 'object' ||
    /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i.test(String(value))
  )
    return '—';
  return statusNames[value] || String(value);
}

export function auditRows(detail) {
  const before = detail?.beforeValues || detail?.before || {};
  const after = detail?.afterValues || detail?.after || {};
  const rows = [];
  function add(field, previous, next) {
    if (!fieldNames[field]) return;
    const oldValue = auditValue(previous, field);
    const newValue = auditValue(next, field);
    if (oldValue !== newValue)
      rows.push({ field, label: fieldNames[field], before: oldValue, after: newValue });
  }
  new Set([...Object.keys(before), ...Object.keys(after)]).forEach((field) =>
    add(field, before[field], after[field])
  );
  // The previous snapshot's event belongs to an earlier action, not the before-state of this action.
  const event = after.event;
  if (event && event.type === detail?.event?.action) {
    const previousFields = {
      roomCode: 'previousRoomCode',
      updatedAmount: 'previousAmount',
      startsAt: 'previousStart',
      endsAt: 'previousEnd',
    };
    Object.entries(event.details || {}).forEach(([field, value]) => {
      if (field === 'plannedEnd' && rows.some((row) => row.field === 'planned_end')) return;
      add(field, event.details[previousFields[field]], value);
    });
  }
  return rows;
}

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
          <input
            id="audit-from"
            type="date"
            value={filters.from}
            onChange={(event) => filter('from', event.target.value)}
          />
        </div>
        <div className="audit-date">
          <label htmlFor="audit-to">Đến ngày</label>
          <input
            id="audit-to"
            type="date"
            value={filters.to}
            onChange={(event) => filter('to', event.target.value)}
          />
        </div>
        <input
          aria-label="Tìm nhật ký"
          placeholder="Tìm theo tên người / đối tượng"
          maxLength={200}
          value={filters.q}
          onChange={(event) => filter('q', event.target.value)}
        />
        <select
          aria-label="Lọc hành động"
          value={filters.action}
          onChange={(event) => filter('action', event.target.value)}
        >
          <option value="">Hành động: Tất cả</option>
          {actions.map((action) => (
            <option key={action} value={action}>
              {actionNames[action] || action}
            </option>
          ))}
        </select>
        <select
          aria-label="Lọc cơ sở"
          value={filters.branchId}
          onChange={(event) => filter('branchId', event.target.value)}
        >
          <option value="">Cơ sở: Tất cả</option>
          {branches.map((branch) => (
            <option key={branch.id} value={branch.id}>
              {branch.name}
            </option>
          ))}
        </select>
      </div>
      <div className="accounts-card" aria-busy={loading}>
        {loading ? (
          <p className="accounts-empty" role="status">
            Đang tải nhật ký…
          </p>
        ) : error ? (
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
                    <th>Thời điểm</th>
                    <th>Người thực hiện</th>
                    <th>Hành động</th>
                    <th>Đối tượng</th>
                    <th>Phạm vi</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((entry) => (
                    <tr key={entry.id}>
                      <td>{time(entry.createdAt)}</td>
                      <td>
                        <strong>{entry.actorName}</strong>
                      </td>
                      <td>{actionNames[entry.action] || entry.action}</td>
                      <td>
                        <strong>{entry.entityName}</strong>
                        <small>{entityNames[entry.entityType] || entry.entityType}</small>
                      </td>
                      <td>{entry.branchName}</td>
                      <td>
                        <button className="account-link" onClick={() => setSelected(entry.id)}>
                          Chi tiết
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!page.items.length && <p className="accounts-empty">Không có nhật ký phù hợp.</p>}
            <div className="accounts-pagination">
              <span>
                Hiển thị {page.items.length ? filters.page * 10 + 1 : 0}–
                {page.items.length ? filters.page * 10 + page.items.length : 0} / {page.total}
              </span>
              <span>10 dòng / trang</span>
              <nav aria-label="Phân trang nhật ký">
                <button
                  aria-label="Trang trước"
                  disabled={!filters.page}
                  onClick={() => setFilters((old) => ({ ...old, page: old.page - 1 }))}
                >
                  ‹
                </button>
                <span aria-current="page">{filters.page + 1}</span>
                <button
                  aria-label="Trang sau"
                  disabled={(filters.page + 1) * 10 >= page.total}
                  onClick={() => setFilters((old) => ({ ...old, page: old.page + 1 }))}
                >
                  ›
                </button>
              </nav>
            </div>
          </>
        )}
      </div>
      <div className="account-access-note audit-readonly">
        <strong>Nhật ký chỉ đọc</strong>
        <p>
          Các thay đổi được giữ lại phục vụ tra cứu và kiểm tra. Thời gian hiển thị theo giờ Việt
          Nam.
        </p>
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
    accountAccessApi
      .detail(id)
      .then((data) => {
        if (active) setDetail(data);
      })
      .catch((failure) => {
        if (active) setError(Object.values(apiErrors(failure)).join(' '));
      });
    return () => {
      active = false;
      element.close();
      previousFocus?.focus();
    };
  }, [id]);

  const rows = auditRows(detail);

  return (
    <dialog
      ref={dialog}
      className="account-drawer"
      aria-labelledby="audit-detail-title"
      onCancel={onClose}
    >
      <div className="account-drawer-heading">
        <h2 id="audit-detail-title">Chi tiết nhật ký</h2>
        <button onClick={onClose} aria-label="Đóng">
          ×
        </button>
      </div>
      <div className="audit-detail-body">
        {error ? (
          <p role="alert" className="account-error">
            {error}
          </p>
        ) : !detail ? (
          <p role="status">Đang tải chi tiết…</p>
        ) : (
          <>
            <dl>
              <dt>Thời điểm</dt>
              <dd>{time(detail.event.createdAt)}</dd>
              <dt>Người thực hiện</dt>
              <dd>{detail.event.actorName}</dd>
              <dt>Vai trò</dt>
              <dd>{roleNames[detail.actorRole] || detail.actorRole || 'Không xác định'}</dd>
              <dt>Hành động</dt>
              <dd>{actionNames[detail.event.action] || detail.event.action}</dd>
              <dt>Đối tượng</dt>
              <dd>{detail.event.entityName}</dd>
              <dt>Cơ sở</dt>
              <dd>{detail.event.branchName}</dd>
              {(detail.event.reason ||
                (detail.afterValues?.event?.type === detail.event.action &&
                  detail.afterValues.event.reason)) && (
                <>
                  <dt>Lý do</dt>
                  <dd>{detail.event.reason || detail.afterValues.event.reason}</dd>
                </>
              )}
            </dl>
            {rows.length ? (
              <table className="access-permission-table">
                <thead>
                  <tr>
                    <th>Thông tin</th>
                    <th>Trước</th>
                    <th>Sau</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.field}>
                      <td>{row.label}</td>
                      <td>{row.before}</td>
                      <td>{row.after}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p>Đã ghi nhận thao tác. Không có thay đổi thông tin cần hiển thị.</p>
            )}
          </>
        )}
      </div>
      <div className="account-drawer-footer">
        <button onClick={onClose}>Đóng</button>
      </div>
    </dialog>
  );
}
