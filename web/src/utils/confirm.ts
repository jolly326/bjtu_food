import { ElMessageBox } from 'element-plus'

export interface ConfirmOptions {
  /** 弹窗标题（默认「删除确认」） */
  title?: string
  /** 确认按钮文案（默认「删除」） */
  confirmText?: string
}

/**
 * 统一的危险操作二次确认弹窗：警告样式 + 固定「取消」按钮。
 * 各业务保留自定义正文与确认文案，仅收敛样式与按钮档位，避免逐页散落重复配置。
 * 取消 / 关闭均 reject，由调用方 `catch { return }` 统一吞掉。
 */
export async function confirmDelete(message: string, opts: ConfirmOptions = {}): Promise<void> {
  await ElMessageBox.confirm(message, opts.title ?? '删除确认', {
    type: 'warning',
    confirmButtonText: opts.confirmText ?? '删除',
    cancelButtonText: '取消',
  })
}
