import { ref } from 'vue'

/**
 * 统一按压状态机（global-ui-polish / ui-press-system）。
 * 覆盖 touch 与 mouse 输入及 touchcancel / mouseleave 中断态，
 * 业务组件无需再各自声明 @touchstart/@touchend/@touchcancel/@mousedown/@mouseup/@mouseleave 与 pressed ref。
 *
 * 视觉缩放由 `.pressed` 类承载（scale(var(--press-scale))），由 Pressable 统一加在根元素。
 * 注意：Pressable 不叠加 WXSS hover-class（避免两套机制在滑动划过时高频触发换色型
 * .pressed，导致合成层 border-radius 裁剪失效露出左上角色块）；纯 JS 状态机为唯一来源。
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
