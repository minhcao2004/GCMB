import apiClient from '../../../api/client';

const base = '/branch-management/menu-items';

async function write(method, url, command, image) {
  const { data: csrf } = await apiClient.get('/auth/csrf');
  const body = new FormData();
  body.append('command', new Blob([JSON.stringify(command)], { type: 'application/json' }));
  if (image) body.append('image', image);
  const { data } = await apiClient.request({
    method,
    url,
    data: body,
    headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

const menuItemApi = {
  async list() {
    const { data } = await apiClient.get(base);
    return data;
  },
  async categories() {
    const { data } = await apiClient.get(`${base}/categories`);
    return data;
  },
  async inventoryOptions() {
    const { data } = await apiClient.get(`${base}/inventory-options`);
    return data;
  },
  create(command, image) {
    return write('post', base, command, image);
  },
  update(id, command, image) {
    return write('put', `${base}/${id}`, command, image);
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
