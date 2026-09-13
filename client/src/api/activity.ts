/**
 * 活动接口模块（2026-08-12 新增，前端先行）
 *
 * GET /activities  活动列表（倒序，运营后台录入）
 *
 * 注意：活动模块接入状态（2026-08-19 复核）：
 * - 「我的」页宫格「最新活动」入口已恢复展示，但点击提示「功能暂未实现」（不跳转活动页）；
 * - pages/activity/index 独立页与 pages.json 注册保留（待后续开放）；
 * - 空返回为 []；请求失败向上抛错（MP-012），由调用方区分「失败」与「无活动」。
 */
import { get } from './http'
import { listOf, type PageResult, type RawRow } from './shared'

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

/**
 * 兼容两种返回形态：
 * - 后端 GET /activities 返回裸 List<ActivityVO>（Result.data 即数组）
 * - 旧/他处可能返回 PageResult{list/records}
 * 防止将数组误当 PageResult 读取导致恒返回 []。
 * （listOf 统一版已兼容裸数组，F2 收敛至 shared。）
 */
function toActivity(raw: RawRow): ActivityItem {
  return {
    id: Number(raw.id),
    title: raw.title || '',
    description: raw.description || '',
    publishTime: raw.publishTime || raw.createdAt || raw.publishTimeAt,
    // MP-009 字段核对：后端真源为 ActivityVO.articleUrl（ActivityServiceImpl#toVO setArticleUrl），
    // 后端从不输出 url 字段，移除冗余 fallback
    articleUrl: raw.articleUrl || '',
    image: raw.image || raw.coverImage || raw.cover || '',
  }
}

/**
 * 活动列表（倒序）；空返回（真的没有活动）为 []，请求失败向上抛错（MP-012）——
 * 失败与空数据是两种状态，由调用方（活动页）分别渲染错误重试块与空态。
 */
export async function getActivities(params: {
  page?: number
  pageSize?: number
} = {}): Promise<ActivityItem[]> {
  const query: Record<string, unknown> = {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  }
  const res = await get<PageResult<RawRow>>('/activities', query)
  return listOf(res).map(toActivity)
}
