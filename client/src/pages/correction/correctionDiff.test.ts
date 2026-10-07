import { describe, it, expect } from 'vitest'
import {
  sameList,
  sameValueSet,
  priceValid,
  buildCorrectionDiff,
  snapshotAttributes,
  type CorrectionBaseline,
} from './correctionDiff'
import type { AttributeEditor, CorrectionFormModel } from './useCorrection'

/**
 * 纠错「局部提交」口径回归测试 —— 本模块即 `POST /dishes/{id}/correction` 的请求体组装逻辑。
 *
 * <p>锁死三件改错了会**静默**产生脏数据的事：
 * ① 有序 vs 无序比对（图片顺序算改动、属性顺序不算）；
 * ② 只上传改动项（未改动字段不得进 payload）；
 * ③ 金额元 → 分（金额红线）。
 */

/** 造一份可编辑的维度项（dimensionId 用数值；payload 键 = 维度 ID 字符串） */
function attr(
  dimensionId: number,
  selected: string[],
  valueType: 'single' | 'multi' = 'multi',
): AttributeEditor {
  return { dimensionId, name: `维度${dimensionId}`, valueType, candidates: [], selected }
}

/** 造表单（默认全字段有值，便于只测某一维度） */
function form(over: Partial<CorrectionFormModel> = {}): CorrectionFormModel {
  return {
    name: '红烧肉',
    price: '12.50',
    canteenName: '一食堂',
    floor: '二层',
    stallName: '窗口1',
    images: ['a.jpg'],
    attributes: [],
    ...over,
  }
}

/** 造与 form 完全一致的基线（无改动） */
function base(over: Partial<CorrectionBaseline> = {}): CorrectionBaseline {
  return {
    name: '红烧肉',
    price: '12.50',
    canteenName: '一食堂',
    floor: '二层',
    stallName: '窗口1',
    images: ['a.jpg'],
    attributes: {},
    ...over,
  }
}

describe('sameList（图片：有序）', () => {
  it('同序同内容 → 相同', () => {
    expect(sameList(['a', 'b'], ['a', 'b'])).toBe(true)
  })

  it('顺序不同 → **算改动**（图片顺序即展示顺序）', () => {
    expect(sameList(['a', 'b'], ['b', 'a'])).toBe(false)
  })

  it('长度不同 → 不同', () => {
    expect(sameList(['a'], ['a', 'b'])).toBe(false)
  })
})

describe('sameValueSet（属性值：无序集合）', () => {
  it('同成员不同序 → **不算改动**（弹层 toggle 会打乱次序）', () => {
    // 若按顺序比对，误判为改动 ⇒ 空改动可提交、后端记一次无意义修改
    expect(sameValueSet(['辣', '麻'], ['麻', '辣'])).toBe(true)
  })

  it('成员不同 → 算改动', () => {
    expect(sameValueSet(['辣'], ['甜'])).toBe(false)
  })

  it('长度不同 → 算改动', () => {
    expect(sameValueSet(['辣'], ['辣', '麻'])).toBe(false)
  })

  it('空 vs 空 → 相同', () => {
    expect(sameValueSet([], [])).toBe(true)
  })
})

describe('priceValid（售价格式）', () => {
  it('正常值通过', () => {
    expect(priceValid('12.5')).toBe(true)
    expect(priceValid('12')).toBe(true)
    expect(priceValid('12.50')).toBe(true)
    expect(priceValid('0.01')).toBe(true)
    expect(priceValid('9999')).toBe(true)
  })

  it('空 / 空白不通过', () => {
    expect(priceValid('')).toBe(false)
    expect(priceValid('   ')).toBe(false)
  })

  it('必须为正数（0 / 负数不通过）', () => {
    expect(priceValid('0')).toBe(false)
    expect(priceValid('-1')).toBe(false)
  })

  it('至多两位小数（三位不通过）', () => {
    expect(priceValid('12.505')).toBe(false)
  })

  it('非数字形态不通过', () => {
    expect(priceValid('abc')).toBe(false)
    expect(priceValid('1.')).toBe(false)
    expect(priceValid('.5')).toBe(false)
    expect(priceValid('1e3')).toBe(false)
  })

  it('上限 9999 元', () => {
    expect(priceValid('9999.99')).toBe(false)
    expect(priceValid('10000')).toBe(false)
  })
})

describe('buildCorrectionDiff · 局部提交', () => {
  it('无改动 → 空 payload（后端会 400「未提交任何改动」）', () => {
    expect(buildCorrectionDiff('红烧肉', form(), base())).toEqual({})
  })

  it('详情未就绪（dishName 空）→ 空 payload（此时全表单为空，比对无意义）', () => {
    const empty = form({ name: '', price: '', canteenName: '', floor: '', stallName: '' })
    expect(buildCorrectionDiff('', empty, base({ name: '', price: '' }))).toEqual({})
  })

  it('只上传改动项：改菜名不得连带上传其它未改字段', () => {
    const p = buildCorrectionDiff('红烧肉', form({ name: '梅菜扣肉' }), base())
    expect(p).toEqual({ name: '梅菜扣肉' })
    expect('price' in p).toBe(false)
    expect('canteenName' in p).toBe(false)
  })

  it('多字段同时改动各自进 payload', () => {
    const p = buildCorrectionDiff('红烧肉', form({ name: 'A', stallName: 'B' }), base())
    expect(p).toEqual({ name: 'A', stallName: 'B' })
  })

  it('比对称 trim：仅首尾空白不算改动', () => {
    expect(buildCorrectionDiff('红烧肉', form({ name: '  红烧肉  ' }), base())).toEqual({})
  })

  it('楼层仅此一项改动也算有效改动（归属档口）', () => {
    const p = buildCorrectionDiff('红烧肉', form({ floor: '三层' }), base())
    expect(p).toEqual({ floor: '三层' })
  })

  it('楼层以**汉字**提交（值即存储值，端上零映射）', () => {
    const p = buildCorrectionDiff('红烧肉', form({ floor: '负一层' }), base())
    expect(p).toEqual({ floor: '负一层' })
  })
})

