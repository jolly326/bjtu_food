/**
 * 页面路由集中注册表（R2）。
 *
 * 目标：消除 `/pages/...` 字符串在各页/分享/导航层散落，
 * 分包/页面路径变更时只改这里 + pages.json，避免漏改跳转串。
 * 与 `client/src/pages.json` 严格一致（10 页：主包 3 + 分包 7，其中 pages/detail/ 1、
 * 个人中心域拆为 6 个独立分包 root：pages/profile/、pages/auth/、pages/notifications/、
 * pages/feedback/、pages/my-reviews/、pages/privacy/，各含 1 页）。
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
  myReviews: '/pages/my-reviews/index',
  privacy: '/pages/privacy/index',
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

/* ===== 意见反馈页落点（唯一构造函数，禁止调用点手拼 URL） ===== */

/** 反馈页双模式：我要反馈问题（issue，缺省）/ 我要更新信息（update） */
export type FeedbackMode = 'issue' | 'update'

/**
 * 意见反馈页 URL（双模式口径）：
 * - 「我的」宫格等默认入口 → feedbackUrl()（缺省 issue）；
 * - 菜品详情页「信息有误？」入口 → feedbackUrl('update', dishId)
 *   （update 模式带 dishId 时跳过搜索步骤，进页即拉详情预填表单）。
 */
export function feedbackUrl(mode: FeedbackMode = 'issue', dishId?: number | string): string {
  const params = [`mode=${mode}`]
  if (dishId != null) params.push(`dishId=${dishId}`)
  return `${PATH.feedback}?${params.join('&')}`
}
