/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: Gọi API xem và quản lý tài sản thiết bị UC65–UC69 theo cơ sở.
 */
import apiClient from '../../../api/client';

// BR-202: Các đường dẫn tương đối ghép với baseURL /api/v1 của apiClient.
const root = (headOffice) => `/${headOffice ? 'head-office' : 'branch-management'}/equipment-assets`;
// BR-202: Chỉ quản lý cấp cao gửi branchId; quản lý cơ sở kèm branch header từ cơ sở đang chọn.
const scopeParams = (headOffice, branchId, extra = {}) => ({ ...(headOffice ? { branchId } : {}), ...extra });
// BR-202: Gửi branch header để backend xác thực chi nhánh đang được chọn.
const scopeHeaders = (headOffice, branchId) => (headOffice ? {} : { 'X-GCMB-Branch-Id': branchId });

// BR-111: Mọi thao tác ghi phải lấy token CSRF mới và gửi kèm phạm vi cơ sở.
async function mutate(headOffice, branchId, method, url, data, multipart = false) {
  const { data: csrf } = await apiClient.get('/auth/csrf');
  return apiClient.request({ method, url, data, params: scopeParams(headOffice, branchId),
    headers: { ...scopeHeaders(headOffice, branchId), [csrf.headerName]: csrf.token,
      ...(multipart ? { 'Content-Type': 'multipart/form-data' } : {}) } });
}

export const equipmentAssetApi = {
  // UC-66: Tìm và lọc tài sản; dữ liệu chỉ đọc trong phạm vi chi nhánh hiện tại.
  list: (headOffice, branchId, filters) => apiClient.get(root(headOffice), {
    params: scopeParams(headOffice, branchId, filters), headers: scopeHeaders(headOffice, branchId),
  }).then((r) => r.data),
  // UC-65/67: Lấy danh mục và phòng phù hợp để tạo hoặc sửa hồ sơ.
  options: (headOffice, branchId, assetId) => apiClient.get(`${root(headOffice)}/options`, {
    params: scopeParams(headOffice, branchId, assetId ? { assetId } : {}), headers: scopeHeaders(headOffice, branchId),
  }).then((r) => r.data),
  // UC-65: Tạo tài sản; backend kiểm tra BR-202 và mã toàn chuỗi.
  create: async (headOffice, branchId, body) => (await mutate(headOffice, branchId, 'post', root(headOffice), body)).data,
  // UC-67/BR-110: Cập nhật thông tin nhưng không cho đổi cơ sở ngoài luồng điều chuyển.
  update: async (headOffice, branchId, id, body) => (await mutate(headOffice, branchId, 'put', `${root(headOffice)}/${id}`, body)).data,
  // UC-68: Bật/ngừng sử dụng riêng với tình trạng vận hành của tài sản.
  setActive: async (headOffice, branchId, id, active) => (await mutate(headOffice, branchId, 'put', `${root(headOffice)}/${id}/status`, { active })).data,
  // UC-69/BR-118: Backend chỉ xóa tài sản chưa phát sinh lịch sử nghiệp vụ.
  remove: (headOffice, branchId, id) => mutate(headOffice, branchId, 'delete', `${root(headOffice)}/${id}`),
  // BR-115/BR-116: Tải ảnh theo giới hạn dung lượng và loại file mà backend kiểm tra lại.
  uploadImage: async (headOffice, branchId, id, file) => {
    const data = new FormData(); data.append('image', file);
    return (await mutate(headOffice, branchId, 'post', `${root(headOffice)}/${id}/image`, data, true)).data;
  },
  // UC-66: Đọc ảnh qua cookie phiên và phạm vi branch, không làm lộ khóa lưu file.
  image: (headOffice, branchId, id) => apiClient.get(`${root(headOffice)}/${id}/image`, {
    params: scopeParams(headOffice, branchId), headers: scopeHeaders(headOffice, branchId), responseType: 'blob',
  }).then((r) => r.data),
};
