import { onBeforeUnmount } from 'vue'

/**
 * 弹层焦点管理（client-ui-comprehensive-upgrade 2.3）。
 * 打开时记录触发焦点，关闭后还原到触发处（H5/桌面可达；小程序无 DOM 焦点 API，
 * 函数体内用平台条件编译块守卫，mini-program 编译时剔除，对本端为 no-op，不引入回归）。
 *
 * 注意：本文件注释中不得出现条件编译指令字面量 —— uni-app 预处理器会扫描注释内的
 * 指令词并要求配对，误写将导致构建失败（会报指令缺少配对的结束符）。
 *
 * 使用频次 ≥3（BaseSheet 骨架及其 AuthSheet / 选择器 / 写评价等派生弹层），故抽为独立组合式，
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

  // 注：弹层根节点的无障碍属性（role="dialog" / :aria-modal="true" / tabindex="-1"）
  // 请在各弹层模板上「显式」书写，不要走 v-bind="obj" 展开绑定 ——
  // uni-app 编译 mp-weixin 不支持对象展开绑定（构建期报 v-bind="" is not supported）。
  return { captureTrigger, restoreFocus }
}
