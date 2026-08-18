import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/login/LoginView.vue'
import AdminLayout from '@/views/layout/AdminLayout.vue'
import { userApi } from '@/api'
import { useUserStore } from '@/stores/userStore'
import { ElMessage } from 'element-plus'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
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
        { path: 'system', name: 'systemManage', component: () => import('@/views/system/SystemManageView.vue'), meta: { role: 'super_admin' } },
        { path: 'canteens/:canteenId', name: 'canteenDetail', component: () => import('@/views/canteen/CanteenDetailView.vue') },
        { path: 'canteens/:canteenId/stalls/:stallId', name: 'stallDetail', component: () => import('@/views/canteen/StallDetailView.vue') },
        { path: 'canteens/:canteenId/stalls/:stallId/dishes/:dishId', name: 'dishDetail', component: () => import('@/views/canteen/DishDetailView.vue') },
        { path: 'account', name: 'account', component: () => import('@/views/admin/AccountSettingsView.vue') },
      ],
    },
  ],
})

// 全局前置守卫：401 引导登录；仅 ADMIN / SUPER_ADMIN 可进后台（M11 守卫缓存）
const isBackendRole = (role?: string) => role === 'admin' || role === 'super_admin'
const isSuperAdmin = (role?: string) => role === 'super_admin'

/**
 * 读取当前角色：优先读 userStore 缓存（登录后回填），缺失或 401 才回源 getProfile（M11）。
 * 返回 { role, fromCache, me? }，便于调用方判断是否需清理登录态。
 */
async function resolveRole(): Promise<{ role: string; fresh: boolean }> {
  const userStore = useUserStore()
  // 缓存命中且非 401 触发：直接复用，避免每次进路由都发 getProfile
  if (userStore.role) return { role: userStore.role, fresh: false }
  const me = await userApi.getProfile()
  userStore.adminId = me && me.id != null ? Number(me.id) : null
  userStore.role = me?.role || ''
  if (userStore.adminId != null) localStorage.setItem('adminId', String(userStore.adminId))
  if (me?.username) localStorage.setItem('username', me.username)
  return { role: userStore.role, fresh: true }
}

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')
  if (to.path === '/login') {
    // 已登录且为后台角色时，访问登录页直接进后台
    if (token) {
      try {
        const { role } = await resolveRole()
        if (isBackendRole(role)) return '/dashboard'
      } catch { /* ignore */ }
    }
    return true
  }

  if (!token) return { path: '/login' }

  try {
    const { role } = await resolveRole()
    if (!isBackendRole(role)) {
      // 非后台角色禁止进入
      return { path: '/login' }
    }
    // 细粒度权限：标注 meta.role 的路由（如管理员管理）仅对应角色可进（M10）
    const needRole = to.meta.role as string | undefined
    if (needRole && !isSuperAdmin(role)) {
      ElMessage.warning('无权限访问该页面')
      return { path: '/dashboard' }
    }
    return true
  } catch (e: any) {
    // 401（token 失效）时 http 拦截层已清除 token，无需重复清除；
    // 网络抖动等非 401 错误不清除登录态，避免「登录成功却被踢回登录」的死循环（S-7）。
    // 仅当 token 已不存在时才跳登录，否则原地刷新重试由用户触发。
    if (!localStorage.getItem('token')) {
      useUserStore().clearAuth()
      return { path: '/login' }
    }
    ElMessage.warning('网络异常，请稍后重试')
    return false
  }
})

export default router
