/**
 * 美食地图场景：画布布局 + 手势 + 相机 + 动画的编排层。
 *
 * 与 Vue 组件解耦：本组合式拥有全部场景状态（视口 / 世界 / 平移 / 缩放 / 磁贴 / 手势机 / 动画帧），
 * 组件只负责模板绑定与 emit 转发。纯计算下沉到 `utils/map*`（无 Vue 依赖、可单测）；
 * 本文件只编排它们并驱动 DOM 状态（ref），便于在不挂载组件的情况下用合成事件单测。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import type { DishVO } from '@/api'
import { MAP, REQUEST } from '@/constants'
import { thumbUrl } from '@/utils/image'
import {
  cancelFrame,
  clamp,
  easeInOutCubic,
  easeOutCubic,
  prefersReducedMotion,
  requestFrame,
  shuffled,
  type FrameHandle,
} from '@/utils/mapMotion'
import {
  applyMosaicPlan,
  buildGridIndex,
  buildMosaicPlan,
  cullTiles,
  hitTestGrid,
  type FunctionalPlacement,
  type GridIndex,
  type MosaicPlan,
  type TileBox,
} from '@/utils/mapLayout'
import { computeFocusCamera } from '@/utils/mapCamera'
import { computePanBounds, type PanBounds } from '@/utils/mapPan'
import { refreshWindowSize, rpxToPx, windowSize } from '@/utils/viewport'

/** 菜品磁贴：与 `TileBox` 叠加身份与预计算渲染字段 */
export interface DishTile extends TileBox {
  kind: 'dish'
  dish: DishVO
  /** 预计算的缩略图 URL：避免每帧对每个可见磁贴重复做字符串判断与拼接 */
  src: string
  /** 预计算的内联样式：位置 / 尺寸在 rebuild 后恒定，避免每帧新建 style 对象 */
  style: string
}

/** 功能网格按钮磁贴（日月切换等）：占用一个格子、随画布滑动、仅屏内可点 */
export interface FunctionalTile extends TileBox {
  kind: FunctionalPlacement['kind']
  /** 预计算的内联样式：位置 / 尺寸在 rebuild 后恒定 */
  style: string
}

/** 画布上的全部磁贴（菜品 + 功能按钮） */
export type SceneTile = DishTile | FunctionalTile

export interface MapSceneProps {
  dishes: DishVO[]
  /** 可视净空区高度（px）：底部弹层顶边以上；缺省按整屏计算 */
  netHeight?: number
}

export interface MapSceneHandlers {
  onPick: (dish: DishVO) => void
  onBlank: () => void
  /** 点中功能网格按钮（日月切换）时触发；未提供则忽略 */
  onToggleTheme?: () => void
}

