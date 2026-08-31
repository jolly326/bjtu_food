import { ref } from 'vue'

/**
 * 统一按压状态机（global-ui-polish / ui-press-system）。
 * 覆盖 touch 与 mouse 输入及 touchcancel / mouseleave 中断态，
 * 业务组件无需再各自声明 @touchstart/@touchend/@touchcancel/@mousedown/@mouseup/@mouseleave 与 pressed ref。
 *
 * 视觉缩放由全局 `.pressed` 类承载（scale(var(--press-scale))），
 * 在 mp-weixin 真机另由 `hover-class="pressed"` 兜底（WXSS :active 失效）。
 */
export function usePress() {
  const pressed = ref(false)
  let mouseActive = false

  function onTouchStart() {
    pressed.value = true
  }
  function onTouchEnd() {
    pressed.value = false
  }
  function onPressCancel() {
    // 触摸被系统中断（滑动穿透/来电）：必须复位，避免卡在缩放态
    pressed.value = false
  }
  function onMouseDown() {
    mouseActive = true
    pressed.value = true
  }
  function onMouseUp() {
    mouseActive = false
    pressed.value = false
  }
  function onMouseLeave() {
    if (mouseActive) {
      mouseActive = false
      pressed.value = false
    }
  }

  return {
    pressed,
    onTouchStart,
    onTouchEnd,
    onPressCancel,
    onMouseDown,
    onMouseUp,
    onMouseLeave,
  }
}
