import { createRouter, createWebHashHistory } from 'vue-router'
import { readToken } from '@/api/request'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/canteens',
    children: [
      { path: 'canteens', name: 'canteens', component: () => import('@/views/CanteenView.vue') },
      { path: 'stalls', name: 'stalls', component: () => import('@/views/StallView.vue') },
      { path: 'dishes', name: 'dishes', component: () => import('@/views/DishView.vue') },
      { path: 'issues', name: 'issues', component: () => import('@/views/IssueView.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

/** 全局守卫：无 token 一律跳登录页（登录页本身除外）。 */
router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!readToken()) return { name: 'login' }
  return true
})

export default router
