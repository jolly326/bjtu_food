/**
 * 设备 / 窗口信息 —— **全仓唯一**触碰平台全局 `wx` 的取值入口。
 *
 * ⚠️ 约束：`@ts-ignore` 全仓只允许出现在本文件（全局 `wx` 未纳入项目 TS 类型）；
 * 调用方拿到的是有类型结果，只需判 `null`（H5 / 非微信端）。
 * 若日后要在 `env.d.ts` 正式声明 `wx`，只改本文件，调用方零改动。
 */

/** 窗口信息（只声明本项目实际读取的字段） */
interface WindowInfoLike {
  /** 状态栏高（px） */
  statusBarHeight?: number
  /** 视口宽（px） */
  windowWidth?: number
  /** 视口高（px） */
  windowHeight?: number
}

/** 微信右上角原生胶囊矩形（只声明本项目实际读取的字段） */
export interface MenuButtonRect {
  top: number
  height: number
  left: number
  right?: number
  width?: number
}

/** 取平台句柄：非微信运行时（H5 / 单测）返回 `null` —— 本文件内唯一的 `wx` 触碰点 */
function platform(): any {
  // @ts-ignore - 全局 wx 未在项目 TS 类型中声明（平台例外）
  return typeof wx !== 'undefined' ? wx : null
}

/** 微信运行时句柄（`wx` 本身），供 `wx.cloud.*` / `wx.chooseMedia` 等平台专有能力使用 */
export function getWxApi(): any {
  return platform()
}

/** 取窗口信息；老基础库回退 `getSystemInfoSync`，两者都无则返回 `null` */
export function getWindowInfo(): WindowInfoLike | null {
  const wxApi = platform()
  if (!wxApi) return null
  return wxApi.getWindowInfo ? wxApi.getWindowInfo() : (wxApi.getSystemInfoSync ? wxApi.getSystemInfoSync() : null)
}

/** 取微信右上角原生胶囊矩形；非微信端 / 不支持时返回 `null` */
export function getMenuButtonRect(): MenuButtonRect | null {
  const wxApi = platform()
  if (!wxApi || !wxApi.getMenuButtonBoundingClientRect) return null
  return wxApi.getMenuButtonBoundingClientRect() as MenuButtonRect
}
