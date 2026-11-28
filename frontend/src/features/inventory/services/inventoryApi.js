import apiClient from '../../../api/client';
import menuItemApi from './menuItemApi';

const base = '/branch-management/inventory';

async function writeJson(method, url, command) {
  const { data: csrf } = await apiClient.get('/auth/csrf');
  const { data } = await apiClient.request({
    method,
    url,
    data: command,
    headers: { [csrf.headerName]: csrf.token },
  });
  return data;
}

async function writeForm(url, command, evidence) {
  const { data: csrf } = await apiClient.get('/auth/csrf');
  const body = new FormData();
  body.append('command', new Blob([JSON.stringify(command)], { type: 'application/json' }));
  if (evidence) body.append('evidence', evidence);
  const { data } = await apiClient.post(url, body, {
    headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'multipart/form-data' },
  });
  return data;
}

const inventoryApi = {
  async items() {
    const { data } = await apiClient.get(`${base}/items`);
    return data;
  },
  async metadata() {
    const { data } = await apiClient.get(`${base}/metadata`);
    return data;
  },
  async item(id) {
    const { data } = await apiClient.get(`${base}/items/${id}`);
    return data;
  },
  async history(id, filters = {}) {
    const { data } = await apiClient.get(`${base}/items/${id}/movements`, { params: filters });
    return data;
  },
  async evidence(itemId, movementId) {
    const { data } = await apiClient.get(`${base}/items/${itemId}/movements/${movementId}/evidence`, { responseType: 'blob' });
    return data;
  },
  async createItem(command) {
    return writeJson('post', `${base}/items`, command);
  },
  async createItemWithMenu(item, salePrice) {
    try {
      return { item: await writeJson('post', `${base}/items/with-menu`, { item, salePrice }), menuError: null };
    } catch (error) {
      // Older servers route /items/with-menu to /items/{id} and answer 405.
      if (![404, 405].includes(error.response?.status)) throw error;
    }
    const created = await writeJson('post', `${base}/items`, item);
    try {
      // BR-111: packaged menu items must link to an existing inventory item.
      await menuItemApi.create({ name: created.name, category: created.category, kind: 'PACKAGED',
        inventoryItemId: created.id, salePrice, active: true, description: null, removeImage: false });
      return { item: created, menuError: null };
    } catch (menuError) {
      // Keep the created inventory item visible so a retry cannot create a duplicate.
      return { item: created, menuError };
    }
  },
  async updateItem(id, command) {
    return writeJson('put', `${base}/items/${id}`, command);
  },
  async deleteItem(id) {
    await writeJson('delete', `${base}/items/${id}`);
  },
  receive(command, evidence) {
    return writeForm(`${base}/receipts`, command, evidence);
  },
  adjust(command, evidence) {
    return writeForm(`${base}/adjustments`, command, evidence);
  },
};

export default inventoryApi;
