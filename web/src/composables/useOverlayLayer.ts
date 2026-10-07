import { onBeforeUnmount, watch, type Ref } from 'vue'

/**
 * 浮层层级与背景滚动锁（**全站唯一实现**，`BaseDrawer` / `BaseModal` / `ImagePreview` 共用）。
 *
 * <p>**存在理由**：浮层可叠加（详情抽屉上再开处置弹窗、抽屉里再开大图预览）。若各组件各自
 * `document.body.style.overflow = 'hidden' / ''`，关闭内层时会把**外层仍需要的锁一起抹掉**；
 * 且各自在 `window` 上挂非捕获阶段 `keydown` 时，一次`ESC` 会**同时**触发所有层的关闭。
 *
 * <p>**两条口径**：
 *  1. **层级**：后开浮层压在先开之上（栈顶 = 最上层）。`ESC` 只由**栈顶**层响应 ⇒ 一次 `ESC` 只关一层。
 *  2. **背景滚动锁**：**引用计数** + 保存进入最外层前的原始值；计数归零时**原样还原**，不写死 `'hidden'` / `''`。
 *     ⇒ 内层关闭不破坏外层的锁；退出最外层时页面原始 `overflow`（可能是 `auto`）被正确还原。
 *
 * <p>`ESC` 监听统一走**捕获阶段**：栈顶判定与监听阶段解耦，下层即便仍在监听也不会误响应。
 *
 * 口径真源：docs/ui/web/公共组件与形态基线.md §1.10（浮层共用口径：锁背景滚动 / ESC 关闭）
 */

/** 已打开浮层的 id 栈（后进先出；栈顶 = 最上层） */
const stack: string[] = []

/** 背景滚动锁的引用计数 + 进入最外层前的原始 `overflow` 值 */
let lockCount = 0
let prevOverflow = ''

/**
 * 入栈并上锁。
 *
 * ⚠️ **必须幂等**：`useOverlayLayer(open)` 的 watch 用 `{ immediate: true }`，浮层以 `open=false`
 * 挂载时就可能先走一次 `detach`；卸载时 `onBeforeUnmount(detach)` 又走一次。若 `acquire` 不判重，
 * 同一层重复入栈会把引用计数虚增 ⇒ 后面怎么关都关不掉锁（背景永久不可滚）。
 */
function acquire(id: string): void {
  if (stack.includes(id)) return
  if (lockCount === 0) prevOverflow = document.body.style.overflow
  stack.push(id)
  lockCount += 1
  document.body.style.overflow = 'hidden'
}

/**
 * 出栈并解锁；计数归零时把 `overflow` **原样**还原为进入最外层前的值。
 *
 * ⚠️ **必须幂等**：对本层之外的 `detach`（重复调用 / 未入栈就调用）必须直接返回 ——
 * 否则会把**别的浮层的锁**提前减掉（表现为：内层一关，外层抽屉打开期间背景就能滚了）。
 */
function release(id: string): void {
  const i = stack.indexOf(id)
  if (i < 0) return
  stack.splice(i, 1)
  lockCount -= 1
  if (lockCount === 0) {
    document.body.style.overflow = prevOverflow
    prevOverflow = ''
  }
}

/** 本浮层是否栈顶（最上层） */
function isTop(id: string): boolean {
  return stack[stack.length - 1] === id
}

/** 组件内唯一浮层 id（同组件多实例时后缀区分，避免 id 相撞导致栈失真） */
let seq = 0

export interface OverlayLayer {
  /** 本浮层的层 id */
  id: string
  /** 入栈 + 上锁 + 挂ESC（幂等由调用方保证） */
  attach: () => void
  /** 出栈 + 解锁 + 摘ESC */
  detach: () => void
  /** 本层是否栈顶；供浮层自有的其它键盘处理（如大图的方向键）做守卫 */
  isTop: () => boolean
}

/**
 * 把一个浮层接入层级栈与背景滚动锁。
 *
 * @param prefix 浮层种类前缀（`drawer` / `modal` / `preview`），仅用于生成可读的层 id
 * @param open 可见态；传 `null` 表示「挂载即开」，由调用方自行 `attach` / `detach`
 * @param onEscape `ESC` 响应（**仅栈顶层会被调用**）
 */
export function useOverlayLayer(
  prefix: string,
  open: Ref<boolean> | null,
  onEscape: () => void,
): OverlayLayer {
  seq += 1
  const id = `${prefix}-${seq}`

  function onKeydown(e: KeyboardEvent): void {
    if (e.key !== 'Escape') return
    // 只由栈顶层响应：一次 ESC 只关最上面一层，不连带关闭下层抽屉 / 弹窗
    if (!isTop(id)) return
    e.stopPropagation()
    onEscape()
  }

  function attach(): void {
    acquire(id)
    window.addEventListener('keydown', onKeydown, true)
  }

  function detach(): void {
    window.removeEventListener('keydown', onKeydown, true)
    release(id)
  }

  if (open) {
    watch(
      open,
      (v) => {
        if (v) attach()
        else detach()
      },
      { immediate: true },
    )
    // `immediate: true` 的首次求值走 attach 分支时 open 可能已是 false ⇒ 此处只需兜底卸载路径
    onBeforeUnmount(detach)
  }

  return { id, attach, detach, isTop: () => isTop(id) }
}

/** 当前栈深（供调试与测试断言） */
export function overlayDepth(): number {
  return stack.length
}

/** 仅供测试：复位模块级状态（避免用例间串味） */
export function __resetOverlayState(): void {
  stack.length = 0
  lockCount = 0
  prevOverflow = ''
  document.body.style.overflow = ''
}
