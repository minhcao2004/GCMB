import axios from 'axios';

// Client HTTP dùng chung với đường dẫn API và cookie phiên.
const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
  timeout: 20000,
});

export function apiAssetUrl(path) {
  if (!path) return '';
  if (/^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(path)) return path;

  const base = String(apiClient.defaults.baseURL || '/api/v1').replace(/\/+$/, '');
  const apiBase = /\/api\/v1$/i.test(base) ? base : `${base}/api/v1`;
  const route = String(path).startsWith('/') ? String(path) : `/${path}`;
  const versionlessRoute = route.replace(/^\/api\/v1(?=\/|$)/i, '');
  return `${apiBase}${versionlessRoute}`;
}

export default apiClient;

