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
  /** 我要反馈问题：纯文本 + 配图（≤3 张 COS URL） */
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
