/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-127/BR-125 đã thống nhất lại: lễ tân gửi yêu cầu, kế toán cập nhật tiến độ xử lý bên ngoài.
 */
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { authApi, apiErrors } from '../../auth/services/authApi';
import { AppShell } from './BookingManagementPage';
import {
  taxInvoiceApi,
  invoiceStatuses,
  accountantInvoiceStatuses,
  deliveryResults,
  deliveryStatus,
  deliveryLabel,
} from '../services/taxInvoiceApi';
import '../../workforce/styles/staff.css';
import '../styles/tax-invoice.css';
const errorText = (error) => Object.values(apiErrors(error)).join(' ');
const empty = {
  buyerName: '',
  taxCode: '',
  buyerAddress: '',
  contactEmail: '',
  note: '',
  deliveryResult: '',
  status: 'REQUESTED',
};
export default function TaxInvoicePage({ accounting = false }) {
  const navigate = useNavigate();
  const statuses = accounting ? accountantInvoiceStatuses : invoiceStatuses;
  const [params] = useSearchParams();
  const bookingId = params.get('booking');
  const [user, setUser] = useState(null);
  const [rows, setRows] = useState([]);
  const [detail, setDetail] = useState(null);
  const [form, setForm] = useState(empty);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [review, setReview] = useState(false);
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useState('');
  // Bản nháp chỉ giữ trong bộ nhớ của màn hình, theo từng booking; không lưu dữ liệu khách vào storage.
  const drafts = useRef(new Map());
  const [resume, setResume] = useState(null);
  useEffect(() => {
    let active = true;
    authApi
      .me()
      .then((identity) => {
        if (!active) return;
        if (identity.role !== (accounting ? 'ACCOUNTANT' : 'RECEPTIONIST'))
          navigate(identity.destination, { replace: true });
        else setUser(identity);
      })
      .catch((e) => {
        if (active) setError(errorText(e));
      });
    return () => {
      active = false;
    };
  }, [accounting, navigate]);
  useEffect(() => {
    if (!user) return;
    let active = true;
    setLoading(true);
    setError('');
    setDetail(null);
    setReview(false);
    (bookingId ? taxInvoiceApi.detail(bookingId, accounting) : taxInvoiceApi.list(accounting))
      .then((data) => {
        if (!active) return;
        if (bookingId) {
          const draft = drafts.current.get(bookingId);
          const version = data.request?.version ?? null;
          const canRestore = draft && draft.version === version;
          setDetail(data);
          setForm(
            canRestore
              ? draft.form
              : {
                  ...empty,
                  ...(data.request || {}),
                  ...(accounting && data.request?.version === 0 ? { note: '' } : {}),
                }
          );
          setResume({ id: bookingId, code: data.bookingCode });
          if (draft && !canRestore) {
            drafts.current.delete(bookingId);
            setNotice(
              'Yêu cầu đã được cập nhật. Đã tải thông tin mới nhất; vui lòng kiểm tra trước khi nhập lại.'
            );
          }
        } else setRows(data);
      })
      .catch((e) => {
        if (active) setError(errorText(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [user, bookingId, accounting, revision]);
  const open = (id) => {
    setNotice('');
    navigate(`${accounting ? '/ke-toan' : '/le-tan'}/hoa-don-thue${id ? `?booking=${id}` : ''}`);
  };
  const change = (key, value) => {
    setReview(false);
    const next = { ...form, [key]: value };
    if (key === 'handling') {
      const result = value.startsWith('RESULT:') ? value.slice(7) : '';
      next.status = deliveryStatus(result) || form.status;
      next.deliveryResult = result;
      delete next.handling;
    }
    drafts.current.set(bookingId, { form: next, version: detail?.request?.version ?? null });
    setForm(next);
  };
  async function submit(e) {
    e.preventDefault();
    if (busy || user.readOnly) return;
    if (accounting ? !detail?.request : !!detail?.request) return;
    if (!review) {
      setReview(true);
      return;
    } // MSG14: xem tiến độ/kết quả trước khi xác nhận.
    setBusy(true);
    setError('');
    try {
      const result = detail.request
        ? await taxInvoiceApi.update(bookingId, {
            status: form.status,
            deliveryResult: form.deliveryResult,
            version: detail.request.version,
          })
        : await taxInvoiceApi.create(bookingId, form);
      drafts.current.delete(bookingId);
      setDetail(result);
      setForm({ ...empty, ...result.request });
      setReview(false);
      setNotice(
        detail.request
          ? 'Đã cập nhật yêu cầu hóa đơn thuế.'
          : 'Đã tạo yêu cầu hóa đơn thuế.'
      ); // MSG10/11
    } catch (e) {
      setError(errorText(e));
      setReview(false);
    } finally {
      setBusy(false);
    }
  }
  async function markRead() {
    setBusy(true);
    setError('');
    try {
      await taxInvoiceApi.read(bookingId);
      setNotice('Đã đánh dấu thông báo là đã đọc.');
      window.dispatchEvent(new Event('tax-invoice-read'));
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }
  if (!user) return <p role={error ? 'alert' : 'status'}>{error || 'Đang kiểm tra tài khoản…'}</p>;
  const handlingLabel = (request) =>
    request.deliveryResult ? deliveryLabel(request.deliveryResult) : 'Chưa giao thành công';
  const handlingValue = form.deliveryResult ? `RESULT:${form.deliveryResult}` : '';
  const visibleRows = rows.filter((r) =>
    `${r.bookingCode} ${r.customer} ${r.branchName}`.toLowerCase().includes(search.toLowerCase())
  );
  const originalRequest = detail?.history?.find((h) => h.action === 'CREATE_TAX_INVOICE_REQUEST');
  const customerNote = originalRequest
    ? JSON.parse(originalRequest.data).note
    : detail?.request?.version === 0
      ? detail.request.note
      : '';
  const field = (key, label, max, required = false, multiline = false, placeholder = '') => (
    <label className="staff-field full">
      <span>
        {label}
        {required ? ' *' : ''}
      </span>
      {multiline ? (
        <textarea
          value={form[key]}
          maxLength={max}
          required={required}
          placeholder={placeholder}
          onChange={(e) => change(key, e.target.value)}
        />
      ) : (
        <input
          value={form[key]}
          type={key === 'contactEmail' ? 'email' : 'text'}
          maxLength={max}
          required={required}
          onChange={(e) => change(key, e.target.value)}
        />
      )}
    </label>
  );
  return (
    <AppShell
      user={user}
      branchName={user.branchName}
      audience={accounting ? 'accountant' : 'receptionist'}
      activeTab="tax"
      breadcrumb="Yêu cầu hóa đơn thuế"
      hideSearch
      busy={busy}
      onLogout={async () => {
        await authApi.logout();
        navigate('/dang-nhap');
      }}
    >
      <section className="staff-page tax-invoice-page">
        <header className="staff-heading">
          <div>
            <h1>Yêu cầu hóa đơn thuế</h1>
            <p>
              {accounting
                ? 'Tiếp nhận yêu cầu từ lễ tân và cập nhật tiến độ, kết quả giao hóa đơn thực tế.'
                : 'Gửi yêu cầu cho kế toán và xem tiến độ xử lý.'}{' '}
              Phát hành và giao hóa đơn thực hiện bên ngoài GCMB.
            </p>
          </div>
          <button
            onClick={() => (bookingId ? open(null) : setRevision((v) => v + 1))}
            disabled={busy}
          >
            {bookingId ? 'Danh sách yêu cầu' : 'Tải lại'}
          </button>
        </header>
        {!bookingId && resume && (
          <div className="tax-invoice-resume">
            <button type="button" onClick={() => open(resume.id)}>
              ← Quay lại {resume.code}
            </button>
            <span>
              {drafts.current.has(resume.id)
                ? 'Nội dung đang nhập được giữ khi chuyển sang danh sách.'
                : 'Mở lại yêu cầu vừa xem.'}
            </span>
          </div>
        )}
        {error && (
          <div role="alert" className="staff-banner error">
            {error}
            <button onClick={() => setRevision((v) => v + 1)} disabled={busy}>
              Tải lại dữ liệu
            </button>
          </div>
        )}
        {notice && (
          <p role="status" className="staff-banner success">
            {notice}
          </p>
        )}
        {loading ? (
          <p role="status">Đang tải…</p>
        ) : !bookingId ? (
          <section className="staff-card">
            <input
              aria-label="Tìm yêu cầu"
              placeholder="Mã booking / khách / cơ sở"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <div className="staff-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Booking / khách</th>
                    <th>Cơ sở</th>
                    <th>Tiến độ</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {visibleRows.map((r) => (
                    <tr key={r.bookingId}>
                      <td>
                        {r.bookingCode}
                        <small>{r.customer}</small>
                      </td>
                      <td>{r.branchName}</td>
                      <td>{handlingLabel(r)}</td>
                      <td>
                        <button onClick={() => open(r.bookingId)}>Chi tiết</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!visibleRows.length && (
              <p>Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.</p>
            )}
          </section>
        ) : (
          detail && (
            <section className="staff-card">
              <h2>{detail.bookingCode}</h2>
              {detail.request && (
                <>
                  <h3>Thông tin đã lưu</h3>
                  <dl className="staff-summary">
                    <dt>Người mua / đơn vị</dt>
                    <dd>{detail.request.buyerName}</dd>
                    <dt>Mã số thuế</dt>
                    <dd>{detail.request.taxCode || '—'}</dd>
                    <dt>Địa chỉ</dt>
                    <dd>{detail.request.buyerAddress || '—'}</dd>
                    <dt>Email liên hệ</dt>
                    <dd>{detail.request.contactEmail || '—'}</dd>
                    <dt>Trạng thái xử lý</dt>
                    <dd>{handlingLabel(detail.request)}</dd>
                    <dt>Yêu cầu bổ sung của khách</dt>
                    <dd>{customerNote || 'Không có'}</dd>
                  </dl>
                </>
              )}
              {!accounting && detail.request && (
                <div className="staff-banner" role="status">
                  Yêu cầu đã gửi kế toán. Kế toán cập nhật tiến độ và kết quả giao hóa đơn; lễ tân
                  xem thông tin tại đây để trả lời khách.{' '}
                  <button
                    type="button"
                    className="staff-link"
                    onClick={() => navigate(`/le-tan/bookings/${bookingId}/checkout`)}
                  >
                    Về checkout
                  </button>
                </div>
              )}
              {accounting && !detail.request && <p>Booking chưa có yêu cầu hóa đơn từ lễ tân.</p>}
              {((accounting && detail.request) || (!accounting && !detail.request)) && (
                <form onSubmit={submit}>
                  <fieldset
                    disabled={busy || user.readOnly}
                    className="staff-form-grid"
                    style={{ border: 0, padding: 0 }}
                  >
                    {!detail.request ? (
                      <>
                        {field('buyerName', 'Người mua / đơn vị', 200, true)}
                        {field('taxCode', 'Mã số thuế', 30)}
                        {field('buyerAddress', 'Địa chỉ', 2000)}
                        {field('contactEmail', 'Email liên hệ', 254)}
                      </>
                    ) : (
                      <label className="staff-field full">
                        <span>Trạng thái xử lý</span>
                        <select
                          required
                          value={handlingValue}
                          onChange={(e) => change('handling', e.target.value)}
                        >
                          <option value="" disabled>
                            Chọn trạng thái xử lý
                          </option>
                          <optgroup label="Đã giao thành công">
                            {['EMAIL', 'DIRECT', 'MESSAGE'].map((value) => (
                              <option key={value} value={`RESULT:${value}`}>
                                {deliveryResults[value]}
                              </option>
                            ))}
                          </optgroup>
                          <optgroup label="Chưa giao thành công">
                            {['INVALID_CONTACT', 'UNREACHABLE', 'SEND_FAILED'].map((value) => (
                              <option key={value} value={`RESULT:${value}`}>
                                {deliveryResults[value]}
                              </option>
                            ))}
                          </optgroup>
                          {form.deliveryResult &&
                            ![
                              'EMAIL',
                              'DIRECT',
                              'MESSAGE',
                              'INVALID_CONTACT',
                              'UNREACHABLE',
                              'SEND_FAILED',
                            ].includes(form.deliveryResult) && (
                              <option value={handlingValue}>
                                Đã lưu trước đây: {deliveryLabel(form.deliveryResult)}
                              </option>
                            )}
                        </select>
                      </label>
                    )}
                    {!detail.request &&
                      field(
                        'note',
                        'Yêu cầu bổ sung của khách (không bắt buộc)',
                        2000,
                        false,
                        true,
                        'Ví dụ: Khách muốn nhận hóa đơn qua email vào ngày mai.'
                      )}
                    {review && (
                      <div className="staff-banner full" role="status">
                        <strong>
                          Xác nhận {detail.request ? 'cập nhật' : 'tạo'} yêu cầu hóa đơn cho{' '}
                          {detail.bookingCode}?
                        </strong>
                        <p>
                          {detail.request
                            ? `${handlingLabel(form)}`
                            : `${form.buyerName} · ${form.taxCode || 'Không có mã số thuế'} · ${form.contactEmail || 'Không có email'}`}
                        </p>
                        <p>
                          {detail.request
                            ? 'Lưu tiến độ và kết quả xử lý bên ngoài. Tiền booking và thanh toán giữ nguyên.'
                            : 'Ghi nhận yêu cầu và gửi thông báo trong hệ thống cho kế toán.'}
                        </p>
                      </div>
                    )}
                    <div className="staff-actions full">
                      {review && (
                        <button type="button" onClick={() => setReview(false)}>
                          Quay lại
                        </button>
                      )}
                      <button className="staff-primary" type="submit">
                        {busy
                          ? 'Đang lưu…'
                          : review
                            ? accounting
                              ? 'Xác nhận cập nhật'
                              : 'Xác nhận gửi kế toán'
                            : 'Kiểm tra thông tin'}
                      </button>
                      {!accounting && (
                        <button
                          type="button"
                          onClick={() => navigate(`/le-tan/bookings/${bookingId}/checkout`)}
                        >
                          Về checkout
                        </button>
                      )}
                    </div>
                  </fieldset>
                </form>
              )}
              {!!detail.history?.length && (
                <div>
                  <h3>Lịch sử xử lý</h3>
                  {detail.history.map((h, i) => (
                    <p key={i}>
                      {new Date(h.createdAt).toLocaleString('vi-VN')} ·{' '}
                      {h.action === 'CREATE_TAX_INVOICE_REQUEST'
                        ? 'Ghi nhận yêu cầu'
                        : 'Cập nhật tiến độ'}{' '}
                      · {statuses[JSON.parse(h.data).status]} ·{' '}
                      {JSON.parse(h.data).deliveryResult
                        ? deliveryLabel(JSON.parse(h.data).deliveryResult)
                        : h.action === 'CREATE_TAX_INVOICE_REQUEST'
                          ? JSON.parse(h.data).note
                          : ''}
                    </p>
                  ))}
                </div>
              )}
              {accounting && detail.request && (
                <div className="staff-actions tax-invoice-read-actions">
                  <button type="button" disabled={busy} onClick={markRead}>
                    Đánh dấu đã đọc
                  </button>
                </div>
              )}
            </section>
          )
        )}
      </section>
    </AppShell>
  );
}
