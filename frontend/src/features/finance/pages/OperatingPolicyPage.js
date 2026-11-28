import { useEffect, useRef, useState } from 'react';
import apiClient from '../../../api/client';
import MoneyInput from '../../../shared/components/MoneyInput';
import { apiErrors } from '../../auth/services/authApi';
import '../styles/operating-policy.css';

const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date());
const tomorrow = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date(Date.now() + 86400000));
const money = (amount) => `${Number(amount).toLocaleString('vi-VN')} ₫`;
const dateLabel = (value) => value ? value.split('-').reverse().join('/') : '—';
const policies = [
  { key: 'NIGHT_SHIFT_PREMIUM', title: 'Phụ cấp ca đêm', unit: '₫/giờ', minDate: tomorrow },
  { key: 'PAYMENT_APPROVAL_LIMIT', title: 'Ngưỡng duyệt đề nghị chi', unit: '₫', minDate: today },
];

function PolicyCard({ policy, rows, onSaved }) {
  const minDate = policy.minDate();
  const current = [...rows].filter((row) => row.effectiveFrom <= today())
    .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom) || b.changedAt.localeCompare(a.changedAt))[0];
  const latest = rows[0];
  const [amount, setAmount] = useState(String(current?.amount ?? ''));
  const [effectiveFrom, setEffectiveFrom] = useState(minDate);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const errorRef = useRef(null);
  useEffect(() => { setAmount(String(current?.amount ?? '')); setEffectiveFrom(minDate); }, [latest.id, current?.amount, minDate]);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);

  async function save(event) {
    event.preventDefault();
    setError(''); setNotice(''); setSaving(true);
    try {
      const { data: csrf } = await apiClient.get('/auth/csrf');
      const { data } = await apiClient.put(`/head-office/operating-policies/${policy.key}`, {
        amount, effectiveFrom, reason: reason.trim(), expectedLatestId: latest.id,
      }, { headers: { [csrf.headerName]: csrf.token } });
      onSaved(data);
      setNotice(`Đã lưu mức mới, áp dụng từ ${dateLabel(effectiveFrom)}.`);
      setReason('');
    } catch (e) {
      setError(Object.values(apiErrors(e)).join(' '));
    } finally { setSaving(false); }
  }

  return <section className="operating-policy-card" aria-labelledby={`policy-${policy.key}`}>
    <div className="operating-policy-card__heading">
      <h2 id={`policy-${policy.key}`}>{policy.title}</h2>
      <div className="operating-policy-current"><span>Đang áp dụng</span><strong>{money(current?.amount ?? 0)}{policy.unit === '₫/giờ' ? '/giờ' : ''}</strong></div>
    </div>
    <form onSubmit={save} className="operating-policy-form">
      <div className="operating-policy-field"><label htmlFor={`${policy.key}-amount`}>Mức mới ({policy.unit})</label>
        <MoneyInput id={`${policy.key}-amount`} value={amount} onValueChange={setAmount} min={0} maxDigits={12} required /></div>
      <div className="operating-policy-field"><label htmlFor={`${policy.key}-date`}>Có hiệu lực từ ngày</label>
        <input id={`${policy.key}-date`} type="date" min={minDate} value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} required /></div>
      <div className="operating-policy-field operating-policy-field--reason"><label htmlFor={`${policy.key}-reason`}>Lý do thay đổi</label>
        <input id={`${policy.key}-reason`} value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} required /></div>
      <button type="submit" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu mức mới'}</button>
    </form>
    {error && <p className="operating-policy-error" role="alert" tabIndex={-1} ref={errorRef}>{error}</p>}
    {notice && <p className="operating-policy-notice" role="status">{notice}</p>}
    <details className="operating-policy-history"><summary>Lịch sử thay đổi ({rows.length})</summary>
      <ul>{rows.map((row) => <li key={row.id}><strong>{money(row.amount)}</strong><span>Hiệu lực {dateLabel(row.effectiveFrom)}</span><span>{row.changedBy || 'Hệ thống'} · {new Date(row.changedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })} · {row.reason}</span></li>)}</ul>
    </details>
  </section>;
}

export default function OperatingPolicyPage() {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    apiClient.get('/head-office/operating-policies')
      .then(({ data }) => { if (active) setRows(data); })
      .catch((e) => { if (active) setError(Object.values(apiErrors(e)).join(' ')); });
    return () => { active = false; };
  }, []);
  return <main className="operating-policy-page">
    <header><span>QUẢN TRỊ HỆ THỐNG</span><h1>Chính sách vận hành</h1></header>
    {error && <p className="operating-policy-error" role="alert">{error}</p>}
    {!rows && !error && <p>Đang tải chính sách…</p>}
    {rows && <div className="operating-policy-grid">{policies.map((policy) => <PolicyCard key={policy.key} policy={policy} rows={rows.filter((row) => row.policyKey === policy.key)} onSaved={setRows} />)}</div>}
  </main>;
}
