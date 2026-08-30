/**
 * 活动接口模块（2026-08-12 新增，前端先行）
 *
 * GET /activities  活动列表（倒序，运营后台录入）
 *
 * 注意：活动模块接入状态（2026-08-19 复核）：
 * - 首页万能区「最新活动」入口卡与「我的」页入口均已恢复展示，但点击提示「功能暂未实现」（不跳转活动页）；
 * - pages/standalone/activity/index 独立页与 pages.json 注册保留（待后续开放）；
 * - 接口失败 / 空返回一律回落空数组，不阻断调用方。
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

/**
 * 兼容两种返回形态：
 * - 后端 GET /activities 返回裸 List<ActivityVO>（Result.data 即数组）
 * - 旧/他处可能返回 PageResult{list/records}
 * 防止将数组误当 PageResult 读取导致恒返回 []。
 */
function listOf<T>(res: PageResult<T> | T[] | undefined): T[] {
  if (!res) return []
  if (Array.isArray(res)) return res
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
