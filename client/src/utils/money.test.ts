import { describe, it, expect } from 'vitest'
import { fenToYuan, yuanToFen, formatPrice } from './money'

/**
 * 金额换算回归测试。
 *
 * <p>本模块是「金额红线」的落点：页面/模板禁止裸算 /100、*100。
 * 重点覆盖 **二进制浮点尾差** —— 0.1 + 0.2 这类问题在价格展示上会
 * 变成「10.00 元」被 toFixed 修掉，但 `12.5 * 100 = 1249.99…` 这类
 * 会在入库时静默丢分，故必须锁死「经元→分→元整数化」这一路径。
 */
describe('fenToYuan', () => {
  it('分转元', () => {
    expect(fenToYuan(1250)).toBe(12.5)
    expect(fenToYuan(100)).toBe(1)
    expect(fenToYuan(1)).toBe(0.01)
  })

  it('null / undefined / NaN 均归零（缺价不得显示 NaN）', () => {
    expect(fenToYuan(null)).toBe(0)
    expect(fenToYuan(undefined)).toBe(0)
    expect(fenToYuan(NaN)).toBe(0)
  })

  it('先四舍五入到分再换算（消除浮点尾差）', () => {
    // 1249.9999999999998 分应归整为 1250 分 → 12.5 元，而非 12.499999…
    expect(fenToYuan(1249.9999999999998)).toBe(12.5)
  })
})

describe('yuanToFen', () => {
  it('元转分', () => {
    expect(yuanToFen(12.5)).toBe(1250)
    expect(yuanToFen(1)).toBe(100)
  })

  it('null / undefined / NaN 均归零', () => {
    expect(yuanToFen(null)).toBe(0)
    expect(yuanToFen(undefined)).toBe(0)
    expect(yuanToFen(NaN)).toBe(0)
  })

  it('浮点尾差被四舍五入吸收（0.29*100 不得丢分）', () => {
    // 无处理时 0.29 * 100 === 28.999999999999996（少 1 分），Math.round 吸收为 29
    expect(yuanToFen(0.29)).toBe(29)
    expect(yuanToFen(8.29)).toBe(829)
    expect(yuanToFen(12.5)).toBe(1250)
    expect(yuanToFen(0.07)).toBe(7)
  })

  it('注意：1.005 这类「本应进位」的输入仍得 100（IEEE754 表示限制）', () => {
    // 1.005 在二进制中是 1.00499999999999989…，*100 = 100.49999…，四舍五入为 100。
    // 这是**已知且可接受**的：金额口径以「分」为准且恒由后端下发元值，
    // 端上只做展示格式化，用户无法输入 1.005。此用例固化该边界，避免误判为回归。
    expect(yuanToFen(1.005)).toBe(100)
  })

  it('分 ↔ 元往返稳定（不丢分）', () => {
    for (const yuan of [0.01, 1, 8.29, 12.5, 99.99, 1234.56]) {
      expect(fenToYuan(yuanToFen(yuan))).toBe(yuan)
    }
  })
})

describe('formatPrice（全站价格展示唯一口径）', () => {
  it('补零到两位小数', () => {
    expect(formatPrice(12.5)).toBe('12.50')
    expect(formatPrice(10)).toBe('10.00')
    expect(formatPrice(8.29)).toBe('8.29')
  })

  it('缺价显示 0.00，不得出现 NaN', () => {
    expect(formatPrice(null)).toBe('0.00')
    expect(formatPrice(undefined)).toBe('0.00')
    expect(formatPrice(NaN)).toBe('0.00')
  })

  it('浮点尾差不泄漏到展示层', () => {
    // 0.29 若直接 toFixed(2) 得 "0.29" 但底层是 0.28888…；经元→分→元整数化后稳定为 "0.29"
    expect(formatPrice(0.29)).toBe('0.29')
    expect(formatPrice(8.115)).toBe('8.12')
    // 1.005 的固有限制：得 "1.00"（见 yuanToFen 中的说明用例）
    expect(formatPrice(1.005)).toBe('1.00')
  })
})