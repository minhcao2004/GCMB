/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: Công bố ca tuần và xác nhận đăng ký/thay người; BR-53, BR-192, BR-203, MSG14.
 */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { shiftApi, addDays, dateLabel, shiftLabel, slotKey } from '../services/shiftApi';
export default function ShiftDialog({ dialog, manager, scope, onClose, onSaved, onReload }) {
  const ref = useRef(null),
    sending = useRef(false);
  const { type, data, shift, assignment } = dialog;
  const template = shift && data.templates.find((t) => t.id === shift.templateId);
  const original = new Map(data.shifts.map((s) => [slotKey(s.date, s.templateId), s]));
  const [slots, setSlots] = useState(() =>
    Array.from({ length: 7 }, (_, day) =>
      data.templates.map((t) => {
        const date = addDays(data.week, day),
          old = original.get(slotKey(date, t.id));
        return {
          date,
          templateId: t.id,
          capacity: String(old?.capacity || 2),
          open:
            (dialog.focusSlot === slotKey(date, t.id) &&
              data.editableSlots?.includes(slotKey(date, t.id))) ||
            old?.status === 'OPEN',
          version: old?.version ?? null,
        };
      })
    ).flat()
  );
  const [employeeId, setEmployeeId] = useState(''),
    [reason, setReason] = useState('');
  const [defaultCapacity, setDefaultCapacity] = useState('2');
  const [review, setReview] = useState(false),
    [busy, setBusy] = useState(false),
    [errors, setErrors] = useState({});
  const [stale, setStale] = useState(false);
  const names = {
    publish: 'Mở / chỉnh ca trong tuần',
    register: 'Xác nhận đăng ký ca',
    replace: 'Nhân viên làm thay ca',
    detail: 'Chi tiết ca làm',
  };
  useEffect(() => {
    const element = ref.current,
      previous = document.activeElement,
      overflow = document.body.style.overflow;
    element.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  const updateSlot = (index, key, value) => {
    if (!data.editableSlots?.includes(slotKey(slots[index].date, slots[index].templateId))) return;
    setReview(false);
    setErrors({});
    setSlots((old) => old.map((s, i) => (i === index ? { ...s, [key]: value } : s)));
  };
  const editable = (s) => !!data.editableSlots?.includes(slotKey(s.date, s.templateId));
  const participants = shift ? data.assignments.filter((a) => a.shiftId === shift.id) : [];
  const candidates = data.employees.filter(
    (e) => e.id !== assignment?.employeeId && !participants.some((a) => a.employeeId === e.id)
  );
  const replacement = candidates.find((e) => e.id === employeeId);
  const canReplace =
    manager && !data.readOnly && shift && new Date(shift.endsAt).getTime() > Date.now();
  const changes = slots.filter((s) => {
    const before = original.get(slotKey(s.date, s.templateId));
    return before
      ? before.capacity !== Number(s.capacity) || (before.status === 'OPEN') !== s.open
      : s.open;
  });
  async function submit(event) {
    event.preventDefault();
    if (busy || sending.current || stale) return;
    const found = {};
    if (type === 'publish') {
      if (!changes.length) found.form = 'Chưa có thay đổi để công bố.';
      for (const s of changes) {
        const count = original.get(slotKey(s.date, s.templateId))?.registered || 0;
        if (
          !Number.isInteger(Number(s.capacity)) ||
          Number(s.capacity) < 1 ||
          Number(s.capacity) > 2147483647
        )
          found.form = 'Số chỗ phải là số nguyên lớn hơn 0.'; // MSG44
        else if (Number(s.capacity) < count)
          found.form = `Ca ngày ${dateLabel(s.date)} đã có ${count} người đăng ký. Số chỗ không được nhỏ hơn ${count}.`; // MSG85
      }
    }
    if (type === 'replace') {
      if (!employeeId) found.employeeId = 'Vui lòng bổ sung nhân viên làm thay.';
      if (!reason.trim()) found.reason = 'Vui lòng bổ sung lý do thay người.';
    }
    if (Object.keys(found).length) {
      setErrors(found);
      return;
    }
    if (!review && type !== 'register') {
      setReview(true);
      return;
    }
    sending.current = true;
    setBusy(true);
    setErrors({});
    try {
      const result =
        type === 'publish'
          ? await shiftApi.publish(
              changes.map((s) => ({ ...s, capacity: Number(s.capacity) })),
              scope
            )
          : type === 'replace'
            ? await shiftApi.replace(
                assignment.id,
                { employeeId, reason, version: shift.version },
                scope
              )
            : await shiftApi.register(shift.id);
      onSaved(
        result,
        type === 'register'
          ? 'Đã xác nhận đăng ký ca.'
          : type === 'replace'
            ? 'Đã cập nhật nhân viên làm thay ca.'
            : 'Đã cập nhật lịch mở ca trong tuần.'
      ); // MSG11/12
    } catch (e) {
      setErrors(apiErrors(e));
      setStale(
        e.response?.status === 409 &&
          Object.values(apiErrors(e)).some((v) => v.includes('đã thay đổi'))
      );
      setReview(false);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <dialog
      ref={ref}
      className={`shift-dialog ${type === 'publish' ? 'shift-dialog-wide' : ''}`}
      aria-labelledby="shift-dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
    >
      <header>
        <div>
          <h2 id="shift-dialog-title">{names[type]}</h2>
          <p>
            {data.branchName} ·{' '}
            {type === 'publish'
              ? `${dateLabel(data.week)} – ${dateLabel(addDays(data.week, 6))}`
              : `${dateLabel(shift.date)} · ${shiftLabel(template)}`}
          </p>
        </div>
        <button type="button" onClick={onClose} disabled={busy} aria-label="Đóng">
          ×
        </button>
      </header>
      <form onSubmit={submit}>
        <div className="shift-dialog-body">
          {!!Object.keys(errors).length && (
            <div className="shift-message error" role="alert">
              {Object.values(errors).map((message, i) => (
                <p key={i}>{message}</p>
              ))}
            </div>
          )}
          {stale && (
            <button type="button" onClick={onReload}>
              Tải lại lịch để kiểm tra
            </button>
          )}
          <fieldset disabled={busy || stale || data.readOnly}>
            {type === 'detail' ? (
              <>
                <div className="shift-message">
                  {shift.registered}/{shift.capacity} người ·{' '}
                  {shift.status === 'OPEN' ? 'Đang mở đăng ký' : 'Đóng đăng ký mới'}
                </div>
                <h3>Nhân viên được phân công</h3>
                {participants.length ? (
                  <ul className="shift-people">
                    {participants.map((a) => (
                      <li key={a.id}>
                        <div>
                          <strong>{a.fullName}</strong>
                          <small>
                            {a.code} · {a.source === 'MANAGER' ? 'Quản lý phân công' : 'Tự đăng ký'}
                            {a.employeeStatus !== 'ACTIVE' ? ' · Hồ sơ đã ngừng' : ''}
                          </small>
                        </div>
                        {canReplace && (
                          <button type="button" onClick={() => dialog.onReplace(a)}>
                            Làm thay
                          </button>
                        )}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p>Chưa có nhân viên đăng ký ca này.</p>
                )}
                {data.history.some((h) => h.shiftId === shift.id) && (
                  <>
                    <h3>Lịch sử thay người</h3>
                    <ul>
                      {data.history
                        .filter((h) => h.shiftId === shift.id)
                        .map((h) => (
                          <li key={h.id}>
                            {h.fullName} · {h.reason} ·{' '}
                            {new Date(h.cancelledAt).toLocaleString('vi-VN')}
                          </li>
                        ))}
                    </ul>
                  </>
                )}
              </>
            ) : review ? (
              <div className="shift-review">
                <h3>
                  Xác nhận {type === 'publish' ? 'công bố các thay đổi' : 'nhân viên làm thay'}?
                </h3>
                {type === 'publish' ? (
                  <>
                    <p>Những ca không có trong danh sách dưới đây được giữ nguyên.</p>
                    <ul>
                      {changes.map((s) => {
                        const before = original.get(slotKey(s.date, s.templateId));
                        const t = data.templates.find((t) => t.id === s.templateId);
                        return (
                          <li key={slotKey(s.date, s.templateId)}>
                            {dateLabel(s.date)} · {shiftLabel(t)}:{' '}
                            {before
                              ? `${before.capacity} chỗ, ${before.status === 'OPEN' ? 'mở' : 'đóng'}`
                              : 'Chưa công bố'}{' '}
                            → {s.capacity} chỗ, {s.open ? 'mở đăng ký' : 'đóng đăng ký'}
                          </li>
                        );
                      })}
                    </ul>
                  </>
                ) : (
                  <>
                    <p>
                      <strong>
                        {assignment.fullName} → {replacement?.fullName}
                      </strong>
                    </p>
                    <p>Giữ nguyên ngày, giờ và sức chứa của ca. Người cũ được lưu trong lịch sử.</p>
                    <p>Lý do: {reason}</p>
                  </>
                )}
              </div>
            ) : type === 'publish' ? (
              <>
                <div className="shift-bulk">
                  <label>
                    Số người mặc định
                    <input
                      type="number"
                      min="1"
                      step="1"
                      value={defaultCapacity}
                      onChange={(e) => setDefaultCapacity(e.target.value)}
                    />
                  </label>
                  <button
                    type="button"
                    onClick={() =>
                      setSlots((old) =>
                        old.map((s) =>
                          editable(s) && s.open ? { ...s, capacity: defaultCapacity } : s
                        )
                      )
                    }
                  >
                    Áp dụng cho ca đã chọn
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      setSlots((old) => old.map((s) => (editable(s) ? { ...s, open: true } : s)))
                    }
                  >
                    Chọn các ca còn chỉnh được
                  </button>
                </div>
                <p className="shift-help">
                  Chọn ca để mở đăng ký. Bỏ chọn một ca đã mở chỉ đóng đăng ký mới, không xóa người
                  đã đăng ký. Ca đã kết thúc, đã có chấm công hoặc thuộc kỳ đã chốt sẽ bị khóa.
                </p>
                <div className="shift-editor-grid">
                  {Array.from({ length: 7 }, (_, day) => (
                    <section key={day}>
                      <h3>
                        {['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'][day]}
                        <small>{dateLabel(addDays(data.week, day)).slice(0, 5)}</small>
                      </h3>
                      {data.templates.map((t, i) => {
                        const index = day * data.templates.length + i,
                          s = slots[index],
                          before = original.get(slotKey(s.date, t.id));
                        return (
                          <div key={t.id} className={s.open ? 'selected' : ''}>
                            <label>
                              <input
                                type="checkbox"
                                checked={s.open}
                                disabled={!editable(s)}
                                onChange={(e) => updateSlot(index, 'open', e.target.checked)}
                              />
                              Ca {i + 1}
                            </label>
                            <small>{shiftLabel(t)}</small>
                            <label>
                              Số chỗ
                              <input
                                aria-label={`Số chỗ ${s.date} ca ${i + 1}`}
                                type="number"
                                min="1"
                                max="2147483647"
                                step="1"
                                value={s.capacity}
                                disabled={!editable(s) || (!s.open && !before)}
                                onChange={(e) => updateSlot(index, 'capacity', e.target.value)}
                              />
                            </label>
                            <small>Đã đăng ký: {before?.registered || 0}</small>
                            {!editable(s) && <small>Ca đã khóa chỉnh sửa</small>}
                          </div>
                        );
                      })}
                    </section>
                  ))}
                </div>
              </>
            ) : type === 'replace' ? (
              <div className="shift-fields">
                <div className="shift-message">
                  <strong>
                    Người hiện tại: {assignment.fullName} · {assignment.code}
                  </strong>
                  <p>Chọn người làm thay trong chính ca này. Không đổi ngày hoặc giờ ca.</p>
                </div>
                <label>
                  Nhân viên làm thay *
                  <select
                    required
                    value={employeeId}
                    onChange={(e) => {
                      setEmployeeId(e.target.value);
                      setErrors({});
                    }}
                  >
                    <option value="">Chọn lễ tân</option>
                    {candidates.map((e) => (
                      <option key={e.id} value={e.id}>
                        {e.code} · {e.fullName}
                      </option>
                    ))}
                  </select>
                </label>
                {!candidates.length && (
                  <p>Không có nhân viên khác đang hoạt động tại cơ sở để lựa chọn.</p>
                )}
                <label>
                  Lý do thay người *
                  <textarea
                    required
                    maxLength={1000}
                    value={reason}
                    placeholder="Ví dụ: A bận việc, B nhận làm thay ca."
                    onChange={(e) => {
                      setReason(e.target.value);
                      setErrors({});
                    }}
                  />
                </label>
              </div>
            ) : (
              <div className="shift-message">
                <h3>
                  Đăng ký ca {shiftLabel(template)} ngày {dateLabel(shift.date)}?
                </h3>
                <p>
                  Còn {Math.max(0, shift.capacity - shift.registered)} chỗ. Đăng ký hợp lệ được xác
                  nhận tự động, không cần quản lý duyệt.
                </p>
              </div>
            )}
          </fieldset>
        </div>
        <footer>
          <button type="button" disabled={busy} onClick={review ? () => setReview(false) : onClose}>
            {review ? 'Quay lại chỉnh sửa' : 'Đóng'}
          </button>
          {type !== 'detail' && (
            <button
              type="submit"
              className="shift-primary"
              disabled={busy || stale || data.readOnly}
            >
              {busy
                ? 'Đang lưu…'
                : type === 'register'
                  ? 'Xác nhận đăng ký'
                  : review
                    ? 'Xác nhận lưu'
                    : 'Kiểm tra thông tin'}
            </button>
          )}
        </footer>
      </form>
    </dialog>
  );
}
