import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '运营看板' } },
        { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: '修改密码' } },
        { path: 'canteens', name: 'canteens', component: () => import('@/views/CanteensView.vue'), meta: { title: '食堂管理' } },
        { path: 'stalls', name: 'stalls', component: () => import('@/views/StallsView.vue'), meta: { title: '档口管理' } },
        { path: 'dishes', name: 'dishes', component: () => import('@/views/DishesView.vue'), meta: { title: '菜品管理' } },
        { path: 'dimensions', name: 'dimensions', component: () => import('@/views/DimensionsView.vue'), meta: { title: '属性维度管理' } },
        { path: 'banners', name: 'banners', component: () => import('@/views/BannersView.vue'), meta: { title: '首页 Banner 管理' } },
        { path: 'reviews', name: 'reviews', component: () => import('@/views/ReviewsView.vue'), meta: { title: '评价管理' } },
        { path: 'feedbacks', name: 'feedbacks', component: () => import('@/views/FeedbacksView.vue'), meta: { title: '意见反馈管理' } },
        { path: 'reports', name: 'reports', component: () => import('@/views/ReportsView.vue'), meta: { title: '举报管理' } },
        { path: 'corrections', name: 'corrections', component: () => import('@/views/CorrectionsView.vue'), meta: { title: '菜品纠错管理' } },
        { path: 'users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户管理' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!auth.isLoggedIn()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!auth.admin) {
    try {
      await auth.fetchMe()
    } catch {
      return { name: 'login' }
    }
  }
  // 强制改密：除改密页外一律拦截
  if (auth.admin?.mustChangePassword && to.name !== 'profile') {
    return { name: 'profile', query: { force: '1' } }
  }
  return true
})

export default router