describe('buildCorrectionDiff · 金额（红线）', () => {
  it('价格改动按元 → 分提交（12.5 → 1250）', () => {
    const p = buildCorrectionDiff('红烧肉', form({ price: '12.5' }), base())
    expect(p.price).toBe(1250)
  })

  it('浮点尾差不得丢分（8.29 → 829）', () => {
    const p = buildCorrectionDiff('红烧肉', form({ price: '8.29' }), base())
    expect(p.price).toBe(829)
  })

  it('两位小数价格准确（基线 12.5 → 改为 12.50，提交 1250）', () => {
    // 注意：基线是 '12.5' 而 form 是 '12.50' —— 字符串不等即算改动，
    // 提交值应同为 1250 分（展示格式差异不算金额差异，但此处确为一次真实改动）
    const p = buildCorrectionDiff('红烧肉', form({ price: '12.50' }), base({ price: '12.5' }))
    expect(p.price).toBe(1250)
  })
})

describe('buildCorrectionDiff · 图片', () => {
  it('顺序变化算改动', () => {
    const p = buildCorrectionDiff('红烧肉', form({ images: ['b.jpg', 'a.jpg'] }), base())
    expect(p.images).toEqual(['b.jpg', 'a.jpg'])
  })

  it('删除图片算改动', () => {
    const p = buildCorrectionDiff('红烧肉', form({ images: [] }), base())
    expect(p.images).toEqual([])
  })

  it('空串被过滤（不得提交空 URL）', () => {
    const p = buildCorrectionDiff('红烧肉', form({ images: ['a.jpg', '', 'c.jpg'] }), base())
    expect(p.images).toEqual(['a.jpg', 'c.jpg'])
  })
})

describe('buildCorrectionDiff · 描述属性', () => {
  it('维度值成员变化 → 进 payload（键 = 维度 ID 字符串）', () => {
    const f = form({ attributes: [attr(3, ['辣', '麻'])] })
    const b = base({ attributes: { '3': ['辣'] } })
    const p = buildCorrectionDiff('红烧肉', f, b)
    expect(p.attributes).toEqual({ '3': ['辣', '麻'] })
  })

  it('维度值仅次序不同 → **不**算改动（无意义提交）', () => {
    const f = form({ attributes: [attr(3, ['麻', '辣'])] })
    const b = base({ attributes: { '3': ['辣', '麻'] } })
    expect(buildCorrectionDiff('红烧肉', f, b)).toEqual({})
  })

  it('single 维度只提交首个值（不是数组）', () => {
    const f = form({ attributes: [attr(3, ['辣', '麻'], 'single')] })
    const b = base({ attributes: { '3': ['甜'] } })
    const p = buildCorrectionDiff('红烧肉', f, b)
    expect(p.attributes).toEqual({ '3': '辣' })
  })

  it('single 维度被清空 → 提交空串（表达「清掉这个维度」）', () => {
    const f = form({ attributes: [attr(3, [], 'single')] })
    const b = base({ attributes: { '3': ['辣'] } })
    const p = buildCorrectionDiff('红烧肉', f, b)
    expect(p.attributes).toEqual({ '3': '' })
  })

  it('multi 维度被清空 → 提交空数组', () => {
    const f = form({ attributes: [attr(3, [], 'multi')] })
    const b = base({ attributes: { '3': ['辣'] } })
    const p = buildCorrectionDiff('红烧肉', f, b)
    expect(p.attributes).toEqual({ '3': [] })
  })

  it('基线中无该维度（详情未返回）且未选值 → 视为无改动', () => {
    const f = form({ attributes: [attr(9, [])] })
    const p = buildCorrectionDiff('红烧肉', f, base())
    expect(p).toEqual({})
  })

  it('多维度各自独立比对，只提交有改动的', () => {
    const f = form({
      attributes: [attr(3, ['辣', '麻']), attr(4, ['热', '温'])],
    })
    const b = base({ attributes: { '3': ['辣', '麻'], '4': ['温'] } })
    const p = buildCorrectionDiff('红烧肉', f, b)
    expect(p.attributes).toEqual({ '4': ['热', '温'] })
  })

  it('无任何维度改动时不产出 attributes 键（不留空对象）', () => {
    const f = form({ attributes: [attr(3, ['辣'])] })
    const b = base({ attributes: { '3': ['辣'] } })
    const p = buildCorrectionDiff('红烧肉', f, b)
    expect('attributes' in p).toBe(false)
  })
})

describe('snapshotAttributes（预填基线）', () => {
  it('冻结当时的选中值为基线（键 = 维度 ID 字符串）', () => {
    const snap = snapshotAttributes([attr(3, ['辣', '麻']), attr(4, ['热'])])
    expect(snap).toEqual({ '3': ['辣', '麻'], '4': ['热'] })
  })

  it('快照为**副本**：后续改 selected 不得影响基线', () => {
    const ed = attr(3, ['辣'])
    const snap = snapshotAttributes([ed])
    ed.selected.push('麻')
    // 否则「加载后改一下再改回来」会被误判为无改动、提交丢失
    expect(snap['3']).toEqual(['辣'])
  })
})
