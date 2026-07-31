/**
 * Spring 动效基础工具（Apple Design 风格）
 *
 * 与 project_spec.md §4.3 对齐：
 * - 默认全站 damping 1.0（无过冲）
 * - 抽屉 / 底部弹层 damping 0.8、response 0.3
 * - 始终从「当前呈现值」起步，支持可中断与速度接力
 *
 * 说明：uni-app 小程序端无 Pointer Events / Web Animations API，
 * 手势驱动场景由各组件用 touch 事件 + 自记速度历史实现；
 * 本模块提供 spring 数值积分器，供 JS 驱动动画（如数字滚动、非手势类过渡）复用。
 */

export interface SpringConfig {
  /** 阻尼比：1.0 无过冲（默认），0.8 带回弹 */
  damping: number
  /** 响应时间（秒）：数值越小越快收敛 */
  response: number
}

/** §4.3 预设：常规 UI（卡片/按钮/导航） */
export const SPRING_DEFAULT: SpringConfig = { damping: 1.0, response: 0.3 }
/** §4.3 预设：抽屉 / 底部弹层（Sheet） */
export const SPRING_SHEET: SpringConfig = { damping: 0.8, response: 0.3 }
/** §4.3 预设：旋转 / 翻动 */
export const SPRING_ROTATE: SpringConfig = { damping: 0.8, response: 0.4 }

/**
 * 单步 spring 数值积分（半隐式欧拉）。
 * @param current 当前值
 * @param target  目标值
 * @param velocity 当前速度（与 current 同单位/秒）
 * @param dt       帧间隔（秒）
 * @param config   spring 参数
 * @returns [next, nextVelocity]
 */
export function springStep(
  current: number,
  target: number,
  velocity: number,
  dt: number,
  config: SpringConfig = SPRING_DEFAULT,
): [number, number] {
  // 由 response 推导刚度 k 与阻尼 c（标准二阶系统）
  const k = 1 / (config.response * config.response)
  const c = (2 * config.damping) / config.response
  const force = -k * (current - target) - c * velocity
  const nextVelocity = velocity + force * dt
  const next = current + nextVelocity * dt
  return [next, nextVelocity]
}

/** 是否应停止动画（已足够接近目标且速度可忽略） */
export function springSettled(
  current: number,
  target: number,
  velocity: number,
  epsilon = 0.5,
): boolean {
  return Math.abs(current - target) < epsilon && Math.abs(velocity) < epsilon
}

/**
 * 动量落点估算（§4.3）：
 * current + (v/1000)·d/(1−d)，d≈0.998，再交由调用方吸附最近 snap 点。
 */
export function momentumProjection(current: number, velocityPerSec: number, decay = 0.998): number {
  return current + (velocityPerSec / 1000) * (decay / (1 - decay))
}

/** 是否开启「减少动态效果」系统偏好（小程序端降级为交叉淡入） */
export function prefersReducedMotion(): boolean {
  // uni-app 小程序端不支持 matchMedia；以 wx 接口为准（H5/App 走 matchMedia）
  // @ts-ignore - 跨端兼容
  const w = typeof wx !== 'undefined' ? wx : (typeof window !== 'undefined' ? window : undefined)
  if (!w) return false
  // @ts-ignore
  if (typeof w.matchMedia === 'function') {
    // @ts-ignore
    return w.matchMedia('(prefers-reduced-motion: reduce)').matches
  }
  return false
}
