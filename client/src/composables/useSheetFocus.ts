import { onBeforeUnmount } from 'vue'

/**
 * 弹层焦点管理（client-ui-comprehensive-upgrade 2.3 + UI-OPT-01 Tab 焦点循环陷阱）。
 * 打开时记录触发焦点，关闭后还原到触发处（H5/桌面可达；小程序无 DOM 焦点 API，
 * 函数体内用平台条件编译块守卫，mini-program 编译时剔除，对本端为 no-op，不引入回归）。
 *
 * UI-OPT-01：弹层打开期间注册 document 级 Tab 键监听，焦点在 role="dialog" 容器内
 * Tab / Shift+Tab 循环（首个↔末个互绕），不逃逸到背景页面；随开/关对称注册/卸载，
 * 不改变既有 capture/restore 行为，无需调用方组件另改（BaseSheet / ReportModal 自动获益）。
 *
 * 注意：本文件注释中不得出现条件编译指令字面量 —— uni-app 预处理器会扫描注释内的
 * 指令词并要求配对，误写将导致构建失败（会报指令缺少配对的结束符）。
 *
 * 使用频次 ≥3（BaseSheet 骨架及其 AuthSheet / 选择器 / 写评价等派生弹层），故抽为独立组合式，
 * 符合「仅高频复用才抽取」的抽象阈值（避免过度抽象）。
 */

/** 可聚焦元素选择器：原生控件 + 显式 tabindex（排除 tabindex="-1" 的容器本身） */
const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ')

export function useSheetFocus() {
  let trigger: HTMLElement | null = null
  let trapActive = false

  /** 容器内当前可见的可聚焦元素（用 clientRects 过滤 display:none / v-show 隐藏项） */
  function getVisibleFocusable(root: HTMLElement): HTMLElement[] {
    return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
      (el) => el.getClientRects().length > 0,
    )
  }

  /** Tab 循环陷阱：焦点已在某个 dialog 容器内时，Tab/Shift+Tab 在容器内互绕 */
  function onTabKey(e: KeyboardEvent) {
    if (e.key !== 'Tab') return
    const current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    const anchor = current ?? (e.target instanceof HTMLElement ? e.target : null)
    const container = anchor?.closest<HTMLElement>('[role="dialog"]')
    if (!container) return
    const focusables = getVisibleFocusable(container)
    // 无可聚焦元素时兜底聚焦容器本身（弹层根节点按约定带 tabindex="-1"）
    const first = focusables[0] ?? container
    const last = focusables[focusables.length - 1] ?? container
    e.preventDefault()
    if (!current || !container.contains(current)) {
      first.focus()
      return
    }
    const idx = focusables.indexOf(current)
    if (e.shiftKey) {
      ;(idx <= 0 ? last : focusables[idx - 1]).focus()
    } else {
      ;(idx === -1 || idx === focusables.length - 1 ? first : focusables[idx + 1]).focus()
    }
  }

  /** 打开弹层时调用：记录当前聚焦元素，关闭后还原；同时启用 Tab 循环陷阱 */
  function captureTrigger() {
    // #ifdef H5
    if (typeof document !== 'undefined' && document.activeElement instanceof HTMLElement) {
      trigger = document.activeElement
    }
    if (!trapActive && typeof document !== 'undefined') {
      document.addEventListener('keydown', onTabKey)
      trapActive = true
    }
    // #endif
  }

  /** 关闭弹层时调用：焦点还原到触发元素；同步卸载 Tab 陷阱监听（与注册对称） */
  function restoreFocus() {
    // #ifdef H5
    if (trapActive && typeof document !== 'undefined') {
      document.removeEventListener('keydown', onTabKey)
      trapActive = false
    }
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
