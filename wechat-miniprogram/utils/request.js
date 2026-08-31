import { getBaseUrl } from './config';
import { clearSession, getToken } from './auth';

const CACHE_PREFIX = 'api-cache:';
const REFERENCE_CACHE_TTL = 24 * 60 * 60 * 1000;
const BUSINESS_LIST_CACHE_TTL = 10 * 60 * 1000;
const DETAIL_CACHE_TTL = 5 * 60 * 1000;
const OPERATION_CACHE_TTL = 30 * 1000;
const USER_PACKAGE_CACHE_TTL = 30 * 1000;
const memoryCache = {};
const inflightRequests = {};
let cacheToken = null;
let responseCacheVersion = 0;

function routeOf(url) {
  return String(url || '').split('?')[0];
}

// Keep operational data short-lived while reusing stable reference data.
function cacheTtl(url) {
  const route = routeOf(url);
  if (route === '/admin/processing-plans/by-scan' ||
      route.startsWith('/admin/packages/by-code/')) return 0;
  if (route === '/admin/doctors' || route === '/admin/dictionaries' ||
      route === '/stores' || route === '/admin/store-transfers/stores' ||
      route === '/admin/herb-locations/stores' ||
      route === '/admin/processing-equipment' || route === '/admin/products/stores' ||
      route === '/admin/e6-pharmacy/category-mappings' ||
      route === '/admin/e6-pharmacy/barcode-template') return REFERENCE_CACHE_TTL;
  if (route === '/admin/herb-locations') return REFERENCE_CACHE_TTL;
  if (route === '/admin/prescriptions' || route === '/admin/processing-plans' ||
      route === '/admin/packages' || route === '/admin/store-transfers' ||
      route === '/admin/store-transfers/stats') {
    return BUSINESS_LIST_CACHE_TTL;
  }
  if (route === '/admin/e6/imports') return 5 * 60 * 1000;
  if (route === '/admin/stats' || route === '/admin/product-differences/stats' ||
      route === '/admin/product-differences/logs' || route === '/admin/products' ||
      route === '/admin/e6-pharmacy/products' || route === '/admin/yd-goods-check') {
    return OPERATION_CACHE_TTL;
  }
  if (route === '/user/packages' || route.startsWith('/user/packages/')) {
    return USER_PACKAGE_CACHE_TTL;
  }
  if (route.startsWith('/admin/prescriptions/') ||
      route.startsWith('/admin/processing-plans/') ||
      route.startsWith('/admin/packages/') ||
      route.startsWith('/admin/store-transfers/') ||
      route.startsWith('/admin/herb-locations/') ||
      route.startsWith('/admin/product-differences/') ||
      route.startsWith('/admin/e6-pharmacy/') ||
      route.startsWith('/admin/yd-goods-check/')) return DETAIL_CACHE_TTL;
  return 0;
}

function stableSerialize(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(stableSerialize).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableSerialize(value[key])}`).join(',')}}`;
}

function hash(value) {
  let result = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    result ^= value.charCodeAt(index);
    result = Math.imul(result, 16777619);
  }
  return (result >>> 0).toString(36);
}

function responseKey(options, token) {
  const source = `${options.method || 'GET'}|${options.url}|${stableSerialize(options.data || {})}|${token || ''}`;
  return `${CACHE_PREFIX}${hash(source)}`;
}

function cacheKey(options, token) {
  return options.forceRefresh || options.bypassCache ? '' : responseKey(options, token);
}

function readCached(key, ttl) {
  const memoryValue = memoryCache[key];
  let cached = memoryValue;
  if (!cached) {
    try {
      cached = wx.getStorageSync(key);
    } catch (error) {
      cached = null;
    }
  }
  const expiresAt = Number(cached?.expiresAt) || (Number(cached?.savedAt) + ttl);
  if (!cached || !cached.savedAt || expiresAt <= Date.now()) {
    delete memoryCache[key];
    try { wx.removeStorageSync(key); } catch (error) { /* storage may be unavailable */ }
    return null;
  }
  memoryCache[key] = cached;
  return cached.data;
}

