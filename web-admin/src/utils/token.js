import { getStorage, removeStorage, setStorage } from './storage';

const TOKEN_KEY = 'token';

export function getToken() {
  return getStorage(TOKEN_KEY, '');
}

export function setToken(token, persistent = true) {
  setStorage(TOKEN_KEY, token || '', persistent);
}

export function removeToken() {
  removeStorage(TOKEN_KEY);
}
