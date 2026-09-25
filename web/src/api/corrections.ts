import { get, post, put } from './http'
import { imagesToList, pageRecords } from './adapter'
import { parseCsv } from '@/constants'

/**
 * 信息纠错（从反馈列表拆出的独立处理链路）。
 *
 * 背景：原「菜品信息纠错」挂在 user_feedback（「信息更新」类型：提交快照 + 采纳端点），
 * 本轮后端同批重构为**独立资源** /admin/corrections，feedback 回归纯问题反馈（VO 删提交快照/目标菜品名）。
 *
 * 端点（ADM）：
 *  - GET  /admin/corrections            分页列表（status 筛选：pending / adopted / rejected）
 *  - POST /admin/corrections/{id}/adopt 两段式采纳（见 adoptCorrection）
 *  - PUT  /admin/corrections/{id}       拒绝（reply + rejectReason 必填，形态同 feedback rejected）
 *
 * 契约要点：
 *  - VO = { id, dishId, dishName, userId?, snapshot(七字段), status, reply, rejectReason, handledAt, createdAt }；
 *  - snapshot.price 后端单位为**分**，本层统一 /100 转元后下发（金额换算不出 API 层，视图零换算）；
 *  - snapshot.stallName / canteenName 为**提交文本**（不再像旧快照载荷只带档口 ID），
 *    视图层无需再拉 /admin/stalls 字典翻译提交侧；原菜品侧名称由 DishAdminVO 联表字段直供。
 */

/** 用户提交的菜品信息快照（七字段；price 已由本层分→元） */
export interface CorrectionSnapshot {
  name?: string
  /** 价格（**元**，后端分为本层已 /100） */
  price?: number
  /** 提交的目标食堂名（文本） */
  canteenName?: string
  /** 提交的目标档口名（文本；两段式采纳的「新建档口」即用此文本） */
  stallName?: string
  flavorTags: string[]
  ingredients: string[]
  images: string[]
}

export interface CorrectionAdminVO {
  id: number
  /** 目标菜品 ID（视图层据此取原菜品做对比卡；采纳写回的目标） */
  dishId: number
  /** 目标菜品名（后端回填，含已下架/已删除菜品） */
  dishName: string
  /** 提交人（匿名提交为 undefined，UI 显示「游客」） */
  userId?: number
  /** 用户提交的七字段快照 */
  snapshot: CorrectionSnapshot
  /** pending / adopted / rejected */
  status: string
  reply: string
  /** 拒绝原因（status=rejected 时由后端填写） */
  rejectReason?: string
  /** 处理时间（已处理非空） */
  handledAt: string
  createdAt: string
}

/** 档口候选（两段式采纳：提交档口文本未命中现有档口时下发） */
export interface StallCandidate {
  id: number
  name: string
}

/**
 * 采纳响应（两段式）：
 *  - 直接采纳成功：data 为空 → 本函数返回 undefined；
 *  - 档口文本未命中且未带确认参数：HTTP 200 + data = { needStallConfirm, candidates }，
 *    **未执行采纳** —— 视图层弹档口确认对话框，选定后重发 adopt（带 stallId 或 createIfMissing）。
 */
export interface AdoptResult {
  needStallConfirm?: boolean
  candidates?: StallCandidate[]
}

function normalizeSnapshot(raw: any): CorrectionSnapshot {
  const s = raw?.snapshot ?? {}
  if (!s || typeof s !== 'object') {
    return { flavorTags: [], ingredients: [], images: [] }
  }
  return {
    name: s.name || undefined,
    // 分 → 元（金额换算收口在 API 层，视图零换算）
    price: s.price == null ? undefined : Math.round(s.price) / 100,
    canteenName: (s.canteenName ?? s.canteen_name) || undefined,
    stallName: (s.stallName ?? s.stall_name) || undefined,
    flavorTags: parseCsv(s.flavorTags ?? s.flavor_tags),
    ingredients: parseCsv(s.ingredients ?? s.ingredients_json),
    images: imagesToList(s.images),
  }
}

function toVO(raw: any): CorrectionAdminVO {
  return {
    id: raw.id,
    dishId: raw.dishId ?? raw.dish_id,
    dishName: (raw.dishName ?? raw.dish_name) || '',
    userId: raw.userId ?? raw.user_id ?? undefined,
    snapshot: normalizeSnapshot(raw),
    status: raw.status || 'pending',
    reply: raw.reply || '',
    rejectReason: (raw.rejectReason ?? raw.reject_reason) || undefined,
    handledAt: raw.handledAt ?? raw.handled_at ?? '',
    createdAt: raw.createdAt ?? raw.created_at ?? '',
  }
}

/** 纠错列表（分页，status 筛选：pending / adopted / rejected） */
export async function listCorrections(params: {
  status?: string
  page?: number
  pageSize?: number
}): Promise<{ list: CorrectionAdminVO[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.status) query.status = params.status
  const data: any = await get('/admin/corrections', query)
  return {
    list: pageRecords(data).map(toVO),
    total: (data as any)?.total ?? pageRecords(data).length,
  }
}

/**
 * 采纳（两段式，见 AdoptResult 说明）：
 *  - 第一段：`adoptCorrection(id)` —— 档口文本命中现有档口 → 直接采纳（返回 undefined）；
 *    未命中 → 返回 { needStallConfirm, candidates }，**未执行采纳**；
 *  - 第二段：`adoptCorrection(id, { stallId })` 选候选档口，或
 *    `adoptCorrection(id, { createIfMissing: true })` 确认按提交文本新建档口。
 * 前置不符（菜品已删除 4001 等）→ 400，message 直透由视图层 toast 展示。
 */
export async function adoptCorrection(
  id: number,
  options: { stallId?: number; createIfMissing?: boolean } = {},
): Promise<AdoptResult | undefined> {
  const body: Record<string, unknown> = {}
  if (options.stallId != null) body.stallId = options.stallId
  if (options.createIfMissing) body.createIfMissing = true
  const data = await post<AdoptResult | null>(`/admin/corrections/${id}/adopt`, body)
  return data || undefined
}

/**
 * 拒绝（形态同 feedback rejected）：reply 与 rejectReason 均必填（后端 @NotBlank）。
 */
export async function rejectCorrection(id: number, reply: string, rejectReason: string): Promise<void> {
  await put<void>(`/admin/corrections/${id}`, { reply, outcome: 'rejected', rejectReason })
}
