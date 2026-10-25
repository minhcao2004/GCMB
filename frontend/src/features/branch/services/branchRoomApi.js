/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Tích hợp API quản lý phòng theo cơ sở.
 */

import api from '../../../api/client';

const base='/branch-management/rooms';
// Gắn cơ sở vào từng request để các tab đang xem cơ sở khác không ghi nhầm dữ liệu.
export const branchRoomApi={
  forBranch(branchId) {
    const headers = { 'X-GCMB-Branch-Id': branchId };
    async function mutate(method,url,data){const {data:csrf}=await api.get('/auth/csrf');return (await api.request({method,url,data,headers:{...headers,[csrf.headerName]:csrf.token}})).data;}
    return {
      rooms:async()=> (await api.get(base,{headers})).data,
      types:async()=> (await api.get(`${base}/types`,{headers})).data,
      create:(body)=>mutate('post',base,body),
      update:(id,body)=>mutate('put',`${base}/${id}`,body),
      remove:(id)=>mutate('delete',`${base}/${id}`),
      updateStatus:(id,body)=>mutate('put',`${base}/${id}/status`,body),
    };
  },
};
