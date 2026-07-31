/** 提交反馈（project_spec.md §3.x.5：POST /feedback，需 STUDENT）
 * 对齐二期 FeedbackReq：type 取值 suggestion(功能建议)/error(内容纠错)/other(其他)/report(举报) */
export interface FeedbackSubmit {
  type: 'suggestion' | 'error' | 'other' | 'report'
  content: string
  contact?: string
  /** 举报关联类型（type=report 时必填，如 'moment'） */
  relatedType?: string
  /** 举报关联对象 ID（type=report 时必填） */
  relatedId?: number
}
