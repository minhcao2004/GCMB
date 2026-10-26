import apiClient from '../../../api/client';

const base = '/branch-management/menu-items';

const menuItemApi = {
  async list() {
    const { data } = await apiClient.get(base);
    return data;
  },
  async categories() {
    const { data } = await apiClient.get(`${base}/categories`);
    return data;
  },
  async setActive(id, active) {
    const { data } = await apiClient.put(`${base}/${id}/status`, { active });
    return data;
  },
  async remove(id) {
    await apiClient.delete(`${base}/${id}`);
  },
};

export default menuItemApi;
