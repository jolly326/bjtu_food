/**
 * 顶部导航栏高度 / 胶囊高度 的单一真源计算。
 *
 * 抽离目的：统一计算导航栏 / 胶囊高度，保证全站 header 一致，避免各页各自实现导致高度漂移。
 *
 * 公式：navBarHeight = (胶囊.top - 状态栏高) * 2 + 胶囊高
 * 只有等于该值，微信原生胶囊才会在导航栏行内真正垂直居中
 * （之前加 Math.max(...,54) 下限会让行比系统导航栏高，胶囊中心偏移）。
 */

export interface MenuButtonRect {
  top: number
  height: number
  left?: number
}

/** 导航栏内容区高度（px）：与系统导航栏真实高度一致 */
export function getNavBarHeight(statusBarHeight: number, menu?: MenuButtonRect | null): number {
  if (menu && menu.height) {
    return (menu.top - statusBarHeight) * 2 + menu.height
  }
  return 56
}

/** 胶囊（头像/搜索框）高度（px），用于与导航栏内元素对齐 */
export function getCapsuleHeight(menu?: MenuButtonRect | null): number {
  return menu && menu.height ? menu.height : 32
}
