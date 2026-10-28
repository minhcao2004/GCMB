/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Description: Gọi API danh mục thiết bị theo phạm vi vai trò.
 */
import apiClient from '../../../api/client';

// Scope: chọn endpoint đúng vai trò để quản lý cơ sở không thể truyền đổi cơ sở tùy ý.
const root = (headOffice) => `/${headOffice ? 'head-office' : 'branch-management'}/equipment-categories`;
// Scope: chỉ quản lý cấp cao gửi branchId; quản lý cơ sở dùng chi nhánh từ phiên đăng nhập.
const params = (headOffice, branchId, extra = {}) => ({ ...(headOffice ? { branchId } : {}), ...extra });
// Scope: đính kèm cơ sở hiện chọn cho quản lý cơ sở và lấy CSRF mới cho mỗi thao tác ghi.
const headers = (headOffice, branchId) => (headOffice ? {} : { 'X-GCMB-Branch-Id': branchId });
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
  // UC-60: Tạo danh mục; backend áp dụng BR-111 và kiểm tra mã duy nhất theo cơ sở.
  create: async (headOffice, branchId, body) => (await mutate(headOffice, branchId, 'post', root(headOffice), body)).data,
  // UC-62: Cập nhật thông tin nhóm trong phạm vi cơ sở.
  update: async (headOffice, branchId, id, body) => (await mutate(headOffice, branchId, 'put', `${root(headOffice)}/${id}`, body)).data,
  // UC-63: Đổi trạng thái nhưng giữ nguyên các tham chiếu asset.
  setActive: async (headOffice, branchId, id, active) => (await mutate(headOffice, branchId, 'put', `${root(headOffice)}/${id}/status`, { active })).data,
  // UC-64: Backend chỉ xóa danh mục chưa được asset nào sử dụng (BR-118/BR-191).
  remove: (headOffice, branchId, id) => mutate(headOffice, branchId, 'delete', `${root(headOffice)}/${id}`),
};