export function useMapScene(props: MapSceneProps, handlers: MapSceneHandlers) {
  const { onPick, onBlank, onToggleTheme } = handlers

  const viewport = reactive(windowSize())

  const worldW = ref(0)
  const worldH = ref(0)
  const tx = ref(0)
  const ty = ref(0)
  const scale = ref(1)
  const tiles = ref<SceneTile[]>([])
  const failedIds = reactive(new Set<number>())

  /** 网格索引：按中心点把磁贴落格，点击命中从 O(N) 降到常数级 */
  let gridIndex: GridIndex<SceneTile> | null = null

  /** 首 / 末行卡片中心到世界边缘的距离（= 网格步长的一半）：平移边界据此放开到「边缘卡片可居中」 */
  let edgeInset = 0

  /** 当前载入的马赛克逻辑结构（随机生成一次）；窗口变化只重算坐标、不重掷 */
  let plan: MosaicPlan | null = null
  /** 与 `plan.placements` 一一对应的洗牌后菜品顺序 */
  let order: DishVO[] = []

  /**
   * 当前有效视口高度：弹层架起时只剩「顶边以上」的净空区可操作。
   * 平移边界与 camera 聚焦**必须共用它** —— 否则聚焦目标会被按整屏算的边界夹住，
   * 表现为「点了不居中，偏差正好是半个弹层」。
   */
  const activeHeight = computed(() => {
    const net = props.netHeight
    return net != null && net > 0 && net < viewport.height ? net : viewport.height
  })

  /** 视口裁剪：仅渲染「视口外扩半屏」内的磁贴，DOM 数量与可视区挂钩 */
  const visibleTiles = computed<SceneTile[]>(() =>
    cullTiles(tiles.value, {
      width: viewport.width,
      height: viewport.height,
      tx: tx.value,
      ty: ty.value,
      scale: scale.value,
    }),
  )

  const worldStyle = computed(() => ({
    width: `${worldW.value}px`,
    height: `${worldH.value}px`,
    transform: `translate3d(${tx.value}px, ${ty.value}px, 0) scale(${scale.value})`,
  }))

  /**
   * 平移边界缓存：手势 / 惯性期间每帧都会调用（rubber + inertia + settle），
   * 输入参数除 scale 外恒定 —— 命中缓存可省掉每帧的重复计算与对象分配。
   */
  let boundsCache: {
    worldW: number
    worldH: number
    scale: number
    viewW: number
    viewH: number
    inset: number
    value: PanBounds
  } | null = null

  function bounds(): PanBounds {
    const w = worldW.value
    const h = worldH.value
    const s = scale.value
    const vw = viewport.width
    const vh = activeHeight.value
    const c = boundsCache
    if (
      c !== null &&
      c.worldW === w &&
      c.worldH === h &&
      c.scale === s &&
      c.viewW === vw &&
      c.viewH === vh &&
      c.inset === edgeInset
    ) {
      return c.value
    }
    const value = computePanBounds(w, h, s, vw, vh, edgeInset)
    boundsCache = { worldW: w, worldH: h, scale: s, viewW: vw, viewH: vh, inset: edgeInset, value }
    return value
  }

  /**
   * 按当前 `plan` 把逻辑结构折算成像素坐标并装配磁贴（菜品 + 功能网格按钮）。
   * 窗口尺寸变化时只重算（`layoutTiles`），随机结构不变。
   */
  function layoutTiles(): void {
    failedIds.clear()
    if (!order.length || !plan) {
      tiles.value = []
      gridIndex = null
      worldW.value = 0
      worldH.value = 0
      return
    }

    const tile = rpxToPx(MAP.TILE_RPX, viewport.width)
    const cell = rpxToPx(MAP.CELL_RPX, viewport.width)
    const layout = applyMosaicPlan(plan, tile, cell)
    // 首 / 末卡片中心到世界边缘 = 步长的一半（halfGap + tile/2 化简）
    edgeInset = cell / 2
    worldW.value = layout.worldW
    worldH.value = layout.worldH

    const dishTiles: DishTile[] = order.map((dish, index) => {
      const box = layout.boxes[index]!
      return {
        kind: 'dish',
        dish,
        ...box,
        // 缩略图与内联样式只在重建时算一次：渲染层每帧都要读，重复计算会被帧率成倍放大
        src: thumbUrl(dish.imageUrl),
        style: `width:${box.size}px;height:${box.size}px;left:${box.x}px;top:${box.y}px`,
      }
    })

    // 功能网格按钮（日月切换等）随画布滑动，仅占其格子、不随菜品排布
    const functionalTiles: FunctionalTile[] = layout.functional.map((box) => ({
      kind: 'theme',
      ...box,
      style: `width:${box.size}px;height:${box.size}px;left:${box.x}px;top:${box.y}px`,
    }))

    tiles.value = [...dishTiles, ...functionalTiles]
    gridIndex = buildGridIndex(tiles.value, cell)

    scale.value = 1
    // 首屏落在世界几何中心，并显式收敛到合法边界（极端尺寸/窗口变化下也不越界）
    const b = bounds()
    tx.value = clamp((viewport.width - worldW.value) / 2, b.minX, b.maxX)
    ty.value = clamp((activeHeight.value - worldH.value) / 2, b.minY, b.maxY)
  }

  /**
   * 重建布局：**马赛克拼贴**（规整对齐网格 + 若干 2×2 大图 + 散布空位 + 功能网格按钮）。
   *
   * - 每次载入重新生成逻辑结构（`buildMosaicPlan`）：按比例选 2×2、散布互不相邻空位、落功能按钮；
   * - 菜品洗牌后与结构一一对应落格，落点每次新鲜、结构规整对齐；
   * - 首屏居中于世界几何中心，进入即见地图中心区。
   */
  function rebuild(): void {
    stopAnimation()
    order = shuffled(props.dishes)
    plan = order.length ? buildMosaicPlan(order.length, { bigRatio: MAP.BIG_TILE_RATIO }) : null
    layoutTiles()
  }

  // ---------------- 手势状态（声明在 immediate watch 之前，避免 frame 的 TDZ） ----------------
  type Mode = 'none' | 'pan' | 'pinch'

  let mode: Mode = 'none'
  let startX = 0
  let startY = 0
  let startTx = 0
  let startTy = 0
  let startAt = 0
  let moved = false
  let pinchDist = 0
  let pinchScale = 1
  let anchorWorldX = 0
  let anchorWorldY = 0
  let lastX = 0
  let lastY = 0
  let lastAt = 0
  let speedX = 0
  let speedY = 0
  /** 当前进行中的动画帧（同一时刻只有一个：平移 / 惯性 / 弹性收敛互斥） */
  let frame: FrameHandle | null = null

  /** 是否跳过非位移动画（系统「减少动态效果」开启 ⇒ 惯性 / 弹性一律即时到位） */
  const reduceMotion = prefersReducedMotion()

  function now(): number {
    return Date.now()
  }

  function stopAnimation(): void {
    if (frame != null) {
      cancelFrame(frame)
      frame = null
    }
  }

  /**
   * 软边界阻尼：越界越深、跟手比例越低 —— 位移按指数收敛到 `RUBBER * RUBBER_TRAVEL`，
   * 抵达饱和区后增量趋于 0（画布几乎不动）；🔴 不做硬停、不做瞬移回位（美食地图.md §5）。
   */
  function rubberResist(overshoot: number): number {
    const travel = MAP.RUBBER_TRAVEL
    return Math.sign(overshoot) * travel * MAP.RUBBER * (1 - Math.exp(-Math.abs(overshoot) / travel))
  }

  function rubber(nx: number, ny: number): void {
    const b = bounds()
    let x = nx
    let y = ny
    if (nx < b.minX) x = b.minX + rubberResist(nx - b.minX)
    else if (nx > b.maxX) x = b.maxX + rubberResist(nx - b.maxX)
    if (ny < b.minY) y = b.minY + rubberResist(ny - b.minY)
    else if (ny > b.maxY) y = b.maxY + rubberResist(ny - b.maxY)
    tx.value = x
    ty.value = y
  }

  /**
   * 缓动到指定位置。
   * - `out`：三次 ease-out（弹性收敛 / 回弹用，快速落位）
   * - `inOut`：三次 ease-in-out（camera 聚焦用，起止都柔和，避免大位移「猛地一窜」）
   */
  function animateTo(targetX: number, targetY: number, duration: number, mode: 'out' | 'inOut' = 'out'): void {
    stopAnimation()
    const fromX = tx.value
    const fromY = ty.value
    if (duration <= 0 || reduceMotion || (fromX === targetX && fromY === targetY)) {
      tx.value = targetX
      ty.value = targetY
      return
    }
    const startedAt = now()
    const tick = () => {
      const progress = clamp((now() - startedAt) / duration, 0, 1)
      const eased = mode === 'inOut' ? easeInOutCubic(progress) : easeOutCubic(progress)
      tx.value = fromX + (targetX - fromX) * eased
      ty.value = fromY + (targetY - fromY) * eased
      frame = progress < 1 ? requestFrame(tick) : null
    }
    tick()
  }

  /** 弹性收敛到合法边界（duration = 0 即时） */
  function settle(duration: number): void {
    const b = bounds()
    animateTo(clamp(tx.value, b.minX, b.maxX), clamp(ty.value, b.minY, b.maxY), duration)
  }

  /** 待聚焦的菜品：弹层尚未落定时先记下，等实测高度回传后再动 */
  let pendingFocusId: number | null = null
  /** 兜底定时器：measured 失败时也不会永不聚焦 */
  let focusTimer: ReturnType<typeof setTimeout> | null = null

  function clearFocusTimer(): void {
    if (focusTimer != null) {
      clearTimeout(focusTimer)
      focusTimer = null
    }
  }

  /** 落地执行一次聚焦（目标与边界共用 activeHeight ⇒ 不会被 clamp 夹偏） */
  function applyFocus(): void {
    const id = pendingFocusId
    if (id == null) return
    const tile = tiles.value.find((item) => item.kind === 'dish' && item.dish.id === id)
    pendingFocusId = null
    clearFocusTimer()
    if (!tile || tile.kind !== 'dish') return
    const target = computeFocusCamera(
      tile,
      scale.value,
      { width: viewport.width, height: activeHeight.value },
      bounds(),
    )
    animateTo(target.tx, target.ty, MAP.FOCUS_MS, 'inOut')
  }

  /** 底部弹层是否已架起（净空区有效）—— 聚焦据此决定立即动还是等实测高度回传 */
  function isSheetOpen(): boolean {
    const net = props.netHeight
    return net != null && net > 0 && net < viewport.height
  }

  /**
   * 请求聚焦某个磁贴。
   * 弹层已架起（净空区有效）⇒ 立即动；否则等实测高度回传再动 ——
   * 避免「先按整屏居中、随后被弹层压住再拽一次」的双段位移。
   */
  function requestFocus(dishId: number): void {
    pendingFocusId = dishId
    if (isSheetOpen()) {
      applyFocus()
      return
    }
    clearFocusTimer()
    focusTimer = setTimeout(() => {
      focusTimer = null
      applyFocus()
    }, MAP.FOCUS_WAIT_MS)
  }

  watch(
    () => props.netHeight,
    () => {
      // 弹层落定后补聚焦；收起时（净空区回到整屏）保持视角不变
      if (pendingFocusId != null && isSheetOpen()) applyFocus()
    },
  )

  function inertia(): void {
    if (reduceMotion) {
      settle(0)
      return
    }
    let vx = speedX
    let vy = speedY
    if (Math.abs(vx) < MAP.MIN_SPEED && Math.abs(vy) < MAP.MIN_SPEED) {
      settle(MAP.SETTLE_MS)
      return
    }
    stopAnimation()
    const step = () => {
      vx *= MAP.FRICTION
      vy *= MAP.FRICTION
      const b = bounds()
      const nx = tx.value + vx
      const ny = ty.value + vy
      if (nx < b.minX || nx > b.maxX || ny < b.minY || ny > b.maxY) {
        tx.value = nx
        ty.value = ny
        settle(MAP.EDGE_SETTLE_MS)
        return
      }
      tx.value = nx
      ty.value = ny
      if (Math.abs(vx) < MAP.MIN_SPEED && Math.abs(vy) < MAP.MIN_SPEED) {
        frame = null
        return
      }
      frame = requestFrame(step)
    }
    step()
  }

  function pointOf(touch: Touch): { x: number; y: number } {
    return { x: touch.clientX, y: touch.clientY }
  }

  function distance(a: Touch, b: Touch): number {
    return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
  }

  /** 统一初始化单指平移起点（消除三处重复的起点重置逻辑） */
  function beginPan(point: { x: number; y: number }, isMoved: boolean): void {
    mode = 'pan'
    startX = point.x
    startY = point.y
    startTx = tx.value
    startTy = ty.value
    startAt = now()
    lastX = point.x
    lastY = point.y
    lastAt = startAt
    speedX = 0
    speedY = 0
    moved = isMoved
  }

  function beginPinch(touches: TouchList): void {
    const a = touches[0]
    const b = touches[1]
    pinchDist = Math.max(1, distance(a, b))
    pinchScale = scale.value
    const midX = (a.clientX + b.clientX) / 2
    const midY = (a.clientY + b.clientY) / 2
    anchorWorldX = (midX - tx.value) / scale.value
    anchorWorldY = (midY - ty.value) / scale.value
    mode = 'pinch'
    moved = true
  }

  function onTouchStart(event: TouchEvent): void {
    stopAnimation()
    if (event.touches.length >= 2) {
      beginPinch(event.touches)
      return
    }
    beginPan(pointOf(event.touches[0]), false)
  }

  function onTouchMove(event: TouchEvent): void {
    if (event.touches.length >= 2) {
      if (mode !== 'pinch') beginPinch(event.touches)
      const a = event.touches[0]
      const b = event.touches[1]
      const next = clamp(pinchScale * (distance(a, b) / pinchDist), MAP.MIN_SCALE, MAP.MAX_SCALE)
      const midX = (a.clientX + b.clientX) / 2
      const midY = (a.clientY + b.clientY) / 2
      scale.value = next
      rubber(midX - anchorWorldX * next, midY - anchorWorldY * next)
      return
    }

    if (event.touches.length !== 1) return
    if (mode === 'pinch') {
      // 双指变单指：把剩下这根手指作为新的平移起点，避免跳变
      beginPan(pointOf(event.touches[0]), true)
      return
    }

    const point = pointOf(event.touches[0])
    if (
      Math.abs(point.x - startX) > MAP.TAP_MOVE_THRESHOLD_PX ||
      Math.abs(point.y - startY) > MAP.TAP_MOVE_THRESHOLD_PX
    ) {
      moved = true
    }
    rubber(startTx + (point.x - startX), startTy + (point.y - startY))

    const at = now()
    if (at > lastAt) {
      speedX = ((point.x - lastX) / (at - lastAt)) * 16
      speedY = ((point.y - lastY) / (at - lastAt)) * 16
    }
    lastX = point.x
    lastY = point.y
    lastAt = at
  }

  function hitTest(x: number, y: number): SceneTile | null {
    if (!gridIndex) return null
    const worldX = (x - tx.value) / scale.value
    const worldY = (y - ty.value) / scale.value
    return hitTestGrid(gridIndex, worldX, worldY)
  }

  function onTouchEnd(event: TouchEvent): void {
    // 仍有手指在屏上（双指抬起其一）：切换为单指平移，不结束手势
    if (event.touches.length >= 1) {
      beginPan(pointOf(event.touches[0]), true)
      return
    }

    const wasPan = mode === 'pan'
    const point = event.changedTouches[0]
    mode = 'none'
    if (wasPan && !moved && now() - startAt <= MAP.TAP_DURATION_MS && point) {
      const hit = hitTest(point.clientX, point.clientY)
      if (hit) {
        if (hit.kind === 'theme') {
          // 功能网格按钮（日月切换）：随画布滑动、仅屏内可点
          onToggleTheme?.()
          return
        }
        requestFocus(hit.dish.id)
        onPick(hit.dish)
        return
      }
      // 位移与时长都在阈值内、且未命中磁贴 ⇒ 点空白（拖动过的手势不会走到这里）
      onBlank()
      return
    }
    if (wasPan) inertia()
    else settle(MAP.EDGE_SETTLE_MS)
  }

  function onImageError(id: number): void {
    failedIds.add(id)
  }

  /** 窗口尺寸变化：刷新缓存并重建布局（磁贴尺寸随之重算） */
  let resizeTimer: ReturnType<typeof setTimeout> | null = null
  function handleResize(): void {
    if (resizeTimer != null) clearTimeout(resizeTimer)
    resizeTimer = setTimeout(() => {
      Object.assign(viewport, refreshWindowSize())
      layoutTiles()
    }, REQUEST.RESIZE_DEBOUNCE_MS)
  }

  onMounted(() => {
    uni.onWindowResize(handleResize)
  })

  onBeforeUnmount(() => {
    stopAnimation()
    clearFocusTimer()
    if (resizeTimer != null) clearTimeout(resizeTimer)
    uni.offWindowResize(handleResize)
  })

  // 首屏载入即重建（在全部场景状态就绪后触发，避免动画帧 `frame` 的 TDZ）
  watch(() => props.dishes, rebuild, { immediate: true })

  return {
    visibleTiles,
    worldStyle,
    failedIds,
    onTouchStart,
    onTouchMove,
    onTouchEnd,
    onImageError,
  }
}
