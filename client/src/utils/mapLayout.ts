/**
 * 地图纯布局工具（无 DOM / 无 Vue 依赖，可单测）。
 *
 * - `buildGridLayout`：圆角正方形磁贴的规整网格密铺（固定行列、无行间错位；共享网格原语，供单测复用；生产走 `applyMosaicPlan`）；
 * - `buildGridIndex`：按中心点把磁贴落格，点击命中从 O(N) 降到常数级；
 * - `cullTiles`：仅保留视口外扩半屏内的磁贴，控制 DOM 数量；
 * - `hitTestGrid`：在网格索引中做命中检测；
 * - `buildMosaicPlan` / `applyMosaicPlan`：马赛克拼贴（规整对齐网格 + 若干 2×2 大图 + 散布空位 + 功能网格按钮位）。
 */
import { shuffleInPlace } from './mapMotion'

export interface TileBox {
  x: number
  y: number
  size: number
}

export interface GridLayout {
  boxes: TileBox[]
  /** 功能网格按钮的像素盒（日月切换等）；随画布滑动、不参与菜品排布 */
  functional: TileBox[]
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
    return { boxes: [], functional: [], worldW: 0, worldH: 0, cols: 0, rows: 0 }
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
    functional: [],
    worldW: cols * step,
    worldH: rows * step,
    cols,
    rows,
  }
}

/**
 * 马赛克拼贴（口径见 docs/ui/client/美食地图.md §2 / §3）：在规整对齐网格上放置
 * 1×1 磁贴 + 若干 2×2 跨度大图 + 散布空位 + 功能网格按钮（日月切换）位。
 *
 * - `buildMosaicPlan`：逻辑结构，**每载入随机生成一次** —— 决定哪些菜是 2×2、空位落点、行列数、功能按钮落点；
 *   与菜品洗牌后顺序一一对应，第 i 项即第 i 道菜的落格。
 * - `applyMosaicPlan`：把逻辑结构折算成像素坐标；窗口尺寸变化时只重算坐标、**随机结果保持不变**（不重掷）。
 */
/** 单格落点：所在格行列 + 是否 2×2 跨度大图 */
export interface Placement {
  col: number
  row: number
  big: boolean
}

/** 功能网格按钮落点（日月切换等）：随画布滑动、仅屏内可点 */
export interface FunctionalPlacement extends Placement {
  kind: 'theme'
}

export interface MosaicPlan {
  cols: number
  rows: number
  /** 与菜品洗牌后顺序一一对应：第 i 项即第 i 道菜的落格 */
  placements: Placement[]
  /** 功能网格按钮落点（日月切换等）；随画布滑动、仅屏内可点 */
  functional: FunctionalPlacement[]
}

export interface MosaicOptions {
  /** 注入随机源（默认 Math.random），便于单测确定性 */
  rng?: () => number
  /** 2×2 大图数量占菜品数的比例（默认 0.12） */
  bigRatio?: number
}

function randInt(rng: () => number, minInclusive: number, maxInclusive: number): number {
  return minInclusive + Math.floor(rng() * (maxInclusive - minInclusive + 1))
}

/**
 * 从空闲格中挑选 `holes` 个作为空位，保证任意两个空位 **8 邻域不相邻**（散布、不扎堆）。
 * `free` 为全部未占用格（含可能的边角余量），`holes` 不超其长度。
 */
export function pickScatteredHoles(
  free: { r: number; c: number }[],
  holes: number,
  rng: () => number,
): Set<number> {
  const chosen: { r: number; c: number }[] = []
  const result = new Set<number>()
  let guard = 0
  const maxTries = free.length * 20 + holes * 10
  while (result.size < holes && guard < maxTries) {
    guard++
    const idx = randInt(rng, 0, free.length - 1)
    if (result.has(idx)) continue
    const cell = free[idx]
    const adjacent = chosen.some((o) => Math.abs(o.r - cell.r) <= 1 && Math.abs(o.c - cell.c) <= 1)
    if (!adjacent) {
      chosen.push(cell)
      result.add(idx)
    }
  }
  // 极小网格兜底：直接补齐（允许相邻）
  for (let i = 0; i < free.length && result.size < holes; i++) {
    if (!result.has(i)) result.add(i)
  }
  return result
}

