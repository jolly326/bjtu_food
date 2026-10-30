/**
 * 画布平移边界（纯函数，便于单测）。
 *
 * 口径：缩放后的世界**小于**视口时不可拖动、居中（min 与 max 同为居中偏移）；
 * **大于**视口时可拖动，范围 `[视口 - 世界, 0]`（即世界边缘不越出视口边缘）。
 */

export type PanBounds = { minX: number; maxX: number; minY: number; maxY: number }

export function computePanBounds(
  worldW: number,
  worldH: number,
  scale: number,
  viewportW: number,
  viewportH: number,
): PanBounds {
  const w = worldW * scale
  const h = worldH * scale
  return {
    minX: w <= viewportW ? (viewportW - w) / 2 : viewportW - w,
    maxX: w <= viewportW ? (viewportW - w) / 2 : 0,
    minY: h <= viewportH ? (viewportH - h) / 2 : viewportH - h,
    maxY: h <= viewportH ? (viewportH - h) / 2 : 0,
  }
}
