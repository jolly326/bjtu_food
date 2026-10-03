import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表（**13 项菜单**，口径见 [UI 基线 §四](../../../docs/web/ui/公共组件与形态基线.md)）。
 *
 * <p><b>无登录页 / 无个人页 / 无守卫</b>：管理后台无登录体系，口令由构建期注入
 * （见 [C1](../../../docs/web/C-账号与访问/C1-管理员登录与访问控制.md)）；
 * 口令不匹配时由接口返回 `403`，页面渲染「会话失效态」（[UI 基线 §1.5 ⑥](../../../docs/web/ui/公共组件与形态基线.md)），
 * **不做路由级拦截**。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
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
        { path: 'corrections', name: 'corrections', component: () => import('@/views/CorrectionsView.vue'), meta: { title: '菜品纠错' } },
        { path: 'users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户管理' } },
        { path: 'report-reasons', name: 'report-reasons', component: () => import('@/views/ReportReasonsView.vue'), meta: { title: '举报原因' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

export default router
