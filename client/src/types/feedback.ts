/** 提交反馈（project_spec.md §3.x.5：POST /feedback，需 STUDENT）
 * 对齐二期 FeedbackReq：type 只传枚举 suggestion(功能建议)/error(内容纠错)/bug(系统问题)/other(其他)/report(举报)，
 * 禁止复合串（后端 admin 按枚举过滤，复合串匹配不到致审核闭环断裂）。
 * 二级"反馈对象"映射 relatedType（dish/stall/canteen），三级"操作类型"（新增菜品/信息有误）并入 content 前缀，均不塞进 type。 */
export interface FeedbackSubmit {
  type: 'suggestion' | 'error' | 'bug' | 'other' | 'report'
  content: string
  contact?: string
  /** 反馈对象细分类型（dish/stall/canteen，未选实体可不传；type=report 举报时可为 'moment' 等） */
  relatedType?: string
  /** 关联对象 ID（用户未选实体可不传；type=report 举报时按需填） */
  relatedId?: number
}
