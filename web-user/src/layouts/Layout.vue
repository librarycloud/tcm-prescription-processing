<template>
  <div class="layout">
    <aside class="layout-sidebar" :class="{ collapsed }">
      <div class="brand">
        <img src="@/assets/logo.svg" alt="logo" class="brand-logo" />
        <span v-if="!collapsed" class="brand-title">取货中心</span>
      </div>

      <el-menu
        class="side-menu"
        :default-active="activePath"
        :collapse="collapsed"
        router
        unique-opened
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <template #title>{{ item.meta.title }}</template>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="layout-main">
      <header class="layout-header">
        <div class="header-left">
          <el-button text class="collapse-btn" @click="toggleCollapsed">
            <el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-entry">
            <el-avatar :size="32" :src="avatar" />
            <div class="user-meta">
              <span class="user-name">{{ displayName }}</span>
              <span class="user-role">用户</span>
            </div>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ userStore.user?.phone || '-' }}</el-dropdown-item>
              <el-dropdown-item command="profile">个人资料</el-dropdown-item>
              <el-dropdown-item command="toggleTheme">{{ themeStore.isDark ? "切换亮色" : "切换暗色" }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import avatar from '@/assets/avatar.svg';
import { useThemeStore } from "@/stores/theme";
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const themeStore = useThemeStore();
const collapsed = ref(window.innerWidth <= 1024);

const rootPath = computed(() => '/user');

const menuItems = computed(() => {
  const rootRoute = router.options.routes.find((item) => item.path === rootPath.value);
  return (rootRoute?.children || [])
    .filter((item) => !item.meta?.hiddenInMenu)
    .map((item) => ({
      ...item,
      path: `${rootPath.value}/${item.path}`.replace(/\/+/g, '/')
    }));
});

const activePath = computed(() => {
  const matched = menuItems.value.find((item) => route.path.startsWith(item.path));
  return matched?.path || route.path;
});

const breadcrumbs = computed(() =>
  route.matched.filter((item) => item.meta?.title && item.path !== rootPath.value)
);

const displayName = computed(() => userStore.user?.nickname || userStore.user?.phone || '用户');

function toggleCollapsed() {
  collapsed.value = !collapsed.value;
}

function handleResize() {
  collapsed.value = window.innerWidth <= 1024;
}

function handleCommand(command) {
  if (command === 'toggleTheme') {
    themeStore.toggleDark();
    return;
  }

  if (command === 'profile') {
    router.push('/profile');
    return;
  }

  if (command === 'logout') {
    userStore.logout();
    router.replace('/login');
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
});
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--app-bg);
}

.layout-sidebar {
  position: sticky;
  top: 0;
  z-index: 10;
  width: 232px;
  height: 100vh;
  border-right: 1px solid var(--app-border);
  background: var(--app-sidebar);
  transition: width 0.2s ease;
}

.layout-sidebar.collapsed {
  width: 72px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid var(--app-border);
  overflow: hidden;
}

.brand-logo {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
}

.brand-title {
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
}

.side-menu {
  height: calc(100vh - 64px);
  border-right: 0;
}

.layout-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.layout-header {
  position: sticky;
  top: 0;
  z-index: 9;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid var(--app-border);
  background: var(--el-bg-color);
}

.header-left {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 14px;
}

.collapse-btn {
  font-size: 20px;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.2;
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
}

.user-role {
  color: var(--app-muted);
  font-size: 12px;
}

.content {
  flex: 1;
  padding: 24px;
}

@media (max-width: 768px) {
  .layout-sidebar {
    width: 72px;
  }

  .brand {
    padding: 0 18px;
  }

  .brand-title,
  .user-meta {
    display: none;
  }

  .layout-header {
    padding: 0 14px;
  }

  .content {
    padding: 16px;
  }
}
</style>
