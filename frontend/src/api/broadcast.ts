import { get } from './http'

/** 广播类型（后端契约 A.14：BroadcastVO.broadcastType） */
export type BroadcastType = 'NOTICE' | 'ACTIVITY' | 'DISH' | 'URL' | 'NONE'

export interface BroadcastVO {
  id: number
  title: string
  content: string
  broadcastType: BroadcastType
  targetId?: number
  targetUrl?: string
  createdAt?: string
}

/** 首页广播条分发类型（前端 UI 语义，对应 home BroadcastItem.type） */
export type BroadcastDispatch = 'dish' | 'community' | 'url' | 'canteen' | 'stall'

export interface BroadcastItem {
  text: string
  type: BroadcastDispatch
  targetId?: number
  targetUrl?: string
}

function mapType(t: BroadcastType): BroadcastDispatch {
  switch (t) {
    case 'DISH': return 'dish'
    case 'URL': return 'url'
    // NOTICE / ACTIVITY / NONE 及未知类型：回落社区流（与历史默认公告行为一致）
    default: return 'community'
  }
}

/** GET /broadcasts（公开）→ 首页广播条数据源；无数据返回空数组，UI 保留轻量占位不隐藏 */
export async function getBroadcasts(): Promise<BroadcastItem[]> {
  const list = await get<BroadcastVO[]>('/broadcasts')
  if (!Array.isArray(list)) return []
  return list.map(b => ({
    text: b.title || b.content || '',
    type: mapType(b.broadcastType),
    targetId: b.targetId,
    targetUrl: b.targetUrl,
  }))
}
