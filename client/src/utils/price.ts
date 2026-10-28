/**
 * 价格换算：分 → 元字符串（两位定点）。
 * 菜品弹窗统一消费，避免多处各写一份分/元换算。
 */
export function fenToYuan(fen: number): string {
  return (fen / 100).toFixed(2)
}
