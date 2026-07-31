/**
 * 动态 / 评论 VO → 前端类型归一化（camelCase 对齐，图片绝对地址补全）。
 * 后端 MomentVO.images 已是 List<String>，这里仅做绝对地址补全与字段兜底。
 */
import { getImageUrl } from '@/utils/image'
import type { Moment, MomentComment, RelatedType } from '@/types/moment'

function normalizeImages(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === 'string' && item.length > 0).map(getImageUrl)
  if (typeof value === 'string' && value.trim()) return value.split(',').map(s => s.trim()).filter(Boolean).map(getImageUrl)
  return []
}

export function toMoment(raw: any): Moment {
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
    auditStatus: raw.auditStatus,
    rejectReason: raw.rejectReason ?? null,
    usefulCount: Number(raw.usefulCount ?? 0),
    commentCount: Number(raw.commentCount ?? 0),
    status: raw.status ?? 0,
    createdAt: raw.createdAt,
  }
}

export function toMomentComment(raw: any): MomentComment {
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
