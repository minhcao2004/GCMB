/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Màn hình gán quản lý cho từng cơ sở.
 */

import { useEffect, useState } from 'react';
import api from '../../../api/client';

export default function BranchAssignmentPanel(){
 const [branches,setBranches]=useState([]),[managers,setManagers]=useState([]),[branchId,setBranchId]=useState(''),[userId,setUserId]=useState(''),[busy,setBusy]=useState(false),[message,setMessage]=useState(''),[error,setError]=useState('');
 const load=async()=>{const [b,m]=await Promise.all([api.get('/head-office/branches'),api.get('/head-office/branches/managers')]);setBranches(b.data);setManagers(m.data)};
 useEffect(()=>{load().catch(e=>setError(e.response?.data?.detail||e.response?.data?.message||'Không tải được danh sách cơ sở và tài khoản.'))},[]);
 const save=async(e)=>{e.preventDefault();setBusy(true);setError('');setMessage('');try{const {data:csrf}=await api.get('/auth/csrf');const {data}=await api.put(`/head-office/branches/${branchId}/manager`,{userId},{headers:{[csrf.headerName]:csrf.token}});setMessage(data.message);await load()}catch(e){setError(e.response?.data?.detail||e.response?.data?.message||'Không thể phân quyền quản lý cơ sở.')}finally{setBusy(false)}};
 return <div style={{maxWidth:1050,color:'#24273c'}}><h1 style={{fontSize:28,margin:'0 0 8px'}}>Cơ sở</h1><p style={{color:'#7b8497'}}>Phân công tài khoản quản lý cho từng cơ sở. Mỗi tài khoản được quản lý một cơ sở.</p><form onSubmit={save} style={{display:'grid',gridTemplateColumns:'1fr 1fr auto',gap:12,alignItems:'end',padding:20,background:'#fff',borderRadius:14,margin:'22px 0'}}><label style={{display:'grid',gap:8,color:'#778194'}}>Cơ sở<select required value={branchId} onChange={e=>setBranchId(e.target.value)} style={{padding:13,border:'1px solid #dce3ec',borderRadius:8}}><option value="">Chọn cơ sở</option>{branches.map(b=><option key={b.branchId} value={b.branchId}>{b.branchName} ({b.code}){b.managerUsername?` · ${b.managerUsername}`:''}</option>)}</select></label><label style={{display:'grid',gap:8,color:'#778194'}}>Tài khoản quản lý cơ sở<select required value={userId} onChange={e=>setUserId(e.target.value)} style={{padding:13,border:'1px solid #dce3ec',borderRadius:8}}><option value="">Chọn tài khoản đang hoạt động</option>{managers.map(m=><option key={m.userId} value={m.userId}>{m.username} · {m.email}</option>)}</select></label><button disabled={busy||!branches.length||!managers.length} style={{padding:'14px 22px',background:'#ed148d',border:0,borderRadius:9,color:'#fff',fontWeight:700,cursor:'pointer'}}>{busy?'Đang lưu…':'Gán quản lý'}</button></form>{error&&<p role="alert" style={{color:'#b12642'}}>{error}</p>}{message&&<p role="status" style={{padding:12,borderRadius:8,background:'#e8f8ee',color:'#14834f'}}>{message}</p>}<section style={{background:'#fff',borderRadius:14,padding:20}}><h2 style={{fontSize:18}}>Phân quyền hiện tại</h2>{branches.map(b=><div key={b.branchId} style={{display:'flex',justifyContent:'space-between',gap:12,padding:'15px 0',borderBottom:'1px solid #edf0f5'}}><strong>{b.branchName}</strong><span style={{color:b.managerUsername?'#14834f':'#9a6b15'}}>{b.managerUsername||'Chưa gán tài khoản quản lý'}</span></div>)}</section></div>
}
