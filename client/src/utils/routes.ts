/**
 * 页面路由集中注册表（R2）。
 *
 * 目标：消除 `/pages/...` 字符串在各页/分享/导航层散落，
 * 分包/页面路径变更时只改这里 + pages.json，避免漏改跳转串。
 * 与 `client/src/pages.json` 严格一致（12 页：主包 3 + 分包 9，其中 pages/detail/ 1、pages/correction/ 1、
 * 个人中心域拆为 6 个独立分包 root：pages/profile/、pages/auth/、pages/notifications/、
 * pages/feedback/、pages/my-reviews/ 各含 1 页，pages/privacy/ 含 2 页——隐私政策与用户协议）。
 * 跳转统一用便捷构造函数（见文件底部），禁止在调用点手拼 URL。
 */

/** 静态路径常量（与 pages.json path 完全一致，不含前导斜杠的前缀按页面注册形态书写） */
export const PATH = {
  // 主包（TabBar 2 + find 二级搜索页）
  home: '/pages/home/index',
  mine: '/pages/mine/index',
  find: '/pages/find/index',
  // 分包 pages/detail/（内容阅读域）
  dishDetail: '/pages/detail/dish/index',
  // 个人中心域：6 个独立分包（各自为 subPackage root，禁止再合并为单包）
  profile: '/pages/profile/index',
  auth: '/pages/auth/index',
  notifications: '/pages/notifications/index',
  feedback: '/pages/feedback/index',
  correction: '/pages/correction/index',
  myReviews: '/pages/my-reviews/index',
  privacy: '/pages/privacy/index',
  agreement: '/pages/privacy/agreement',
} as const

/** tab key → 主根页路径（TabBar 渲染与跳转共用） */
export const TAB_URL_BY_KEY: Record<'home' | 'profile', string> = {
  home: PATH.home,
  profile: PATH.mine,
}

/** 去掉前导斜杠：主根页 route（页面栈 cur.route 不带斜杠，供 TabBar 显隐 routeMap 使用） */
export const ROUTE_KEY_BY_URL: Record<string, string> = {
  [PATH.home.slice(1)]: 'home',
  [PATH.mine.slice(1)]: 'profile',
}

/* ========== 带参路由便捷构造函数（禁止手拼 URL） ========== */

/** 菜品详情：/pages/detail/dish/index?id= */
export function dishDetailUrl(id: number | string): string {
  return `${PATH.dishDetail}?id=${id}`
}

/* ===== 意见反馈页 / 菜品纠错页落点（唯一构造函数，禁止调用点手拼 URL） ===== */

/**
 * 意见反馈页 URL（**单一形态**：Bug / 产品建议 / 其他问题 + 描述 + 截图）。
 * 入口：「我的」页宫格；搜索页「没搜到 → 推荐这道菜」。
 * ⚠️ 2026-09-27 起**菜品纠错已迁出本页**（见 `correctionUrl`），本页不再有模式参数。
 */
export function feedbackUrl(): string {
  return PATH.feedback
}

/**
 * 菜品纠错页 URL（独立页面，**仅**菜品详情页底栏「反馈错误」触发）：
 * 进页即按 `dishId` 预绑定该菜品并拉详情预填（名称 / 价格 / 食堂名 / 档口 / 描述属性 / 图片），
 * 表单内不可切换菜品，提交**只传改动项**。
 */
export function correctionUrl(dishId: number | string): string {
  return `${PATH.correction}?dishId=${dishId}`
}
