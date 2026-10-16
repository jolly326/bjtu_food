import { describe, expect, it } from 'vitest'
import { fenToYuan, yuanToFen } from '@/utils/money'

describe('money', () => {
  it('分 -> 元固定两位小数', () => {
    expect(fenToYuan(1200)).toBe('12.00')
    expect(fenToYuan(5)).toBe('0.05')
    expect(fenToYuan(0)).toBe('0.00')
  })

  it('元 -> 分四舍五入到分', () => {
    expect(yuanToFen('12.00')).toBe(1200)
    expect(yuanToFen('0.05')).toBe(5)
    expect(yuanToFen('12.345')).toBe(1235)
  })

  it('非法输入返回 NaN', () => {
    expect(Number.isNaN(yuanToFen('abc'))).toBe(true)
    expect(Number.isNaN(yuanToFen(-1))).toBe(true)
    expect(Number.isNaN(yuanToFen(''))).toBe(true)
  })

  it('往返一致', () => {
    expect(yuanToFen(fenToYuan(1235))).toBe(1235)
  })
})
