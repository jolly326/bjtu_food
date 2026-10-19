/**
 * 429 退避（口径见 docs/secur/S-03 §二）：命中限频的入口按服务端给的等待秒数禁用，
 * 倒计时结束自动恢复。
 *
 * - 等待秒数取自服务端 `message`（形如「操作过于频繁，请 60 秒后再试」），端上不推算阈值；
 * - 退避期间入口不可点，也不再发起请求 —— 避免连续重试把配额打满；
 * - 到点自动恢复，无需用户操作（请求层对 429 **不自动重试**）。
 */
import { computed, ref } from 'vue'

import type { ComputedRef } from 'vue'

/** 限频业务码（docs/api/README.md「业务状态码」） */
export const CODE_RATE_LIMITED = 429

/** 各入口的退避截止时刻（毫秒时间戳） */
const deadlines = ref<Record<string, number>>({})
/** 计时基准：有退避在途时每秒推进，驱动剩余秒数重算 */
const now = ref(Date.now())
let ticker: ReturnType<typeof setInterval> | null = null

function ensureTicking(): void {
  if (ticker != null) return
  const timer = setInterval(() => {
    now.value = Date.now()
    const pending = Object.values(deadlines.value).some((until) => until > now.value)
    if (pending) return
    deadlines.value = {}
    clearInterval(timer)
    ticker = null
  }, 1000)
  ticker = timer
}

/** 从服务端文案里取可等待秒数（取不到返回 0） */
export function parseRetrySeconds(message: string): number {
  const matched = /(\d+)\s*秒/.exec(message ?? '')
  return matched ? Number(matched[1]) : 0
}

/** 登记退避：seconds 秒内该入口不可用 */
export function markCooldown(key: string, seconds: number): void {
  if (seconds <= 0) return
  deadlines.value = { ...deadlines.value, [key]: Date.now() + seconds * 1000 }
  now.value = Date.now()
  ensureTicking()
}

/** 该入口剩余退避秒数（0 = 可用） */
export function cooldownLeft(key: string): number {
  const until = deadlines.value[key]
  if (!until || until <= now.value) return 0
  return Math.ceil((until - now.value) / 1000)
}

/** 入口的退避状态：`locked` 为真时该入口不可点（`locked` 由 `left` 派生，避免重复求值） */
export function useCooldown(key: string): { locked: ComputedRef<boolean>; left: ComputedRef<number> } {
  const left = computed(() => cooldownLeft(key))
  return { locked: computed(() => left.value > 0), left }
}

/** 命中 429 时登记退避并返回等待秒数；其它错误返回 0（不禁用入口） */
export function cooldownFrom(key: string, error: unknown): number {
  const failure = error as { code?: number; retryAfter?: number; message?: string } | null
  if (!failure || failure.code !== CODE_RATE_LIMITED) return 0
  const seconds = failure.retryAfter ?? parseRetrySeconds(failure.message ?? '')
  markCooldown(key, seconds)
  return seconds
}
