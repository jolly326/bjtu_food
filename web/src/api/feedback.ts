import { get, put } from './http'
import { imagesToList, pageRecords } from './adapter'

/**
 * 反馈处理（task-09 Web · 反馈闭环 W1；prelaunch-loop-closure 收口 UGC 图片链下线）。
 * 列表 GET /admin/feedbacks（status/type 过滤）；
 * 处理 PUT /admin/feedbacks/{id}（status=handled + reply）。
 * 后端出参 camelCase：FeedbackAdminVO{ id, userId, userNickname, type, sub, content, images, status, reply, createdAt, handledAt, relatedType, relatedId, relatedDishName }。
 * 2026-09-16（产品定型）：不再收集联系方式，user_feedback 联系方式列随服务端删列退役，前端同步移除读写。
 * sub（DEV-01）：建议二级类型 idea/problem，仅 type=suggestion 有值；可选字段，缺省不展示。
 * relatedType/relatedId 用于举报类反馈（report）关联被举报评价（review）；信息纠错（error）关联菜品（dish）。
 * relatedDishName（DEV-04 收口）：仅 relatedType='dish' 时由后端批量回填（含已下架菜品），
 * 前端不再走公开端点 GET /dishes/{id} 取名（Web 只经 /admin/**，且公开端点查不到下架菜品）。
 * images 为用户上传配图（COS 公网地址数组）。
 *
 * 2026-09-15（取消人工复核）：内容机检放行态与待复核态均放行、仅风险项拒绝，
 * 反馈的安检状态不再由后台消费，出参字段与本模块的查询参数一并移除。
 */

export interface FeedbackAdminVO {
  id: number
  /** 匿名反馈为 undefined（WA-02：不再归一为 0，UI 空值显示「游客」） */
  userId?: number
  userNickname: string
  type: string
  /**
   * 二级类型（DEV-01）：仅 type='suggestion'（功能建议）有值 —— idea=想法 / problem=问题。
   * 后端字段为可选（历史数据、其他类型均无值），故前端一律按可选字段容错：缺省/非法值 → undefined（UI 不展示）。
   */
  sub?: 'idea' | 'problem'
  content: string
  /** 用户上传配图（adapter 归一为 string[]，COS 公网地址可直接展示） */
  images: string[]
  status: string
  reply: string
  /** 处理结论（§7.23 第 5 条）：handled=已处理；rejected=不采纳/退回（未回显时为 undefined） */
  outcome?: string
  /** 不采纳原因（outcome=rejected 时由后端填写；随回执一并向提交人展示） */
  rejectReason?: string
  createdAt: string
  handledAt: string
  relatedType?: string
  relatedId?: number
  /**
   * 关联菜品名（DEV-04）：仅 relatedType='dish' 时由后端填充（服务端批量查询，含已下架菜品）。
   * 为空/缺省（历史数据、其他关联类型）时由视图层退回「菜品#id」占位，不做二次请求。
   */
  relatedDishName?: string
}

/**
 * 二级类型归一（DEV-01，沿用 snake→camel 归一样式）：
 * 白名单收窄 idea / problem，其余（含未落库的 null、后端未就绪时的字段缺失）→ undefined，
 * 由视图层决定是否展示（UI 侧再按 type='suggestion' 限定层级归属）。
 */
function normalizeSub(raw: any): 'idea' | 'problem' | undefined {
  const v = raw.sub ?? raw.subType ?? raw.sub_type
  return v === 'idea' || v === 'problem' ? v : undefined
}

function feedbackToLegacy(raw: any): FeedbackAdminVO {
  return {
    id: raw.id,
    userId: raw.userId ?? raw.user_id ?? undefined,
    userNickname: raw.userNickname || '',
    type: raw.type || 'other',
    sub: normalizeSub(raw),
    content: raw.content || '',
    images: imagesToList(raw.images),
    status: raw.status || 'pending',
    reply: raw.reply || '',
    outcome: raw.outcome ?? undefined,
    rejectReason: (raw.rejectReason ?? raw.reject_reason) || undefined,
    createdAt: raw.createdAt ?? raw.created_at ?? '',
    handledAt: raw.handledAt ?? raw.handled_at ?? '',
    relatedType: raw.relatedType ?? raw.related_type ?? undefined,
    relatedId: raw.relatedId ?? raw.related_id ?? undefined,
    // 空串归一为 undefined（视图层只判真值），沿用既有 snake→camel 归一样式
    relatedDishName: (raw.relatedDishName ?? raw.related_dish_name) || undefined,
  }
}

/** 反馈列表（分页，按 status / type / userId 过滤） */
export async function listFeedbacks(params: {
  status?: string
  type?: string
  userId?: number
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<{ list: FeedbackAdminVO[]; total: number }> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  if (params.status) query.status = params.status
  if (params.type) query.type = params.type
  if (params.userId != null) query.userId = params.userId
  if (params.keyword) query.keyword = params.keyword
  const data: any = await get('/admin/feedbacks', query)
  return {
    list: pageRecords(data).map(feedbackToLegacy),
    total: (data as any)?.total ?? pageRecords(data).length,
  }
}

/**
 * 标记处理/回复（RB18 收敛：仅 JSON body；路径与方法不变）。
 * §7.23 第 5 条（2026-09-15）：处理 = 标记 handled + 回执。
 *  - `reply` 必填（1~1000 字）；
 *  - `outcome`：'handled'=通过/已处理（缺省）；'rejected'=不采纳/退回；
 *  - `rejectReason`：outcome='rejected' 时必填（1~200 字，纯空白视为未填写 → 后端 400），
 *    随回执一并向提交人展示；outcome='handled' 时不消费（后端保持落库 NULL）。
 */
export async function handleFeedback(
  id: number,
  reply: string,
  options: { outcome?: 'handled' | 'rejected'; rejectReason?: string } = {},
) {
  const body: Record<string, string> = { reply }
  if (options.outcome) body.outcome = options.outcome
  if (options.rejectReason) body.rejectReason = options.rejectReason
  await put<void>(`/admin/feedbacks/${id}`, body)
}
