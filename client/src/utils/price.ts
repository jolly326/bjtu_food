/**
 * 价格换算：分 → 元字符串（两位定点）。
 * 美味卡与记忆卡共用 —— 避免两处各写一份分/元换算（docs 治理：去重）。
 */
export function fenToYuan(fen: number): string {
  return (fen / 100).toFixed(2)
}
