import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/login/LoginView.vue'
import AdminLayout from '@/views/layout/AdminLayout.vue'

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
        { path: 'canteens', name: 'canteens', component: () => import('@/views/canteen/CanteensView.vue') },
        { path: 'canteens/:canteenId', name: 'canteenDetail', component: () => import('@/views/canteen/CanteenDetailView.vue') },
        { path: 'canteens/:canteenId/stalls/:stallId', name: 'stallDetail', component: () => import('@/views/canteen/StallDetailView.vue') },
        { path: 'canteens/:canteenId/stalls/:stallId/dishes/:dishId', name: 'dishDetail', component: () => import('@/views/canteen/DishDetailView.vue') },
        { path: 'users', name: 'users', component: () => import('@/views/user/UserView.vue') },
        { path: 'admins', name: 'admins', component: () => import('@/views/admin/AdminManageView.vue') },
      ],
    },
  ],
})

export default router
