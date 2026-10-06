/**
 * 反馈 / 举报 / 菜品问题反馈写入契约（三条互不耦合的链路）。
 *
 * - **意见反馈**（`POST /feedback`）：3 类型（bug / suggestion / other）+ 描述 + 截图（≤1 张）
 * - **评价举报**（`POST /reviews/{id}/report`）：RESTful 子资源，举报对象（评价 ID）在路径中，`reason` 必选
 * - **菜品问题反馈**（`POST /dishes/{id}/correction`）：**先选 `type`**；`field` 局部提交（只传改动项），
 *   描述属性经 `attributes`（键 = 维度 `fieldKey`）；`gone` 一键提交（仅选填 `note` / `images`）
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
 * 菜品问题反馈类型（`dish_correction.type`；与后端 `CorrectionConst.QUERY_TYPES` 同源）
 *
 * - `field`：**信息有误** —— 局部提交改动项，表单完整
 * - `gone`：**已经下架** —— **一键提交即可成立**，说明与图片均为**选填**
 */
export type DishProblemType = 'field' | 'gone'

/**
 * 菜品问题反馈 payload（`POST /dishes/{id}/correction`）。
 *
 * **`type` 必填**，两类字段集合完全不同：
 *
 * - **`field`（信息有误）· 局部提交（patch）**：只含用户改动过的字段，未传 = 保持原值 ⇒ 全部选填；
 *   无任何改动项时后端返回 400「未提交任何改动」；`images` ≤5 张。
 * - **`gone`（已经下架）· 一键提交**：仅用 `note`（≤200 字）与 `images`（≤3 张），
 *   **均为选填、允许全不传**（提交即成立）；传任何差异项字段 → 400。
 *
 * 请求体**无 `dishId`**（在路径中）。
 */
export interface DishCorrectionPayload {
  /** 问题类型（**必填**；不做「不传即 field」的兜底，避免端上漏传导致语义错位） */
  type: DishProblemType
  /** 菜品名称（`field` 型改动时传；敏感词由后端 400 message 直透） */
  name?: string
  /** 价格，单位 = **分**（端上以元填写，提交前经 `yuanToFen` 转分 —— 金额红线） */
  price?: number
  /** 食堂名（`field` 型；自由文本，无字典 / 无 picker） */
  canteenName?: string
  /** 档口名（`field` 型；自由文本） */
  stallName?: string
  /** 楼层（`field` 型；**受控字典值·值即汉字**，归属档口 `stall.floor`）：采纳时写回目标档口，同档口其他菜品一并生效 */
  floor?: string
  /** 动态描述属性（`field` 型）：键 = 维度 `fieldKey`，值 = 中文文本（single）或中文数组（multi）；仅含改动维度 */
  attributes?: Record<string, string | string[]>
  /**
   * 图片 URL 数组（经 ImagePicker → 上传安检）。
   * `field` 型 ≤5 张（改动后的完整数组）；`gone` 型 **≤3 张**（选填补充）。
   */
  images?: string[]
  /**
   * 补充说明（**仅 `gone` 型**，≤200 字，选填）。
   *
   * 用途：让管理员免跑现场即可判断「变成了别的菜 / 换窗口了 / 今天临时没供」——
   * 这三种情况若只靠「一键提交」会被压平成同一信号，导致误下架。
   * ⚠️ **不得设为必填** —— 一旦必填，用户成本从「点一下」回升到「填表」，提交量将大幅下降。
   */
  note?: string
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
