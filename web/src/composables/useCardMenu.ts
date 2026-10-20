/**
 * 卡片长按操作（规格见 docs/ui/web/公共组件与形态基线.md §2.3「长按操作浮层」）。
 *
 * 三通道同一入口：① 按住卡片 500ms（触屏 / 鼠标；期间移动 > 10px 或抬起即取消）；
 * ② 桌面右键；③ 键盘（卡片聚焦后 Enter / 空格 / 菜单键）。唤起底部浮层，
 * 浮层本身无红色警示，破坏性操作仍由视图走二次确认。
 *
 * 两个入口，共用同一套手势与浮层：
 * - `useCardMenu`：固定「编辑 / 删除」两类动作（主数据五页）；
 * - `useCardActions`：任意动作列表（纠错处理页：去改菜品 / 标记已处理 / 撤销）。
 */
import { reactive, ref } from 'vue'

/** 浮层里的一个动作行 */
export type CardAction = {
  key: string
  label: string
  disabled?: boolean
}

/** 浮层元信息：标题行 + 删除禁用态 */
export type CardMenuMeta = {
  /** 浮层标题（一般为行名，弱化展示） */
  title?: string
  /** 删除行禁用（如纠错选项被引用） */
  removeDisabled?: boolean
  /** 删除禁用原因（删除行下方弱化说明） */
  removeHint?: string
}

/** 按住时长（毫秒） */
const LONG_PRESS_MS = 500
/** 按住期间移动超过该像素视为滚动手势，取消长按 */
const MOVE_TOLERANCE = 10

/** 长按 / 右键 / 键盘三通道的手势处理器工厂（两条入口共用；`M` 为各自的元信息类型） */
function createPressHandlers<T, M extends CardMenuMeta>(
  row: T,
  meta: M,
  show: (row: T, meta: M) => void,
) {
  let timer: ReturnType<typeof setTimeout> | undefined
  let startX = 0
  let startY = 0

  function cancel() {
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }

  return {
    onPointerdown: (e: PointerEvent) => {
      startX = e.clientX
      startY = e.clientY
      cancel()
      timer = setTimeout(() => {
        timer = undefined
        show(row, meta)
      }, LONG_PRESS_MS)
    },
    onPointermove: (e: PointerEvent) => {
      if (Math.hypot(e.clientX - startX, e.clientY - startY) > MOVE_TOLERANCE) cancel()
    },
    onPointerup: cancel,
    onPointerleave: cancel,
    onPointercancel: cancel,
    onContextmenu: (e: MouseEvent) => {
      e.preventDefault()
      show(row, meta)
    },
    onKeydown: (e: KeyboardEvent) => {
      if (e.repeat) return
      if (e.key === 'Enter' || e.key === ' ' || e.key === 'ContextMenu') {
        e.preventDefault()
        show(row, meta)
      }
    },
  }
}

export function useCardMenu<T>(handlers: { onEdit: (row: T) => void; onRemove: (row: T) => void }) {
  const open = ref(false)
  const title = ref('')
  const removeDisabled = ref(false)
  const removeHint = ref('')
  /** 唤起浮层的那一行，pickEdit / pickRemove 时消费（不进 reactive，避免代理业务对象） */
  let current: T | null = null

  function show(row: T, meta: CardMenuMeta) {
    current = row
    title.value = meta.title ?? ''
    removeDisabled.value = meta.removeDisabled ?? false
    removeHint.value = meta.removeHint ?? ''
    open.value = true
  }

  function close() {
    open.value = false
  }

  function pickEdit() {
    open.value = false
    if (current) handlers.onEdit(current)
  }

  function pickRemove() {
    open.value = false
    if (current && !removeDisabled.value) handlers.onRemove(current)
  }

  /** 卡片手势处理器：平铺（`v-bind`）到卡片元素 */
  function pressAttrs(row: T, meta: CardMenuMeta = {}) {
    return createPressHandlers(row, meta, show)
  }

  // reactive 包装让模板里 cardMenu.open / cardMenu.title 自动解包（无需 .value）
  return reactive({
    open,
    title,
    removeDisabled,
    removeHint,
    pressAttrs,
    close,
    pickEdit,
    pickRemove,
  })
}

/** 任意动作列表版：`meta.actions` 决定浮层里显示哪些行 */
export function useCardActions<T>(onPick: (row: T, key: string) => void) {
  const open = ref(false)
  const title = ref('')
  const actions = ref<CardAction[]>([])
  let current: T | null = null

  function show(row: T, meta: CardMenuMeta & { actions: CardAction[] }) {
    current = row
    title.value = meta.title ?? ''
    actions.value = meta.actions
    open.value = true
  }

  function close() {
    open.value = false
  }

  function pick(key: string) {
    const row = current
    open.value = false
    const action = actions.value.find((a) => a.key === key)
    if (row && action && !action.disabled) onPick(row, key)
  }

  function pressAttrs(row: T, meta: CardMenuMeta & { actions: CardAction[] }) {
    return createPressHandlers(row, meta, show)
  }

  return reactive({ open, title, actions, pressAttrs, close, pick })
}
