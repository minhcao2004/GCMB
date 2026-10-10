import { useState } from 'react';

export default function AuthField({ name, label, type = 'text', error, hint, ...props }) {
  const [visible, setVisible] = useState(false);
  const password = type === 'password';
  return <div className="auth-field">
    <label htmlFor={`auth-${name}`}>{label}</label>
    <div className="auth-input-wrap">
      <input id={`auth-${name}`} name={name} type={password && visible ? 'text' : type} aria-invalid={Boolean(error)} aria-describedby={[error && `${name}-error`, hint && `${name}-hint`].filter(Boolean).join(' ') || undefined} {...props} />
      {password && <button type="button" className="auth-eye" aria-label={`${visible ? 'Ẩn' : 'Hiện'} ${label.toLowerCase()}`} aria-pressed={visible} onClick={() => setVisible(!visible)} disabled={props.disabled}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" />{!visible && <path d="m3 3 18 18" />}</svg>
      </button>}
    </div>
    {hint && <p className="auth-hint" id={`${name}-hint`}>{hint}</p>}
    {error && <p className="auth-error" id={`${name}-error`}>{error}</p>}
  </div>;
}
