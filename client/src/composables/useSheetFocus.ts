import { onBeforeUnmount } from 'vue'

/**
 * 弹层焦点管理（client-ui-comprehensive-upgrade 2.3）。
 * 打开时记录触发焦点，关闭后还原到触发处（H5/桌面可达；小程序无 DOM 焦点 API，
 * 以 // #ifdef H5 守卫，mini-program 编译时被剔除，对本端为 no-op，不引入回归）。
 * 返回弹层根节点应透传的无障碍属性，统一 dialog 语义与模态声明。
 *
 * 使用频次 ≥3（AuthSheet / ApplySheet / ReviewWriteSheet 等），故抽为独立组合式，
 * 符合「仅高频复用才抽取」的抽象阈值（避免过度抽象）。
 */
export function useSheetFocus() {
  let trigger: HTMLElement | null = null

  /** 打开弹层时调用：记录当前聚焦元素，关闭后还原 */
  function captureTrigger() {
    // #ifdef H5
    if (typeof document !== 'undefined' && document.activeElement instanceof HTMLElement) {
      trigger = document.activeElement
    }
    // #endif
  }

  /** 关闭弹层时调用：焦点还原到触发元素 */
  function restoreFocus() {
    // #ifdef H5
    if (trigger && typeof trigger.focus === 'function') {
      trigger.focus()
    }
    // #endif
    trigger = null
  }

  // 组件卸载（如父层 v-if 移除弹层）兜底还原，避免焦点丢失在遮罩上
  onBeforeUnmount(restoreFocus)

  /** 弹层根节点无障碍属性：dialog 语义 + 模态声明，配合 tabindex="-1" 接收初始焦点 */
  const dialogAttrs = {
    role: 'dialog',
    'aria-modal': true,
    tabindex: '-1',
  }

  return { captureTrigger, restoreFocus, dialogAttrs }
}
