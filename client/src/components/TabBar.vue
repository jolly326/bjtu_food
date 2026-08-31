<template>
  <!-- 底部菜单栏：区分「首页 / 社区 / 我的」三主区；仅主根页可见，二级页（navigateTo）自动隐藏 -->
  <view v-if="tabVisible" class="tab-bar" :class="{ 'theme-dark': theme.isDark }">
    <view
      v-for="item in tabs"
      :key="item.key"
      class="tab-item press"
      :class="{ active: item.key === activeTab }"
      hover-class="pressed"
      :aria-label="item.label"
      @tap="onTap(item)"
    >
      <IconSvg
        :name="item.icon"
        :size="44"
        :color="item.key === activeTab ? 'var(--color-primary)' : 'var(--text-tertiary)'"
      />
      <text class="tab-label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'
import IconSvg from '@/components/IconSvg.vue'
import { activeTab, tabVisible, syncRoute, ensureTabForUrl } from '@/stores/route'

const theme = useThemeStore()

const tabs = [
  { key: 'home', label: '首页', icon: 'home', url: '/pages/home/index' },
  { key: 'community', label: '社区', icon: 'comment', url: '/pages/community/index' },
  { key: 'profile', label: '我的', icon: 'profile', url: '/pages/profile/index' },
] as const

function onTap(item: (typeof tabs)[number]) {
  if (item.key === activeTab.value) return
  // 主区切换重置页面栈（reLaunch），避免叠加多层历史
  uni.reLaunch({ url: item.url })
}

// 跳转发起时即按目标 URL 判定显隐（URL 已知，不依赖页面栈就绪时序，最稳定）；
// navigateBack 无可预知目标，待 complete（栈已更新）再据栈重算。
// 主根页的初始显示由各自 onShow 锚定（见 pages/*/index.vue）。
uni.addInterceptor('navigateTo', { invoke: (a: any) => ensureTabForUrl(a?.url) })
uni.addInterceptor('redirectTo', { invoke: (a: any) => ensureTabForUrl(a?.url) })
uni.addInterceptor('reLaunch', { invoke: (a: any) => ensureTabForUrl(a?.url) })
uni.addInterceptor('switchTab', { invoke: (a: any) => ensureTabForUrl(a?.url) })
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
  z-index: 100;
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2xs);
  height: var(--tabbar-height);
  -webkit-tap-highlight-color: transparent;
}
.tab-label {
  font-size: var(--font-aux);
  line-height: 1;
  color: var(--text-tertiary);
}
.tab-item.active .tab-label {
  color: var(--color-primary);
  font-weight: var(--weight-semibold);
}
</style>
