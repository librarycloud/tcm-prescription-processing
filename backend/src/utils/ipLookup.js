import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import IPDB from 'ipdb';

let database = null;
const defaultDatabasePath = fileURLToPath(new URL('../../data/qqwry.ipdb', import.meta.url));

function normalizeIp(value) {
  const ip = String(value || '')
    .split(',')[0]
    .trim();
  return ip.startsWith('::ffff:') ? ip.slice(7) : ip;
}

function isPrivateIp(ip) {
  return (
    ip === '::1' ||
    ip === '127.0.0.1' ||
    ip.startsWith('10.') ||
    ip.startsWith('192.168.') ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(ip) ||
    ip.startsWith('fc') ||
    ip.startsWith('fd') ||
    ip.startsWith('fe80:')
  );
}

export function initializeIpLookup(databasePath = '') {
  const resolvedPath = databasePath ? resolve(databasePath) : defaultDatabasePath;
  const buffer = readFileSync(resolvedPath);
  database = new IPDB(buffer);
  return {
    lookup: lookupIp,
    normalize: normalizeIp,
    databasePath: resolvedPath,
    databaseSize: buffer.byteLength
  };
}

export function lookupIp(value) {
  const ip = normalizeIp(value);
  if (!ip) return { ip: '', country: '', province: '', city: '', isp: '' };
  if (isPrivateIp(ip)) {
    return { ip, country: '本地网络', province: '本地网络', city: '', isp: '内网' };
  }
  if (!database) throw new Error('IP 数据库尚未初始化');

  let result;
  try {
    result = database.find(ip);
  } catch {
    return { ip, country: '', province: '', city: '', isp: '' };
  }
  if (!result || result.code !== 0 || !result.data) {
    return { ip, country: '', province: '', city: '', isp: '' };
  }

  return {
    ip,
    country: result.data.country_name || '',
    province: result.data.region_name || '',
    city: result.data.city_name || '',
    isp: result.data.isp_domain || ''
  };
}
