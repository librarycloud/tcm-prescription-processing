import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { login as loginApi } from '@/api/login';
import { getProfile } from '@/api/user';
import {
  getHomePath,
  isManager as checkIsManager,
  isStoreAdmin as checkIsStoreAdmin,
  isSuperAdmin as checkIsSuperAdmin
} from '@/utils/permission';
import { getStorage, removeStorage, setStorage } from '@/utils/storage';
import { getToken, removeToken, setToken } from '@/utils/token';

const USER_KEY = 'user';

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken());
  const user = ref(getStorage(USER_KEY, null));

  const isLoggedIn = computed(() => Boolean(token.value));
  const isManager = computed(() => checkIsManager(user.value));
  const isSuperAdmin = computed(() => checkIsSuperAdmin(user.value));
  const isStoreAdmin = computed(() => checkIsStoreAdmin(user.value));
  const homePath = computed(() => getHomePath(user.value));

  function persist(authData, persistent = true) {
    if (!authData?.token || !authData?.user) {
      throw new Error('登录响应缺少 token 或用户信息');
    }

    token.value = authData.token || '';
    user.value = authData.user || null;
    setToken(token.value, persistent);
    setStorage(USER_KEY, user.value, persistent);
  }

  async function login(payload, persistent = true) {
    const authData = await loginApi(payload);
    persist(authData, persistent);
    return authData;
  }

  async function refreshProfile() {
    if (!token.value) return null;
    const profile = await getProfile();
    user.value = profile;
    setStorage(USER_KEY, profile);
    return profile;
  }

  function updateAuth(authData) {
    persist(authData);
  }

  function logout() {
    token.value = '';
    user.value = null;
    removeToken();
    removeStorage(USER_KEY);
  }

  return {
    token,
    user,
    isLoggedIn,
    isManager,
    isSuperAdmin,
    isStoreAdmin,
    homePath,
    login,
    refreshProfile,
    updateAuth,
    logout
  };
});
