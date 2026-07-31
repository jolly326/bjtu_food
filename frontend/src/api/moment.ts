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
import { toMoment, toMomentComment } from './momentMapper'
import type { Moment, MomentComment, MomentPublish, MomentCommentPublish, MomentUsefulResult } from '@/types/moment'

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

/** 删除自己动态（STU 仅作者） */
export async function deleteMoment(id: number): Promise<void> {
  await del<void>(`/my/moments/${id}`)
}

/** 我的动态列表（STU，补齐契约缺口） */
export async function getMyMoments(auditStatus?: string): Promise<Moment[]> {
  const query: Record<string, any> = {}
  if (auditStatus) query.auditStatus = auditStatus
  const res = await get<any[]>('/my/moments', query)
  return (res || []).map(toMoment)
}

/** 👍 有用切换（STU，幂等） */
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

/** 评论「有用」👍 幂等切换（STU，task-12.4） */
export async function toggleCommentUseful(momentId: number, commentId: number): Promise<MomentUsefulResult> {
  return post<MomentUsefulResult>(`/moments/${momentId}/comments/${commentId}/useful`)
}
