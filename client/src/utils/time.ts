/**
 * 绝对时间格式化：一律输出「YYYY-MM-DD HH:mm」（年月日 + 时分）。
 * 产品决策：社区动态 / 评价 / 评论 / 消息一律显示绝对日期，不保留「X天前」相对时间，
 * 保证内容发布时间可精确回溯（用户反馈"每一条都缺少发送时间，起码显示年月日"）。
 */
export function formatDateTime(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 相对时间格式化（保留：个别场景如需"刚刚/X分钟前"可继续用）。
 * 注意：社区 / 评价 / 评论已全面切换 formatDateTime，此函数仅作兼容保留。
 */
export function relativeTime(dateStr?: string): string {
  if (!dateStr) return ''
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  if (Number.isNaN(then)) return ''
  const diff = Math.floor((now - then) / 1000)
  if (diff < 0) return '刚刚'
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  const d = new Date(then)
  return formatDateTime(dateStr)
}
