import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/views/layout/AdminLayout.vue'
// ElMessage 由 unplugin-auto-import + ElementPlusResolver 自动导入（含样式，WEB-116）

/**
 * 2026-09-13 定型：Web 后台为**本地数据操作工具**，无登录体系、无角色体系。
 * 打开即用；管理端接口（/admin/**）由后端 AdminTokenFilter 校验请求头 X-Admin-Token（见 api/http.ts）。
 *
 * 2026-09-15（信息架构精简，去层层嵌套）：一级导航 = 菜品 / 评价 / 反馈 / 学生账号，
 * 路由与一级导航 1:1，不再有「分类卡 + 二级壳」中间层：
 *  - 原「用户与系统」聚合页（SystemManageView：账号 / 操作日志两张分类卡）整体下线，
 *    /dashboard/system 直接渲染学生账号页（UserView.vue）；
 *  - 操作日志链路（页面 / constants 元数据 / api 层）同轮物理删除——
 *    后端 AOP 埋点与存量日志数据不受影响，仅前端不再展示；
 *  - 原「内容审核」聚合页已拆分，但**保留 /dashboard/audit 旧书签兜底重定向**（history 深链/收藏夹仍可能命中）：
 *    `?tab=feedback*` / `?tab=apply*` → /dashboard/feedback，其余 → /dashboard/reviews，并整份保留 query（含 `?fid=`）。
 * 默认落地页不变：所有空入口 / 非法 URL 一律落菜品列表 /dashboard/content?tab=dish。
 */
const DISH_LIST_PATH = '/dashboard/content?tab=dish'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', redirect: DISH_LIST_PATH },
    {
      path: '/dashboard',
      component: AdminLayout,
      children: [
        // 进后台首屏 = 菜品列表（默认落点）
        { path: '', redirect: DISH_LIST_PATH },
        // 菜品列表：路由直接指向视图，不再经「聚合别名壳」（避免凭空多一层包装；`?q=` 预填搜索在视图内处理）
        { path: 'content', name: 'dishList', component: () => import('@/views/content/DishManageView.vue') },
        // 菜品详情：挂在内容管理下（2026-09-14：食堂/档口只是筛选条件，后台主体是「菜品列表 + 菜品详情」）。
        // 入口为「菜品管理」列表行点击；编辑仍走 DishFormDialog 弹窗。
        { path: 'content/dishes/:dishId', name: 'dishDetail', component: () => import('@/views/content/DishDetailView.vue') },
        // 评价：事后处置（隐藏 / 显示 / 删除），后台已无人工复核动作
        { path: 'reviews', name: 'reviewManage', component: () => import('@/views/audit/ReviewManageView.vue') },
        // 反馈：处理闭环（采纳回复 / 不采纳必填原因）
        { path: 'feedback', name: 'feedbackManage', component: () => import('@/views/audit/FeedbackView.vue') },
        // 信息纠错：独立处理页（自反馈拆分，/admin/corrections；采纳两段式 / 拒绝必填原因）
        { path: 'corrections', name: 'correctionManage', component: () => import('@/views/audit/CorrectionView.vue') },
        // 学生账号：直接渲染学生账号视图
        { path: 'system', name: 'studentAccount', component: () => import('@/views/system/UserView.vue') },
        // 旧书签兼容：原「内容审核」聚合页地址（拆分后不再有该页，仅重定向；整份保留 query）
        {
          path: 'audit',
          redirect: (to) => {
            const tab = String(to.query.tab ?? '')
            const toFeedback = tab.startsWith('feedback') || tab.startsWith('apply')
            return { path: toFeedback ? '/dashboard/feedback' : '/dashboard/reviews', query: to.query }
          },
        },
        // 食堂与档口随菜品一起维护（§7.15：归属选择收敛到 DishFormDialog，见 project_spec §7.15）。
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
