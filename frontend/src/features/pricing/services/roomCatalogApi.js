import api from '../../../api/client';

async function mutate(method,url,data) {
  const {data:csrf}=await api.get('/auth/csrf');
  return (await api.request({method,url,data,headers:{[csrf.headerName]:csrf.token}})).data;
}

export const roomCatalogApi={
  roomTypes:async()=> (await api.get('/head-office/room-types')).data,
  prices:async()=> (await api.get('/head-office/room-prices')).data,
  priceRules:async()=> (await api.get('/head-office/price-rules')).data,
  createRoomType:(data)=>mutate('post','/head-office/room-types',data),
  updateRoomType:(id,data)=>mutate('put',`/head-office/room-types/${id}`,data),
  deleteRoomType:(id)=>mutate('delete',`/head-office/room-types/${id}`),
  createPriceRule:(data)=>mutate('post','/head-office/price-rules',data),
  updatePriceRule:(id,data)=>mutate('put',`/head-office/price-rules/${id}`,data),
  deletePriceRule:(id)=>mutate('delete',`/head-office/price-rules/${id}`),
  createHourly:(roomTypeId,data)=>mutate('post',`/head-office/room-prices/hourly/${roomTypeId}`,data),
  createCombo:(roomTypeId,data)=>mutate('post',`/head-office/room-prices/combos/${roomTypeId}`,data),
  updatePrice:(kind,id,data)=>mutate('put',`/head-office/room-prices/${kind.toLowerCase()}/${id}`,data),
  deletePrice:(kind,id)=>mutate('delete',`/head-office/room-prices/${kind.toLowerCase()}/${id}`),
};

export function catalogError(error){
  if(!error.response)return 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối và thử lại.';
  return error.response.data?.detail||error.response.data?.message||'Không thể lưu dữ liệu. Vui lòng thử lại.';
}
