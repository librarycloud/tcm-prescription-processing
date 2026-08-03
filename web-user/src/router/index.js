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
    path: '/user',
    component: () => import('@/layouts/Layout.vue'),
    redirect: '/user/packages',
    meta: { requiresAuth: true, role: 'user' },
    children: [
      {
        path: 'packages',
        name: 'UserPackages',
        component: () => import('@/views/user/UserHome.vue'),
        meta: { title: '我的包裹', icon: 'Tickets' }
      },
      {
        path: 'packages/:id',
        name: 'UserPackageDetail',
        component: () => import('@/views/user/UserPackageDetail.vue'),
        meta: { title: '包裹详情', hiddenInMenu: true }
      }
    ]
  },
  {
    path: '/profile',
    component: () => import('@/layouts/Layout.vue'),
    meta: { requiresAuth: true, role: 'user' },
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

function getUserRedirectTarget(redirect) {
  const target = Array.isArray(redirect) ? redirect[0] : redirect;
  return typeof target === 'string' && (target.startsWith('/user') || target === '/profile')
    ? target
    : '/user/packages';
}

router.beforeEach((to) => {
  const userStore = useUserStore();

  if (to.meta.public) {
    if (to.path === '/login' && userStore.isLoggedIn) {
      if (userStore.isRegularUser) return userStore.homePath;
      userStore.logout();
    }
    return true;
  }

  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  if (!userStore.isRegularUser) {
    userStore.logout();
    return '/login';
  }

  const role = to.matched.find((record) => record.meta.role)?.meta.role;
  if (role === 'user') return true;

  return getUserRedirectTarget(to.fullPath);
});

export { getUserRedirectTarget };
export default router;
