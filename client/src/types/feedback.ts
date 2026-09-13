/** 提交反馈（project_spec.md §3.x.5：POST /feedback，公开可提交，无需登录）
 * 2026-08-17 重设计：type 支持 add（新增菜品）一级枚举。
 * 对齐后端 FeedbackReq：type 只传枚举 suggestion/error/add/bug/other/report，禁止复合串。
 * 「新增菜品」为独立一级类型 add（对应 content 结构化文本）；纠错/举报的二级信息走 content/related 字段。
 * UGC 图片已全量下线，反馈仅提交纯文本（见 project_spec §0.5）。 */
export interface FeedbackSubmit {
  type: 'suggestion' | 'error' | 'add' | 'bug' | 'other' | 'report'
  content: string
  /** 反馈对象细分类型（error 信息纠错为 'dish'；type=report 举报为 'review'（评价）；未选实体可不传） */
  relatedType?: string
  /** 关联对象 ID（用户未选实体可不传；type=report 举报时按需填） */
  relatedId?: number
  /** 配图（COS URL，≤3 张；2026-09 恢复反馈配图，经 ImagePicker → /upload/images 安检后回传） */
  images?: string[]
}
