/** 提交反馈（project_spec.md §3.x.5：POST /feedback，公开可提交，无需登录）
 * 2026-08-17 重设计：type 支持 add（新增菜品）一级枚举。
 * 2026-09-15 收口（spec §7.23）：写入口径四类 suggestion / add / error / report。
 * bug / other 为历史遗留枚举，**禁止新增写入**（后端对历史数据兼容读展示），
 * 故从可写类型中移除；纠错子项「其他」走 error + content 结构化文本承载，不占用独立枚举。
 * 「新增菜品」为独立一级类型 add（对应 content 结构化文本）；纠错/举报的二级信息走 content/related 字段。
 * 反馈支持配图（≤3 张 COS URL，2026-09-13 拍板恢复，见 spec §0.5 与 images 字段）。 */
export interface FeedbackSubmit {
  type: 'suggestion' | 'add' | 'error' | 'report'
  content: string
  /**
   * 二级选项（2026-09-15 DEV-01 补齐）：仅 type=suggestion 时上送，
   * 承载「提建议 idea / 报问题 problem」的二级语义（此前仅端上选中、提交时被丢弃）。
   * 契约：仅 suggestion 携带；其他类型不传（后端按 type 走白名单校验，非法值 400）。
   */
  sub?: 'idea' | 'problem'
  /** 反馈对象细分类型（error 信息纠错为 'dish'；type=report 举报为 'review'（评价）；未选实体可不传） */
  relatedType?: string
  /** 关联对象 ID（用户未选实体可不传；type=report 举报时按需填） */
  relatedId?: number
  /** 配图（COS URL，≤3 张；2026-09 恢复反馈配图，经 ImagePicker → /upload/images 安检后回传） */
  images?: string[]
}
