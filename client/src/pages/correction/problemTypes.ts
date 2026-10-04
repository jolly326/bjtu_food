import type { DishProblemType } from '@/types/feedback'

/**
 * 菜品问题反馈的**问题类型选项**（页面「先选类型」第一屏的唯一数据源）。
 *
 * <p>**为什么只有两项**：「其他问题」在 MVP **暂不做**（评审问题 1 决议 5.2 · 决定 4）——
 * 自由文本的「其他」会把无结构诉求灌进同一条链路，稀释「信息有误 / 已经下架」这两个
 * 可结构化、可判读的信号。
 *
 * <p>**类型值与后端同源**：`CorrectionConst.QUERY_TYPES`（`field` / `gone`）。
 */
export interface ProblemTypeOption {
  value: DishProblemType
  label: string
  /** 一行说明「选它会提交什么」—— 降低误选（美团报错模型的同类做法） */
  hint: string
}

export const PROBLEM_TYPES: readonly ProblemTypeOption[] = [
  {
    value: 'field',
    label: '信息有误',
    hint: '菜名 / 价格 / 食堂 / 档口等写错了',
  },
  {
    value: 'gone',
    label: '已经下架',
    hint: '这道菜已经不在了',
  },
]

/** 按值取选项（`DishProblemType` → 选项；未知值兜底取第一项，保证 UI 不空白） */
export function problemTypeOption(value: DishProblemType): ProblemTypeOption {
  return PROBLEM_TYPES.find((o) => o.value === value) ?? PROBLEM_TYPES[0]
}
