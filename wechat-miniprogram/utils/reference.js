/**
 * ReferenceDataManager
 * 统一管理低频变化的参考数据（门店、医生、数据字典），在小程序运行期间内存缓存，
 * 避免各页面 onLoad 时发起重复的并发请求。
 *
 * 用法：
 *   import { loadRefData } from '../../../utils/reference';
 *   const { doctors, sources, stores } = await loadRefData(['doctors', 'sources', 'stores']);
 *
 * 如需强制刷新（如管理员新增了医生），调用：
 *   import { invalidateRefData } from '../../../utils/reference';
 *   invalidateRefData(); // 清除所有缓存
 *   invalidateRefData(['doctors']); // 只清 doctors
 */

import { getDictionaries, getDoctors, getStores, getTransferStores, getHerbLocationStores } from '../api/admin';

// 内存中的缓存结构: { key: Promise | resolvedValue }
const _cache = {};
// 当前用户身份（isSuperAdmin 影响 stores 参数）
let _isSuperAdmin = false;

/**
 * 声明各 key 的加载函数
 * 注意：stores 依赖 isSuperAdmin，通过 getters 延迟求值。
 */
const LOADERS = {
  doctors: () => getDoctors(),
  stores: () => getStores({ page: 1, pageSize: 100, status: 1 }).then((data) => data?.list || []),
  transferStores: () => getTransferStores(),
  herbLocationStores: () => getHerbLocationStores(),
  sources: () => getDictionaries('PrescriptionSource'),
  processTypes: () => getDictionaries('ProcessType'),
  notifyTypes: () => getDictionaries('NotifyType'),
  processEquipment: () => getDictionaries('ProcessEquipment'),
};

/**
 * 设置当前用户是否为超级管理员（会影响 stores 数据）。
 * 建议在 app.js 登录成功后或各页面 onShow 首行调用。
 */
export function setRefDataUserRole(isSuperAdmin) {
  if (_isSuperAdmin !== Boolean(isSuperAdmin)) {
    _isSuperAdmin = Boolean(isSuperAdmin);
    // 角色变了需要重置 stores 缓存，其他数据无影响
    delete _cache.stores;
  }
}

/**
 * 加载指定的参考数据 key 列表，返回 { [key]: data } 的对象。
 * 已缓存的 key 直接从内存返回，未缓存的并发发起请求。
 *
 * @param {string[]} keys - 需要的数据 key 列表，如 ['doctors', 'stores']
 * @returns {Promise<Object>}
 */
export async function loadRefData(keys = []) {
  const pending = keys.map((key) => {
    if (!LOADERS[key]) {
      console.warn(`[ReferenceDataManager] Unknown key: "${key}"`);
      return Promise.resolve([]);
    }
    if (!_cache[key]) {
      // 存储 Promise，防止同一 key 并发重复发起请求
      _cache[key] = LOADERS[key]().catch((err) => {
        delete _cache[key]; // 请求失败时清除，下次允许重试
        throw err;
      });
    }
    return _cache[key];
  });
  const results = await Promise.all(pending);
  return Object.fromEntries(keys.map((key, i) => [key, results[i]]));
}

/**
 * 清除参考数据缓存。
 * @param {string[]} [keys] - 不传则清除全部
 */
export function invalidateRefData(keys) {
  if (!keys) {
    Object.keys(_cache).forEach((key) => delete _cache[key]);
  } else {
    keys.forEach((key) => delete _cache[key]);
  }
}
