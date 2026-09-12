/**
 * 页面路由集中注册表（R2：2026-09-06 第二轮审计收敛）。
 *
 * 目标：消除 `/pages/...` 字符串在各页/分享/导航层散落（此前 14 文件 30+ 处），
 * 分包/页面路径变更时只改这里 + pages.json，避免漏改跳转串。
 * 与 `client/src/pages.json` 严格一致（13 页：主包 4 + 分包 9）。
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
  // 分包 pages/me/（个人中心域）
  profile: '/pages/me/profile/index',
  notifications: '/pages/me/notifications/index',
  feedback: '/pages/me/feedback/index',
  // 分包 pages/activity/（活动 + webview，独立分包）
  activity: '/pages/activity/index',
  activityWebview: '/pages/activity/webview',
} as const

/** 主包 TabBar 路由集合（TabBar.vue / route store 复用） */
export const TAB_PATHS = [PATH.home, PATH.mine] as const

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

/** 活动 webview：?url= 编码后的公众号文章链接 */
export function activityWebviewUrl(articleUrl: string): string {
  return `${PATH.activityWebview}?url=${encodeURIComponent(articleUrl)}`
}
