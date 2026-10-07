import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 「用户偏好减少动态效果」在**端上**的实时值。
 *
 * <p>**为什么需要它**：全局 CSS 兜底（`App.vue` 的 `@media (prefers-reduced-motion: reduce)`）
 * 只能覆盖 CSS `animation` / `transition`，而 `<swiper autoplay>` 是**原生组件行为**，
 * 媒体查询对它无效。结果是全站唯一的持续位移动画恰好是唯一降级不到的。
 *
 * <p>**平台差异**：`matchMedia` 仅 H5 / 浏览器可用；小程序无此 API ⇒ 恒为 `false`
 * （即不降级）。小程序侧要降级只能靠 `wx.getSystemSetting` 读系统「减少动画」开关，
 * 属独立能力，未纳入本 composable。
 *
 * <p>用法：`const reduceMotion = usePrefersReducedMotion()`，模板里 `:autoplay="list.length > 1 && !reduceMotion"`。
 * 值在 `onMounted` 后才可能从 `true` 变为实际值，首次渲染按「不降级」处理（安全侧）。
 */
export function usePrefersReducedMotion() {
  const reduceMotion = ref(false)
  let mql: MediaQueryList | null = null
  /** 解除监听（避免页面回退后仍持有回调） */
  const dispose = (): void => {
    mql?.removeEventListener?.('change', onChange)
    mql = null
  }
  const onChange = (e: MediaQueryListEvent): void => {
    reduceMotion.value = e.matches
  }

  onMounted(() => {
    // 非浏览器环境（小程序）无 matchMedia ⇒ 保持 false
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return
    mql = window.matchMedia('(prefers-reduced-motion: reduce)')
    reduceMotion.value = mql.matches
    mql.addEventListener?.('change', onChange)
  })

  onUnmounted(dispose)

  return reduceMotion
}
