/**
 * HUD 筛选行约束解析（规则见 docs/func/client/A-浏览与发现/A1-搜索找菜.md）。
 *
 * 约束只有食堂 / 预算两类，统一以 query 文本为唯一载体：端上按服务端同源规则
 * 从已提交 query 拆出约束用于展示，交互后重新拼装 query 再提交，契约零改动。
 */

export type Constraint = {
  kind: 'canteen' | 'budget'
  /** 拼进 query 的片段（食堂名 / `N 元`） */
  value: string
  /** 胶囊展示文案 */
  label: string
}

const PRICE_RE = /(\d+)\s*(?:元|块)/

export function canteenConstraint(name: string): Constraint {
  return { kind: 'canteen', value: name, label: name }
}

export function budgetConstraint(yuan: number): Constraint {
  return { kind: 'budget', value: `${yuan} 元`, label: `≤${yuan} 元` }
}

/** 从已提交 query 解析生效约束；食堂按最长名优先且单选 */
export function parseConstraints(text: string, canteenNames: string[]): Constraint[] {
  const found: Constraint[] = []
  const price = PRICE_RE.exec(text)
  if (price) found.push(budgetConstraint(Number(price[1])))

  const hit = [...canteenNames]
    .filter((name) => name && text.includes(name))
    .sort((a, b) => b.length - a.length)[0]
  if (hit) found.push(canteenConstraint(hit))
  return found
}

/** 从 query 中剥离已解析的约束片段，保留软意图 */
export function stripConstraints(text: string, constraints: Constraint[]): string {
  let base = text
  for (const c of constraints) {
    base = c.kind === 'budget' ? base.replace(PRICE_RE, ' ') : base.split(c.value).join(' ')
  }
  return base.replace(/\s+/g, ' ').trim()
}

export function buildQuery(base: string, constraints: Constraint[]): string {
  return [base, ...constraints.map((c) => c.value)].filter(Boolean).join(' ').trim()
}

/** 同类单选：重复点选即取消，否则替换同类约束 */
export function toggleConstraint(list: Constraint[], next: Constraint): Constraint[] {
  if (list.some((c) => c.kind === next.kind && c.value === next.value)) {
    return list.filter((c) => !(c.kind === next.kind && c.value === next.value))
  }
  return [...list.filter((c) => c.kind !== next.kind), next]
}
