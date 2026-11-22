/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Created: 28/10
 * Description: Danh sách và xác nhận luân chuyển tài sản giữa các cơ sở UC74–UC76.
 */
import { useCallback, useEffect, useState } from 'react';
import apiClient from '../../../api/client';
import { equipmentAssetApi } from '../services/equipmentAssetApi';
import { equipmentIncidentApi } from '../services/equipmentIncidentApi';
import './equipment-incidents.css';
import './equipment-harmony.css';

const states = { REQUESTED: 'Chờ bàn giao', IN_TRANSIT: 'Đang vận chuyển', COMPLETED: 'Đã tiếp nhận', CANCELLED: 'Đã hủy' };
const err = (error) => error.response?.data?.detail || error.response?.data?.message || 'Không thể hoàn tất thao tác.';

export default function EquipmentTransfersPage({ user, headOffice = false }) {
  const [rows, setRows] = useState([]);
  const [branches, setBranches] = useState([]);
  const [source, setSource] = useState('');
  const [target, setTarget] = useState('');
  const [assetId, setAssetId] = useState('');
  const [assets, setAssets] = useState([]);
  const [reason, setReason] = useState('');
  const [rooms, setRooms] = useState([]);
  const [receiptRooms, setReceiptRooms] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const reload = useCallback(async () => {
    try {
      const transfers = await equipmentIncidentApi.transfers(headOffice, headOffice ? source : undefined) || [];
      setRows(transfers);

      if (!headOffice) {
        const hasIncomingTransfer = transfers.some((item) => item.toBranchId === user?.branchId && ['REQUESTED', 'IN_TRANSIT'].includes(item.status));
        if (hasIncomingTransfer) {
          const options = await equipmentAssetApi.options(false, user.branchId);
          setRooms(options.rooms || []);
        } else {
          setRooms([]);
        }
      }
    } catch (cause) {
      setError(err(cause));
    }
  }, [headOffice, source, user?.branchId]);

  useEffect(() => {
    if (!headOffice) return undefined;
    apiClient.get('/head-office/branches')
      .then((response) => {
        const items = response.data || [];
        setBranches(items);
        setSource((current) => current || items[0]?.id || items[0]?.branchId || '');
      })
      .catch((cause) => setError(err(cause)));
    return undefined;
  }, [headOffice]);

  useEffect(() => {
    reload();
    if (headOffice) return undefined;

    // UC-76: nhận thay đổi bàn giao của cơ sở nguồn khi trang đang mở.
    const timer = window.setInterval(reload, 15000);
    window.addEventListener('focus', reload);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', reload);
    };
  }, [headOffice, reload]);

  useEffect(() => {
    if (!headOffice || !source) return;
    equipmentAssetApi.list(true, source, { active: true }).then(setAssets).catch((cause) => setError(err(cause)));
  }, [headOffice, source]);

  const create = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await equipmentIncidentApi.createTransfer({ assetId, toBranchId: target, reason });
      setReason('');
      await reload();
    } catch (cause) {
      setError(err(cause));
    } finally {
      setBusy(false);
    }
  };

  const action = async (operation) => {
    setBusy(true);
    setError('');
    try {
      await operation();
      await reload();
    } catch (cause) {
      setError(err(cause));
    } finally {
      setBusy(false);
    }
  };

  const isIncoming = (item) => !headOffice && item.toBranchId === user?.branchId;
  const pendingHandover = rows.filter((item) => item.status === 'REQUESTED').length;
  const inTransit = rows.filter((item) => item.status === 'IN_TRANSIT').length;
  const completed = rows.filter((item) => item.status === 'COMPLETED').length;

  return <main className="equipment-incidents ei-transfer-page">
    <div className="ei-heading">
      <div>
        <span className="ei-transfer-eyebrow">QUẢN LÝ TÀI SẢN</span>
        <h1>Điều chuyển thiết bị</h1>
        <p>Theo dõi bàn giao và tiếp nhận tài sản giữa các cơ sở.</p>
      </div>
      {!headOffice && <button className="ei-transfer-refresh" type="button" onClick={reload} disabled={busy}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M20 7v5h-5M4 17v-5h5"/><path d="M5.6 9A7 7 0 0 1 18 6l2 6M4 12l2 6a7 7 0 0 0 12.4-3"/></svg>
        Làm mới trạng thái
      </button>}
    </div>

    <section className="ei-transfer-summary" aria-label="Tổng hợp trạng thái phiếu điều chuyển">
      <article><span className="ei-summary-mark is-requested" aria-hidden="true"/><div><small>Chờ bàn giao</small><strong>{pendingHandover}</strong></div></article>
      <article><span className="ei-summary-mark is-transit" aria-hidden="true"/><div><small>Đang vận chuyển</small><strong>{inTransit}</strong></div></article>
      <article><span className="ei-summary-mark is-completed" aria-hidden="true"/><div><small>Đã tiếp nhận</small><strong>{completed}</strong></div></article>
    </section>

    {headOffice && <form className="ei-transfer-form" onSubmit={create}>
      <div className="ei-transfer-form-heading"><div><h2>Tạo yêu cầu điều chuyển</h2><p>Chọn tài sản nguồn, cơ sở nhận và nêu lý do điều chuyển.</p></div></div>
      <div>
        <label>Cơ sở nguồn<select value={source} onChange={(event) => { setSource(event.target.value); setAssetId(''); setTarget(''); }}>
          {branches.map((branch) => <option key={branch.id || branch.branchId} value={branch.id || branch.branchId}>{branch.name || branch.branchName}</option>)}
        </select></label>
        <label>Thiết bị<select required value={assetId} onChange={(event) => setAssetId(event.target.value)}>
          <option value="">Chọn tài sản</option>{assets.map((asset) => <option key={asset.id} value={asset.id}>{asset.code} · {asset.name}</option>)}
        </select></label>
        <label>Cơ sở nhận<select required value={target} onChange={(event) => setTarget(event.target.value)}>
          <option value="">Chọn cơ sở đích</option>{branches.filter((branch) => (branch.id || branch.branchId) !== source).map((branch) => <option key={branch.id || branch.branchId} value={branch.id || branch.branchId}>{branch.name || branch.branchName}</option>)}
        </select></label>
        <label>Lý do<input required value={reason} onChange={(event) => setReason(event.target.value)} /></label>
        <button className="ei-primary" disabled={busy || !assetId || !target}>{busy ? 'Đang tạo…' : 'Tạo yêu cầu'}</button>
      </div>
    </form>}

    {error && <p className="ei-error" role="alert">{error}</p>}
    <section className="ei-transfer-records" aria-labelledby="ei-transfer-list-title">
      <div className="ei-transfer-records-heading"><div><h2 id="ei-transfer-list-title">Phiếu điều chuyển</h2><p>{rows.length} phiếu trong phạm vi đang xem</p></div></div>
      <div className="ei-table-wrap"><table>
      <thead><tr><th>Phiếu / thiết bị</th><th>Nguồn → đích</th><th>Lý do</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>
        {rows.map((item) => <tr key={item.id}>
          <td><strong>{item.assetCode} · {item.assetName}</strong><small>DC-{item.id.slice(0, 8).toUpperCase()}</small></td>
          <td>{item.fromBranchName} → {item.toBranchName}</td>
          <td>{item.reason}</td>
          <td><span className={`ei-status ${item.status?.toLowerCase()}`}>{states[item.status] || item.status}</span></td>
          <td className="ei-transfer-actions">
            {!headOffice && item.status === 'REQUESTED' && item.fromBranchId === user?.branchId && <button type="button" disabled={busy} onClick={() => action(() => equipmentIncidentApi.handover(item.id))}>Xác nhận bàn giao</button>}
            {isIncoming(item) && ['REQUESTED', 'IN_TRANSIT'].includes(item.status) && <div className="ei-receipt-action">
              <select aria-label={`Vị trí nhận ${item.assetName}`} value={receiptRooms[item.id] || ''} onChange={(event) => setReceiptRooms((current) => ({ ...current, [item.id]: event.target.value }))}>
                <option value="">Chọn phòng nhận</option>{rooms.map((room) => <option value={room.id} key={room.id}>{room.code} · {room.name}</option>)}
              </select>
              <button type="button" disabled={busy || item.status !== 'IN_TRANSIT' || !receiptRooms[item.id]} onClick={() => action(() => equipmentIncidentApi.receive(item.id, { toRoomId: receiptRooms[item.id] }))}>
                {busy ? 'Đang xác nhận…' : 'Xác nhận tiếp nhận'}
              </button>
              {item.status === 'REQUESTED' && <small>Chờ cơ sở nguồn xác nhận bàn giao.</small>}
            </div>}
          </td>
        </tr>)}
        {!rows.length && <tr><td colSpan="5" className="ei-empty">Chưa có phiếu điều chuyển.</td></tr>}
      </tbody>
      </table></div>
    </section>
    <p className="ei-footnote">Quy trình gồm hai bước: cơ sở nguồn xác nhận bàn giao; cơ sở đích chọn phòng nhận và xác nhận tiếp nhận. Thiết bị chỉ đổi cơ sở sau bước tiếp nhận.</p>
  </main>;
}
