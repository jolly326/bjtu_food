/**
 * 地图画布的帧调度 / 动效降级 / 布局换算（无 Vue 依赖，可单测）。
 *
 * - 帧调度：优先 requestAnimationFrame（H5），小程序无全局 rAF 时回退 setTimeout；
 * - 动效降级：系统开启「减少动态效果」时由调用方跳过惯性 / 弹性动画；
 * - 落点随机映射：每次载入把菜品随机映射到固定格子（结构固定、落点新鲜）。
 */

/** 帧句柄：rAF 为 number，回退路径为 setTimeout 句柄 */
export type FrameHandle = number | ReturnType<typeof setTimeout>

/** 无 rAF 环境下的帧间隔（≈60fps） */
const FRAME_FALLBACK_MS = 16

/**
 * 是否有可用的 requestAnimationFrame。
 * 运行环境恒定 ⇒ 模块加载时求值一次，避免每帧重复 `typeof` 检查（惯性期每帧要判两次）。
 */
const HAS_RAF = typeof requestAnimationFrame === 'function'

/** 下一帧执行（无 rAF 回退 setTimeout） */
export function requestFrame(callback: () => void): FrameHandle {
  return HAS_RAF ? requestAnimationFrame(callback) : setTimeout(callback, FRAME_FALLBACK_MS)
}

/** 取消 `requestFrame` 调度的帧（回退路径走 clearTimeout） */
export function cancelFrame(handle: FrameHandle): void {
  if (HAS_RAF) cancelAnimationFrame(handle as number)
  else clearTimeout(handle as ReturnType<typeof setTimeout>)
}

/** 系统是否开启「减少动态效果」（开启时跳过惯性 / 弹性等非必要动画） */
export function prefersReducedMotion(): boolean {
  try {
    const host = globalThis as unknown as {
      wx?: { getSystemSetting?: () => { reduceMotion?: boolean } }
    }
    return host.wx?.getSystemSetting?.().reduceMotion === true
  } catch {
    return false
  }
}

/** 区间收敛 */
export function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

/** 洗牌（Fisher-Yates）：不改动入参，返回新数组 */
export function shuffled<T>(list: readonly T[]): T[] {
  const out = [...list]
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const tmp = out[i]
    out[i] = out[j]
    out[j] = tmp
  }
  return out
}
