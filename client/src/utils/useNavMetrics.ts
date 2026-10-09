import { ref, computed, onMounted } from 'vue'
import { getWindowInfo, getMenuButtonRect } from '@/utils/device'
import type { MenuButtonRect } from '@/utils/device'

/**
 * 顶部导航度量（跨页统一实现）
 *
 * **唯一真源**：状态栏高 / 导航行高只能从这里取。
 * `client-page-structure` 明确要求「页面 SHALL NOT 各自计算状态栏高度、导航行高或胶囊避让量」，
 * 故首页与搜索页的固定标题带、搜索行一律经本 composable 取值（避免各页自行计算导致 header 高度漂移）。
 *
 * 消费方：`components/AppHeader.vue`（二级页顶栏：顶部安全区 + 导航行高）/
 * `components/AppTitleBand.vue`（标题带 / 返回 icon）。（搜索栏高度由 `--search-bar-height` CSS 变量自持，间接沿用本 composable 的度量）
 */
const DEFAULT_STATUS_BAR_PX = 20
const DEFAULT_NAV_BAR_PX = 44
const FALLBACK_NAV_BAR_PX = 56

export function useNavMetrics() {
  const statusBarPx = ref(DEFAULT_STATUS_BAR_PX)
  /** 导航行高（px）：胶囊所在那一行的真实高度——标题带行高必须取它，标题才会与胶囊**同中心** */
  const navBarHeightPx = ref(DEFAULT_NAV_BAR_PX)
  /* 注：胶囊高与右侧避让量均不单独暴露 —— 页面名 / 可点件不再与页头任何控件争水平空间
     （`AppHeader` 只承载「返回 + 居中标题」）；搜索栏高度由 `--search-bar-height` 自持。 */

  onMounted(() => {
    // 跨端兼容（H5 无 wx，退化为固定值）：平台全局只经 `utils/device` 访问 ⇒ 本文件无 `@ts-ignore`
    const win = getWindowInfo()
    statusBarPx.value = (win && win.statusBarHeight) || 20
    // 微信胶囊按钮位置（右上角原生组件）
    const mb = getMenuButtonRect()
    if (mb && mb.height) {
      // navBarHeight = (胶囊.top − 状态栏高) × 2 + 胶囊高 —— 只有取该值，胶囊才在行内垂直居中
      navBarHeightPx.value = getNavBarHeight(statusBarPx.value, mb)
    }
  })

  /** 固定标题带高（px）= 状态栏 + 导航行高；标题在行内垂直居中 → 与胶囊同一条水平线 */
  const titleBandPx = computed(() => statusBarPx.value + navBarHeightPx.value)

  return { statusBarPx, navBarHeightPx, titleBandPx }
}

/** 导航栏内容区高度（px）= (胶囊.top − 状态栏高) × 2 + 胶囊高 —— 取该值胶囊才在行内垂直居中 */
function getNavBarHeight(statusBarHeight: number, menu?: MenuButtonRect | null): number {
  if (menu && menu.height) {
    return (menu.top - statusBarHeight) * 2 + menu.height
  }
  return FALLBACK_NAV_BAR_PX
}
