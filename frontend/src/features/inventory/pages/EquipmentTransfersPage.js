/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Created: 28/10
 * Description: Danh sách và xác nhận luân chuyển tài sản giữa các cơ sở UC74–UC76.
 */
import {useCallback,useEffect,useState} from 'react';
import apiClient from '../../../api/client';
import {equipmentAssetApi} from '../services/equipmentAssetApi';
import {equipmentIncidentApi} from '../services/equipmentIncidentApi';
import './equipment-incidents.css';
const states={REQUESTED:'Chờ bàn giao',IN_TRANSIT:'Đang vận chuyển',COMPLETED:'Đã tiếp nhận',CANCELLED:'Đã hủy'};
// BR-111: Hiển thị lỗi điều kiện và phân quyền do backend trả về.
const err=e=>e.response?.data?.detail||e.response?.data?.message||'Không thể hoàn tất thao tác.';
// UC-74–UC-76: Theo dõi phiếu và cung cấp hành động theo vai trò, cơ sở nguồn/đích.
export default function EquipmentTransfersPage({user,headOffice=false}){
 const [rows,setRows]=useState([]),[branches,setBranches]=useState([]),[source,setSource]=useState(''),[target,setTarget]=useState(''),[assetId,setAssetId]=useState(''),[assets,setAssets]=useState([]),[reason,setReason]=useState(''),[rooms,setRooms]=useState([]),[receiptRooms,setReceiptRooms]=useState({}),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 // UC-74–UC-76: Nạp phiếu trong cơ sở được chọn hoặc cơ sở của người quản lý.
 const reload=useCallback(async()=>{try{setRows(await equipmentIncidentApi.transfers(headOffice,headOffice?source:undefined))}catch(e){setError(err(e))}},[headOffice,source]);
 useEffect(()=>{if(headOffice)apiClient.get('/head-office/branches').then(r=>{setBranches(r.data||[]);setSource(s=>s||r.data?.[0]?.id||r.data?.[0]?.branchId||'')}).catch(e=>setError(err(e)))},[headOffice]);
 useEffect(()=>{reload()},[reload]);
 useEffect(()=>{if(headOffice&&source)equipmentAssetApi.list(true,source,{active:true}).then(setAssets).catch(e=>setError(err(e)))},[headOffice,source]);
 useEffect(()=>{if(!headOffice&&user?.branchId)equipmentIncidentApi.transfers(false).then(async items=>{setRows(items);if(items.some(x=>x.toBranchId===user.branchId&&x.status==='IN_TRANSIT')){try{const o=await equipmentAssetApi.options(false,user.branchId);setRooms(o.rooms||[])}catch(e){setError(err(e))}}}).catch(e=>setError(err(e)))},[headOffice,user?.branchId]);
 // UC-74/BR-48: Tạo yêu cầu điều chuyển chỉ từ màn hình quản lý cấp cao.
 const create=async e=>{e.preventDefault();setBusy(true);setError('');try{await equipmentIncidentApi.createTransfer({assetId,toBranchId:target,reason});setReason('');await reload()}catch(x){setError(err(x))}finally{setBusy(false)}};
 // UC-75/UC-76: Thực hiện bàn giao hoặc tiếp nhận và tải lại lịch sử phiếu.
 const action=async fn=>{setBusy(true);setError('');try{await fn();await reload()}catch(x){setError(err(x))}finally{setBusy(false)}};
 return <main className="equipment-incidents"><div className="ei-heading"><div><p>Quản lý cơ sở / Thiết bị &amp; sự cố</p><h1>Điều chuyển thiết bị</h1><span>Luân chuyển giữa các cơ sở · Lịch sử được lưu theo tài sản</span></div></div>
 {headOffice&&<form className="ei-transfer-form" onSubmit={create}><h2>Tạo yêu cầu điều chuyển</h2><div><label>Cơ sở nguồn<select value={source} onChange={e=>{setSource(e.target.value);setAssetId("");setTarget("")}}>{branches.map(b=><option key={b.id||b.branchId} value={b.id||b.branchId}>{b.name||b.branchName}</option>)}</select></label><label>Thiết bị<select required value={assetId} onChange={e=>setAssetId(e.target.value)}><option value="">Chọn tài sản</option>{assets.map(a=><option key={a.id} value={a.id}>{a.code} · {a.name}</option>)}</select></label><label>Cơ sở nhận<select required value={target} onChange={e=>setTarget(e.target.value)}><option value="">Chọn cơ sở đích</option>{branches.filter(b=>(b.id||b.branchId)!==source).map(b=><option key={b.id||b.branchId} value={b.id||b.branchId}>{b.name||b.branchName}</option>)}</select></label><label>Lý do<input required value={reason} onChange={e=>setReason(e.target.value)}/></label><button className="ei-primary" disabled={busy||!assetId||!target}>Tạo yêu cầu</button></div></form>}
 {error&&<p className="ei-error" role="alert">{error}</p>}<div className="ei-table-wrap"><table><thead><tr><th>Phiếu / thiết bị</th><th>Nguồn → đích</th><th>Lý do</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{rows.map(x=><tr key={x.id}><td><strong>{x.assetCode} · {x.assetName}</strong><small>DC-{x.id.slice(0,8).toUpperCase()}</small></td><td>{x.fromBranchName} → {x.toBranchName}</td><td>{x.reason}</td><td><span className={`ei-status ${x.status?.toLowerCase()}`}>{states[x.status]||x.status}</span></td><td>{!headOffice&&x.status==='REQUESTED'&&x.fromBranchId===user?.branchId&&<button disabled={busy} onClick={()=>action(()=>equipmentIncidentApi.handover(x.id))}>Xác nhận bàn giao</button>}{!headOffice&&x.status==='IN_TRANSIT'&&x.toBranchId===user?.branchId&&<><select aria-label="Vị trí nhận" value={receiptRooms[x.id]||''} onChange={e=>setReceiptRooms({...receiptRooms,[x.id]:e.target.value})}><option value="">Chọn phòng nhận</option>{rooms.map(r=><option value={r.id} key={r.id}>{r.code} · {r.name}</option>)}</select><button disabled={busy||!receiptRooms[x.id]} onClick={()=>action(()=>equipmentIncidentApi.receive(x.id,{toRoomId:receiptRooms[x.id]}))}>Xác nhận tiếp nhận</button></>}</td></tr>)}{!rows.length&&<tr><td colSpan="5" className="ei-empty">Chưa có phiếu điều chuyển.</td></tr>}</tbody></table></div><p className="ei-footnote">BR-49/BR-50: Tiếp nhận chỉ khả dụng sau khi cơ sở nguồn xác nhận bàn giao.</p></main>
}

