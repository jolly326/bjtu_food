import { DEFAULT_NICKNAME, GUEST_NICKNAME, GUEST_SUB_TEXT, EMPTY_FIELD_TEXT } from '@/constants/copy'

/**
 * 身份卡的展示口径（跨页唯一实现）。
 *
 * 「我的」页用户信息模块与「我的主页」用户信息卡是同一身份版面的两种形态，
 * 本文件统一收口其主行 / 副行取值，两页只调用以下两个函数。
 */

/** 主行昵称：认证态缺省「食客」，游客态缺省「游客」 */
export function displayNickname(nickname: string | undefined, verified: boolean): string {
  return nickname || (verified ? DEFAULT_NICKNAME : GUEST_NICKNAME)
}

/** 副行：认证态 = 校园邮箱（空则占位符）；游客态 = 固定引导文案 */
export function displaySubLine(bindEmail: string | undefined, verified: boolean): string {
  if (!verified) return GUEST_SUB_TEXT
  return bindEmail || EMPTY_FIELD_TEXT
}
