/**
 * v-press：按下即时反馈 scale(0.97)（§4.4 交互反馈）。
 * 在 pointerdown 触发，pointerup/leave 复原；命中区带 ~10px 滞回取消。
 * 尊重 prefers-reduced-motion：不缩放。
 */
import type { App, Directive, DirectiveBinding } from 'vue'

const reduceMotion =
  typeof window !== 'undefined' &&
  window.matchMedia &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

// 统一读取设计 Token（variables.css §4）：缓动曲线与按压缩放都走 Token，
// 与 CSS 基线一致，避免硬编码 easing/scale 造成两端观感割裂。读不到时回退默认值。
const FALLBACK_EASE = 'cubic-bezier(0.23, 1, 0.32, 1)' // --ease-out
const DEFAULT_SCALE = 0.97 // --press-scale

function designToken(el: HTMLElement, name: string): string {
  if (typeof window !== 'undefined' && el && window.getComputedStyle) {
    const value = window.getComputedStyle(el).getPropertyValue(name).trim()
    if (value) return value
  }
  return ''
}

function apply(el: HTMLElement, binding: DirectiveBinding) {
  const tokenScale = parseFloat(designToken(el, '--press-scale'))
  const scale =
    typeof binding.value === 'number'
      ? binding.value
      : Number.isFinite(tokenScale) && tokenScale > 0
        ? tokenScale
        : DEFAULT_SCALE
  const easeOut = designToken(el, '--ease-out') || FALLBACK_EASE
  el.style.transition = `transform 140ms ${easeOut}`
  el.style.willChange = 'transform'
  el.style.transform = `scale(${scale})`
}
function reset(el: HTMLElement) {
  el.style.transform = ''
}

const press: Directive = {
  mounted(el: HTMLElement, binding) {
    if (reduceMotion) return
    el.style.touchAction = 'manipulation'
    el.__pressHandlers__ = {
      down: () => apply(el, binding),
      up: () => reset(el),
      leave: () => reset(el),
      cancel: () => reset(el),
    }
    el.addEventListener('pointerdown', el.__pressHandlers__.down)
    el.addEventListener('pointerup', el.__pressHandlers__.up)
    el.addEventListener('pointerleave', el.__pressHandlers__.leave)
    el.addEventListener('pointercancel', el.__pressHandlers__.cancel)
  },
  unmounted(el: HTMLElement) {
    const h = el.__pressHandlers__
    if (!h) return
    el.removeEventListener('pointerdown', h.down)
    el.removeEventListener('pointerup', h.up)
    el.removeEventListener('pointerleave', h.leave)
    el.removeEventListener('pointercancel', h.cancel)
    delete el.__pressHandlers__
  },
}

export function setupPress(app: App) {
  app.directive('press', press)
}

declare global {
  interface HTMLElement {
    __pressHandlers__?: {
      down: () => void
      up: () => void
      leave: () => void
      cancel: () => void
    }
  }
}

export default press
