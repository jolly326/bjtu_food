/**
 * 错误文案与失败提示的**统一出口**（UI 统一 Loop Round 17 · 代码质量轮）
 *
 * 背景：`catch (e: any) { uni.showToast({ title: e?.message || 'xx失败' }) }` 这类样板原先在
 * 认证页 / 资料页 / 我的评价 / 菜品详情 / 评价编辑器 / 图片上传等处各写一份 ——
 * 既重复，口径也不一致（有的 `e.message`、有的 `e?.message`），且 `any` 让错误对象类型不可信
 * （`e` 为 `null` 时会直接抛 `Cannot read properties of null`，把「删除失败」变成未捕获异常）。
 *
 * 约定：
 * · 后端业务错误（HTTP 层已把业务 `message` 透出到 `Error`）→ **直接展示**，便于用户理解
 *   （如「内容包含违规信息，请修改后重试」「图片包含违规内容，无法上传」）；
 * · 非 Error / 无 message / 空白 message（网络中断、平台回调失败）→ 回落调用方给的固定文案。
 */

/** 从任意错误对象安全取展示文案：`Error.message` 优先，缺省回落 `fallback` */
export function errorMessage(e: unknown, fallback = '操作失败，请稍后重试'): string {
  const raw = e instanceof Error ? e.message : (e as { message?: unknown } | null | undefined)?.message
  return typeof raw === 'string' && raw.trim() ? raw : fallback
}

/** 失败提示统一形态：`errorMessage` 文案 + `icon: 'none'` 的 Toast */
export function toastError(e: unknown, fallback?: string): void {
  uni.showToast({ title: errorMessage(e, fallback), icon: 'none' })
}
