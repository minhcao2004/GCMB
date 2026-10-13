/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Xem, sửa và hủy chỉnh sửa thông tin của chính người đang đăng nhập.
 */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../auth/services/authApi';
import { accountProfileApi } from './accountProfileApi';
import { roleNames } from './accountAccessApi';
import './accounts.css';
import './accountAccess.css';
import './accountProfile.css';

const statuses = { ACTIVE: 'Hoạt động', LOCKED: 'Đã khóa', REVOKED: 'Đã thu hồi' };

export default function AccountProfileDialog({ onClose }) {
  const dialog = useRef(null);
  const [profile, setProfile] = useState(null);
  const [form, setForm] = useState({ fullName: '', phone: '', email: '', currentPassword: '' });
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState({});
  const [notice, setNotice] = useState('');
  const [retry, setRetry] = useState(0);
  const emailChanged = profile && form.email.trim().toLowerCase() !== profile.account.email;

  useEffect(() => {
    const element = dialog.current;
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    element.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      previousFocus?.focus();
    };
  }, []);

  useEffect(() => {
    let active = true;
    setErrors({});
    accountProfileApi.view()
      .then((data) => {
        if (active) setProfile(data);
      })
      .catch((error) => {
        if (active) setErrors(apiErrors(error));
      });
    return () => { active = false; };
  }, [retry]);

  function beginEdit(event) {
    // Chặn hành vi mặc định của lần bấm mở form khi nút Lưu được hiển thị.
    event.preventDefault();
    setForm({
      fullName: profile.fullName || '',
      phone: profile.phone || '',
      email: profile.account.email,
      currentPassword: '',
    });
    setNotice('');
    setErrors({});
    setEditing(true);
  }

  function cancelEdit() {
    setEditing(false);
    setErrors({});
    setForm((old) => ({ ...old, currentPassword: '' }));
  }

  async function save(event) {
    event.preventDefault();
    if (!editing || busy) return;
    setBusy(true);
    setErrors({});
    try {
      const data = await accountProfileApi.update({
        fullName: profile.linkedEmployee ? form.fullName : null,
        phone: profile.linkedEmployee ? form.phone : null,
        email: form.email,
        currentPassword: emailChanged ? form.currentPassword : null,
      });
      setProfile(data);
      setEditing(false);
      setNotice('Đã cập nhật thông tin tài khoản.');
    } catch (error) {
      setErrors(apiErrors(error));
    } finally {
      // Không giữ mật khẩu xác nhận sau khi gửi yêu cầu.
      setForm((old) => ({ ...old, currentPassword: '' }));
      setBusy(false);
    }
  }

  function change(event) {
    const { name, value } = event.target;
    setForm((old) => ({ ...old, [name]: value }));
  }

  const information = profile ? [
    ['Tên đăng nhập', profile.account.username],
    ['Mã nhân viên', profile.account.employeeCode || 'Chưa liên kết'],
    ['Họ và tên', profile.fullName || 'Chưa có hồ sơ nhân viên'],
    ['Email', profile.account.email],
    ['Số điện thoại', profile.phone || 'Chưa cập nhật'],
    ['Vai trò', roleNames[profile.account.role] || profile.account.role],
    ['Phạm vi cơ sở', profile.account.branchName],
    ['Trạng thái', statuses[profile.account.status] || profile.account.status],
  ] : [];

  return (
    <dialog
      ref={dialog}
      className="account-drawer profile-dialog"
      aria-labelledby="profile-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className="account-drawer-heading">
        <div>
          <h2 id="profile-title">Thông tin tài khoản</h2>
          <p>{editing ? 'Cập nhật thông tin cá nhân của bạn' : 'Thông tin cá nhân và phạm vi truy cập'}</p>
        </div>
        <button onClick={onClose} disabled={busy} aria-label="Đóng">×</button>
      </div>
      <form className="account-create-form" onSubmit={save}>
        <div className="account-form-body">
          {Object.entries(errors).map(([field, message]) => (
            <p key={field} className="account-error" role="alert">{message}</p>
          ))}
          {notice && <p className="account-success-message" role="status">{notice}</p>}
          {!profile ? (
            Object.keys(errors).length ? (
              <button type="button" onClick={() => setRetry((value) => value + 1)}>Thử lại</button>
            ) : <p role="status">Đang tải thông tin…</p>
          ) : !editing ? (
            <dl className="profile-information">
              {information.map(([label, value]) => (
                <div key={label}><dt>{label}</dt><dd>{value}</dd></div>
              ))}
            </dl>
          ) : (
            <fieldset className="profile-fields" disabled={busy}>
              <div className="account-field">
                <label htmlFor="profile-name">Họ và tên</label>
                <input id="profile-name" name="fullName" value={form.fullName} onChange={change}
                  disabled={!profile.linkedEmployee} required={profile.linkedEmployee} maxLength={200} autoComplete="name" />
              </div>
              <div className="account-field">
                <label htmlFor="profile-phone">Số điện thoại</label>
                <input id="profile-phone" name="phone" type="tel" value={form.phone} onChange={change}
                  disabled={!profile.linkedEmployee} maxLength={30} autoComplete="tel" />
              </div>
              {!profile.linkedEmployee && (
                <p className="account-access-note">Tài khoản chưa liên kết hồ sơ nhân viên. Liên hệ quản lý để cập nhật họ tên và số điện thoại.</p>
              )}
              <div className="account-field">
                <label htmlFor="profile-email">Email</label>
                <input id="profile-email" name="email" type="email" value={form.email} onChange={change}
                  required maxLength={254} autoComplete="email" />
              </div>
              {emailChanged && (
                <div className="account-field">
                  <label htmlFor="profile-password">Mật khẩu hiện tại</label>
                  <input id="profile-password" name="currentPassword" type="password" value={form.currentPassword}
                    onChange={change} required maxLength={128} autoComplete="current-password" aria-describedby="profile-email-note" />
                  <p id="profile-email-note">Xác nhận mật khẩu để đổi email dùng khôi phục tài khoản.</p>
                </div>
              )}
              <p className="account-access-note">Tên đăng nhập, vai trò, cơ sở và trạng thái do hệ thống quản lý.</p>
            </fieldset>
          )}
        </div>
        <div className="account-drawer-footer">
          <button type="button" disabled={busy} onClick={editing ? cancelEdit : onClose}>
            {editing ? 'Hủy chỉnh sửa' : 'Đóng'}
          </button>
          {profile && (editing ? (
            <button key="save-profile" type="submit" className="account-primary" disabled={busy}>
              {busy ? 'Đang lưu…' : 'Lưu thay đổi'}
            </button>
          ) : (
            <button key="edit-profile" type="button" className="account-primary" onClick={beginEdit}>
              Chỉnh sửa thông tin
            </button>
          ))}
        </div>
      </form>
    </dialog>
  );
}
