/**
 * 设备 / 窗口信息 —— **全仓唯一**触碰平台全局 `wx` 的取值入口
 *
 * 为什么要有这个文件：
 * `getWindowInfo() → getSystemInfoSync()` 的兼容回退原先在 **4 处各写一份**
 * （`utils/useNavMetrics.ts` / `components/PageWallpaper.vue` / `pages/home/index.vue` /
 * `pages/detail/dish/useDishPage.ts`），且每处都要用 `@ts-ignore` 触碰**未在项目 TS 类型中声明**的
 * 全局 `wx`（漏一行就报 TS2304）。现集中到本文件：
 * · `@ts-ignore` 全仓只出现在这里；
 * · 所有调用方拿到的是**有类型**的结果，且只需判 `null`（H5 / 非微信端）即可。
 *
 * 平台例外（与 MP-08 同口径）：若未来项目要在 `env.d.ts` 里正式声明 `wx`，
 * 只需改动本文件，调用方零改动。
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
  // @ts-ignore - 全局 wx 未在项目 TS 类型中声明（平台例外，MP-08 同口径）
  return typeof wx !== 'undefined' ? wx : null
}

/**
 * 取微信运行时句柄（`wx` 对象本身），供 `wx.cloud.*` / `wx.chooseMedia` 等**平台专有能力**使用。
 *
 * ⚠️ 本仓**唯一**允许触碰全局 `wx` 的入口：原先在
 * `App.vue` / `api/http.ts`（×2）/ `api/upload.ts` / `components/ImagePicker.vue`（×3）
 * 各自写 `const wxApi: any = (globalThis as any).wx` 并附一份重复的「平台例外」说明 ——
 * 现统一从此处取，调用方只需判 `null`（H5 / 非微信端退化为不支持）。
 *
 * 返回 `any`：`wx` 的平台 API 面极大且随基础库演进，逐项声明收益低、维护成本高；
 * 收敛到**单一出口**后平台例外的影响范围可控（若日后要正式声明 `wx`，只改本文件即可）。
 */
export function getWxApi(): any {
  return platform()
}

/**
 * 取窗口信息。
 * 兼容老基础库：`getWindowInfo` 不存在时回退 `getSystemInfoSync`；两者都无则返回 `null`。
 */
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
