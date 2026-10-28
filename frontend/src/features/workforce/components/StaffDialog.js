/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 26/10/2026
 * Updated: 27/10/2026
 * Description: Form tạo, sửa, đổi trạng thái, gán cơ sở và điều chuyển nhân viên.
 */

import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { staffApi, positions, staffErrors, dateLabel } from '../services/staffApi';

const fieldLabels = {
  code: 'Mã nhân viên',
  fullName: 'Họ tên',
  phone: 'Điện thoại',
  position: 'Vị trí',
  contactEmail: 'Email liên hệ',
  identityNumber: 'Số định danh',
  address: 'Địa chỉ',
  notes: 'Ghi chú',
  hiredOn: 'Ngày vào làm',
  resignedOn: 'Ngày nghỉ việc',
};
const today = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());

export default function StaffDialog({
  mode,
  employee,
  branches,
  user,
  branchScope,
  onClose,
  onSaved,
  onHistory,
  onAccounts,
  onBranches,
}) {
  const ref = useRef(null);
  const sending = useRef(false);
  const [form, setForm] = useState(() =>
    mode === 'create'
      ? {
          code: '',
          fullName: '',
          phone: '',
          position: 'RECEPTIONIST',
          contactEmail: '',
          identityNumber: '',
          address: '',
          notes: '',
          hiredOn: today(),
          resignedOn: '',
        }
      : mode === 'edit'
        ? Object.fromEntries(Object.keys(fieldLabels).map((k) => [k, employee[k] || '']))
        : { branchId: '', reason: '', resignedOn: employee.resignedOn || '' }
  );
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [review, setReview] = useState(false);
  const [saved, setSaved] = useState(null);
  const [conflict, setConflict] = useState(false);
  const [current, setCurrent] = useState(employee);
  const isStatus = mode === 'status';
  const activate = isStatus && current.status !== 'ACTIVE';
  const titles = {
    create: 'Thêm hồ sơ nhân viên',
    edit: 'Sửa hồ sơ nhân viên',
    status: activate ? 'Kích hoạt lại hồ sơ' : 'Ngừng sử dụng hồ sơ',
    assign: 'Gán cơ sở làm việc chính',
    transfer: 'Điều chuyển nhân viên',
  };
  const destination = branches.find((b) => b.id === form.branchId);

  useEffect(() => {
    const dialog = ref.current;
    const previous = document.activeElement;
    const overflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      dialog.close();
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  function change(key, value) {
    setForm((old) => ({ ...old, [key]: value }));
    setErrors((old) => ({ ...old, [key]: '', form: '' }));
  }
  const label = (key, value) =>
    key === 'position'
      ? positions[value]
      : ['hiredOn', 'resignedOn'].includes(key)
        ? dateLabel(value)
        : value || '—';
  const attrs = (key) => ({
    id: `staff-${key}`,
    value: form[key] || '',
    onChange: (e) => change(key, e.target.value),
    'aria-invalid': !!errors[key],
    'aria-describedby': errors[key] ? `staff-error-${key}` : undefined,
  });
  const field = (key, content, full = false, required = false) => (
    <div className={`staff-field ${full ? 'full' : ''}`}>
      <label htmlFor={`staff-${key}`}>
        {fieldLabels[key] || (key === 'branchId' ? 'Cơ sở mới' : 'Lý do')}
        {required ? ' *' : ''}
      </label>
      {content}
      {errors[key] && (
        <small className="staff-error" id={`staff-error-${key}`} role="alert">
          {errors[key]}
        </small>
      )}
    </div>
  );

  async function reload() {
    setBusy(true);
    try {
      const fresh = await staffApi.detail(current.id, branchScope);
      setCurrent(fresh);
      setReview(false);
      setConflict(false);
      setErrors({});
      if (mode === 'edit')
        setForm(Object.fromEntries(Object.keys(fieldLabels).map((k) => [k, fresh[k] || ''])));
      else setForm({ branchId: '', reason: '', resignedOn: fresh.resignedOn || '' });
    } catch (e) {
      setErrors(apiErrors(e));
    } finally {
      setBusy(false);
    }
  }
  async function submit(event) {
    event.preventDefault();
    if (sending.current || busy || conflict) return;
    if (!review) {
      let found = ['create', 'edit'].includes(mode) ? staffErrors(form) : {};
      if (['assign', 'transfer'].includes(mode) && !form.branchId)
        found.branchId = 'Vui lòng bổ sung cơ sở làm việc.';
      if (isStatus && form.resignedOn && form.resignedOn < current.hiredOn)
        found.resignedOn = 'Ngày nghỉ việc phải bằng hoặc sau ngày vào làm.';
      if ((form.reason || '').length > 1000)
        found.reason = 'Lý do chỉ được chứa tối đa 1000 ký tự.';
      setErrors(found);
      if (Object.keys(found).length) return;
      setReview(true);
      return;
    }
    sending.current = true;
    setBusy(true);
    setErrors({});
    try {
      const command = { ...form, resignedOn: form.resignedOn || null, version: current?.version };
      const result =
        mode === 'create'
          ? await staffApi.create(command, branchScope)
          : mode === 'edit'
            ? await staffApi.update(current.id, command, branchScope)
            : isStatus
              ? await staffApi.status(
                  current.id,
                  {
                    active: activate,
                    reason: form.reason,
                    resignedOn: activate ? null : form.resignedOn || null,
                    version: current.version,
                  },
                  branchScope
                )
              : await staffApi[mode](
                  current.id,
                  { branchId: form.branchId, reason: form.reason, version: current.version },
                  branchScope
                );
      const message =
        mode === 'create'
          ? 'Đã tạo hồ sơ nhân viên.'
          : isStatus
            ? `Đã chuyển trạng thái của hồ sơ thành “${activate ? 'Đang làm việc' : 'Đã ngừng'}”.`
            : 'Đã cập nhật hồ sơ nhân viên.';
      setSaved(result);
      onSaved(result, message);
    } catch (e) {
      const found = apiErrors(e);
      setErrors(found);
      if (e.response?.status === 409 && found.form?.includes('đã thay đổi')) setConflict(true);
      if (Object.keys(found).some((k) => k !== 'form')) setReview(false);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  const selectedBranch =
    current?.branchName || (branchScope ? user.branchName : 'Chưa gán · thực hiện sau khi lưu');
  return (
    <dialog
      ref={ref}
      className={`staff-dialog ${isStatus ? 'staff-status-dialog' : ''}`}
      aria-labelledby="staff-dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
    >
      <header className="staff-dialog-header">
        <div>
          <h2 id="staff-dialog-title">{titles[mode]}</h2>
          {current && (
            <p>
              {current.fullName} · {current.code}
            </p>
          )}
        </div>
        <button
          type="button"
          className="staff-close"
          aria-label="Đóng"
          disabled={busy}
          onClick={onClose}
        >
          ×
        </button>
      </header>
      {saved ? (
        <>
          <div className="staff-dialog-body">
            <div className="staff-banner success" role="status">
              <strong>
                {mode === 'transfer'
                  ? 'Đã ghi nhận điều chuyển'
                  : mode === 'assign'
                    ? 'Đã ghi nhận phân công'
                    : 'Đã lưu hồ sơ'}
              </strong>
              {['assign', 'transfer'].includes(mode) && (
                <p>
                  {current.branchName || 'Chưa gán'} → {saved.branchName}
                </p>
              )}
            </div>
            <dl className="staff-summary">
              <dt>Nhân viên</dt>
              <dd>
                {saved.fullName} · {saved.code}
              </dd>
              <dt>Cơ sở làm việc chính</dt>
              <dd>{saved.branchName || 'Chưa gán'}</dd>
              <dt>Người thực hiện</dt>
              <dd>{user.fullName || user.username}</dd>
            </dl>
            {saved.userId && onAccounts && (
              <button
                type="button"
                className="staff-outline"
                onClick={() => {
                  onClose();
                  onAccounts(saved.username);
                }}
              >
                Kiểm tra quyền tài khoản
              </button>
            )}
          </div>
          <footer className="staff-dialog-footer">
            <button type="button" onClick={onClose}>
              Đóng
            </button>
            <button
              type="button"
              className="staff-primary"
              onClick={() => {
                onClose();
                onHistory();
              }}
            >
              {['assign', 'transfer'].includes(mode) ? 'Xem lịch sử' : 'Xem hồ sơ'}
            </button>
          </footer>
        </>
      ) : (
        <form noValidate onSubmit={submit} className="staff-dialog-form">
          <div className="staff-dialog-body">
            <fieldset disabled={busy}>
              {errors.form && (
                <div className="staff-banner error" role="alert">
                  {errors.form}
                </div>
              )}
              {conflict && (
                <button type="button" className="staff-outline" onClick={reload}>
                  Tải lại hồ sơ
                </button>
              )}
              {review ? (
                <>
                  <div className="staff-banner">
                    <strong>
                      Xác nhận{' '}
                      {mode === 'create'
                        ? 'tạo hồ sơ'
                        : mode === 'edit'
                          ? 'cập nhật hồ sơ'
                          : isStatus
                            ? activate
                              ? 'kích hoạt hồ sơ'
                              : 'ngừng hồ sơ'
                            : mode === 'assign'
                              ? 'gán cơ sở'
                              : 'điều chuyển'}{' '}
                      {form.fullName || current?.fullName}?
                    </strong>
                    <p>
                      {isStatus
                        ? activate
                          ? 'Hồ sơ hoạt động trở lại. Tài khoản đăng nhập cần được kiểm tra riêng.'
                          : 'Tài khoản đăng nhập liên kết sẽ tự động bị khóa. Lịch sử vẫn được giữ nguyên.'
                        : 'Thông tin dưới đây sẽ được ghi nhận sau khi xác nhận.'}
                    </p>
                  </div>
                  <dl className="staff-review">
                    {['create', 'edit'].includes(mode) ? (
                      Object.keys(fieldLabels)
                        .filter((k) =>
                          mode === 'create' ? !!form[k] : form[k] !== (current[k] || '')
                        )
                        .map((k) => (
                          <div key={k}>
                            <dt>{fieldLabels[k]}</dt>
                            <dd>
                              {mode === 'edit' && <del>{label(k, current[k])}</del>}
                              <strong>{label(k, form[k])}</strong>
                            </dd>
                          </div>
                        ))
                    ) : isStatus ? (
                      <>
                        <div>
                          <dt>Ngày nghỉ việc</dt>
                          <dd>{activate ? '—' : dateLabel(form.resignedOn)}</dd>
                        </div>
                        <div>
                          <dt>Lý do</dt>
                          <dd>{form.reason || '—'}</dd>
                        </div>
                      </>
                    ) : (
                      <>
                        <div>
                          <dt>Cơ sở hiện tại</dt>
                          <dd>{current.branchName || 'Chưa gán'}</dd>
                        </div>
                        <div>
                          <dt>Cơ sở mới</dt>
                          <dd>
                            {destination?.name} · {destination?.code}
                          </dd>
                        </div>
                        <div>
                          <dt>Lý do</dt>
                          <dd>{form.reason || '—'}</dd>
                        </div>
                      </>
                    )}
                  </dl>
                </>
              ) : ['create', 'edit'].includes(mode) ? (
                <>
                  <div className="staff-form-grid">
                    {field('code', <input {...attrs('code')} autoComplete="off" />, false, true)}
                    {field(
                      'fullName',
                      <input
                        {...attrs('fullName')}
                        autoComplete="off"
                        placeholder="Nhập thông tin…"
                      />,
                      false,
                      true
                    )}
                    {field(
                      'phone',
                      <input {...attrs('phone')} type="tel" placeholder="Nhập thông tin…" />
                    )}
                    {field(
                      'position',
                      <select {...attrs('position')}>
                        {Object.entries(positions).map(([key, value]) => (
                          <option key={key} value={key}>
                            {value}
                          </option>
                        ))}
                      </select>,
                      false,
                      true
                    )}
                    {field(
                      'contactEmail',
                      <input
                        {...attrs('contactEmail')}
                        type="email"
                        placeholder="Nhập thông tin…"
                      />,
                      true
                    )}
                    <div className="staff-field full">
                      <label htmlFor="staff-current-branch">Cơ sở làm việc chính</label>
                      <input id="staff-current-branch" readOnly value={selectedBranch} />
                    </div>
                    {field(
                      'hiredOn',
                      <input {...attrs('hiredOn')} type="date" min="0001-01-01" max="9999-12-31" />,
                      false,
                      true
                    )}
                    {field(
                      'identityNumber',
                      <input {...attrs('identityNumber')} autoComplete="off" />
                    )}
                    {mode === 'edit' &&
                      field(
                        'resignedOn',
                        <input
                          {...attrs('resignedOn')}
                          type="date"
                          min="0001-01-01"
                          max="9999-12-31"
                        />
                      )}
                    {field('address', <input {...attrs('address')} />, true)}
                    {field(
                      'notes',
                      <textarea
                        {...attrs('notes')}
                        placeholder="Hồ sơ nhân sự và quyền truy cập được quản lý riêng."
                      />,
                      true
                    )}
                  </div>
                  <p className="staff-help">
                    Vị trí trong hồ sơ không tự thay đổi vai trò tài khoản.
                  </p>
                </>
              ) : isStatus ? (
                <>
                  {current.userId && (
                    <div className={`staff-banner ${activate ? '' : 'warning'}`}>
                      <strong>
                        {activate
                          ? 'Tài khoản được quản lý riêng'
                          : 'Tài khoản liên kết sẽ bị khóa'}
                      </strong>
                      <p>
                        {activate
                          ? 'Kích hoạt lại hồ sơ không tự mở tài khoản đăng nhập.'
                          : `Khi ngừng hồ sơ, hệ thống tự động khóa tài khoản ${current.username} và thu hồi phiên truy cập.`}
                      </p>
                    </div>
                  )}
                  <div className="staff-form-grid">
                    {!activate &&
                      field('resignedOn', <input {...attrs('resignedOn')} type="date" />, true)}
                    {field(
                      'reason',
                      <textarea
                        {...attrs('reason')}
                        placeholder="Nhân viên không còn làm việc tại đơn vị."
                      />,
                      true
                    )}
                  </div>
                  {current.managedBranches && (
                    <p className="staff-warning-text">
                      Đang quản lý {current.managedBranches} · kiểm tra người thay thế{' '}
                      {onBranches && (
                        <button
                          type="button"
                          className="staff-link"
                          onClick={() => {
                            onClose();
                            onBranches();
                          }}
                        >
                          Xem cơ sở ›
                        </button>
                      )}
                    </p>
                  )}
                  {current.userId && onAccounts && (
                    <button
                      type="button"
                      className="staff-link"
                      onClick={() => {
                        onClose();
                        onAccounts(current.username);
                      }}
                    >
                      Kiểm tra quyền truy cập ›
                    </button>
                  )}
                </>
              ) : (
                <>
                  <div className="staff-banner">
                    <strong>
                      {mode === 'assign'
                        ? 'Hiện tại: chưa có cơ sở chính'
                        : 'Cơ sở hiện tại: ' + current.branchName}
                    </strong>
                    <p>Vị trí nhân sự: {positions[current.position]}</p>
                  </div>
                  <div className="staff-form-grid">
                    {field(
                      'branchId',
                      <select {...attrs('branchId')}>
                        <option value="">Chọn cơ sở</option>
                        {branches
                          .filter((b) => b.id !== current.branchId)
                          .map((b) => (
                            <option key={b.id} value={b.id} disabled={b.status !== 'ACTIVE'}>
                              {b.name} · {b.code}
                              {b.status !== 'ACTIVE' ? ' · Ngừng hoạt động' : ''}
                            </option>
                          ))}
                      </select>,
                      true,
                      true
                    )}
                    {mode === 'transfer' &&
                      field(
                        'reason',
                        <textarea
                          {...attrs('reason')}
                          placeholder="Bố trí nhân sự theo kế hoạch vận hành."
                        />,
                        true
                      )}
                  </div>
                  {destination && (
                    <div className="staff-assignment-preview">
                      <h3>Phân công sau khi lưu</h3>
                      <dl className="staff-summary">
                        <dt>Nhân viên</dt>
                        <dd>
                          {current.fullName} · {current.code}
                        </dd>
                        <dt>Cơ sở</dt>
                        <dd>
                          {destination.name} · {destination.code}
                        </dd>
                      </dl>
                    </div>
                  )}
                  {current.managedBranches && (
                    <div className="staff-banner warning">
                      <strong>Đối chiếu trách nhiệm quản lý</strong>
                      <p>
                        Nhân viên đang quản lý {current.managedBranches}. Điều chuyển cơ sở làm việc
                        không tự thay đổi các cơ sở đang quản lý.
                      </p>
                      {onBranches && (
                        <button
                          type="button"
                          className="staff-link"
                          onClick={() => {
                            onClose();
                            onBranches();
                          }}
                        >
                          Xem quản lý cơ sở hiện tại ›
                        </button>
                      )}
                    </div>
                  )}
                  <p className="staff-help">
                    Lịch sử phân công trước đó được giữ lại.
                    {current.accountRole === 'RECEPTIONIST'
                      ? ' Phạm vi tài khoản Lễ tân sẽ chuyển sang cơ sở mới; nhân viên cần đăng nhập lại.'
                      : ' Quyền truy cập cần được kiểm tra riêng.'}
                  </p>
                </>
              )}
            </fieldset>
          </div>
          <footer className="staff-dialog-footer">
            <button
              type="button"
              disabled={busy}
              onClick={() => (review ? setReview(false) : onClose())}
            >
              {review ? 'Quay lại' : 'Hủy'}
            </button>
            <button
              type="submit"
              disabled={busy || conflict}
              className={isStatus && !activate && review ? 'staff-danger' : 'staff-primary'}
            >
              {busy
                ? 'Đang lưu…'
                : review
                  ? isStatus
                    ? activate
                      ? 'Kích hoạt hồ sơ'
                      : 'Ngừng hồ sơ'
                    : mode === 'create' || mode === 'edit'
                      ? 'Lưu hồ sơ'
                      : 'Xác nhận phân công'
                  : 'Xem lại & xác nhận'}
            </button>
          </footer>
        </form>
      )}
    </dialog>
  );
}
