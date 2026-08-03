import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';

const routes = [
  {
    path: '/',
    redirect: () => {
      const userStore = useUserStore();
      return userStore.isLoggedIn ? userStore.homePath : '/login';
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/admin',
    component: () => import('@/layouts/Layout.vue'),
    redirect: '/admin/dashboard',
    meta: { requiresAuth: true, allowedRoles: [0, 2] },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '首页', icon: 'DataAnalysis' }
      },
      {
        path: 'packages',
        name: 'AdminPackages',
        component: () => import('@/views/admin/Packages.vue'),
        meta: { title: '包裹管理', icon: 'Box' }
      },
      {
        path: 'packages/add',
        name: 'PackageAdd',
        component: () => import('@/views/admin/PackageAdd.vue'),
        meta: { title: '新增包裹', icon: 'Plus', hiddenInMenu: true }
      },
      {
        path: 'packages/:id',
        name: 'AdminPackageDetail',
        component: () => import('@/views/admin/PackageDetail.vue'),
        meta: { title: '包裹详情', hiddenInMenu: true }
      },
      {
        path: 'packages/edit/:id',
        name: 'PackageEdit',
        component: () => import('@/views/admin/PackageEdit.vue'),
        meta: { title: '编辑包裹', hiddenInMenu: true }
      },
      {
        path: 'verify',
        name: 'PackageVerify',
        component: () => import('@/views/admin/Verify.vue'),
        meta: { title: '核销', icon: 'CircleCheck', hiddenInMenu: true }
      },
      {
        path: 'prescriptions',
        name: 'Prescriptions',
        component: () => import('@/views/admin/Prescriptions.vue'),
        meta: { title: '处方管理', icon: 'Tickets' }
      },
      {
        path: 'e6-imports',
        name: 'E6Imports',
        component: () => import('@/views/admin/E6Imports.vue'),
        meta: { title: 'E6导入', icon: 'Download' }
      },
      {
        path: 'prescriptions/:id',
        name: 'PrescriptionDetail',
        component: () => import('@/views/admin/PrescriptionDetail.vue'),
        meta: { title: '处方详情', hiddenInMenu: true }
      },
      {
        path: 'processing-plans',
        name: 'ProcessingPlans',
        component: () => import('@/views/admin/ProcessingPlans.vue'),
        meta: { title: '加工工作台', icon: 'Calendar' }
      },
      {
        path: 'store-transfers',
        name: 'StoreTransfers',
        component: () => import('@/views/admin/StoreTransfers.vue'),
        meta: { title: '门店调拨', icon: 'Sort' }
      },
      {
        path: 'product-differences',
        name: 'ProductDifferences',
        component: () => import('@/views/admin/ProductDifferences.vue'),
        meta: { title: '库存差异', icon: 'Notebook' }
      },
      {
        path: 'herb-locations',
        name: 'HerbLocations',
        component: () => import('@/views/admin/HerbLocations.vue'),
        meta: { title: '斗谱管理', icon: 'Grid', allowedRoles: [0, 2] }
      },
      {
        path: 'ready-pickup',
        name: 'ReadyPickup',
        redirect: { name: 'ProcessingPlans', query: { mode: 'pickup' } },
        meta: { title: '待领取', hiddenInMenu: true }
      },
      {
        path: 'basic-data',
        name: 'BasicData',
        component: () => import('@/views/admin/BasicData.vue'),
        meta: { title: '基础资料', icon: 'Collection', group: 'system', allowedRoles: [0] }
      },
      {
        path: 'products',
        name: 'Products',
        component: () => import('@/views/admin/Products.vue'),
        meta: { title: '货品项目', icon: 'Goods', group: 'system', allowedRoles: [0, 2] }
      },
      {
        path: 'profile',
        name: 'StoreAdminProfile',
        component: () => import('@/views/profile/Profile.vue'),
        meta: { title: '个人中心', icon: 'UserFilled', allowedRoles: [2] }
      },
      {
        path: 'login-logs',
        name: 'LoginLogs',
        component: () => import('@/views/admin/LoginLogs.vue'),
        meta: { title: '登录日志', icon: 'Document', group: 'system', allowedRoles: [0] }
      },
      {
        path: 'operation-logs',
        name: 'OperationLogs',
        component: () => import('@/views/admin/OperationLogs.vue'),
        meta: { title: '操作日志', icon: 'DocumentChecked', group: 'system', allowedRoles: [0] }
      },
      {
        path: 'sms-settings',
        name: 'SmsSettings',
        component: () => import('@/views/admin/SmsSettings.vue'),
        meta: { title: '短信设置', icon: 'Message', group: 'system', allowedRoles: [0] }
      },
      {
        path: 'email-settings',
        name: 'EmailSettings',
        component: () => import('@/views/admin/EmailSettings.vue'),
        meta: { title: '邮件设置', icon: 'MessageBox', group: 'system', allowedRoles: [0] }
      },
      {
        path: 'robot-notifications',
        name: 'RobotNotifications',
        component: () => import('@/views/admin/RobotNotifications.vue'),
        meta: { title: '群机器人通知', icon: 'Bell', group: 'system', allowedRoles: [0, 2] }
      },
      {
        path: 'print-templates',
        name: 'PrintTemplates',
        component: () => import('@/views/admin/PrintTemplates.vue'),
        meta: { title: '打印设置', icon: 'Printer', group: 'system', allowedRoles: [0, 2] }
      },
      {
        path: 'stores',
        name: 'Stores',
        component: () => import('@/views/admin/Stores.vue'),
        meta: { title: '门店管理', icon: 'OfficeBuilding', group: 'system', allowedRoles: [0, 2] }
      },
      {
        path: 'store-admins',
        name: 'StoreAdmins',
        component: () => import('@/views/admin/StoreAdmins.vue'),
        meta: { title: '门店管理员', icon: 'Avatar', group: 'system', allowedRoles: [0] }
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/Users.vue'),
        meta: { title: '用户管理', icon: 'User', group: 'system', allowedRoles: [0, 2] }
      }
    ]
  },
  {
    path: '/profile',
    component: () => import('@/layouts/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Profile',
        component: () => import('@/views/profile/Profile.vue'),
        meta: { title: '个人资料', hiddenInMenu: true }
      }
    ]
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/404/NotFound.vue'),
    meta: { public: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
});

router.beforeEach((to) => {
  const userStore = useUserStore();

  if (to.meta.public) {
    if (to.path === '/login' && userStore.isLoggedIn) {
      if (userStore.isManager) return userStore.homePath;
      userStore.logout();
    }
    return true;
  }

  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  if (!userStore.isManager) {
    userStore.logout();
    return '/login';
  }

  const allowedRoles = [...to.matched].reverse().find((record) => record.meta.allowedRoles)
    ?.meta.allowedRoles;
  if (allowedRoles && !allowedRoles.includes(Number(userStore.user?.role))) {
    return userStore.homePath;
  }
  return true;
});

export default router;
