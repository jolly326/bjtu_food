<template>
  <!-- 底部菜单栏：区分「首页 / 我的」两主区；仅主根页可见，二级页（navigateTo）自动隐藏 -->
  <view v-if="tabVisible" class="tab-bar" :class="{ 'is-wallpaper': wallpaper }">
    <view
      v-for="item in tabs"
      :key="item.key"
      class="tab-item"
      :class="{ active: item.key === activeTab }"
      hover-class="pressed"
      :aria-label="item.label"
      @tap="onTap(item)"
    >
      <!-- 选中态：图标切换为填充变体（<name>-filled），图标与文字同步变主色 -->
      <IconSvg
        :name="item.key === activeTab ? `${item.icon}-filled` : item.icon"
        :size="48"
        :color="item.key === activeTab ? COLOR_MAP['primary-bright'] : COLOR_MAP['text-tertiary']"
      />
      <text class="tab-label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'
import { activeTab, tabVisible, syncRoute, ensureTabForUrl } from '@/stores/route'
import { TAB_URL_BY_KEY } from '@/utils/routes'
import { COLOR_MAP } from '@/theme/tokens'

defineProps<{
  /**
   * `true` = **透明底**（首页传）：菜单栏自身不铺底色、也不铺任何壁纸切片 —— 背后就是 `fixed` 页底壁纸，
   * 壁纸天然铺满到底部（2026-09-26 决议：切片与页底像素同源，纯属多余的一层拷贝）。
   * 不传（如「我的」页）= 原样：白底菜单栏。⚠️ 本开关只解决「背景」；图标 / 文案配色不变。
   */
  wallpaper?: boolean
}>()

/* ⚠️ 已删除「壁纸切片」与其视口高测量（2026-09-26）：切片曾用于裁出一条同源壁纸、并挡住从菜单栏背后
   滚上来的卡片；现改为**全部不铺** —— 代价是卡片会从菜单栏背后滚过并被看见（与标题带 / 吸顶容器同一取舍）。 */

const tabs = [
  { key: 'home', label: '首页', icon: 'home', url: TAB_URL_BY_KEY.home },
  { key: 'profile', label: '我的', icon: 'profile', url: TAB_URL_BY_KEY.profile },
] as const

function onTap(item: (typeof tabs)[number]) {
  if (item.key === activeTab.value) return
  // 主区切换重置页面栈（reLaunch），避免叠加多层历史
  uni.reLaunch({ url: item.url })
}

/**
 * uni 路由拦截器入参的**最小接口**（MP-08）：本组件只读取目标 URL 一个字段，
 * 故声明为 `{ url?: string }` 即可，无需退到裸 any（uni 未对 invoke 入参建模）。
 */
interface RouteInvokeArgs {
  url?: string
}

// 跳转发起时即按目标 URL 判定显隐（URL 已知，不依赖页面栈就绪时序，最稳定）；
// navigateBack 无可预知目标，待 complete（栈已更新）再据栈重算。
// 主根页的初始显示由各自 onShow 锚定（见 pages/*/index.vue）。
uni.addInterceptor('navigateTo', { invoke: (a: RouteInvokeArgs) => ensureTabForUrl(a?.url) })
uni.addInterceptor('redirectTo', { invoke: (a: RouteInvokeArgs) => ensureTabForUrl(a?.url) })
uni.addInterceptor('reLaunch', { invoke: (a: RouteInvokeArgs) => ensureTabForUrl(a?.url) })
uni.addInterceptor('switchTab', { invoke: (a: RouteInvokeArgs) => ensureTabForUrl(a?.url) })
uni.addInterceptor('navigateBack', { complete: () => syncRoute() })

// 首屏兜底（主根页 onShow 才是可靠锚点，此处仅双保险）
syncRoute()
</script>

<style scoped lang="scss">
.tab-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  /* 固定高度 + 底部安全区，覆盖在页面内容之上但不遮挡（页面 scroll-wrap 已留白） */
  height: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border-top: 1rpx solid var(--border-color);
  box-shadow: var(--shadow-bar);
  z-index: var(--z-tabbar);
}
/* 透明底模式（首页）：菜单栏自身无底色 —— 背后即 `fixed` 页底壁纸；保留上边框与投影做「材质」分层 */
.tab-bar.is-wallpaper {
  background: transparent;
}
.tab-item {
  /* `position: relative` 保留：与 `fixed` 页底壁纸（`z-index: -1`）分层，确保图标恒在最上 */
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  /* tab-pages-visual-polish-3：图标与文字间距 8rpx */
  gap: var(--spacing-xs);
  height: var(--tabbar-height);
  -webkit-tap-highlight-color: transparent;
}
.tab-label {
  /* tab-pages-visual-polish-3：标签 24rpx */
  font-size: var(--font-small);
  line-height: 1;
  color: var(--text-tertiary);
}
.tab-item.active .tab-label {
  color: var(--color-primary-text);
  font-weight: var(--weight-semibold);
}
</style>
