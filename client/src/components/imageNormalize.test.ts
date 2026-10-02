import { describe, it, expect } from 'vitest'
import {
  MAX_EDGE,
  MAX_SIZE,
  FIRST_QUALITY,
  RETRY_QUALITIES,
  exceedsMaxEdge,
  computeScaledSize,
  normalizeImage,
  assertSizeWithinLimit,
  type ImageNormalizeAdapters,
} from './imageNormalize'

/**
 * 图片规格收敛回归测试 —— 即「用户选的图能不能传上去」的唯一判据。
 *
 * <p>策略（压几轮 / 缩到多少 / 何时判失败）已抽离平台依赖，故此处用**假适配器**驱动，
 * 可精确断言每一轮的画质 / 目标尺寸，而不必真的去压图。
 */
const ONE_MB = 1024 * 1024

/** 造适配器：记录每次 compress 调用，并可编排各路径的产出大小与尺寸 */
function adapters(opts: {
  sizes?: Record<string, number>
  dims?: Record<string, { width: number; height: number }>
  compress?: (
    src: string,
    o: { quality?: number; compressedWidth?: number; compressedHeight?: number },
  ) => Promise<string>
} = {}) {
  const calls: Array<{ src: string; quality?: number; w?: number; h?: number }> = []
  const a: ImageNormalizeAdapters = {
    compress: async (src, o) => {
      calls.push({ src, quality: o.quality, w: o.compressedWidth, h: o.compressedHeight })
      if (opts.compress) return opts.compress(src, o)
      // 默认：带目标尺寸 → 'scaled'；否则按画质命名
      if (o.compressedWidth) return 'scaled'
      return `q${o.quality ?? 0}`
    },
    getSize: async (src) => opts.dims?.[src] ?? { width: 800, height: 600 },
    getFileSize: async (src) => opts.sizes?.[src] ?? 0,
  }
  return { a, calls }
}

describe('常量（后端契约）', () => {
  it('最长边 1334 / 大小 1MB / 首压 80', () => {
    expect(MAX_EDGE).toBe(1334)
    expect(MAX_SIZE).toBe(ONE_MB)
    expect(FIRST_QUALITY).toBe(80)
  })

  it('降质阶梯为 60 → 40（逐级降，不一次到底）', () => {
    expect(RETRY_QUALITIES).toEqual([60, 40])
  })
})

describe('exceedsMaxEdge', () => {
  it('未超限不触发', () => {
    expect(exceedsMaxEdge(1334, 1334)).toBe(false)
    expect(exceedsMaxEdge(800, 600)).toBe(false)
  })

  it('任一边超限即触发（横图看宽、竖图看高）', () => {
    expect(exceedsMaxEdge(2000, 600)).toBe(true)
    expect(exceedsMaxEdge(600, 2000)).toBe(true)
  })

  it('非正数视为读取失败、不触发（交由大小校验兜底）', () => {
    expect(exceedsMaxEdge(0, 0)).toBe(false)
    expect(exceedsMaxEdge(-1, 5000)).toBe(false)
  })
})

describe('computeScaledSize（等比缩边）', () => {
  it('未超限返回 null（不缩放）', () => {
    expect(computeScaledSize(800, 600)).toBeNull()
  })

  it('横图按宽收敛到 1334，高度等比', () => {
    expect(computeScaledSize(2668, 1334)).toEqual({ width: 1334, height: 667 })
  })

  it('竖图按高收敛到 1334', () => {
    // 1334/3000 = 0.4447 ⇒ 1000 × 0.4447 = 444.67，四舍五入得 445
    const r = computeScaledSize(1000, 3000)!
    expect(r.width).toBe(445)
    expect(r.height).toBe(1334)
  })

  it('极端长图缩放后不塌为 0（至少 1px）', () => {
    const r = computeScaledSize(133400, 10)!
    expect(r.width).toBe(1334)
    expect(r.height).toBeGreaterThanOrEqual(1)
  })

  it('保持宽高比', () => {
    const r = computeScaledSize(4000, 3000)!
    expect(r.width / r.height).toBeCloseTo(4000 / 3000, 2)
  })
})

describe('normalizeImage · 正常路径', () => {
  it('小图仅首压一次即通过', async () => {
    const { a, calls } = adapters({ sizes: { q80: 500 * 1024, p1: 900 * 1024 } })
    const out = await normalizeImage({ path: 'p1', size: 900 * 1024 }, a)
    expect(out).toBe('q80')
    expect(calls).toHaveLength(1)
    expect(calls[0].quality).toBe(FIRST_QUALITY)
  })

  it('压缩无产出 → 沿用原图', async () => {
    const { a } = adapters({ compress: async () => '', sizes: { p1: 100 * 1024 } })
    expect(await normalizeImage({ path: 'p1', size: 100 * 1024 }, a)).toBe('p1')
  })

  it('首压后已达标 → 不再降质', async () => {
    const { a, calls } = adapters({ sizes: { q80: 800 * 1024, p1: 2 * ONE_MB } })
    await normalizeImage({ path: 'p1', size: 2 * ONE_MB }, a)
    expect(calls.map((c) => c.quality)).toEqual([80])
  })
})

