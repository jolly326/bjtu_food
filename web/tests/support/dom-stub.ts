/**
 * 最小 DOM 存根（**仅供测试**）：让「以 DOM 为载体的状态机」在 node 环境下可测。
 *
 * <p>**为什么不用 happy-dom**：本轮测试对象是浮层层级栈与背景滚动锁这类**状态机**，
 * 只需要 `window.addEventListener` / `dispatchEvent` 与 `document.body.style.overflow` 三个能力；
 * 引入一整个 DOM 实现只为这三点并不划算（且会拖慢测试进程）。
 * 将来若要做组件渲染测试，按文件头切 `happy-dom` 环境即可，届时删掉本文件。
 *
 * <p>`window` 用原生 `EventTarget` 实现 —— 事件注册 / 派发 / 注销语义与浏览器一致，
 * 不会为了让断言通过而“假实现”掉冒泡与捕获阶段。
 */
class Target extends EventTarget {}

/** 安装存根；返回卸载函数（恢复原来的全局值，避免用例间串味） */
export function installDomStub(): () => void {
  const g = globalThis as unknown as Record<string, unknown>
  const prevWindow = g.window
  const prevDocument = g.document

  const win = new Target()
  const doc = { body: { style: { overflow: '' } } }

  g.window = win
  g.document = doc

  return () => {
    g.window = prevWindow
    g.document = prevDocument
  }
}

/**
 * 派发一次 ESC（走真实事件路径，含捕获阶段）。
 *
 * ⚠️ 用 `Event` 再显式挂 `key` 而非 `KeyboardEvent`：node 环境**没有** `KeyboardEvent` 原生实现，
 * 而浮层只消费 `e.key` 这一个字段，构造一个同形状的 `Event` 即可，不必为此引入 DOM 实现。
 */
export function pressEscape(): void {
  const e = new Event('keydown') as Event & { key?: string }
  e.key = 'Escape'
  const g = globalThis as unknown as { window: EventTarget }
  g.window.dispatchEvent(e)
}