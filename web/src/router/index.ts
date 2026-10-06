import { createRouter, createWebHistory } from 'vue-router'
import { fetchMe } from '@/api/auth'
import { hasToken } from '@/api/session'

/**
 * 路由表（**13 项菜单** + 登录页，口径见 [UI 基线 §四](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p><b>有登录页、有守卫</b>（TD-23，真源 [C1](../../../docs/func/web/C-账号与访问/C1-管理员登录与访问控制.md)）：
 * 管理后台为账密登录，🔴 **前端不持有口令**，只持有登录后签发的 JWT。所有业务页都需要有效 token，
 * 由下方 `beforeEach` 统一校验；`/login` 为公开页（已登录访问它 → 直接回 `/dashboard`）。
 *
 * <p><b>校验判据是 `GET /admin/auth/me` 实测</b>，不是「本地存了 token」—— 本地有 token
 * 可能已过期 / 被吊销，只看存储会在业务页首屏白跑一次请求。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '运营看板' } },
        { path: 'canteens', name: 'canteens', component: () => import('@/views/CanteensView.vue'), meta: { title: '食堂管理' } },
        { path: 'stalls', name: 'stalls', component: () => import('@/views/StallsView.vue'), meta: { title: '档口管理' } },
        { path: 'dishes', name: 'dishes', component: () => import('@/views/DishesView.vue'), meta: { title: '菜品管理' } },
        { path: 'dimensions', name: 'dimensions', component: () => import('@/views/DimensionsView.vue'), meta: { title: '属性维度' } },
        { path: 'views', name: 'views', component: () => import('@/views/ViewsView.vue'), meta: { title: '首页筛选视图' } },
        { path: 'banners', name: 'banners', component: () => import('@/views/BannersView.vue'), meta: { title: '首页 Banner' } },
        { path: 'reviews', name: 'reviews', component: () => import('@/views/ReviewsView.vue'), meta: { title: '评价管理' } },
        { path: 'feedbacks', name: 'feedbacks', component: () => import('@/views/FeedbacksView.vue'), meta: { title: '意见反馈' } },
        { path: 'reports', name: 'reports', component: () => import('@/views/ReportsView.vue'), meta: { title: '举报管理' } },
        { path: 'corrections', name: 'corrections', component: () => import('@/views/CorrectionsView.vue'), meta: { title: '菜品问题反馈' } },
        { path: 'users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户管理' } },
        { path: 'report-reasons', name: 'report-reasons', component: () => import('@/views/ReportReasonsView.vue'), meta: { title: '举报原因' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

/**
 * 本次页面加载内是否已用 `GET /admin/auth/me` 实测过凭证。
 *
 * <p>只验一次：`sessionStorage` 中的 token 在同一标签页内不会凭空失效，
 * 每次导航都打一次 `/me` 纯属浪费。token 真正过期/被吊销时，业务请求会拿到 `401`，
 * 由请求层清 token 并跳回登录页（见 `api/http.ts`）—— 守卫这一层只负责「进来之前问一句」。
 */
let sessionVerified = false

/** 核验登录态：无 token → 拒；已验过 → 放行；否则打 `/me` 实测 */
async function verifySession(): Promise<boolean> {
  // 🔴 **必须先判 `hasToken()`**：`sessionVerified` 是「上次验过」的缓存，而请求层在 401 时
  // 只清 storage、不知道本模块的存在。若缓存判据在前，token 失效后会变成
  // 「401 → 跳 /login → 守卫见缓存为真 → 推回 /dashboard → 再 401」的死循环。
  if (!hasToken()) return false
  if (sessionVerified) return true
  try {
    await fetchMe()
    sessionVerified = true
    return true
  } catch {
    // 401 时 http.ts 已清 token；网络异常同样按未登录处理（后端不可达时管理台本就不可用）
    return false
  }
}

router.beforeEach(async (to) => {
  const authed = await verifySession()

  if (to.meta.public) {
    // 已登录访问登录页 → 直接跳转 /dashboard（登录页.md「已登录访问本页」）
    return authed ? '/dashboard' : true
  }
  // 未登录访问其它页 → 路由守卫重定向登录页（登录页.md「未登录访问其它页」）
  return authed ? true : '/login'
})

export default router
