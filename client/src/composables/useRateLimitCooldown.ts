/**
 * 提交限频退避（2026-09-29 新增）。
 *
 * <b>解决的问题</b>：后端对写入口做了 IP 限频（`POST /feedback`、`POST /dishes/{id}/correction`
 * 均为 2 次/分钟、10 次/小时），超限返回 400「提交过于频繁，请 N 秒后再试」。而端上此前
 * 只弹一个 toast —— 用户在弱网下手滑连点会**持续撞限频**，把 1 分钟的封锁越拖越长，
 * 体验是「怎么点都没用，也看不出要等多久」。
 *
 * <p><b>本 composable 提供</b>：
 * <ol>
 *   <li><b>倒计时禁用</b>：被限频后 {@link cooldownSeconds} > 0，调用方据此禁用提交按钮并展示剩余秒数；</li>
 *   <li><b>到点自动解锁</b>：无需用户猜「什么时候能再试」；</li>
 *   <li><b>纯 UI 退避，不自动重发请求</b>——用户已填好的表单内容可能已过期（如菜品下架），
 *       静默重发可能写入脏数据，故只解锁、由用户主动再点。</li>
 * </ol>
 *
 * <p><b>数据来源</b>：{@link isRateLimited} + {@link RateLimitedError.retryAfterSeconds}
 * （请求层从后端 message「请 N 秒后再试」解析）。解析不出秒数时用保守缺省值 {@link DEFAULT_COOLDOWN}。
 */
import { ref, onUnmounted } from 'vue'
import { isRateLimited } from '@/api/http'

/** 解析不出后端建议秒数时的保守缺省（秒）——宁可多等，不可让用户再撞一次限频 */
export const DEFAULT_COOLDOWN = 30

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
    const capped = Math.min(Math.max(Math.round(seconds) || DEFAULT_COOLDOWN, 1), MAX_COOLDOWN)
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
