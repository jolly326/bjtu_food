/**
 * 页面路由集中注册表。
 * 目的：消除 `/pages/...` 字面量在各页 / 分享 / 导航层散落 —— 分包或路径变更时只改这里 + `pages.json`。
 * 与 `client/src/pages.json` 严格一致（12 页）；跳转统一用文件底部的便捷构造函数，**禁止调用点手拼 URL**。
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

/** 主区 tab key（唯一真源；`showTab` 实参 / TabBar 渲染 / routeMap 共用，禁止裸写 'home' / 'profile'） */
export const TAB_HOME = 'home'
export const TAB_PROFILE = 'profile'

/** tab key → 主根页路径（TabBar 渲染与跳转共用） */
export const TAB_URL_BY_KEY: Record<'home' | 'profile', string> = {
  [TAB_HOME]: PATH.home,
  [TAB_PROFILE]: PATH.mine,
}

/** 去掉前导斜杠：主根页 route（页面栈 cur.route 不带斜杠，供 TabBar 显隐 routeMap 使用） */
export const ROUTE_KEY_BY_URL: Record<string, string> = {
  [PATH.home.slice(1)]: TAB_HOME,
  [PATH.mine.slice(1)]: TAB_PROFILE,
}

/* ========== 带参路由便捷构造函数（禁止调用点手拼 URL） ========== */

/** 菜品详情：/pages/detail/dish/index?id= */
export function dishDetailUrl(id: number | string): string {
  return `${PATH.dishDetail}?id=${id}`
}

/**
 * 意见反馈页 URL（**单一形态**，无模式参数）。
 * 入口：「我的」页宫格；搜索页「没搜到 → 推荐这道菜」。
 */
export function feedbackUrl(): string {
  return PATH.feedback
}

/**
 * 菜品问题反馈页 URL（独立页面，**仅**菜品详情页信息卡「菜品有问题?」触发）：
 * 进页即按 `dishId` 预绑定该菜品并拉详情预填，表单内不可切换菜品，提交**只传改动项**。
 */
export function correctionUrl(dishId: number | string): string {
  return `${PATH.correction}?dishId=${dishId}`
}
