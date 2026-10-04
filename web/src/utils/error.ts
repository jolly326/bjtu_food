import { ElMessage } from 'element-plus'

/**
 * 统一失败兜底：优先展示异常消息，缺失时回退到调用方给定的文案（默认「操作失败」）。
 * 替代各视图里散落的 `ElMessage.error(e instanceof Error ? e.message : '…')` 同模式。
 */
export function fail(e: unknown, fallback = '操作失败'): void {
  ElMessage.error(e instanceof Error ? e.message : fallback)
}
