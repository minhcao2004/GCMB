/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Description: Gọi API danh mục thiết bị theo phạm vi vai trò.
 */
import apiClient from '../../../api/client';

// Danh mục nhóm thiết bị là toàn chuỗi, do quản lý cấp cao sở hữu.
const root = (headOffice) => `/${headOffice ? 'head-office' : 'branch-management'}/equipment-categories`;
const params = (_headOffice, _branchId, extra = {}) => extra;
const headers = (headOffice, branchId) => (headOffice || !branchId ? {} : { 'X-GCMB-Branch-Id': branchId });
// BR-111: Mọi request thay đổi dữ liệu phải mang token CSRF do backend cấp.
async function mutate(headOffice, branchId, method, url, data) {
  const { data: csrf } = await apiClient.get('/auth/csrf');
  return apiClient.request({ method, url, data, params: params(headOffice, branchId),
    headers: { ...headers(headOffice, branchId), [csrf.headerName]: csrf.token } });
}

// UC-61: Tra cứu nhóm theo từ khóa, trạng thái và phạm vi cơ sở được phân quyền.
export const equipmentCategoryApi = {
  list: (headOffice, branchId, filters) => apiClient.get(root(headOffice), {
    params: params(headOffice, branchId, filters), headers: headers(headOffice, branchId),
  }).then((r) => r.data),
  // UC-60: Tạo danh mục dùng chung, không yêu cầu branchId.
  create: async (headOffice, branchId, body) => (await mutate(headOffice, branchId, 'post', root(headOffice), body)).data,
  // UC-62: Cập nhật thông tin nhóm trong phạm vi cơ sở.
  update: async (headOffice, branchId, id, body) => (await mutate(headOffice, branchId, 'put', `${root(headOffice)}/${id}`, body)).data,
  // UC-63: Đổi trạng thái nhưng giữ nguyên các tham chiếu asset.
  setActive: async (headOffice, branchId, id, active) => (await mutate(headOffice, branchId, 'put', `${root(headOffice)}/${id}/status`, { active })).data,
  // UC-64: Backend chỉ xóa danh mục chưa được asset nào sử dụng (BR-118/BR-191).
  remove: (headOffice, branchId, id) => mutate(headOffice, branchId, 'delete', `${root(headOffice)}/${id}`),
};
