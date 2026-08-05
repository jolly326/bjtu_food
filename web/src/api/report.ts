import { API_BASE_URL } from './config'

/**
 * 报表导出（task-12 W3）。
 * GET /admin/reports/{type}/export?startAt=&endAt=
 * 后端返回 CSV 文件流（text/csv），需带 Bearer token，故用 fetch + blob 触发下载。
 */

export type ReportType = 'dishes' | 'reviews' | 'users' | 'moments'

export const REPORT_LABELS: Record<ReportType, string> = {
  dishes: '菜品报表',
  reviews: '评价报表',
  users: '用户报表',
  moments: '动态报表',
}

export const REPORT_DESCS: Record<ReportType, string> = {
  dishes: '菜品基础信息、价格（分）、浏览/收藏、评分等',
  reviews: '评价内容、评分、有用数、隐藏状态等',
  users: '学生/管理员账号信息与状态',
  moments: '社区动态内容、审核状态、互动数据等',
}

export interface ReportRange {
  startAt?: string
  endAt?: string
}

/** 下载 CSV 报表并触发浏览器保存 */
export async function downloadReport(type: ReportType, range?: ReportRange): Promise<void> {
  const query = new URLSearchParams()
  if (range?.startAt) query.append('startAt', `${range.startAt} 00:00:00`)
  if (range?.endAt) query.append('endAt', `${range.endAt} 23:59:59`)
  const qs = query.toString()
  const url = `${API_BASE_URL}/admin/reports/${type}/export${qs ? `?${qs}` : ''}`

  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
  })

  if (!res.ok) {
    let message = `下载失败（HTTP ${res.status}）`
    try {
      const body = await res.json()
      if (body?.message) message = body.message
    } catch {
      /* 非 JSON 响应，忽略 */
    }
    throw new Error(message)
  }

  const blob = await res.blob()
  const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '')
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${type}_${stamp}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(a.href)
}