function pruneExpiredCache() {
  try {
    const now = Date.now();
    (wx.getStorageInfoSync().keys || [])
      .filter((key) => key.startsWith(CACHE_PREFIX))
      .forEach((key) => {
        const cached = wx.getStorageSync(key);
        if (!cached || !cached.savedAt || !cached.expiresAt || cached.expiresAt <= now) {
          delete memoryCache[key];
          wx.removeStorageSync(key);
        }
      });
  } catch (error) {
    // Cache pruning is best effort.
  }
}

function writeCached(key, data, ttl) {
  const savedAt = Date.now();
  const cached = { savedAt, expiresAt: savedAt + ttl, data };
  memoryCache[key] = cached;
  try {
    wx.setStorageSync(key, cached);
    pruneExpiredCache();
  } catch (error) {
    delete memoryCache[key];
  }
}

export function clearResponseCache() {
  responseCacheVersion += 1;
  Object.keys(memoryCache).forEach((key) => delete memoryCache[key]);
  Object.keys(inflightRequests).forEach((key) => delete inflightRequests[key]);
  try {
    const keys = wx.getStorageInfoSync().keys || [];
    keys.filter((key) => key.startsWith(CACHE_PREFIX)).forEach((key) => wx.removeStorageSync(key));
  } catch (error) {
    // Cache cleanup is best effort.
  }
}

export function request(options) {
  const token = getToken();
  if (token !== cacheToken) {
    Object.keys(memoryCache).forEach((key) => delete memoryCache[key]);
    cacheToken = token;
  }
  const method = String(options.method || 'GET').toUpperCase();
  const bypass = Boolean(options.forceRefresh || options.bypassCache);
  const ttl = (method === 'GET' && !bypass) ? cacheTtl(options.url) : 0;
  const key = ttl ? cacheKey(options, token) : '';
  if (ttl) {
    const cached = readCached(key, ttl);
    if (cached !== null && cached !== undefined) return Promise.resolve(cached);
  }
  const inflightKey = method === 'GET' ? responseKey({ ...options, method }, token) : '';
  if (inflightKey && inflightRequests[inflightKey]) return inflightRequests[inflightKey];
  const requestCacheVersion = responseCacheVersion;

  const pending = new Promise((resolve, reject) => {
    wx.request({
      url: `${getBaseUrl()}${options.url}`,
      method,
      data: options.data || {},
      header: {
        'content-type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success(res) {
        const body = res.data || {};
        if (body.code === 0) {
          if (method === 'GET' && ttl && requestCacheVersion === responseCacheVersion) {
            writeCached(key, body.data, ttl);
          }
          if (method !== 'GET') clearResponseCache();
          resolve(body.data);
          return;
        }

        if (res.statusCode === 401) {
          clearSession();
          clearResponseCache();
          wx.reLaunch({ url: '/pages/login/login' });
          reject(new Error(body.message || '登录已过期'));
          return;
        }

        wx.showToast({ title: body.message || '请求失败', icon: 'none' });
        reject(new Error(body.message || '请求失败'));
      },
      fail(error) {
        wx.showToast({ title: '网络异常', icon: 'none' });
        reject(error);
      }
    });
  });
  if (!inflightKey) return pending;
  inflightRequests[inflightKey] = pending;
  return pending.finally(() => {
    if (inflightRequests[inflightKey] === pending) delete inflightRequests[inflightKey];
  });
}

export function uploadFile(options) {
  const token = getToken();
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${getBaseUrl()}${options.url}`,
      filePath: options.filePath,
      name: options.name || 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      formData: options.formData || {},
      success(res) {
        try {
          const body = JSON.parse(res.data || '{}');
          if (body.code === 0) {
            clearResponseCache();
            resolve(body.data);
            return;
          }
          if (res.statusCode === 401) {
            clearSession();
            clearResponseCache();
            wx.reLaunch({ url: '/pages/login/login' });
            reject(new Error(body.message || '登录已过期'));
            return;
          }
          wx.showToast({ title: body.message || '上传失败', icon: 'none' });
          reject(new Error(body.message || '上传失败'));
        } catch (error) {
          wx.showToast({ title: '解析响应失败', icon: 'none' });
          reject(error);
        }
      },
      fail(error) {
        wx.showToast({ title: '上传失败', icon: 'none' });
        reject(error);
      }
    });
  });
}
