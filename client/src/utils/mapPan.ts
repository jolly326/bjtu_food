/**
 * 画布平移边界（纯函数，便于单测）。
 *
 * 口径：边界不是「世界边缘贴住视口边缘」，而是**最边缘卡片能被拖到视口中心**。
 * `edgeInset` = 首 / 末行卡片中心到世界边缘的距离（规整网格下恰为步长的一半），
 * 未传时退化为「世界边缘可到视口中心」。
 *
 * 因此：内容与视口同宽时仍可拖动一段；单张卡片（世界边长 = 2 × edgeInset）时
 * 两侧边界收敛为同一点 ⇒ 自动居中锁定。
 */
export type PanBounds = { minX: number; maxX: number; minY: number; maxY: number }

export function computePanBounds(
  worldW: number,
  worldH: number,
  scale: number,
  viewportW: number,
  viewportH: number,
  edgeInset = 0,
): PanBounds {
  const w = worldW * scale
  const h = worldH * scale
  const inset = edgeInset * scale
  return {
    minX: viewportW / 2 - (w - inset),
    maxX: viewportW / 2 - inset,
    minY: viewportH / 2 - (h - inset),
    maxY: viewportH / 2 - inset,
  }
}
