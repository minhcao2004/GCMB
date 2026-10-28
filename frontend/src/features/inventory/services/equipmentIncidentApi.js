/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Created: 28/10
 * Description: API báo cáo và xử lý sự cố thiết bị UC70–UC73.
 */
import apiClient from '../../../api/client';
const root=(role)=>`/${role==='RECEPTIONIST'?'reception':role==='HEAD_OFFICE_MANAGER'?'head-office':'branch-management'}/equipment-incidents`;
async function mutate(method,url,data,multipart=false){const {data:c}=await apiClient.get('/auth/csrf');return apiClient.request({method,url,data,headers:{[c.headerName]:c.token,...(multipart?{'Content-Type':'multipart/form-data'}:{})}});}
const formData=(command,evidence)=>{const d=new FormData();d.append('command',new Blob([JSON.stringify(command)],{type:'application/json'}));if(evidence)d.append('evidence',evidence);return d;};
export const equipmentIncidentApi={
 list:(params={},role)=>apiClient.get(root(role),{params}).then(r=>r.data),
 assets:(role)=>apiClient.get(`${root(role)}/assets`).then(r=>r.data),
 create:(body,evidence,role)=>mutate('post',root(role),formData(body,evidence),true).then(r=>r.data),
 assess:(id,body)=>mutate('put',`${root('BRANCH_MANAGER')}/${id}/assessment`,body),
 repair:(id,body,evidence)=>mutate('post',`${root('BRANCH_MANAGER')}/${id}/repair-outcome`,formData(body,evidence),true),
 transfers:(headOffice,branchId)=>apiClient.get(`/${headOffice?'head-office':'branch-management'}/equipment-transfers`,{params:headOffice?{branchId}:{}}).then(r=>r.data),
 createTransfer:(body)=>mutate('post','/head-office/equipment-transfers',body).then(r=>r.data),
 handover:(id)=>mutate('put',`/branch-management/equipment-transfers/${id}/handover`,{}),
 receive:(id,body)=>mutate('put',`/branch-management/equipment-transfers/${id}/receipt`,body),
};
