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

const SCALE = 0.97

function apply(el: HTMLElement, binding: DirectiveBinding) {
  const scale = typeof binding.value === 'number' ? binding.value : SCALE
  el.style.transition = 'transform 140ms cubic-bezier(0.33, 1, 0.68, 1)'
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
