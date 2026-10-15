import { useState } from 'react';
import { authApi, apiErrors } from '../../auth/services/authApi';

export default function ChangePasswordForm({ busy, onBusyChange, onCancel }) {
  const [form, setForm] = useState({ currentPassword: '', password: '', confirmation: '' });
  const [visible, setVisible] = useState({});
  const [errors, setErrors] = useState({});
  const [notice, setNotice] = useState('');

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setNotice('');
    if (form.password !== form.confirmation) {
      setErrors({ confirmation: 'Mật khẩu xác nhận không khớp' });
      return;
    }
    if (form.password === form.currentPassword) {
      setErrors({ password: 'Mật khẩu mới phải khác mật khẩu hiện tại' });
      return;
    }
    onBusyChange(true);
    setErrors({});
    try {
      await authApi.changePassword(form);
      setNotice('Đổi mật khẩu thành công.');
    } catch (error) {
      setErrors(apiErrors(error));
    } finally {
      setForm({ currentPassword: '', password: '', confirmation: '' });
      setVisible({});
      onBusyChange(false);
    }
  }

  return <form className="account-create-form" onSubmit={submit}>
    <div className="account-form-body">
      <h3 className="profile-password-title">Đổi mật khẩu</h3>
      {notice && <p className="account-success-message" role="status">{notice}</p>}
      <p className="profile-password-intro" id="password-guidance">Nhập mật khẩu mới khác mật khẩu hiện tại và xác nhận lại.</p>
      {errors.form && <p className="account-error" role="alert">{errors.form}</p>}
      <fieldset className="profile-fields" disabled={busy}>
        {[
          ['currentPassword', 'Mật khẩu hiện tại', 'current-password'],
          ['password', 'Mật khẩu mới', 'new-password'],
          ['confirmation', 'Xác nhận mật khẩu mới', 'new-password'],
        ].map(([name, label, autoComplete]) => <div className="account-field" key={name}>
          <label htmlFor={`change-${name}`}>{label}</label>
          <div className="profile-password-input">
            <input id={`change-${name}`} name={name} type={visible[name] ? 'text' : 'password'}
              autoComplete={autoComplete} required value={form[name]}
              aria-invalid={Boolean(errors[name])} aria-describedby={errors[name] ? `error-${name}` : 'password-guidance'}
              onChange={event => setForm({ ...form, [name]: event.target.value })} />
            <button type="button" aria-label={`${visible[name] ? 'Ẩn' : 'Hiện'} ${label.toLowerCase()}`}
              aria-pressed={Boolean(visible[name])} onClick={() => setVisible({ ...visible, [name]: !visible[name] })}>{visible[name] ? 'Ẩn' : 'Hiện'}</button>
          </div>
          {errors[name] && <p id={`error-${name}`} className="account-error" role="alert">{errors[name]}</p>}
        </div>)}
      </fieldset>
    </div>
    <div className="account-drawer-footer">
      <button type="button" disabled={busy} onClick={onCancel}>Hủy</button>
      <button type="submit" className="account-primary" disabled={busy}>{busy ? 'Đang cập nhật…' : 'Đổi mật khẩu'}</button>
    </div>
  </form>;
}
