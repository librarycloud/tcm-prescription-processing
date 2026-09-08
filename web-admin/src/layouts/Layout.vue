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

        <div class="header-right">
          <el-tooltip content="小程序码与 Android App 下载" placement="bottom">
            <el-button text class="client-entry-btn" @click="openClientDisplayModal">
              <el-icon><Cellphone /></el-icon>
              <span v-if="!isMobile" class="client-btn-text">客户端</span>
            </el-button>
          </el-tooltip>

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
                <el-dropdown-item command="theme-light">
                  <span class="theme-item">
                    <span>🌞 亮色</span>
                    <el-icon v-if="themeStore.themeMode === 'light'" class="theme-check"><Check /></el-icon>
                  </span>
                </el-dropdown-item>
                <el-dropdown-item command="theme-dark">
                  <span class="theme-item">
                    <span>🌙 暗色</span>
                    <el-icon v-if="themeStore.themeMode === 'dark'" class="theme-check"><Check /></el-icon>
                  </span>
                </el-dropdown-item>
                <el-dropdown-item command="theme-system">
                  <span class="theme-item">
                    <span>🖥 跟随系统</span>
                    <el-icon v-if="themeStore.themeMode === 'system'" class="theme-check"><Check /></el-icon>
                  </span>
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>

    <!-- 客户端下载与小程序弹窗 -->
    <el-dialog
      v-model="clientDisplayVisible"
      title="移动端接入与下载"
      width="700px"
      append-to-body
      destroy-on-close
      class="client-modal"
    >
      <div v-loading="clientInfoLoading" class="client-modal-body">
        <el-alert
          v-if="clientInfo?.announcement"
          :title="clientInfo.announcement"
          type="info"
          show-icon
          :closable="false"
          class="modal-announcement"
        />

        <div class="client-columns">
          <!-- 微信小程序卡片 -->
          <div class="client-col">
            <div class="col-header">
              <span class="col-title">{{ clientInfo?.wechat?.appName || '微信小程序' }}</span>
              <el-tag size="small" type="success">小程序</el-tag>
            </div>
            <div class="qr-container">
              <el-image
                v-if="clientInfo?.wechat?.qrcodeUrl"
                :src="clientInfo.wechat.qrcodeUrl"
                class="modal-qr-img"
                fit="contain"
                :preview-src-list="[clientInfo.wechat.qrcodeUrl]"
              />
              <el-empty
                v-else
                description="暂未上传小程序码"
                :image-size="80"
              />
            </div>
            <p class="qr-hint">微信扫一扫即可快速使用</p>
            <p v-if="clientInfo?.wechat?.appId" class="qr-subhint">AppID: {{ clientInfo.wechat.appId }}</p>
          </div>

          <!-- 分割线 -->
          <div class="col-divider" />

          <!-- Android APK 卡片 -->
          <div class="client-col">
            <div class="col-header">
              <span class="col-title">{{ clientInfo?.android?.displayName || 'Android 客户端' }}</span>
              <el-tag v-if="clientInfo?.android?.versionName" size="small" type="primary">
                v{{ clientInfo.android.versionName }}
              </el-tag>
            </div>
            <div class="qr-container">
              <img
                v-if="androidQrDataUrl"
                :src="androidQrDataUrl"
                class="modal-qr-img"
                alt="APK下载二维码"
              />
              <el-empty
                v-else-if="!clientInfo?.android?.downloadUrl"
                description="暂无下载链接"
                :image-size="80"
              />
              <div v-else class="qr-loading">生成二维码中...</div>
            </div>
            <p class="qr-hint">手机扫码或点击下方按钮下载</p>

            <div class="apk-actions">
              <el-button
                v-if="clientInfo?.android?.downloadUrl"
                type="primary"
                :icon="Download"
                @click="downloadApk(clientInfo.android.downloadUrl)"
              >
                直接下载 APK
              </el-button>
            </div>

            <div v-if="clientInfo?.android?.size" class="apk-meta">
              <span>大小：{{ formatApkSize(clientInfo.android.size) }}</span>
              <span v-if="clientInfo.android.publishedAt">发布日期：{{ clientInfo.android.publishedAt }}</span>
            </div>

            <div v-if="clientInfo?.android?.releaseNotes?.length" class="modal-notes-box">
              <div class="notes-caption">最新版本更新说明：</div>
              <ul class="notes-items">
                <li v-for="(item, i) in clientInfo.android.releaseNotes" :key="i">{{ item }}</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
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
  Cellphone,
  Check,
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
import QRCode from 'qrcode';
import avatar from '@/assets/avatar.svg';
import { useThemeStore } from "@/stores/theme";
import { useUserStore } from '@/stores/user';
import { logout as logoutApi } from '@/api/login';
import { getClientDisplayInfo } from '@/api/clientDisplay';
import { roleText } from '@/utils/permission';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const themeStore = useThemeStore();
const isMobile = ref(window.innerWidth <= 768);
const collapsed = ref(window.innerWidth <= 1024 && window.innerWidth > 768);
const mobileMenuOpen = ref(false);
const menuIcons = {
  Avatar,
  Bell,
  Box,
  Calendar,
  Cellphone,
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
    'print-templates',
    'client-display'
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
  if (command === 'theme-light') {
    themeStore.setThemeMode('light');
    return;
  }

  if (command === 'theme-dark') {
    themeStore.setThemeMode('dark');
    return;
  }

  if (command === 'theme-system') {
    themeStore.setThemeMode('system');
    return;
  }

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

const clientDisplayVisible = ref(false);
const clientInfoLoading = ref(false);
const clientInfo = ref(null);
const androidQrDataUrl = ref('');

async function openClientDisplayModal() {
  clientDisplayVisible.value = true;
  clientInfoLoading.value = true;
  try {
    const res = await getClientDisplayInfo();
    clientInfo.value = res;
    if (res?.android?.downloadUrl) {
      androidQrDataUrl.value = await QRCode.toDataURL(res.android.downloadUrl, {
        width: 160,
        margin: 1,
        color: { dark: '#000000', light: '#ffffff' }
      });
    } else {
      androidQrDataUrl.value = '';
    }
  } catch (err) {
    console.error('Failed to load client display info', err);
  } finally {
    clientInfoLoading.value = false;
  }
}

function downloadApk(url) {
  if (!url) return;
  window.open(url, '_blank');
}

function formatApkSize(bytes) {
  if (!bytes) return '';
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
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

.theme-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 24px;
}

.theme-check {
  color: var(--el-color-primary);
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.client-entry-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  padding: 6px 10px;
  border-radius: 6px;
  color: var(--el-text-color-regular);
}

.client-entry-btn:hover {
  color: var(--el-color-primary);
  background-color: var(--el-fill-color-light);
}

.client-btn-text {
  font-size: 13px;
}

.client-modal-body {
  padding: 8px 0;
}

.modal-announcement {
  margin-bottom: 20px;
}

.client-columns {
  display: flex;
  align-items: stretch;
  gap: 20px;
}

.client-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 20px 16px;
}

.col-divider {
  width: 1px;
  background: var(--el-border-color-light);
}

.col-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.col-title {
  font-size: 15px;
  font-weight: 600;
}

.qr-container {
  width: 170px;
  height: 170px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px;
  background: #fff;
  margin-bottom: 12px;
}

.modal-qr-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.qr-loading {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.qr-hint {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin: 0 0 4px;
}

.qr-subhint {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin: 0;
}

.apk-actions {
  margin-top: 10px;
}

.apk-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}

.modal-notes-box {
  margin-top: 14px;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  width: 100%;
  box-sizing: border-box;
  text-align: left;
  max-height: 130px;
  overflow-y: auto;
}

.notes-caption {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--el-text-color-primary);
}

.notes-items {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
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
