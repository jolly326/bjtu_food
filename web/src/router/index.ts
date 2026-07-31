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
        { path: '', redirect: { name: 'feedback' } },
        { path: 'canteens', name: 'canteens', component: () => import('@/views/canteen/CanteensView.vue') },
        { path: 'dishes', name: 'dishManage', component: () => import('@/views/canteen/DishManageView.vue') },
        { path: 'canteens/:canteenId', name: 'canteenDetail', component: () => import('@/views/canteen/CanteenDetailView.vue') },
        { path: 'canteens/:canteenId/stalls/:stallId', name: 'stallDetail', component: () => import('@/views/canteen/StallDetailView.vue') },
        { path: 'canteens/:canteenId/stalls/:stallId/dishes/:dishId', name: 'dishDetail', component: () => import('@/views/canteen/DishDetailView.vue') },
        { path: 'reviews', name: 'reviews', component: () => import('@/views/admin/ApplyReviewView.vue') },
        { path: 'dish-review', redirect: '/dashboard/reviews' },
        { path: 'stall-review', redirect: '/dashboard/reviews' },
        { path: 'canteen-review', redirect: '/dashboard/reviews' },
        { path: 'review-review', redirect: '/dashboard/reviews' },
        { path: 'users', name: 'users', component: () => import('@/views/user/UserView.vue') },
        { path: 'admins', name: 'admins', component: () => import('@/views/admin/AdminManageView.vue') },
        { path: 'account', name: 'account', component: () => import('@/views/admin/AccountSettingsView.vue') },
        { path: 'banners', name: 'banners', component: () => import('@/views/banner/BannerManageView.vue') },
        { path: 'moments', name: 'momentManage', component: () => import('@/views/admin/MomentManageView.vue') },
        { path: 'feedbacks', name: 'feedback', component: () => import('@/views/admin/FeedbackView.vue') },
        { path: 'operation-logs', name: 'operationLog', component: () => import('@/views/admin/OperationLogView.vue') },
      ],
    },
  ],
})

// 全局前置守卫：401 引导登录；非 ADMIN 禁入后台
router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    // 已登录且为 ADMIN 时，访问登录页直接进后台
    if (token) {
      try {
        const me = await userApi.getProfile()
        if (me?.role === 'admin') return '/dashboard/feedbacks'
      } catch { /* ignore */ }
    }
    return true
  }

  if (!token) return { path: '/login' }

  try {
    const me = await userApi.getProfile()
    if (me?.role !== 'admin') {
      // 非 ADMIN 角色禁止进入后台
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
