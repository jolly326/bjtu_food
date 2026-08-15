/**
 * 活动接口模块（2026-08-12 新增，前端先行）
 *
 * GET /activities  活动列表（倒序，运营后台录入）
 * 后端 Activity 模块由技术负责人补齐；此处按约定契约定义类型与兜底，
 * 接口失败 / 空返回一律回落空数组，不阻塞首页其余模块渲染。
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
