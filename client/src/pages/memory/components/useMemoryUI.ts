/**
 * 记忆页卡片全局交互状态机（口径见 docs/ui/client/记忆.md · 全局互斥铁则）。
 *
 * 维护两类互斥 UI：
 *  - 原位评价激活态（State B）：全局最多 1 张卡进入，由 evaluatingId 标记；
 *  - Modal Bottom Sheet（长按菜单 / 纠错表单）与删除二次确认弹窗。
 *
 * 铁则：
 *  1. Bottom Sheet 与 原位评价面板 永不同时渲染；
 *  2. 单卡仅 浏览态(A) 或 评价激活态(B)；
 *  3. 横向滑动（卡片行 scroll）自动关闭评价面板；
 *  4. 页面返回 / Tab 切换（onHide）强制销毁全部状态。
 */
import { ref } from 'vue'

const evaluatingId = ref<number | null>(null)
const sheetOpen = ref(false)
const sheetMode = ref<'menu' | 'issue'>('menu')
const sheetRecordId = ref<number | null>(null)
const confirmOpen = ref(false)
const confirmRecordId = ref<number | null>(null)

/** 笑脸角标 → 进入原位评价态；同时收敛任何已开的 Sheet */
function openEval(id: number) {
  sheetOpen.value = false
  sheetRecordId.value = null
  evaluatingId.value = id
}

function closeEval() {
  evaluatingId.value = null
}

/** 长按卡片 → 唤起菜单；同时收敛任何已开的评价态 */
function openMenu(id: number) {
  evaluatingId.value = null
  sheetMode.value = 'menu'
  sheetRecordId.value = id
  sheetOpen.value = true
}

function closeSheet() {
  sheetOpen.value = false
  sheetRecordId.value = null
}

/** 菜单「反馈」→ 纠错表单（同一 Sheet 内容切换，不另起浮层） */
function openIssue() {
  sheetMode.value = 'issue'
}

/** 菜单「删除记忆」→ 关闭 Sheet、唤起高优先级二次确认弹窗 */
function requestDelete() {
  const id = sheetRecordId.value
  sheetOpen.value = false
  sheetRecordId.value = null
  if (id != null) {
    confirmRecordId.value = id
    confirmOpen.value = true
  }
}

function closeConfirm() {
  confirmOpen.value = false
  confirmRecordId.value = null
}

/** 页面隐藏 / 卸载：强制销毁全部交互状态 */
function resetAll() {
  evaluatingId.value = null
  sheetOpen.value = false
  sheetRecordId.value = null
  confirmOpen.value = false
  confirmRecordId.value = null
}

export function useMemoryUI() {
  return {
    evaluatingId,
    sheetOpen,
    sheetMode,
    sheetRecordId,
    confirmOpen,
    confirmRecordId,
    openEval,
    closeEval,
    openMenu,
    closeSheet,
    openIssue,
    requestDelete,
    closeConfirm,
    resetAll,
  }
}
