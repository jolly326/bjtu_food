/**
 * 地图纯布局工具（无 DOM / 无 Vue 依赖，可单测）。
 *
 * - `buildGridLayout`：圆角正方形磁贴的规整网格密铺（固定行列、无行间错位）；
 * - `buildGridIndex`：按中心点把磁贴落格，点击命中从 O(N) 降到常数级；
 * - `cullTiles`：仅保留视口外扩半屏内的磁贴，控制 DOM 数量；
 * - `hitTestGrid`：在网格索引中做命中检测。
 */

export interface TileBox {
  x: number
  y: number
  size: number
}

export interface GridLayout {
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

/**
 * 选列数：让整体宽高比**趋近 1:1**，同等方正时优先「末行更满」。
 *
 * 评分 = 宽高比偏差 × 10 + 末行空缺率 × 6 —— 前项保证接近正方形，后项避免末行只剩一颗。
 */
function pickColumns(count: number): number {
  let bestCols = 1
  let bestScore = Number.POSITIVE_INFINITY
  for (let cols = 1; cols <= count; cols++) {
    const rows = Math.ceil(count / cols)
    const ratioDiff = Math.abs(cols / rows - 1)
    const emptyRatio = 1 - count / (cols * rows)
    const score = ratioDiff * 10 + emptyRatio * 6
    if (score < bestScore) {
      bestScore = score
      bestCols = cols
    }
  }
  return bestCols
}

/** 规整网格密铺：固定行列逐行铺满，行与行**不做水平错位**（末行不足则右侧留空）。 */
export function buildGridLayout(count: number, tile: number, cell: number): GridLayout {
  if (count <= 0) {
    return { boxes: [], worldW: 0, worldH: 0, cols: 0, rows: 0 }
  }

  const step = Math.max(1, cell)
  const cols = pickColumns(count)
  const rows = Math.ceil(count / cols)
  const halfGap = (step - tile) / 2

  const boxes: TileBox[] = Array.from({ length: count }, (_, index) => ({
    x: (index % cols) * step + halfGap,
    y: Math.floor(index / cols) * step + halfGap,
    size: tile,
  }))

  return {
    boxes,
    worldW: cols * step,
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
