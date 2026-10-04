/**
 * 请求层错误类型与守卫 —— 请求层与消费方之间唯一的错误契约。
 *
 * 本模块只含类型与判别函数，**无副作用**（toast / 弹窗 / 跳转由请求层与调用方各自处置）。
 * 调用方据此分支：`isResourceNotFound` ⇒ 「不存在」文案 + 返回路径；
 * `isRateLimited` ⇒ 退避倒计时；`SurfacedError` ⇒ 已提示过，只做状态回滚。
 */

/** 「资源不存在」（业务码 4001）：对象已删除或已下架，**重试无意义**；请求层不为此码弹 toast */
export class ResourceNotFoundError extends Error {}

/** 类型守卫：资源不存在（4001） */
export function isResourceNotFound(e: unknown): boolean {
  return e instanceof ResourceNotFoundError
}

/** 「已由请求层提示过」的错误标记：调用方 catch 后只回滚状态，不要重复提示（避免一次失败弹两条） */
export class SurfacedError extends Error {}

/**
 * 「被限频」错误（对应后端 IpRateLimiter + 各 Controller 的 checkIpRateLimit）。
 *
 * 这是**可恢复**错误（等几秒即可重试），与「参数非法」这类不可恢复错误分开，
 * 调用方才能做正确处置：禁用提交按钮 N 秒、展示倒计时，而不是无脑重试延长封锁。
 */
export class RateLimitedError extends Error {
  /** 后端建议等待秒数（从 message 解析；解析不出为 undefined） */
  readonly retryAfterSeconds?: number

  constructor(message: string, retryAfterSeconds?: number) {
    super(message)
    this.name = 'RateLimitedError'
    this.retryAfterSeconds = retryAfterSeconds
  }
}

/** 类型守卫：被限频（可恢复，`retryAfterSeconds` 后可重试） */
export function isRateLimited(e: unknown): e is RateLimitedError {
  return e instanceof RateLimitedError
}

/** 限频文案特征（与后端限频抛出的文案对齐） */
export const RATE_LIMIT_PATTERNS = ['过于频繁', '操作频繁', '稍后再试']

/** 从后端 message 解析「请 N 秒后再试」中的 N；用正则而非固定切分，避免后端改文案即失效 */
export function parseRetryAfter(message: string): number | undefined {
  const m = message.match(/(\d+)\s*秒/)
  if (!m) return undefined
  const n = Number(m[1])
  return Number.isFinite(n) && n > 0 ? n : undefined
}
