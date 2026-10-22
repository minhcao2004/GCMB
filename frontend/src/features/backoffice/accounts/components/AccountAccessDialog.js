/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Bảng đổi vai trò và hộp xác nhận thu hồi truy cập có lý do.
 */
import { useEffect, useRef, useState } from 'react';
import { apiErrors } from '../../../auth/services/authApi';
import { accountAccessApi, roleNames } from '../services/accountAccessApi';
import '../styles/accountAccess.css';

export default function AccountAccessDialog({ account, options, actorId, onClose, onSaved }) {
  const dialog = useRef(null);
  const [role, setRole] = useState(account.role);
  const [branchId, setBranchId] = useState(account.branchId || '');
  const [mode, setMode] = useState('role');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState({});
  const chain = ['HEAD_OFFICE_MANAGER', 'ACCOUNTANT'].includes(role);
  const immutable = account.id === actorId || account.status === 'REVOKED';
  const branchName = chain
    ? 'Toàn chuỗi'
    : options.branches.find((branch) => branch.id === branchId)?.name || 'Chưa chọn cơ sở';

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

  async function submit(event) {
    event.preventDefault();
    if (busy || immutable) return;
    setBusy(true);
    setErrors({});
    try {
      if (mode === 'deactivate') {
        await accountAccessApi.deactivate(account.id, reason);
      } else {
        await accountAccessApi.setRole(account.id, {
          role,
          branchId: chain ? null : branchId,
        });
      }
      onSaved(mode === 'deactivate' ? 'Đã thu hồi quyền truy cập.' : 'Đã lưu vai trò và phạm vi truy cập.');
    } catch (error) {
      setErrors(apiErrors(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <dialog
      ref={dialog}
      className={mode === 'deactivate' ? 'account-drawer account-revoke-dialog' : 'account-drawer'}
      aria-labelledby="account-access-title"
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
    >
      <div className="account-drawer-heading">
        <div>
          <h2 id="account-access-title">
            {mode === 'deactivate' ? 'Thu hồi quyền truy cập' : 'Vai trò & phạm vi truy cập'}
          </h2>
          <p>{account.employeeName} · {account.username}</p>
        </div>
        <button aria-label="Đóng" disabled={busy} onClick={onClose}>×</button>
      </div>
      <form className="account-create-form" onSubmit={submit}>
        <fieldset className="account-form-body" disabled={busy || immutable}>
          {Object.entries(errors).map(([field, message]) => (
            <p className="account-error" role="alert" key={field}>{message}</p>
          ))}
          {immutable && (
            <p className="account-access-note">
              {account.id === actorId
                ? 'Không thể thay đổi quyền hoặc thu hồi chính tài khoản đang đăng nhập.'
                : 'Tài khoản đã bị thu hồi truy cập.'}
            </p>
          )}
          {mode === 'role' ? (
            <>
              <div className="account-field">
                <label htmlFor="access-role">Vai trò</label>
                <select id="access-role" value={role} onChange={(event) => setRole(event.target.value)}>
                  {options.roles.map((item) => (
                    <option key={item.id} value={item.id}>{roleNames[item.id] || item.name}</option>
                  ))}
                </select>
              </div>
              <div className="account-field">
                <label htmlFor="access-branch">Phạm vi cơ sở</label>
                {chain ? <input id="access-branch" readOnly value="Toàn chuỗi" /> : (
                  <select id="access-branch" required value={branchId} onChange={(event) => setBranchId(event.target.value)}>
                    <option value="">Chọn cơ sở</option>
                    {options.branches.map((branch) => (
                      <option key={branch.id} value={branch.id}>{branch.name}</option>
                    ))}
                  </select>
                )}
              </div>
              <div className="account-access-note access-preview">
                <strong>Phạm vi sau khi lưu</strong>
                <p>{roleNames[role]} · {branchName}</p>
                <p>Tài khoản cần đăng nhập lại sau khi thay đổi quyền.</p>
              </div>
              <button
                type="button"
                className="account-link access-revoke-link"
                onClick={() => { setErrors({}); setMode('deactivate'); }}
              >
                Thu hồi quyền truy cập ›
              </button>
            </>
          ) : (
            <>
              <div className="access-warning">
                <strong>Hồ sơ nhân viên vẫn được giữ</strong>
                <p>Tài khoản sẽ không thể tiếp tục truy cập; lịch sử thao tác vẫn có thể tra cứu.</p>
              </div>
              <div className="account-field">
                <label htmlFor="revoke-reason">Lý do</label>
                <textarea
                  id="revoke-reason"
                  required
                  maxLength={1000}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="Nhập lý do thu hồi truy cập"
                />
              </div>
              {account.role === 'BRANCH_MANAGER' && (
                <p className="account-error">Đang có quyền quản lý tại {account.branchName}. Hãy bố trí người thay thế nếu cần.</p>
              )}
            </>
          )}
        </fieldset>
        <div className="account-drawer-footer">
          <button type="button" disabled={busy} onClick={onClose}>Hủy</button>
          <button
            className={mode === 'deactivate' ? 'account-danger' : 'account-primary'}
            disabled={busy || immutable}
            type="submit"
          >
            {busy ? 'Đang lưu…' : mode === 'deactivate' ? 'Thu hồi truy cập' : 'Lưu phân quyền'}
          </button>
        </div>
      </form>
    </dialog>
  );
}
