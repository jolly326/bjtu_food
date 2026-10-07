/**
 * 绝对时间格式化：一律输出「YYYY-MM-DD HH:mm」（年月日 + 时分）。
 * 产品决策：全站内容（评价 / 消息等）一律显示绝对日期，不保留「X天前」相对时间，
 * 保证内容发布时间可精确回溯（用户反馈"每一条都缺少发送时间，起码显示年月日"）。
 */
const pad = (n: number): string => String(n).padStart(2, '0')

/**
 * 把后端契约的时间串归一为可被 `Date` 安全解析的形态。
 *
 * <p>契约格式是 `yyyy-MM-dd HH:mm:ss`（见 `docs/api/README.md` 的〈时间〉节），
 * 而 **iOS / JavaScriptCore 不解析带空格的日期串**（`new Date('2026-10-05 12:09:17')`
 * 直接得到 `Invalid Date`，Android / 开发者工具却正常）—— 不归一就会出现「只有部分机型
 * 的时间显示为空」这类最难复现的问题。归一口径：空格式 → ISO 的 `T` 分隔符，已是 ISO 的串原样返回。
 */
function normalize(dateStr: string): string {
  return dateStr.trim().replace(/^(\d{4}-\d{2}-\d{2}) /, '$1T')
}

export function formatDateTime(dateStr?: string): string {
  if (!dateStr) return ''
  const d = new Date(normalize(dateStr))
  if (Number.isNaN(d.getTime())) return ''
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
  const d = new Date(normalize(dateStr))
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
