/**
 * 提交限频退避（后端对写入口做 IP 限频）。
 *
 * 消费方：意见反馈 / 菜品问题反馈 / 写评价（`ReviewComposer`）/ 发邮箱验证码（auth）。
 *
 * ⚠️ **只做 UI 退避，不自动重发**：用户已填好的表单可能已过期（如菜品下架），静默重发会写入脏数据 ——
 * 倒计时结束仅解锁按钮，由用户主动再点。
 * 秒数来源 = 请求层从后端 message「请 N 秒后再试」解析（`isRateLimited` + `retryAfterSeconds`）。
 */
import { ref, onUnmounted } from 'vue'
import { isRateLimited } from '@/api/errors'

/** 解析不出后端建议秒数时的保守缺省（秒）——宁可多等，不可让用户再撞一次限频 */
const DEFAULT_COOLDOWN = 30

/** 后端建议秒数的上限：防止异常文案（如「请 99999 秒」）把 UI 锁死 */
const MAX_COOLDOWN = 300

export function useRateLimitCooldown() {
  /** 剩余冷却秒数；0 = 可提交 */
  const cooldownSeconds = ref(0)
  /** 是否处于退避期（语义别名，模板里读起来更直白） */
  const cooling = () => cooldownSeconds.value > 0

  let timer: ReturnType<typeof setInterval> | null = null

  function stopTimer() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function startCooldown(seconds: number) {
    // ⚠️ 不可写成 `Math.round(seconds) || DEFAULT_COOLDOWN`：`0` 是 falsy，会被 `||`
    // 吞掉退回 30 秒，使「下限 1 秒」的后端返 0 场景失效 —— 用户被无谓多锁 29 秒。
    // 故先归一到 null 再判空（仅 null / undefined / NaN 才用保守缺省）。
    const n = Number.isFinite(seconds) ? Math.round(seconds) : null
    const capped = Math.min(Math.max(n ?? DEFAULT_COOLDOWN, 1), MAX_COOLDOWN)
    cooldownSeconds.value = capped
    stopTimer()
    timer = setInterval(() => {
      cooldownSeconds.value -= 1
      if (cooldownSeconds.value <= 0) {
        cooldownSeconds.value = 0
        stopTimer()
      }
    }, 1000)
  }

  /**
   * 供 catch 分支调用：若异常是限频则进入倒计时退避。
   * @returns 是否识别为限频（调用方据此决定是否**跳过**自己的兜底提示，避免重复 toast）
   */
  function handleError(e: unknown): boolean {
    if (!isRateLimited(e)) return false
    startCooldown(e.retryAfterSeconds ?? DEFAULT_COOLDOWN)
    return true
  }

  /** 提交成功后清零（一次成功即代表封锁窗口已过） */
  function clearCooldown() {
    stopTimer()
    cooldownSeconds.value = 0
  }

  onUnmounted(stopTimer)

  return { cooldownSeconds, cooling, handleError, clearCooldown }
}
