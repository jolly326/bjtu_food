import { ref, computed, onMounted } from 'vue'
import { getNavBarHeight, getCapsuleHeight } from '@/utils/navMetrics'

/**
 * 顶部导航度量（跨页统一实现，2026-09-22 change `search-page-refresh`）
 *
 * **唯一真源**：状态栏高 / 导航行高 / 胶囊高 / 右侧胶囊避让量只能从这里取。
 * `client-page-structure` 明确要求「页面 SHALL NOT 各自计算状态栏高度、导航行高或胶囊避让量」，
 * 故首页与搜索页的固定标题带、搜索行一律经本 composable 取值（此前首页内联一份、AppHeader 内联一份，
 * 已出现「标题带取了 capsuleHeight 而非 navBarHeight → 标题比胶囊高 6px」这类漂移）。
 *
 * 消费方：`components/AppTitleBand.vue`（标题带 / 返回 icon）、`components/SearchBar.vue`（胶囊高）。
 */
export function useNavMetrics() {
  const statusBarPx = ref(20)
  /** 导航行高（px）：胶囊所在那一行的真实高度——标题带行高必须取它，标题才会与胶囊**同中心** */
  const navBarHeightPx = ref(44)
  const capsuleHeightPx = ref(32)
  /** 右侧胶囊避让量（CSS 长度串）：标题 / 可点件不得进入胶囊水平范围 */
  const navPadRight = ref('180rpx')

  onMounted(() => {
    // 兼容老基础库：getWindowInfo 不存在时回退 getSystemInfoSync
    // @ts-ignore - 跨端兼容（H5 无 wx，退化为固定值）
    const win = (typeof wx !== 'undefined')
      // @ts-ignore
      ? (wx.getWindowInfo ? wx.getWindowInfo() : (wx.getSystemInfoSync ? wx.getSystemInfoSync() : null))
      : null
    statusBarPx.value = (win && win.statusBarHeight) || 20
    const widthPx = (win && win.windowWidth) || 375
    // @ts-ignore - 微信胶囊按钮位置（右上角原生组件）
    const mb = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect)
      ? wx.getMenuButtonBoundingClientRect()
      : null
    if (mb && mb.height) {
      // navBarHeight = (胶囊.top − 状态栏高) × 2 + 胶囊高 —— 只有取该值，胶囊才在行内垂直居中
      navBarHeightPx.value = getNavBarHeight(statusBarPx.value, mb)
      capsuleHeightPx.value = getCapsuleHeight(mb)
      // 右侧留白必须用 px（胶囊尺寸由微信按设备写死、不随屏宽缩放，用 rpx 会换机型就歪）
      navPadRight.value = `calc(env(safe-area-inset-right, 0px) + ${Math.max(widthPx - mb.left + 8, 0)}px)`
    }
  })

  /** 固定标题带高（px）= 状态栏 + 导航行高；标题在行内垂直居中 → 与胶囊同一条水平线 */
  const titleBandPx = computed(() => statusBarPx.value + navBarHeightPx.value)

  return { statusBarPx, navBarHeightPx, capsuleHeightPx, navPadRight, titleBandPx }
}
