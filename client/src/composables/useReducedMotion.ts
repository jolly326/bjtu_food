/**
 * 减动效偏好（prefers-reduced-motion）组合式。
 *
 * 用途：统一读取用户对「减少动效」的系统偏好，供列表入场（enter-up / enterFade）、
 * 底部 Sheet 过渡等动效做降级（去除位移、仅保留/不保留淡入）。
 *
 * 实现：
 * - 微信小程序/浏览器均支持 `window.matchMedia('(prefers-reduced-motion: reduce)')`；
 *   非 H5 环境（如某些小程序运行时 window 不存在）安全降级为 false。
 * - 支持的环境额外监听 `matchMedia.change`/`change` 事件，系统偏好切换时实时同步。
 * - 全局 CSS `@media (prefers-reduced-motion: reduce)` 仍是兜底（App.vue），
 *   本组合式用于 JS 侧（如条件性禁用 transition/animation）的精确控制。
 *
 * 与既有全局 `.pressed`/`.scroll-wrap`/reduced-motion 体系一致，不改变视觉规范。
 */
import { ref, onMounted, onUnmounted } from 'vue'

const QUERY = '(prefers-reduced-motion: reduce)'

function read(): boolean {
  try {
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      return !!window.matchMedia(QUERY).matches
    }
  } catch {
    /* 不支持时默认不降级 */
  }
  return false
}

export function useReducedMotion() {
  const reduceMotion = ref(false)

  let mql: MediaQueryList | null = null
  const onChange = (e: MediaQueryListEvent) => {
    reduceMotion.value = e.matches
  }

  onMounted(() => {
    reduceMotion.value = read()
    try {
      if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
        mql = window.matchMedia(QUERY)
        // Safari 旧版用 addListener，现代用 addEventListener
        if (typeof mql.addEventListener === 'function') {
          mql.addEventListener('change', onChange)
        } else if (typeof (mql as any).addListener === 'function') {
          ;(mql as any).addListener(onChange)
        }
      }
    } catch {
      /* ignore */
    }
  })

  onUnmounted(() => {
    try {
      if (mql) {
        if (typeof mql.removeEventListener === 'function') {
          mql.removeEventListener('change', onChange)
        } else if (typeof (mql as any).removeListener === 'function') {
          ;(mql as any).removeListener(onChange)
        }
      }
    } catch {
      /* ignore */
    }
  })

  return { reduceMotion }
}
