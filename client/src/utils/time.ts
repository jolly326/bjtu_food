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
