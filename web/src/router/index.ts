import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/views/layout/AdminLayout.vue'
// ElMessage 由 unplugin-auto-import + ElementPlusResolver 自动导入（含样式，WEB-116）

/**
 * 2026-09-13 定型：Web 后台为**本地数据操作工具**，无登录体系、无角色体系。
 * 打开即用；管理端接口（/admin/**）由后端 AdminTokenFilter 校验请求头 X-Admin-Token（见 api/http.ts）。
 * 因此移除登录页、全局角色守卫与账号设置页；用户管理（查看 / 禁用学生账号）保留。
 *
 * 2026-09-15（本轮）：**工作台（DashboardView / 首屏仪表盘）整体下线**，
 * 一级导航收敛为 信息管理 / 内容审核 / 用户与系统 三项；所有「无落实目标」的入口
 * （`/`、`/dashboard` 空子路由、根级兜底）统一落到菜品页 `/dashboard/content?tab=dish`，
 * 避免出现空渲染白屏（旧书签 / 拼错 URL 亦由此兜底）。
 *
 * 2026-09-15（取消人工复核 · 信息架构 3 → 4 项）：
 * 一级导航 = 信息管理（菜品）/ 评价管理（事后处置）/ 反馈处理 / 用户与系统。
 * 原「内容审核」聚合页（页内两卡切换的容器视图）删除，职责拆为两个同级路由：
 *   /dashboard/reviews  ← 评价（隐藏 / 删除等事后处置）
 *   /dashboard/feedback ← 反馈（处理闭环）
 * 旧深链 /dashboard/audit** 由下方 auditLegacyPath 兜底跳转（保留查询参数，fid 深链仍可达）。
 * 默认落地页不变：需求入口一律仍落菜品页 `/dashboard/content?tab=dish`。
 */
const DISH_LIST_PATH = '/dashboard/content?tab=dish'

/**
 * 旧「内容审核」聚合页深链兜底（该页已拆分为两个一级入口）：
 * - `?tab=feedback`（以及历史上并入反馈卡片的 `apply` / `apply-feedback`）→ 反馈处理页；
 * - 其余（`?tab=review` / 无参数）→ 评价管理页。
 * 查询参数整份保留：反馈单条深链 `?fid=<id>` 跳转后仍能自动定位并打开处理抽屉（P1-03）。
 */
function auditLegacyPath(tab: unknown): string {
  const toFeedback = tab === 'feedback' || tab === 'apply' || tab === 'apply-feedback'
  return toFeedback ? '/dashboard/feedback' : '/dashboard/reviews'
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: DISH_LIST_PATH },
    {
      path: '/dashboard',
      component: AdminLayout,
      children: [
        // 工作台已下线：进后台首屏直接是菜品列表（默认落点）
        { path: '', redirect: DISH_LIST_PATH },
        { path: 'content', name: 'contentManage', component: () => import('@/views/content/ContentManageView.vue') },
        // 菜品详情：挂在内容管理下（2026-09-14：食堂/档口只是筛选条件，后台主体是「菜品列表 + 菜品详情」）。
        // 入口为「菜品管理」列表行点击；编辑仍走 DishFormDialog 弹窗。
        { path: 'content/dishes/:dishId', name: 'dishDetail', component: () => import('@/views/content/DishDetailView.vue') },
        // 评价管理：事后处置（隐藏 / 显示 / 删除），后台已无人工复核动作
        { path: 'reviews', name: 'reviewManage', component: () => import('@/views/audit/ReviewManageView.vue') },
        // 反馈处理：原「内容审核 → 反馈」卡独立成页
        { path: 'feedback', name: 'feedbackManage', component: () => import('@/views/audit/FeedbackView.vue') },
        // 旧「内容审核」深链兜底（页面已拆分，见文件头 auditLegacyPath）
        { path: 'audit', redirect: (to) => ({ path: auditLegacyPath(to.query.tab), query: to.query }) },
        { path: 'system', name: 'systemManage', component: () => import('@/views/system/SystemManageView.vue') },
        // 独立的食堂/档口管理页已删除（2026-09-14 §7.15：食堂与档口随菜品一起维护，
        // 归属选择收敛到 DishFormDialog，见 project_spec §7.15）。
        // 旧书签（含旧的食堂/档口下钻详情链接）按下方兜底重定向到菜品列表，避免白屏。
        { path: 'canteens/:pathMatch(.*)*', redirect: DISH_LIST_PATH },
      ],
    },
    /**
     * 根级兜底（PR-03「新入口必须具备失败态」）：非法/历史/拼错 URL 命中不到任何路由时，
     * 若无此条则 <router-view> 空渲染 → 全白无提示。统一重定向到菜品列表（后台默认落点）。
     * 注意：必须放在最后，且为根级（非 /dashboard 子路由），否则 `/xxx` 不匹配 children 前缀。
     */
    { path: '/:pathMatch(.*)*', redirect: DISH_LIST_PATH },
  ],
})

export default router
