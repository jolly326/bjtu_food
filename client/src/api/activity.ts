/**
 * 活动接口模块（2026-08-12 新增，前端先行）
 *
 * GET /activities  活动列表（倒序，运营后台录入）
 *
 * 注意：活动后端模块暂不补齐（既定决策），该接口当前大概率 404/无数据。
 * 本模块保留契约定义与空数组兜底；前端首页（pages/home/index）已不再调用
 * getActivities（降级为隐藏活动入口，见 UniversalGrid），避免静默失败阻塞渲染。
 * 仅活动独立页（pages/activity/index）仍在调用，失败时回落空列表。
 * 接口失败 / 空返回一律回落空数组，不阻断调用方。
 */
import { get } from './http'

export interface ActivityItem {
  id: number
  /** 活动标题 */
  title: string
  /** 活动描述（摘要） */
  description?: string
  /** 发布时间（ISO 字符串） */
  publishTime?: string
  /** 公众号文章链接（微信 web-view 跳转） */
  articleUrl?: string
  /** 封面图 URL（公众号文章封面，后端录入；缺失时用图标占位） */
  image?: string
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

function toActivity(raw: any): ActivityItem {
  return {
    id: Number(raw.id),
    title: raw.title || '',
    description: raw.description || '',
    publishTime: raw.publishTime || raw.createdAt || raw.publishTimeAt,
    articleUrl: raw.articleUrl || raw.url || '',
    image: raw.image || raw.coverImage || raw.cover || '',
  }
}

/** 活动列表（倒序）；失败 / 空返回 [] */
export async function getActivities(params: {
  page?: number
  pageSize?: number
} = {}): Promise<ActivityItem[]> {
  try {
    const query: Record<string, any> = {
      page: params.page ?? 1,
      pageSize: params.pageSize ?? 20,
    }
    const res = await get<PageResult<any>>('/activities', query)
    return listOf(res).map(toActivity)
  } catch (e) {
    console.error('[activity] 活动列表加载失败', e)
    return []
  }
}