describe('normalizeImage · 尺寸超限', () => {
  it('超限 → 等比缩边一次', async () => {
    const { a, calls } = adapters({
      dims: { q80: { width: 2668, height: 1334 }, scaled: { width: 1334, height: 667 } },
      sizes: { q80: 2 * ONE_MB, scaled: 300 * 1024 },
    })
    const out = await normalizeImage({ path: 'p1', size: 2 * ONE_MB }, a)
    expect(calls[1].w).toBe(1334)
    expect(calls[1].h).toBe(667)
    expect(out).toBe('scaled')
  })

  it('尺寸读取失败 → 不阻断，交大小校验兜底', async () => {
    const { a } = adapters({
      compress: async (src, o) => (o.quality ? 'q80' : ''),
      sizes: { q80: 100 * 1024 },
    })
    const bad: ImageNormalizeAdapters = {
      ...a,
      getSize: async () => {
        throw new Error('fail')
      },
    }
    expect(await normalizeImage({ path: 'p1', size: 0 }, bad)).toBe('q80')
  })

  it('缩边无产出 → 保持当前图（低基础库不支持）', async () => {
    const { a } = adapters({
      dims: { q80: { width: 3000, height: 3000 } },
      compress: async (src, o) => (o.compressedWidth ? '' : 'q80'),
      sizes: { q80: 100 * 1024 },
    })
    expect(await normalizeImage({ path: 'p1', size: 0 }, a)).toBe('q80')
  })
})

describe('normalizeImage · 降质阶梯', () => {
  it('首轮 60 即达标 → 只压一轮', async () => {
    const { a, calls } = adapters({ sizes: { q80: 2 * ONE_MB, q60: 800 * 1024 } })
    const out = await normalizeImage({ path: 'p1', size: 2 * ONE_MB }, a)
    expect(calls.map((c) => c.quality)).toEqual([80, 60])
    expect(out).toBe('q60')
  })

  it('两轮 60 → 40 才达标', async () => {
    const { a, calls } = adapters({
      sizes: { q80: 2 * ONE_MB, q60: 1.2 * ONE_MB, q40: 700 * 1024 },
    })
    const out = await normalizeImage({ path: 'p1', size: 2 * ONE_MB }, a)
    expect(calls.map((c) => c.quality)).toEqual([80, 60, 40])
    expect(out).toBe('q40')
  })

  it('压缩不再变小 → 立即停止，不做无谓的下一轮', async () => {
    const { a, calls } = adapters({ sizes: { q80: 2 * ONE_MB, q60: 2 * ONE_MB } })
    await expect(normalizeImage({ path: 'p1', size: 2 * ONE_MB }, a)).rejects.toThrow('图片过大')
    // q60 压不动即停，不该再试 q40
    expect(calls.map((c) => c.quality)).toEqual([80, 60])
  })

  it('降质无产出 → 停止并按超限抛错', async () => {
    const { a } = adapters({
      compress: async (src, o) => (o.quality === 80 ? 'q80' : ''),
      sizes: { q80: 2 * ONE_MB },
    })
    await expect(normalizeImage({ path: 'p1', size: 2 * ONE_MB }, a)).rejects.toThrow('图片过大')
  })

  it('阶梯用尽仍超限 → 抛错（调用方 toast 跳过该张）', async () => {
    const { a } = adapters({
      sizes: { q80: 2 * ONE_MB, q60: 1.5 * ONE_MB, q40: 1.2 * ONE_MB },
    })
    await expect(normalizeImage({ path: 'p1', size: 2 * ONE_MB }, a)).rejects.toThrow('图片过大')
  })

  it('新图大小读不出 → 视为无进展，不换图', async () => {
    const { a } = adapters({ sizes: { q80: 2 * ONE_MB } })
    const bad: ImageNormalizeAdapters = {
      ...a,
      getFileSize: async (p) => {
        if (p === 'q60') throw new Error('read fail')
        return ONE_MB * 2
      },
    }
    await expect(normalizeImage({ path: 'p1', size: 0 }, bad)).rejects.toThrow('图片过大')
  })

  it('恰好等于 1MB → 通过（边界含等号）', async () => {
    const { a } = adapters({ sizes: { q80: ONE_MB } })
    expect(await normalizeImage({ path: 'p1', size: 0 }, a)).toBe('q80')
  })
})

describe('normalizeImage · 容错', () => {
  it('首压抛错 → 用原图继续（网络抖动不该让用户选不了图）', async () => {
    const { a, calls } = adapters({
      compress: async (src, o) => {
        if (o.quality === 80) throw new Error('compress fail')
        return 'q60'
      },
      sizes: { p1: 2 * ONE_MB, q60: 500 * 1024 },
    })
    const out = await normalizeImage({ path: 'p1', size: 2 * ONE_MB }, a)
    expect(calls[0].src).toBe('p1')
    expect(out).toBe('q60')
  })
})

describe('assertSizeWithinLimit（H5 直传门禁）', () => {
  it('超限抛错', () => {
    expect(() => assertSizeWithinLimit(2 * ONE_MB)).toThrow('图片过大')
  })

  it('未超限放行', () => {
    expect(() => assertSizeWithinLimit(ONE_MB)).not.toThrow()
  })
})
