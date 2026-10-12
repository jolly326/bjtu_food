import { GUEST_SUB_TEXT, EMPTY_FIELD_TEXT } from '@/constants/copy'

/**
 * 身份卡的展示口径（跨页唯一实现）。
 *
 * 「我的」页用户信息模块与「我的主页」用户信息卡是同一身份版面的两种形态，
 * 本文件统一收口其主行 / 副行取值，两页只调用以下两个函数。
 */

/**
 * 主行昵称：**直出后端 `name`，端上不兜底、不派生**。
 *
 * <p>后端保证该出参非空（新游客号默认「食客 + ID 尾 4 位」由后端生成），
 * 故端上既不按认证态兜「食客」，也 **SHALL NOT** 把游客态替换为「游客」
 * （基线 §三 身份卡：昵称口径的唯一真源在后端）。
 */
export function displayNickname(name: string | undefined): string {
  return name ?? ''
}

/** 副行：认证态 = 校园邮箱（空则占位符）；游客态 = 固定引导文案 */
export function displaySubLine(email: string | undefined, verified: boolean): string {
  if (!verified) return GUEST_SUB_TEXT
  return email || EMPTY_FIELD_TEXT
}
