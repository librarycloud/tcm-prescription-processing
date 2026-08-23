<template>
  <div class="layout">
    <div
      v-if="isMobile && mobileMenuOpen"
      class="mobile-menu-mask"
      @click="mobileMenuOpen = false"
    />
    <aside
      class="layout-sidebar"
      :class="{
        collapsed: collapsed && !isMobile,
        'mobile-open': isMobile && mobileMenuOpen
      }"
    >
      <div class="brand">
        <img src="@/assets/logo.svg" alt="logo" class="brand-logo" />
        <span v-if="!collapsed" class="brand-title">中药处方加工</span>
      </div>

      <el-menu
        class="side-menu"
        :default-active="activePath"
        :collapse="collapsed && !isMobile"
        router
        unique-opened
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in primaryMenuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.meta.title }}</template>
        </el-menu-item>
        <el-sub-menu v-if="systemMenuItems.length" index="system-management">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item v-for="item in systemMenuItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.meta.title }}</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </aside>

    <div class="layout-main">
      <header class="layout-header">
        <div class="header-left">
          <el-button
            text
            class="collapse-btn"
            :aria-label="isMobile ? '打开导航菜单' : '折叠导航菜单'"
            @click="toggleCollapsed"
          >
            <el-icon>
              <Menu v-if="isMobile" />
              <Expand v-else-if="collapsed" />
              <Fold v-else />
            </el-icon>
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
              <span class="user-role">{{ roleLabel }}</span>
            </div>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ userStore.user?.phone || '-' }}</el-dropdown-item>
              <el-dropdown-item command="profile">个人资料</el-dropdown-item>
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
import {
  ArrowDown,
  Avatar,
  Bell,
  Box,
  Calendar,
  CircleCheck,
  Collection,
  DataAnalysis,
  Document,
  DocumentChecked,
  Download,
  Expand,
  Finished,
  Fold,
  Grid,
  Goods,
  Message,
  MessageBox,
  Menu,
  Monitor,
  OfficeBuilding,
  Notebook,
  Printer,
  Setting,
  Sort,
  Tickets,
  User,
  UserFilled
} from '@element-plus/icons-vue';
import avatar from '@/assets/avatar.svg';
import { useUserStore } from '@/stores/user';
import { logout as logoutApi } from '@/api/login';
import { roleText } from '@/utils/permission';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const isMobile = ref(window.innerWidth <= 768);
const collapsed = ref(window.innerWidth <= 1024 && window.innerWidth > 768);
const mobileMenuOpen = ref(false);
const menuIcons = {
  Avatar,
  Bell,
  Box,
  Calendar,
  CircleCheck,
  Collection,
  DataAnalysis,
  Document,
  DocumentChecked,
  Download,
  Finished,
  Grid,
  Goods,
  Message,
  MessageBox,
  Monitor,
  OfficeBuilding,
  Notebook,
  Printer,
  Sort,
  Tickets,
  User,
  UserFilled
};

const rootPath = computed(() => '/admin');

const menuItems = computed(() => {
  const rootRoute = router.options.routes.find((item) => item.path === rootPath.value);
  return (rootRoute?.children || [])
    .filter(
      (item) =>
        !item.meta?.hiddenInMenu &&
        (!item.meta?.allowedRoles || item.meta.allowedRoles.includes(Number(userStore.user?.role)))
    )
    .map((item) => ({
      ...item,
      icon: menuIcons[item.meta.icon],
      path: `${rootPath.value}/${item.path}`.replace(/\/+/g, '/')
    }));
});

const primaryMenuItems = computed(() => {
  const order = [
    'dashboard',
    'prescriptions',
    'e6-imports',
    'processing-plans',
    'product-differences',
    'e6-pharmacy-products',
    'yd-goods-checks',
    'store-transfers',
    'herb-locations',
    'packages',
    'profile'
  ];
  return menuItems.value
    .filter((item) => item.meta.group !== 'system')
    .sort(
      (left, right) =>
        order.indexOf(left.path.split('/').pop()) - order.indexOf(right.path.split('/').pop())
    );
});
const systemMenuItems = computed(() => {
  const order = [
    'stores',
    'store-admins',
    'products',
    'basic-data',
    'users',
    'login-logs',
    'operation-logs',
    'sms-settings',
    'email-settings',
    'robot-notifications',
    'processing-equipment',
    'print-templates'
  ];
  return menuItems.value
    .filter((item) => item.meta.group === 'system')
    .sort(
      (left, right) =>
        order.indexOf(left.path.split('/').pop()) - order.indexOf(right.path.split('/').pop())
    );
});

const activePath = computed(() => {
  const matched = menuItems.value.find((item) => route.path.startsWith(item.path));
  return matched?.path || route.path;
});

const breadcrumbs = computed(() =>
  route.matched.filter((item) => item.meta?.title && item.path !== rootPath.value)
);

const displayName = computed(() => userStore.user?.nickname || userStore.user?.phone || '用户');
const roleLabel = computed(() => roleText(userStore.user));

function toggleCollapsed() {
  if (isMobile.value) {
    mobileMenuOpen.value = !mobileMenuOpen.value;
    return;
  }
  collapsed.value = !collapsed.value;
}

function handleResize() {
  isMobile.value = window.innerWidth <= 768;
  mobileMenuOpen.value = false;
  collapsed.value = window.innerWidth <= 1024 && !isMobile.value;
}

function handleMenuSelect() {
  if (isMobile.value) mobileMenuOpen.value = false;
}

async function handleCommand(command) {
  if (command === 'profile') {
    router.push(userStore.isStoreAdmin ? '/admin/profile' : '/profile');
    return;
  }

  if (command === 'logout') {
    try {
      await logoutApi();
    } finally {
      userStore.logout();
      router.replace('/login');
    }
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
  width: 186px;
  height: 100vh;
  overflow: hidden;
  border-right: 1px solid var(--app-border);
  background: var(--app-sidebar);
  transition: width 0.2s ease;
}

.layout-sidebar.collapsed {
  width: 58px;
}

.layout-sidebar.collapsed .brand {
  padding: 0 11px;
}

.mobile-menu-mask {
  position: fixed;
  z-index: 1000;
  inset: 0;
  background: rgb(15 23 42 / 42%);
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
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.side-menu {
  height: calc(100vh - 64px);
  overflow-y: auto;
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
  background: #ffffff;
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
    position: fixed;
    z-index: 1001;
    width: min(224px, 84vw);
    transform: translateX(-100%);
    box-shadow: 8px 0 24px rgb(15 23 42 / 14%);
    transition: transform 0.2s ease;
  }

  .layout-sidebar.mobile-open {
    transform: translateX(0);
  }

  .brand {
    padding: 0 18px;
  }

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
