<template>
  <!-- 底部菜单栏：区分「首页 / 我的」两主区；仅主根页可见，二级页（navigateTo）自动隐藏 -->
  <view v-if="routeStore.tabVisible" class="tab-bar">
    <view
      v-for="item in tabs"
      :key="item.key"
      class="tab-item"
      :class="{ active: item.key === routeStore.activeTab }"
      hover-class="pressed"
      role="button"
      :aria-label="item.label"
      :aria-current="item.key === routeStore.activeTab ? 'page' : undefined"
      @tap="onTap(item)"
    >
      <!-- 选中态：图标切换为填充变体（<name>-filled），图标与文字同步变主色 -->
      <AppIcon
        :name="item.key === routeStore.activeTab ? `${item.icon}-filled` : item.icon"
        :size="48"
        :color="item.key === routeStore.activeTab ? COLOR_MAP['primary-bright'] : COLOR_MAP['text-tertiary']"
      />
      <text class="tab-label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import { useRouteStore } from '@/stores/route'
const routeStore = useRouteStore()
import { TAB_HOME, TAB_PROFILE, TAB_URL_BY_KEY } from '@/utils/routes'
import { COLOR_MAP } from '@/theme/tokens'

/* ⚠️ 本组件**无 props**：菜单栏恒透明（背后即 `fixed` 页底壁纸）、不再有壁纸切片或 `wallpaper` prop 分支，
   透明底为唯一行为（调用方简化为 `<TabBar />`）。 */

const tabs = [
  { key: TAB_HOME, label: '首页', icon: 'home', url: TAB_URL_BY_KEY[TAB_HOME] },
  { key: TAB_PROFILE, label: '我的', icon: 'profile', url: TAB_URL_BY_KEY[TAB_PROFILE] },
] as const

function onTap(item: (typeof tabs)[number]) {
  if (item.key === routeStore.activeTab) return
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
uni.addInterceptor('navigateTo', { invoke: (a: RouteInvokeArgs) => routeStore.ensureTabForUrl(a?.url) })
uni.addInterceptor('redirectTo', { invoke: (a: RouteInvokeArgs) => routeStore.ensureTabForUrl(a?.url) })
uni.addInterceptor('reLaunch', { invoke: (a: RouteInvokeArgs) => routeStore.ensureTabForUrl(a?.url) })
uni.addInterceptor('switchTab', { invoke: (a: RouteInvokeArgs) => routeStore.ensureTabForUrl(a?.url) })
uni.addInterceptor('navigateBack', { complete: () => routeStore.syncRoute() })

// 首屏兜底（主根页 onShow 才是可靠锚点，此处仅双保险）
routeStore.syncRoute()
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
  /* 恒透明（唯一行为）：背后即 `fixed` 页底壁纸；保留上边框与投影做「材质」分层 */
  background: transparent;
  border-top: 1rpx solid var(--border-color);
  box-shadow: var(--shadow-bar);
  z-index: var(--z-tabbar);
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
/* 按压反馈：TabBar 项属「小件」档（图标 + 文字）⇒ 0.6 */
.tab-item.pressed { opacity: 0.6; }
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
