/**
 * 反馈 / 举报 / 纠错写入契约（三条互不耦合的链路）。
 *
 * - **意见反馈**（`POST /feedback`）：3 类型（bug / suggestion / other）+ 描述 + 截图（≤1 张）
 * - **评价举报**（`POST /reviews/{id}/report`）：RESTful 子资源，举报对象（评价 ID）在路径中，`reason` 必选
 * - **菜品纠错**（`POST /dishes/{id}/correction`）：**局部提交（只传改动项）**，描述属性经 `attributes`
 *   （键 = 维度 `fieldKey`）
 */

/**
 * 反馈类型三项。
 * - `value` **必须**落在服务端 `FeedbackConst.WRITABLE_TYPES` 内（非法值 400）
 * - `label` / `hint` / `placeholder` 为端上文案（当前不由服务端下发；将来若改由字典端点下发，
 *   只需替换本常量，渲染结构不变）
 */
export const FEEDBACK_TYPES = [
  {
    value: 'bug',
    label: '程序功能Bug',
    hint: '页面、图片、评价等程序异常',
    placeholder: '请描述bug现象、复现步骤',
  },
  {
    value: 'suggestion',
    label: '产品功能建议',
    hint: '新功能、交互优化好点子',
    placeholder: '描述你希望新增或改动的功能想法',
  },
  {
    value: 'other',
    label: '其他相关问题',
    hint: '其余平台相关问题反馈',
    placeholder: '描述你遇到的平台相关问题',
  },
] as const

/** 反馈类型机器值（与后端写入值域同源） */
export type FeedbackType = (typeof FEEDBACK_TYPES)[number]['value']

/**
 * 菜品纠错 payload。
 *
 * **局部提交（patch）**：只含用户改动过的字段，未传 = 保持原值 ⇒ 字段全部选填；
 * 空请求体（无任何改动项）后端返回 400「未提交任何改动」。
 * 请求体**无 `type` / 无 `dishId`**（`dishId` 在路径中）。
 */
export interface DishCorrectionPayload {
  /** 菜品名称（改动时传；敏感词由后端 400 message 直透） */
  name?: string
  /** 价格，单位 = **分**（端上以元填写，提交前经 `yuanToFen` 转分 —— 金额红线） */
  price?: number
  /** 食堂名（自由文本，无字典 / 无 picker） */
  canteenName?: string
  /** 档口名（自由文本） */
  stallName?: string
  /** 楼层（自由文本；**归属档口 `stall.floor`**，非菜品字段）：采纳时写回目标档口，同档口其他菜品一并生效 */
  floor?: string
  /** 动态描述属性：键 = 维度 `fieldKey`，值 = 中文文本（single）或中文数组（multi）；仅含改动维度 */
  attributes?: Record<string, string | string[]>
  /** 图片 URL 数组（≤3 张；经 ImagePicker → 上传安检） */
  images?: string[]
}

/** 意见反馈提交（纯反馈三类型）；值域 = 服务端 `FeedbackConst.WRITABLE_TYPES`，非法 / 历史类型 400 */
export interface FeedbackSubmit {
  type: 'bug' | 'suggestion' | 'other'
  /** 反馈内容（必填，端上 ≤600 字 / 服务端 ≤1000 字） */
  content: string
  /** 截图（COS URL，≤1 张） */
  images?: string[]
}

/** 评价举报请求体：举报对象在**路径**中；请求体无 `type` / `relatedType` / `relatedId` */
export interface ReportPayload {
  /** 举报原因机器值（必选，字典端点 `GET /report-reasons` 下发项） */
  reason: string
}
