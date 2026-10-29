/**
 * 卡片操作浮层触发（规格见 docs/ui/web/公共组件与形态基线.md §2.3「卡片操作浮层」）。
 *
 * 单一入口：① **点击卡片**；② 键盘（卡片聚焦后 Enter / 空格）。
 * 浮层本身无红色警示，破坏性操作仍由视图侧走二次确认。
 *
 * 两个入口，共用同一套触发与浮层：
 * - `useCardMenu`：固定「编辑 / 删除」两类动作（主数据页）；
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

/** 点击 / 键盘两通道的触发处理器工厂（两条入口共用；`M` 为各自的元信息类型） */
function createOpenHandlers<T, M extends CardMenuMeta>(
  row: T,
  meta: M,
  show: (row: T, meta: M) => void,
) {
  return {
    onClick: () => show(row, meta),
    onKeydown: (e: KeyboardEvent) => {
      if (e.repeat) return
      if (e.key === 'Enter' || e.key === ' ') {
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

  /** 卡片触发器：平铺（`v-bind`）到卡片元素 */
  function openAttrs(row: T, meta: CardMenuMeta = {}) {
    return createOpenHandlers(row, meta, show)
  }

  // reactive 包装让模板里 cardMenu.open / cardMenu.title 自动解包（无需 .value）
  return reactive({
    open,
    title,
    removeDisabled,
    removeHint,
    openAttrs,
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

  function openAttrs(row: T, meta: CardMenuMeta & { actions: CardAction[] }) {
    return createOpenHandlers(row, meta, show)
  }

  return reactive({ open, title, actions, openAttrs, close, pick })
}
