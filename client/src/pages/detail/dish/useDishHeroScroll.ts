/**
 * 顶部大图滚动模型（dish-hero-scroll-model）：hero 卡几何 + 菜名随滚动淡入。
 *
 * 与首页 §11 **同构**（四周一个页面 gutter + 圆角卡 + 16:10；hero 移出屏幕后菜名淡入）。
 * 纯派生状态 + 一个 `onMounted`，无跨模块可变状态 ⇒ 可独立成文件，不牵动评价编排。
 * 由 `useDishPage` 同步调用，生命周期仍在详情页组件上下文中注册。
 */
import { ref, computed, onMounted } from 'vue'
import type { ComputedRef } from 'vue'
import { getWindowInfo } from '@/utils/device'
import type { DishDetail } from '@/types/dish'

/** 页面级 gutter（rpx）：与 `theme/design-tokens.css` 的 `--page-gutter` 同源（与首页 Banner 同口径） */
const PAGE_GUTTER_RPX = 32
/** rpx → px（750rpx = 窗宽，与 WXSS 同口径）：随屏宽缩放，避免与卡片宽度错位 */
const rpxToPx = (rpx: number, windowPx: number): number => Math.round((rpx * windowPx) / 750)
const TITLE_FADE_START_PX = 16
/** 菜名淡入起点缓冲（px）：hero 滚出标题带下沿后再留这么多才开始淡入 */
const TITLE_FADE_SPAN = 96

export function useDishHeroScroll(dish: ComputedRef<DishDetail | null | undefined>) {
  const scrollTop = ref(0)
  const windowWidth = ref(375)

  /** hero 卡高度（px）：可用宽（窗宽 − 两侧 gutter）按 16:10 —— 与首页 Banner 同口径 */
  const heroHeightPx = computed(() => Math.round(((windowWidth.value - rpxToPx(PAGE_GUTTER_RPX, windowWidth.value) * 2) * 10) / 16))

  /** 菜名渐显（口径 c，R16）：hero 卡完全滚出后再淡入，避免与信息卡菜名同屏重复 */
  const navOpacity = computed(() => {
    if (dish.value == null) return 1
    const start = heroHeightPx.value + TITLE_FADE_START_PX
    const p = (scrollTop.value - start) / TITLE_FADE_SPAN
    return Math.min(1, Math.max(0, p))
  })

  const heroImages = computed(() => dish.value?.images ?? [])
  const dishName = computed(() => (dish.value ? dish.value.name : '菜品详情'))

  /**
   * 滚动区滚动回调（页内 `scroll-view` 的 `@scroll`）：**只驱动菜名淡入**，不参与布局 / 位移。
   * 量化到整数 px + 值未变则不写，避免亚像素抖动触发无意义重算。
   */
  function onScroll(e: { detail?: { scrollTop?: number } }) {
    const raw = e?.detail?.scrollTop ?? 0
    const top = raw > 0 ? Math.round(raw) : 0
    if (top === scrollTop.value) return
    scrollTop.value = top
  }

  onMounted(() => {
    // 只取**视口宽**（hero 卡按 16:10 定高用）。状态栏 / 导航行高 / 胶囊避让均由公共 `AppTitleBand` 自持
    // 。
    // 平台取值统一走 `utils/device`
    const win = getWindowInfo()
    windowWidth.value = (win && win.windowWidth) || 375
  })

  return { heroImages, dishName, heroHeightPx, navOpacity, onScroll }
}
