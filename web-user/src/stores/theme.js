import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';
import { getStorage, setStorage } from '@/utils/storage';

const THEME_STORAGE_KEY = 'theme_mode';

export const useThemeStore = defineStore('theme', () => {
  const themeMode = ref(getStorage(THEME_STORAGE_KEY, 'system'));
  const systemDark = ref(
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(prefers-color-scheme: dark)').matches
      : false
  );

  const isDark = computed(() => {
    if (themeMode.value === 'dark') return true;
    if (themeMode.value === 'light') return false;
    return systemDark.value;
  });

  function applyTheme() {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;
    if (isDark.value) {
      root.classList.add('dark');
      root.setAttribute('data-theme', 'dark');
      root.style.colorScheme = 'dark';
    } else {
      root.classList.remove('dark');
      root.removeAttribute('data-theme');
      root.style.colorScheme = 'light';
    }
  }

  function setThemeMode(mode) {
    if (!['light', 'dark', 'system'].includes(mode)) return;
    themeMode.value = mode;
    setStorage(THEME_STORAGE_KEY, mode);
    applyTheme();
  }

  function toggleDark() {
    setThemeMode(isDark.value ? 'light' : 'dark');
  }

  function initTheme() {
    if (typeof window === 'undefined') return;
    if (window.matchMedia) {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      systemDark.value = mediaQuery.matches;
      mediaQuery.addEventListener('change', (e) => {
        systemDark.value = e.matches;
        if (themeMode.value === 'system') {
          applyTheme();
        }
      });
    }
    applyTheme();
  }

  watch(isDark, () => {
    applyTheme();
  });

  return {
    themeMode,
    isDark,
    setThemeMode,
    toggleDark,
    initTheme,
    applyTheme
  };
});
