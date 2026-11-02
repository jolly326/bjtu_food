/**
 * 画布聚焦（纯函数，便于单测）。
 *
 * 口径：把目标磁贴中心送到「可视净空区」中心 —— 净空区是底部弹层顶边以上的可见高度，
 * 不是整屏（弹层撑起时按屏居中会把磁贴压在弹层下）。
 * 结果再收敛到既有平移边界内，因此世界小于视口时保持不位移。
 *
 * @param tile 目标磁贴（需 x/y/size 以取中心）
 * @param scale 当前缩放
 * @param area 可视净空区（底部弹层顶边以上的可见区域）
 * @param bounds 当前平移边界（由 computePanBounds 给出）
 */
import type { TileBox } from './mapLayout'
import { clamp } from './mapMotion'
import type { PanBounds } from './mapPan'

export interface FocusArea {
  width: number
  height: number
}

export function computeFocusCamera(
  tile: TileBox,
  scale: number,
  area: FocusArea,
  bounds: PanBounds,
): { tx: number; ty: number } {
  const centerX = (tile.x + tile.size / 2) * scale
  const centerY = (tile.y + tile.size / 2) * scale
  return {
    tx: clamp(area.width / 2 - centerX, bounds.minX, bounds.maxX),
    ty: clamp(area.height / 2 - centerY, bounds.minY, bounds.maxY),
  }
}
