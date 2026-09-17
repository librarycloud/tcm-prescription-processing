const PREFIX = 'pickup_web_admin_';

export function getStorage(key, fallback = null) {
  const storageKey = `${PREFIX}${key}`;
  const value = window.localStorage.getItem(storageKey) ?? window.sessionStorage.getItem(storageKey);
  if (value === null || value === undefined) return fallback;

  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

export function setStorage(key, value, persistent = true) {
  const storageKey = `${PREFIX}${key}`;
  const target = persistent ? window.localStorage : window.sessionStorage;
  const other = persistent ? window.sessionStorage : window.localStorage;
  other.removeItem(storageKey);
  target.setItem(storageKey, JSON.stringify(value));
}

export function removeStorage(key) {
  window.localStorage.removeItem(`${PREFIX}${key}`);
  window.sessionStorage.removeItem(`${PREFIX}${key}`);
}

export function clearAppStorage() {
  Object.keys(window.localStorage)
    .filter((key) => key.startsWith(PREFIX))
    .forEach((key) => window.localStorage.removeItem(key));
  Object.keys(window.sessionStorage)
    .filter((key) => key.startsWith(PREFIX))
    .forEach((key) => window.sessionStorage.removeItem(key));
}
