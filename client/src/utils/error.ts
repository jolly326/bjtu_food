/**
 * 错误文案与失败提示的**统一出口**。
 *
 * 文案约定：
 * · 后端业务错误（请求层已把业务 `message` 透出到 `Error`）→ **直接展示**，便于用户理解
 *   （如「内容包含违规信息，请修改后重试」）；
 * · 非 Error / 无 message / 空白 message（网络中断、平台回调失败）→ 回落调用方给的固定文案。
 *
 * ⚠️ 错误入参一律 `unknown`：早期 `e: any` 会在 `e` 为 `null` 时抛
 * `Cannot read properties of null`，把「删除失败」变成未捕获异常。
 *
 * ⚠️ **`toastError` 对「请求层已提示过」的错误静默**：网络异常等由请求层
 * （`api/http.ts`）先弹过一次 toast 并抛 `SurfacedError`，若调用方再弹一次，
 * 一次失败会出现两条相同提示。故此处识别 `SurfacedError` 后直接返回，
 * 调用方 `catch` 无需各自判断（该契约见 `api/errors.ts`）。
 */
import { SurfacedError } from '@/api/errors'

/**
 * 该错误是否**已由请求层提示过**（`SurfacedError`）。
 *
 * 页面把错误渲染成**页内文案**（而非 toast）时，用它跳过重复呈现 ——
 * 否则请求层已弹过 toast、页面又显示一条同文案，等于一次失败用户看到两条。
 */
export function isSurfaced(e: unknown): boolean {
  return e instanceof SurfacedError
}

/** 从任意错误对象安全取展示文案：`Error.message` 优先，缺省回落 `fallback` */
export function errorMessage(e: unknown, fallback = '操作失败，请稍后重试'): string {
  const raw = e instanceof Error ? e.message : (e as { message?: unknown } | null | undefined)?.message
  return typeof raw === 'string' && raw.trim() ? raw : fallback
}

/** 提示统一出口：`icon: 'none'` 时长文案不会被裁成图标 */
function showNone(title: string): void {
  uni.showToast({ title, icon: 'none' })
}

/**
 * 失败提示：`errorMessage` 文案 + `icon: 'none'`。
 *
 * 请求层已提示过的错误（`SurfacedError`）**不再重复弹**。
 */
export function toastError(e: unknown, fallback?: string): void {
  if (e instanceof SurfacedError) return
  showNone(errorMessage(e, fallback))
}

/** 普通提示（非错误）：固定文案 + `icon: 'none'` */
export function toastInfo(title: string): void {
  showNone(title)
}

/** 成功提示：固定文案 + `icon: 'success'` */
export function toastSuccess(title: string): void {
  uni.showToast({ title, icon: 'success' })
}
