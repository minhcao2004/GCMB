/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Created: 28/10
 * Description: API báo cáo và xử lý sự cố thiết bị UC70–UC73.
 */
import apiClient from '../../../api/client';
const root=(role)=>`/${role==='RECEPTIONIST'?'reception':role==='HEAD_OFFICE_MANAGER'?'head-office':'branch-management'}/equipment-incidents`;
async function mutate(method,url,data,multipart=false){const {data:c}=await apiClient.get('/auth/csrf');const headers={[c.headerName]:c.token};if(multipart)headers['Content-Type']=undefined;return apiClient.request({method,url,data,headers});}
const formData=(command,evidence)=>{const body=new FormData();body.append('command',new Blob([JSON.stringify(command)],{type:'application/json'}));if(evidence)body.append('evidence',evidence);return body;};
const repairFormData=(command,invoice,transferProof)=>{const body=formData(command);if(invoice)body.append('invoice',invoice);if(transferProof)body.append('transferProof',transferProof);return body;};
export const equipmentIncidentApi={
 list:(params={},role)=>apiClient.get(root(role),{params}).then(response=>response.data),
 assets:(role)=>apiClient.get(`${root(role)}/assets`).then(response=>response.data),
 create:(body,evidence,role)=>mutate('post',root(role),formData(body,evidence),true).then(response=>response.data),
 assess:(id,body)=>mutate('put',`${root('BRANCH_MANAGER')}/${id}/assessment`,body),
 repair:(id,body,invoice,transferProof)=>mutate('post',`${root('BRANCH_MANAGER')}/${id}/repair-outcome`,repairFormData(body,invoice,transferProof),true),
 suggest:(id,solution)=>mutate('put',`${root('RECEPTIONIST')}/${id}/suggestion`,{solution}),
 review:(id,approved,note)=>mutate('put',`${root('BRANCH_MANAGER')}/${id}/review`,{approved,note}),
 progress:(id,body)=>mutate('put',`${root('BRANCH_MANAGER')}/${id}/progress`,body),
 close:(id,note)=>mutate('put',`${root('BRANCH_MANAGER')}/${id}/close`,{note}),
 history:(id,role)=>apiClient.get(`${root(role)}/${id}/progress`).then(response=>response.data),
 document:(id,kind,role)=>apiClient.get(`${root(role)}/${id}/repair-documents/${kind}`,{responseType:'blob'}),
 transfers:(headOffice,branchId)=>apiClient.get(`/${headOffice?'head-office':'branch-management'}/equipment-transfers`,{params:headOffice?{branchId}:{}}).then(response=>response.data),
 createTransfer:(body)=>mutate('post','/head-office/equipment-transfers',body).then(response=>response.data),
 handover:(id)=>mutate('put',`/branch-management/equipment-transfers/${id}/handover`,{}),
 receive:(id,body)=>mutate('put',`/branch-management/equipment-transfers/${id}/receipt`,body),
};
