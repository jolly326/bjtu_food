/**
 * 窗口尺寸（美食地图画布换算 px 用）。
 *
 * 画布手势坐标是 px，磁贴 / 间距标度是 rpx，故在入口把窗口宽高读一次并缓存：
 * `getWindowInfo` / `getSystemInfoSync` 都是跨 JS↔原生桥的同步调用，运行期恒定。
 */

export interface WindowSize {
  width: number
  height: number
}

const FALLBACK: WindowSize = { width: 375, height: 667 }

let cached: WindowSize | null = null

function read(): WindowSize {
  try {
    const info = uni.getWindowInfo()
    return {
      width: info.windowWidth || FALLBACK.width,
      height: info.windowHeight || FALLBACK.height,
    }
  } catch {
    const info = uni.getSystemInfoSync()
    return {
      width: info.windowWidth || FALLBACK.width,
      height: info.windowHeight || FALLBACK.height,
    }
  }
}

/** 窗口宽高（px），惰性读取并缓存 */
export function windowSize(): WindowSize {
  return (cached ??= read())
}

/** 重新读取并刷新缓存（窗口尺寸变化时调用） */
export function refreshWindowSize(): WindowSize {
  cached = read()
  return cached
}