export function buildMosaicPlan(count: number, options: MosaicOptions = {}): MosaicPlan {
  const rng = options.rng ?? Math.random
  const bigRatio = options.bigRatio ?? 0.12
  if (count <= 0) return { cols: 0, rows: 0, placements: [], functional: [] }

  // 2×2 大图数量：随菜品数按比例放大，下限 1、上限约 12%
  const maxBig = Math.max(2, Math.round(count * bigRatio))
  const bigCount = count >= 4 ? randInt(rng, Math.max(1, Math.round(maxBig * 0.5)), maxBig) : 0
  // 随机留空：少量（2–4 个格），且互不相邻（散布，不扎堆）
  const holes = randInt(rng, 2, 4)

  // 所需单元格 = 1×1×(count−big) + 2×2×big + 空位 + 功能按钮(日月切换) = count + 3·big + holes + 1
  const needed = count + 3 * bigCount + holes + 1
  let cols = pickColumns(needed)
  let rows = Math.ceil(needed / cols)
  if (bigCount > 0) {
    cols = Math.max(cols, 2)
    rows = Math.max(rows, 2)
  }

  const used: boolean[][] = Array.from({ length: rows }, () => new Array<boolean>(cols).fill(false))
  const placed: (Placement | undefined)[] = new Array(count)

  // 1) 随机落 2×2 大图：找 2×2 空闲区，标到某个尚未分配的菜品下标
  let placedBig = 0
  let guard = 0
  while (placedBig < bigCount && guard < 300) {
    guard++
    const r = randInt(rng, 0, rows - 2)
    const c = randInt(rng, 0, cols - 2)
    if (!used[r][c] && !used[r][c + 1] && !used[r + 1][c] && !used[r + 1][c + 1]) {
      used[r][c] = used[r][c + 1] = used[r + 1][c] = used[r + 1][c + 1] = true
      let idx = randInt(rng, 0, count - 1)
      while (placed[idx] !== undefined) idx = (idx + 1) % count
      placed[idx] = { col: c, row: r, big: true }
      placedBig++
    }
  }

  // 2) 功能网格按钮（日月切换）：占一个格子，随画布滑动、仅屏内可点
  let functional: FunctionalPlacement[] = []
  {
    let fguard = 0
    while (fguard < 300) {
      fguard++
      const r = randInt(rng, 0, rows - 1)
      const c = randInt(rng, 0, cols - 1)
      if (!used[r][c]) {
        used[r][c] = true
        functional = [{ kind: 'theme', col: c, row: r, big: false }]
        break
      }
    }
  }

  // 3) 收集剩余空闲格并洗牌
  const free: { r: number; c: number }[] = []
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      if (!used[r][c]) free.push({ r, c })
    }
  }
  shuffleInPlace(free, rng)

  // 4) 散布空位：随机挑，但任意两个空位互不相邻（8 邻域），避免扎堆
  const holeSet = pickScatteredHoles(free, holes, rng)

  // 5) 菜品落格：big 已落，其余填剩余空闲格（跳过空位）
  const out: Placement[] = new Array(count)
  let fi = 0
  for (let i = 0; i < count; i++) {
    const p = placed[i]
    if (p !== undefined) {
      out[i] = p
    } else {
      while (holeSet.has(fi)) fi++
      const cell = free[fi++]!
      out[i] = { col: cell.c, row: cell.r, big: false }
    }
  }

  return { cols, rows, placements: out, functional }
}

export function applyMosaicPlan(plan: MosaicPlan, tile: number, cell: number): GridLayout {
  if (plan.cols <= 0 || plan.rows <= 0) {
    return { boxes: [], functional: [], worldW: 0, worldH: 0, cols: 0, rows: 0 }
  }
  const step = Math.max(1, cell)
  const halfGap = (step - tile) / 2
  const boxes: TileBox[] = plan.placements.map((p) => {
    const size = p.big ? tile + step : tile
    return { x: p.col * step + halfGap, y: p.row * step + halfGap, size }
  })
  const functional: TileBox[] = (plan.functional ?? []).map((f) => ({
    x: f.col * step + halfGap,
    y: f.row * step + halfGap,
    size: tile,
  }))
  return {
    boxes,
    functional,
    worldW: plan.cols * step,
    worldH: plan.rows * step,
    cols: plan.cols,
    rows: plan.rows,
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
