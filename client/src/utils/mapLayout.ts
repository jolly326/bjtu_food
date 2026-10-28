/**
 * 地图纯布局工具（无 DOM / 无 Vue 依赖，可单测）。
 *
 * - `buildStaggeredLayout`：圆角正方形磁贴的错位方格密铺（奇偶行错位半格）；
 * - `buildGridIndex`：按中心点把磁贴落格，点击命中从 O(N) 降到常数级；
 * - `cullTiles`：仅保留视口外扩半屏内的磁贴，控制 DOM 数量；
 * - `hitTestGrid`：在网格索引中做命中检测。
 */

export interface TileBox {
  x: number
  y: number
  size: number
}

export interface StaggeredLayout {
  boxes: TileBox[]
  worldW: number
  worldH: number
  cols: number
  rows: number
}

export interface GridIndex<T extends TileBox> {
  cell: number
  buckets: Map<string, T[]>
}

export interface ViewportTransform {
  width: number
  height: number
  tx: number
  ty: number
  scale: number
}

/** 错位方格密铺：固定网格 + 奇偶行水平错位半格，世界宽为错位留量。 */
export function buildStaggeredLayout(count: number, tile: number, cell: number): StaggeredLayout {
  if (count <= 0) {
    return { boxes: [], worldW: 0, worldH: 0, cols: 0, rows: 0 }
  }

  const step = Math.max(1, cell)
  const cols = Math.max(1, Math.ceil(Math.sqrt(count)))
  const rows = Math.ceil(count / cols)
  const halfGap = (step - tile) / 2
  const stagger = step / 2

  const boxes: TileBox[] = Array.from({ length: count }, (_, index) => {
    const col = index % cols
    const row = Math.floor(index / cols)
    const offsetX = row % 2 === 0 ? 0 : stagger
    return {
      x: col * step + offsetX + halfGap,
      y: row * step + halfGap,
      size: tile,
    }
  })

  return {
    boxes,
    // 奇数行右移半格会多出半格宽度，世界宽为其留量（避免末列越界）
    worldW: cols * step + stagger,
    worldH: rows * step,
    cols,
    rows,
  }
}

function bucketKey(x: number, y: number, cell: number): string {
  return `${Math.floor(x / cell)}:${Math.floor(y / cell)}`
}

/** 按磁贴中心点落格建索引。 */
export function buildGridIndex<T extends TileBox>(tiles: readonly T[], cell: number): GridIndex<T> {
  const step = Math.max(1, cell)
  const buckets = new Map<string, T[]>()
  for (const tile of tiles) {
    const key = bucketKey(tile.x + tile.size / 2, tile.y + tile.size / 2, step)
    const bucket = buckets.get(key)
    if (bucket) bucket.push(tile)
    else buckets.set(key, [tile])
  }
  return { cell: step, buckets }
}

/** 视口裁剪：仅保留视口外扩半屏内的磁贴。 */
export function cullTiles<T extends TileBox>(tiles: readonly T[], view: ViewportTransform): T[] {
  if (!tiles.length) return []
  const scale = Math.max(0.0001, view.scale)
  const pad = (Math.min(view.width, view.height) * 0.5) / scale
  const left = -view.tx / scale - pad
  const top = -view.ty / scale - pad
  const right = (view.width - view.tx) / scale + pad
  const bottom = (view.height - view.ty) / scale + pad
  return tiles.filter(
    (t) => t.x + t.size >= left && t.x <= right && t.y + t.size >= top && t.y <= bottom,
  )
}

/** 命中检测：检查世界坐标所在格及 8 邻格（磁贴约一格大小）。 */
export function hitTestGrid<T extends TileBox>(
  index: GridIndex<T>,
  worldX: number,
  worldY: number,
): T | null {
  if (!index.cell) return null
  const cx = Math.floor(worldX / index.cell)
  const cy = Math.floor(worldY / index.cell)
  for (let ox = -1; ox <= 1; ox++) {
    for (let oy = -1; oy <= 1; oy++) {
      const bucket = index.buckets.get(`${cx + ox}:${cy + oy}`)
      if (!bucket) continue
      for (let i = bucket.length - 1; i >= 0; i--) {
        const tile = bucket[i]
        if (
          worldX >= tile.x &&
          worldX <= tile.x + tile.size &&
          worldY >= tile.y &&
          worldY <= tile.y + tile.size
        ) {
          return tile
        }
      }
    }
  }
  return null
}
