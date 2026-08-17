import { get, put, del } from './http'
import { pageRecords } from './adapter'

/**
 * 社区动态管理（task-06 Web · 动态管理/下架 W5）。
 * 注意区分两类状态：
 *  - audit_status：审核态（pending/approved/rejected），由审核台处理（见 audit.ts）
 *  - status：下架态（0=正常 1=管理员强制下架），由本模块 hide/delete 处理
 * 后端出参均为 camelCase，视图层零 snake；images 为 List<String>。
 */

export interface MomentManageVO {
  id: number
  userId: number
  userNickname: string
  userAvatar: string
  content: string
  /** 图片 URL 列表（后端已解析为 List<String>） */
  images: string[]
  relatedType: string
  relatedId: number | null
  relatedName: string
  auditStatus: string
  rejectReason: string
  usefulCount: number
  commentCount: number
  /** 下架态：0=正常 1=下架 */
  status: number
  createdAt: string
}

function momentToLegacy(raw: any): MomentManageVO {
  // 后端 images 出参已是 List<String>（绝对路径或带 / 前缀），直接采用
  const imgs: string[] = Array.isArray(raw.images) ? raw.images.filter(Boolean) : []
  return {
    id: raw.id,
    userId: raw.userId ?? raw.user_id ?? 0,
    userNickname: raw.userNickname || '',
    userAvatar: raw.userAvatar || '',
    content: raw.content || '',
    images: imgs,
    relatedType: (raw.relatedType ?? raw.related_type) || 'none',
    relatedId: raw.relatedId ?? raw.related_id ?? null,
    relatedName: raw.relatedName || '',
    auditStatus: raw.auditStatus ?? raw.audit_status ?? 'pending',
    rejectReason: (raw.rejectReason ?? raw.reject_reason) || '',
    usefulCount: raw.usefulCount ?? raw.useful_count ?? 0,
    commentCount: raw.commentCount ?? raw.comment_count ?? 0,
    status: raw.status ?? 0,
    createdAt: raw.createdAt ?? raw.created_at ?? '',
  }
}

/**
 * 动态管理列表（ADM，GET /admin/moments）。
 * 后端已补齐 admin 列表端点：返回含待审(pending)/已下架(status=1)的全部动态（区别于公开 GET /moments 仅返 approved+正常）。
 * 入参透传 status(0/1 可选) 与 auditStatus(pending/approved/rejected 可选) 过滤；
 * 出参沿用既有 PageResult<MomentVO> 契约，经 momentToLegacy adapter 转 camelCase。
 */
export async function listMoments(params: {
  status?: number
  auditStatus?: string
  userId?: number
  keyword?: string
  page?: number
  pageSize?: number
} = {}): Promise<{ list: MomentManageVO[]; total: number }> {
  const query: Record<string, unknown> = {
    status: params.status,
    auditStatus: params.auditStatus,
    userId: params.userId,
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 50,
  }
  if (params.keyword) query.keyword = params.keyword
  const data: any = await get('/admin/moments', query)
  return {
    list: pageRecords(data).map(momentToLegacy),
    total: (data as any)?.total ?? pageRecords(data).length,
  }
}

/** 强制下架：status=1（区别于审核态，应对已 approved 违规内容） */
export async function hideMoment(id: number) {
  await put<void>(`/admin/moments/${id}/hide`)
}

/** 物理删除（连带 moment_comment / notification），破坏性操作由调用方二次确认 */
export async function deleteMoment(id: number) {
  await del<void>(`/admin/moments/${id}`)
}

// ===== 动态评论治理（Web 可查看 / 删除单条评论） =====

export interface MomentComment {
  id: number
  momentId: number
  userId: number
  parentId: number | null
  content: string
  usefulCount?: number
  createdAt?: string
}

/** 评论列表（可按动态 / 用户过滤） */
export async function listComments(params: { momentId?: number; userId?: number } = {}): Promise<MomentComment[]> {
  const q: Record<string, unknown> = {}
  if (params.momentId != null) q.momentId = params.momentId
  if (params.userId != null) q.userId = params.userId
  return (await get<any>('/admin/moments/comments', q)) as MomentComment[]
}

/** 删除评论（级联子回复与「有用」标记） */
export async function deleteComment(id: number) {
  await del<void>(`/admin/moments/comments/${id}`)
}
