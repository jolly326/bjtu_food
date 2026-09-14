import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/views/layout/AdminLayout.vue'
// ElMessage 由 unplugin-auto-import + ElementPlusResolver 自动导入（含样式，WEB-116）

/**
 * 2026-09-13 定型：Web 后台为**本地数据操作工具**，无登录体系、无角色体系。
 * 打开即用；管理端接口（/admin/**）由后端 AdminTokenFilter 校验请求头 X-Admin-Token（见 api/http.ts）。
 * 因此移除登录页、全局角色守卫与账号设置页；用户管理（查看 / 禁用学生账号）保留。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: '/dashboard' },
    {
      path: '/dashboard',
      component: AdminLayout,
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
        { path: 'content', name: 'contentManage', component: () => import('@/views/content/ContentManageView.vue') },
        // 菜品详情：挂在内容管理下（2026-09-14：食堂/档口只是筛选条件，后台主体是「菜品列表 + 菜品详情」）。
        // 入口为「菜品管理」列表行点击；编辑仍走 DishFormDialog 弹窗。
        { path: 'content/dishes/:dishId', name: 'dishDetail', component: () => import('@/views/canteen/DishDetailView.vue') },
        { path: 'audit', name: 'auditManage', component: () => import('@/views/audit/AuditManageView.vue') },
        { path: 'system', name: 'systemManage', component: () => import('@/views/system/SystemManageView.vue') },
        // 独立的食堂/档口管理页已删除（2026-09-14 §7.15：食堂与档口随菜品一起维护，
        // 归属选择收敛到 DishFormDialog，见 docs/loop/design/dish-entry-flow.md §1.5）。
        // 旧书签（含旧的食堂/档口下钻详情链接）按下方兜底重定向到菜品列表，避免白屏。
        { path: 'canteens/:pathMatch(.*)*', redirect: '/dashboard/content?tab=dish' },
      ],
    },
    /**
     * 根级兜底（PR-03「新入口必须具备失败态」）：非法/历史/拼错 URL 命中不到任何路由时，
     * 若无此条则 <router-view> 空渲染 → 全白无提示。统一重定向到仪表盘（工作台）。
     * 注意：必须放在最后，且为根级（非 /dashboard 子路由），否则 `/xxx` 不匹配 children 前缀。
     */
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

export default router
