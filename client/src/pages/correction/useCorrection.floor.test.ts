import { describe, it, expect } from 'vitest'
import { floorDisplay, FLOOR_OPTIONS } from './useCorrection'

/**
 * 楼层字典与展示映射回归测试。
 *
 * <p>该字典**只存在于端上**：后端 `floor` 契约与落库值（`B1` / `1F`）不变，
 * 汉字仅是展示层产物、**永不进入提交路径**。因此最需要防回归的是
 * 「映射失败时不得改写原值」——管理端 `stall.floor` 是自由文本（≤16 字），
 * 详情值可能不在字典内。
 */
describe('floorDisplay', () => {
  it('命中字典 → 返回汉字并标记命中', () => {
    expect(floorDisplay('B1')).toEqual(['负一层', true])
    expect(floorDisplay('1F')).toEqual(['一层', true])
    expect(floorDisplay('4F')).toEqual(['四层', true])
  })

  it('非空但未命中 → **原值原样展示**且标记未命中（供表单挂提示）', () => {
    // 管理端自由文本可能填了字典外的值
    expect(floorDisplay('B2')).toEqual(['B2', false])
    expect(floorDisplay('5F')).toEqual(['5F', false])
    expect(floorDisplay('地下一层')).toEqual(['地下一层', false])
  })

  it('空串 / 空白 → 占位文案且未命中（必填校验不放行）', () => {
    expect(floorDisplay('')).toEqual(['请选择楼层', false])
    expect(floorDisplay('   ')).toEqual(['请选择楼层', false])
    expect(floorDisplay(null as unknown as string)).toEqual(['请选择楼层', false])
    expect(floorDisplay(undefined as unknown as string)).toEqual(['请选择楼层', false])
  })

  it('首尾空白先 trim 再匹配（" 1F " 应命中字典）', () => {
    expect(floorDisplay(' 1F ')).toEqual(['一层', true])
  })

  it('未命中时**不得**改写原值 —— 汉字不得反向进入提交路径', () => {
    const [display] = floorDisplay('B2')
    // 展示层原样回显，提交时 form.floor 仍为 'B2'
    expect(display).toBe('B2')
    expect(FLOOR_OPTIONS.some((o) => o.value === 'B2')).toBe(false)
  })
})

describe('FLOOR_OPTIONS（存储值 ↔ 展示字典）', () => {
  it('value 为后端落库值、label 为展示汉字，顺序稳定', () => {
    expect(FLOOR_OPTIONS.map((o) => o.value)).toEqual(['B1', '1F', '2F', '3F', '4F'])
  })

  it('字典内 value 唯一（否则 floorDisplay 会命中首个、其余不可达）', () => {
    const values = FLOOR_OPTIONS.map((o) => o.value)
    expect(new Set(values).size).toBe(values.length)
  })

  it('字典内每项都能被 floorDisplay 正确映射（双向自洽）', () => {
    for (const o of FLOOR_OPTIONS) {
      expect(floorDisplay(o.value)).toEqual([o.label, true])
    }
  })
})