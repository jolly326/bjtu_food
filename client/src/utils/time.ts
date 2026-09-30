/**
 * 绝对时间格式化：一律输出「YYYY-MM-DD HH:mm」（年月日 + 时分）。
 * 产品决策：全站内容（评价 / 消息等）一律显示绝对日期，不保留「X天前」相对时间，
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
 * 日期格式化（**仅**「YYYY-MM-DD」，不含时分）。
 *
 * 使用场景：**菜品评价条目**（`ReviewItem`）—— 食堂菜品评价时效性弱，到日即可定位；
 * 且条目第二行要同时容纳「星级 + 分值 + 时间」，去掉时分可显著降噪。
 * 其余内容（系统通知等）仍走 `formatDateTime`：保留到分钟，便于精确回溯（产品决策不变）。
 */
export function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
