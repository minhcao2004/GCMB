import api from '../../../api/client';

const base='/branch-management/rooms';
async function mutate(method,url,data){const {data:csrf}=await api.get('/auth/csrf');return (await api.request({method,url,data,headers:{[csrf.headerName]:csrf.token}})).data;}
export const branchRoomApi={
  rooms:async()=> (await api.get(base)).data,
  types:async()=> (await api.get(`${base}/types`)).data,
  create:(body)=>mutate('post',base,body),
  update:(id,body)=>mutate('put',`${base}/${id}`,body),
  remove:(id)=>mutate('delete',`${base}/${id}`),
  updateStatus:(id,body)=>mutate('put',`${base}/${id}/status`,body),
};
