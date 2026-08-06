/**
 * 轻量 spring 动画工具（Apple Design：可中断、速度接力、从当前呈现值起步）。
 * 不依赖第三方库；仅动画 transform/opacity 这类合成属性，避免触发布局。
 *
 * 参数映射（§4.3 动效表）：
 *   damping 1.0 ≈ bounce 0（无过冲，默认）
 *   damping 0.8 ≈ bounce 0.2（仅手势带动量时，如抽屉/弹层）
 * response 即动画"响应时间"（秒），非固定 duration。
 */

export interface SpringOptions {
  /** 阻尼比：1 = 临界阻尼无过冲；<1 = 过冲振荡 */
  damping?: number
  /** 响应时间（秒），越小越灵敏 */
  response?: number
  /** 初速度（px/s），用于速度接力 */
  velocity?: number
  /** 质量（默认 1） */
  mass?: number
  /** 精度阈值 */
  precision?: number
  onUpdate: (value: number) => void
  onComplete?: () => void
}

const reduceMotion =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

/** 根据 damping/response 推导刚度 k 与阻尼 c（标准 spring 参数） */
function deriveParams(opts: SpringOptions) {
  const damping = opts.damping ?? 1
  const response = opts.response ?? 0.3
  const mass = opts.mass ?? 1
  // 周期 T 与响应时间相关； stiffness k = (2π / (T))² 的近似
  const omega = 2 * Math.PI * (0.35 / response)
  const k = omega * omega * mass
  const c = 2 * damping * Math.sqrt(k * mass)
  return { k, c, mass }
}

export class Spring {
  private raf = 0
  private value: number
  private target: number
  private velocity: number
  private opts: SpringOptions
  private last = 0

  constructor(initial: number, opts: SpringOptions) {
    this.value = initial
    this.target = initial
    this.velocity = opts.velocity ?? 0
    this.opts = opts
  }

  /** 改变目标；永远从当前呈现值 + 当前速度起步（可中断、可重定向） */
  to(target: number, velocity?: number) {
    this.target = target
    if (velocity !== undefined) this.velocity = velocity
    if (reduceMotion) {
      // 降级：交叉淡入式跳变（仅位置类用；opacity 走 CSS）
      this.value = target
      this.velocity = 0
      this.opts.onUpdate(this.value)
      this.opts.onComplete?.()
      return this
    }
    cancelAnimationFrame(this.raf)
    this.last = performance.now()
    this.tick(this.last)
    return this
  }

  /** 用力反馈直接设值（如手势拖拽中） */
  set(value: number, velocity = 0) {
    cancelAnimationFrame(this.raf)
    this.value = value
    this.velocity = velocity
    this.opts.onUpdate(this.value)
  }

  private tick = (now: number) => {
    const dt = Math.min((now - this.last) / 1000, 1 / 30)
    this.last = now
    const { k, c, mass } = deriveParams(this.opts)
    // 半隐式欧拉积分
    const force = -k * (this.value - this.target) - c * this.velocity
    const accel = force / mass
    this.velocity += accel * dt
    this.value += this.velocity * dt

    this.opts.onUpdate(this.value)

    const precision = this.opts.precision ?? 0.1
    const settled =
      Math.abs(this.velocity) < precision &&
      Math.abs(this.value - this.target) < precision
    if (settled) {
      this.value = this.target
      this.opts.onUpdate(this.value)
      this.opts.onComplete?.()
      return
    }
    this.raf = requestAnimationFrame(this.tick)
  }

  stop() {
    cancelAnimationFrame(this.raf)
  }

  get current() {
    return this.value
  }
}

/**
 * 弹层（抽屉/底部 sheet）开关动画封装。
 * 退回原因抽屉用 damping 0.8 / response 0.3（§4.3）。
 */
export function animateSheet(
  el: HTMLElement,
  from: number,
  to: number,
  axis: 'y' | 'x' = 'y',
  opts: { damping?: number; response?: number; velocity?: number } = {},
) {
  const spring = new Spring(from, {
    damping: opts.damping ?? 0.8,
    response: opts.response ?? 0.3,
    velocity: opts.velocity ?? 0,
    onUpdate: (v) => {
      el.style.transform =
        axis === 'y' ? `translate3d(0, ${v}px, 0)` : `translate3d(${v}px, 0, 0)`
    },
    onComplete: () => {
      if (to === 0) el.style.transform = axis === 'y' ? `translate3d(0, ${to}px, 0)` : `translate3d(${to}px, 0, 0)`
    },
  })
  spring.to(to, opts.velocity)
  return spring
}

/** 是否启用减弱动效（供组件判断交叉淡入） */
export function prefersReducedMotion(): boolean {
  return reduceMotion
}
