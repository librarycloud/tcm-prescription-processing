import axios from 'axios';
import { ElMessage } from 'element-plus/es/components/message/index.mjs';
import { clearAppStorage } from '@/utils/storage';
import { getToken } from '@/utils/token';

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
});

service.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

service.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response.data;
    const body = response.data;

    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data;
      const message = body.message || '请求失败';
      ElMessage.error(message);
      return Promise.reject(new Error(message));
    }

    const message = '接口返回格式异常，请检查后端地址或 /api 代理配置';
    ElMessage.error(message);
    return Promise.reject(new Error(message));
  },
  (error) => {
    const status = error.response?.status;
    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试';

    if (status === 401) {
      clearAppStorage();
      if (window.location.pathname !== '/login') {
        window.location.replace('/login');
      }
      ElMessage.error(message);
      return Promise.reject(new Error(message));
    }

    ElMessage.error(message);
    return Promise.reject(new Error(message));
  }
);

export default service;
