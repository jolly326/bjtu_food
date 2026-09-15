/** 用户信息（user.role 列已退役，2026-09-15：全量用户即学生，类型不再含 role 字段） */
export interface UserInfo {
  id: number
  /** 学号/工号（校园身份，等于邮箱前缀）；游客态为 'wx_'+openid 尾 16 位 */
  username: string
  /** 校园邮箱（{学号}@bjtu.edu.cn）；游客态未认证时为空 */
  email: string
  nickname: string
  avatar: string
  /** 是否已邮箱认证（微信登录体系 §5.y）：true 解锁 UGC 写操作 */
  verified: boolean
  /** 已认证绑定邮箱（bind_email，仅展示用，不公开传播）；未认证为 undefined */
  bindEmail?: string
  /** 游客展示短 ID（后端「食客+ID 尾 4 位」）；未提供时前端本地游客 ID 兜底 */
  guestShortId?: string
}
