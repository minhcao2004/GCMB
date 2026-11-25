import { PaginatedList } from '../../../shared/components/Pagination';
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
  const template = shift && data.templates.find((templateItem) => templateItem.id === shift.templateId);
  const original = new Map(data.shifts.map((shiftItem) => [slotKey(shiftItem.date, shiftItem.templateId), shiftItem]));
  const [slots, setSlots] = useState(() =>
    Array.from({ length: 7 }, (_, day) =>
      data.templates.map((templateItem) => {
        const date = addDays(data.week, day),
          old = original.get(slotKey(date, templateItem.id));
        return {
          date,
          templateId: templateItem.id,
          capacity: String(old?.capacity || 3),
          open:
            (dialog.focusSlot === slotKey(date, templateItem.id) &&
              data.editableSlots?.includes(slotKey(date, templateItem.id))) ||
            old?.status === 'OPEN',
          version: old?.version ?? null,
        };
      })
    ).flat()
  );
  const [employeeId, setEmployeeId] = useState(''),
    [reason, setReason] = useState('');
  const [defaultCapacity, setDefaultCapacity] = useState('3');
  const [busy, setBusy] = useState(false),
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
    setErrors({});
    setSlots((old) => old.map((item, i) => (i === index ? { ...item, [key]: value } : item)));
  };
  const editable = (slot) => !!data.editableSlots?.includes(slotKey(slot.date, slot.templateId));
  const participants = shift ? data.assignments.filter((assignmentItem) => assignmentItem.shiftId === shift.id) : [];
  const candidates = data.employees.filter(
    (e) => e.id !== assignment?.employeeId && !participants.some((participant) => participant.employeeId === e.id)
      && !(data.employeeClosings || []).some((closing) => closing.employeeId === e.id && closing.month.slice(0, 7) === shift?.date.slice(0, 7))
  );
  const canReplace =
    manager && !data.readOnly && shift && new Date(shift.endsAt).getTime() > Date.now();
  const changes = slots.filter((slot) => {
    const before = original.get(slotKey(slot.date, slot.templateId));
    return before
      ? before.capacity !== Number(slot.capacity) || (before.status === 'OPEN') !== slot.open
      : slot.open;
  });
  async function submit(event) {
    event.preventDefault();
    if (busy || sending.current || stale) return;
    const found = {};
    if (type === 'publish') {
      if (!changes.length) found.form = 'Chưa có thay đổi để công bố.';
      for (const slot of changes) {
        const count = original.get(slotKey(slot.date, slot.templateId))?.registered || 0;
        if (
          !Number.isInteger(Number(slot.capacity)) ||
          Number(slot.capacity) < 1 ||
          Number(slot.capacity) > 2147483647
        )
          found.form = 'Số chỗ phải là số nguyên lớn hơn 0.'; // MSG44
        else if (Number(slot.capacity) < count)
          found.form = `Ca này đã có ${count} người đăng ký. Số chỗ không được nhỏ hơn ${count}.`; // MSG85
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
    if (type === 'detail' && (!canReplace || participants.length >= shift.capacity || !employeeId)) return;
    sending.current = true;
    setBusy(true);
    setErrors({});
    try {
      const result =
        type === 'publish'
          ? await shiftApi.publish(
              changes.map((change) => ({ ...change, capacity: Number(change.capacity) })),
              scope
            )
          : type === 'replace'
            ? await shiftApi.replace(
                assignment.id,
                { employeeId, reason, version: shift.version },
                scope
              )
            : type === 'detail'
              ? await shiftApi.assign(shift.id, employeeId, scope)
              : await shiftApi.register(shift.id);
      setEmployeeId('');
      onSaved(
        result,
        type === 'register'
          ? 'Đã xác nhận đăng ký ca.'
          : type === 'replace'
            ? 'Đã cập nhật nhân viên làm thay ca.'
            : type === 'detail'
              ? 'Đã phân công nhân viên vào ca.'
              : 'Đã cập nhật lịch mở ca trong tuần.'
      ); // MSG11/12
    } catch (e) {
      setErrors(apiErrors(e));
      setStale(
        e.response?.status === 409 &&
          Object.values(apiErrors(e)).some((value) => value.includes('đã thay đổi'))
      );
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
                {participants.length ? <PaginatedList as="ul" pageSize={10} className="shift-people">
                  {participants.map((participant) => <li key={participant.id}>
                    <div><strong>{participant.fullName}</strong><small>{participant.code} · {participant.source === 'MANAGER' ? 'Quản lý phân công' : 'Tự đăng ký'}{participant.employeeStatus !== 'ACTIVE' ? ' · Hồ sơ đã ngừng' : ''}</small></div>
                    {canReplace && <button type="button" onClick={() => dialog.onReplace(participant)}>Làm thay</button>}
                  </li>)}
                </PaginatedList> : <p>Chưa có nhân viên đăng ký ca này.</p>}
                {canReplace && shift.status !== 'CANCELLED' && participants.length < Math.min(3, shift.capacity) && (
                  <div className="shift-empty-assignment">
                    <label>Phân công thêm nhân viên (còn {Math.min(3, shift.capacity) - participants.length} chỗ)
                      <select value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
                        <option value="">Chọn nhân viên</option>
                        {candidates.map((e) => <option key={e.id} value={e.id}>{e.fullName} · {e.code}</option>)}
                      </select>
                    </label>
                    <button type="submit" disabled={!employeeId || busy}>Phân công</button>
                  </div>
                )}
                {data.history.some((historyItem) => historyItem.shiftId === shift.id) && (
                  <>
                    <h3>Lịch sử thay người</h3>
                    <PaginatedList as="ul" pageSize={5}>
                      {data.history
                        .filter((historyItem) => historyItem.shiftId === shift.id)
                        .map((historyItem) => (
                          <li key={historyItem.id}>
                            {historyItem.fullName} · {historyItem.reason} ·{' '}
                            {new Date(historyItem.cancelledAt).toLocaleString('vi-VN')}
                          </li>
                        ))}
                    </PaginatedList>
                  </>
                )}
              </>
            ) : type === 'publish' ? (
              <>
                <div className="shift-bulk">
                  <label>
                    Số người mặc định
                    <input
                      type="number"
                      min="3"
                      step="1"
                      readOnly
                      value={defaultCapacity}
                      onChange={(e) => setDefaultCapacity(e.target.value)}
                    />
                  </label>
                  <button
                    type="button"
                    onClick={() =>
                      setSlots((old) =>
                        old.map((item) =>
                          editable(item) && item.open ? { ...item, capacity: defaultCapacity } : item
                        )
                      )
                    }
                  >
                    Áp dụng cho ca đã chọn
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      setSlots((old) => old.map((item) => (editable(item) ? { ...item, open: true } : item)))
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
                      {data.templates.map((templateItem, i) => {
                        const index = day * data.templates.length + i,
                          slot = slots[index],
                          before = original.get(slotKey(slot.date, templateItem.id));
                        return (
                          <div key={templateItem.id} className={slot.open ? 'selected' : ''}>
                            <label>
                              <input
                                type="checkbox"
                                checked={slot.open}
                                disabled={!editable(slot)}
                                onChange={(e) => updateSlot(index, 'open', e.target.checked)}
                              />
                              Ca {i + 1}
                            </label>
                            <small>{shiftLabel(templateItem)}</small>
                            <label>
                              Số chỗ
                              <input
                                aria-label={`Số chỗ ${slot.date} ca ${i + 1}`}
                                type="number"
                                min="1"
                                max="3"
                                step="1"
                                readOnly
                                value={slot.capacity}
                                disabled={!editable(slot) || (!slot.open && !before)}
                                onChange={(e) => updateSlot(index, 'capacity', e.target.value)}
                              />
                            </label>
                            <small>Đã đăng ký: {before?.registered || 0}</small>
                            {!editable(slot) && <small>Ca đã khóa chỉnh sửa</small>}
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
          <button type="button" disabled={busy} onClick={onClose}>
            Đóng
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
                  : 'Lưu'}
            </button>
          )}
        </footer>
      </form>
    </dialog>
  );
}
