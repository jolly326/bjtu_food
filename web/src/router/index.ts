import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/login/LoginView.vue'
import AdminLayout from '@/views/layout/AdminLayout.vue'
import { userApi } from '@/api'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: LoginView },
    {
      path: '/dashboard',
      component: AdminLayout,
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
        { path: 'content', name: 'contentManage', component: () => import('@/views/content/ContentManageView.vue') },
        { path: 'audit', name: 'auditManage', component: () => import('@/views/audit/AuditManageView.vue') },
        { path: 'system', name: 'systemManage', component: () => import('@/views/system/SystemManageView.vue') },
        { path: 'canteens/:canteenId', name: 'canteenDetail', component: () => import('@/views/canteen/CanteenDetailView.vue') },
        { path: 'canteens/:canteenId/stalls/:stallId', name: 'stallDetail', component: () => import('@/views/canteen/StallDetailView.vue') },
        { path: 'account', name: 'account', component: () => import('@/views/admin/AccountSettingsView.vue') },
      ],
    },
  ],
})

// 全局前置守卫：401 引导登录；仅 ADMIN / SUPER_ADMIN 可进后台（super_admin 也能进，且可管理管理员）
const isBackendRole = (role?: string) => role === 'admin' || role === 'super_admin'

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    // 已登录且为后台角色时，访问登录页直接进后台
    if (token) {
      try {
        const me = await userApi.getProfile()
        if (isBackendRole(me?.role)) return '/dashboard'
      } catch { /* ignore */ }
    }
    return true
  }

  if (!token) return { path: '/login' }

  try {
    const me = await userApi.getProfile()
    if (!isBackendRole(me?.role)) {
      // 非后台角色禁止进入
      return { path: '/login' }
    }
    return true
  } catch (e: any) {
    // 401 / 其他错误：清除登录态并跳登录
    localStorage.removeItem('token')
    return { path: '/login' }
  }
})

export default router
