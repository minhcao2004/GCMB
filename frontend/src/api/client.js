import axios from 'axios';

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
  timeout: 20000,
});

export default apiClient;

