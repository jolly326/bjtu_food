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
        { path: 'audit', name: 'auditManage', component: () => import('@/views/audit/AuditManageView.vue') },
        { path: 'system', name: 'systemManage', component: () => import('@/views/system/SystemManageView.vue') },
        // 食堂/档口/菜品详情三条下钻路由已删除（2026-09-14 §7.15：食堂与档口随菜品一起维护，
        // 归属选择收敛到 DishFormDialog，见 docs/loop/design/dish-entry-flow.md §1.5）。
        // 旧书签按下方兜底重定向到信息管理页，避免白屏。
        { path: 'canteens/:pathMatch(.*)*', redirect: '/dashboard/content?tab=dish' },
      ],
    },
  ],
})

export default router
