/**
 * 社区动态接口模块（task-06，ARCH §3.1）
 *
 * 列表/详情/评论为 PUB（GET /moments/** 在后端白名单 GET 放行）；
 * 发布/编辑/删除/有用/评论为 STU。
 *
 * 分页统一 PageResult<MomentVO>{ list, total, page, pageSize }，
 * 对齐一期 http 封装：请求成功返回 body.data（Result.success(data)）。
 */
import { get, post, put, del } from './http'
import { getImageUrl } from '@/utils/image'
import { normalizeImages } from './_shared'
import type { Moment, MomentComment, MomentPublish, MomentCommentPublish, MomentUsefulResult, RelatedType } from '@/types/moment'

/**
 * 动态 / 评论 VO → 前端类型归一化（camelCase 对齐，图片绝对地址补全）。
 * 注意：原 api/momentMapper.ts 并入本模块——新增顶层模块文件在微信开发者工具
 * 中可能不被注册（同 api/broadcast.js 问题），合并到已存在模块根治。
 */

function toMoment(raw: any): Moment {
  if (!raw) return raw
  return {
    id: Number(raw.id),
    userId: Number(raw.userId ?? 0),
    userNickname: raw.userNickname || '',
    userAvatar: getImageUrl(raw.userAvatar),
    content: raw.content || '',
    images: normalizeImages(raw.images),
    relatedType: (raw.relatedType as RelatedType) || 'none',
    relatedId: raw.relatedId ?? null,
    relatedName: raw.relatedName ?? null,
    // 关联档口所属食堂名（后端 MomentVO.relatedCanteen），跳档口详情需携带 navParams.canteen
    relatedCanteen: raw.relatedCanteen ?? null,
    auditStatus: raw.auditStatus,
    rejectReason: raw.rejectReason ?? null,
    usefulCount: Number(raw.usefulCount ?? 0),
    commentCount: Number(raw.commentCount ?? 0),
    status: raw.status ?? 0,
    createdAt: raw.createdAt,
  }
}

function toMomentComment(raw: any): MomentComment {
  if (!raw) return raw
  return {
    id: Number(raw.id),
    momentId: Number(raw.momentId ?? 0),
    userId: Number(raw.userId ?? 0),
    userNickname: raw.userNickname || '',
    userAvatar: getImageUrl(raw.userAvatar),
    parentId: raw.parentId ?? null,
    replyToNickname: raw.replyToNickname ?? null,
    content: raw.content || '',
    usefulCount: Number(raw.usefulCount ?? 0),
    useful: !!raw.useful,
    createdAt: raw.createdAt,
  }
}

interface PageResult<T> {
  list?: T[]
  records?: T[]
  total?: number
  page?: number
  pageSize?: number
}

function listOf<T>(res: PageResult<T> | undefined): T[] {
  if (!res) return []
  return res.list || res.records || []
}

/** 社区广场列表 / 关联过滤（PUB） */
export async function getMoments(params: {
  tab?: 'latest' | 'recommend'
  dishId?: number
  stallId?: number
  canteenId?: number
  page?: number
  pageSize?: number
}): Promise<{ list: Moment[]; total: number }> {
  const query: Record<string, any> = {
    tab: params.tab ?? 'latest',
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 10,
  }
  if (params.dishId != null) query.dishId = params.dishId
  if (params.stallId != null) query.stallId = params.stallId
  if (params.canteenId != null) query.canteenId = params.canteenId
  const res = await get<PageResult<any>>('/moments', query)
  const raw = listOf(res).map(toMoment)
  return { list: raw, total: res?.total ?? raw.length }
}

/** 动态详情（PUB，作者本人可见 rejectReason） */
export async function getMomentDetail(id: number): Promise<Moment> {
  const res = await get<any>(`/moments/${id}`)
  return toMoment(res)
}

/** 发布动态（STU，audit_status=pending） */
export async function publishMoment(payload: MomentPublish): Promise<{ id: number }> {
  return post<{ id: number }>('/moments', payload)
}

/** 编辑重提（STU 仅作者，复用原记录） */
export async function updateMoment(id: number, payload: MomentPublish): Promise<void> {
  await put<void>(`/my/moments/${id}`, payload)
}

/** 我的动态列表（STU，补齐契约缺口） */
export async function getMyMoments(auditStatus?: string): Promise<Moment[]> {
  const query: Record<string, any> = {}
  if (auditStatus) query.auditStatus = auditStatus
  const res = await get<any[]>('/my/moments', query)
  return (res || []).map(toMoment)
}

/** 有用切换（STU，幂等） */
export async function toggleUseful(id: number): Promise<MomentUsefulResult> {
  return post<MomentUsefulResult>(`/moments/${id}/useful`)
}

/** 评论列表（PUB，created_at asc 扁平化） */
export async function getMomentComments(id: number, page = 1, pageSize = 20): Promise<{ list: MomentComment[]; total: number }> {
  const res = await get<PageResult<any>>(`/moments/${id}/comments`, { page, pageSize })
  const raw = listOf(res).map((item: any) => toMomentComment(item))
  return { list: raw, total: res?.total ?? raw.length }
}

/** 发评论（STU，支持 parentId 一层回复） */
export async function commentMoment(id: number, payload: MomentCommentPublish): Promise<{ id: number }> {
  return post<{ id: number }>(`/moments/${id}/comments`, payload)
}

/** 删除自己评论（STU 仅作者） */
export async function deleteMomentComment(momentId: number, commentId: number): Promise<void> {
  await del<void>(`/my/moments/${momentId}/comments/${commentId}`)
}

/** 评论「有用」幂等切换（STU，task-12.4） */
export async function toggleCommentUseful(momentId: number, commentId: number): Promise<MomentUsefulResult> {
  return post<MomentUsefulResult>(`/moments/${momentId}/comments/${commentId}/useful`)
}
