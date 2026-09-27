/**
 * 提交反馈（project_spec.md §3.x.5）。
 *
 * 意见反馈页收敛为两段式：「我要反馈问题（issue）」+「我要更新信息（update）」。
 * - issue：`POST /feedback`，纯文本 + 配图（≤3 张）。
 * - update：`POST /dishes/{id}/correction`，请求体七字段平铺（无 type、无 dishId 字段，dishId 在路径）；
 *   档口 / 食堂均为自由文本（不依赖字典端点）。
 *
 * type=report 为评价举报链路（详情页 useReport）专用写入口径，不在意见反馈页内。
 */

/**
 * 意见反馈页「反馈类型」四项（2026-09-27 单表单改版）。
 *
 * - `value` **必须**落在服务端 `FeedbackConst.WRITABLE_TYPES` 内（非法值 400）；
 * - `label` / `hint` / `placeholder` 为**端上文案**（当前不由服务端下发；若后续要「改文案不发版」，
 *   由服务端字典端点下发后替换本常量即可，端上渲染结构不变）；
 * - `hint` 为选项括号内的说明，缺省不渲染。
 */
export const FEEDBACK_TYPES = [
  {
    value: 'bug',
    label: '小程序功能Bug',
    hint: '页面报错、图片加载、评价展示/提交异常等程序问题',
    placeholder: '请描述bug现象、复现步骤，有截图可以附上',
  },
  {
    value: 'suggestion',
    label: '产品功能建议',
    hint: '',
    placeholder: '描述你希望新增或改动的功能想法',
  },
  {
    value: 'error',
    label: '菜品信息纠错',
    hint: '菜品名称、价格、档口、配图等资料错误',
    placeholder: '写明菜品名称、错误内容以及正确信息',
  },
  {
    value: 'other',
    label: '其他平台相关问题',
    hint: '',
    placeholder: '描述你遇到的平台相关问题',
  },
] as const

/** 反馈类型机器值（取自 {@link FEEDBACK_TYPES}，与后端写入值域同源） */
export type FeedbackType = (typeof FEEDBACK_TYPES)[number]['value']

/** 菜品纠错 payload（`POST /dishes/{id}/correction` 请求体；七字段平铺，字段值为用户改后的差异项） */
export interface DishCorrectionPayload {
  /** 菜品名称（预填详情当前值，用户可改；敏感词由后端 400 message 直透） */
  name: string
  /** 价格，单位 = **分**（端上以元填写，提交前经 yuanToFen 转分，金额红线） */
  price: number
  /** 食堂名（自由文本，预填详情 canteenName） */
  canteenName: string
  /** 档口名（自由文本，预填详情 stallName；无 stallId） */
  stallName: string
  /** 口味标签（预填详情机器值 + 用户自由输入项，可增删） */
  flavorTags: string[]
  /** 食材（预填详情机器值 + 用户自由输入项，可增删） */
  ingredients: string[]
  /** 图片 URL（预填菜品现有图 + 用户新增，经 ImagePicker → /upload/cloud-image 安检） */
  images: string[]
}

export type FeedbackSubmit =
  /**
   * 意见反馈页四种类型（2026-09-27 单表单改版）：纯文本 + 配图（≤1 张）。
   * 值域 = 服务端 `FeedbackConst.WRITABLE_TYPES` 的子集，非法值 400。
   */
  | {
      type: 'bug' | 'suggestion' | 'error' | 'other'
      /** 反馈内容（必填，≤1000 字） */
      content: string
      /** 配图（COS URL，≤1 张；经上传安检后回传） */
      images?: string[]
    }
  /** 我要反馈问题（历史写入值）：纯文本 + 配图（≤3 张 COS URL） */
  | {
      type: 'issue'
      /** 反馈内容（必填） */
      content: string
      /** 配图（COS URL，≤3 张；经 ImagePicker → /upload/cloud-image 安检后回传） */
      images?: string[]
    }
  /** 评价举报（菜品详情页举报弹层，非意见反馈页）：以结构化原因单选为准（sub），content 可空 */
  | {
      type: 'report'
      content?: string
      /** 举报原因机器值（字典端点 `GET /feedback/report-reasons` 下发） */
      sub?: string
      /** 举报对象类型：'review'（评价） */
      relatedType?: string
      /** 关联对象 ID */
      relatedId?: number
    }
