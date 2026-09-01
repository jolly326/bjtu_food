<template>
  <view v-if="open" class="ss-root">
    <!-- 遮罩：点击关闭（点遮罩不选中） -->
    <view class="ss-mask" :class="{ show: maskShow }" @tap="close" />
    <!-- 顶部下拉面板：从筛选条正下方展开，与 CanteenFilter / HomePriceSheet 同款（统一交互方向） -->
    <view class="ss-panel" :class="{ 'theme-dark': theme.isDark, open: panelOpen }">
      <view class="ss-title">排序</view>
      <view
        v-for="opt in options"
        :key="opt.key"
        class="ss-item"
        :class="{ active: opt.key === current }"
        role="button"
        :aria-label="`按${opt.label}排序`"
        @tap="pick(opt.key)"
      >
        <IconSvg :name="opt.icon" :size="34" :color="opt.key === current ? 'var(--color-primary)' : 'var(--text-primary)'" class="ss-item-icon" />
        <text class="ss-item-text">{{ opt.label }}</text>
        <IconSvg v-if="opt.key === current" name="check" :size="34" color="var(--color-primary)" class="ss-item-check" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import IconSvg from './IconSvg.vue'
import { useThemeStore } from '@/stores/theme'
import type { HomeSortKey } from '@/stores/dish'

const props = withDefaults(defineProps<{
  /** 面板显隐（由父级 v-model:open 控制） */
  open: boolean
  /** 当前选中排序键 */
  current: HomeSortKey
}>(), {
  open: false,
  current: 'latest',
})

// 自定义 v-model:open（面板关闭）
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'select', key: HomeSortKey): void
}>()

const theme = useThemeStore()

const options = [
  { key: 'latest' as HomeSortKey, label: '最新', icon: 'time' },
  { key: 'hot' as HomeSortKey, label: '热度最高', icon: 'flame' },
  { key: 'distance' as HomeSortKey, label: '距离最近', icon: 'location' },
  { key: 'priceAsc' as HomeSortKey, label: '价格↑', icon: 'up' },
  { key: 'priceDesc' as HomeSortKey, label: '价格↓', icon: 'down' },
]

const maskShow = ref(false)
const panelOpen = ref(false)

// 进入/退出过渡：next frame 置位，触发 mask 淡入 + panel 下滑
watch(() => props.open, (v) => {
  if (v) {
    requestAnimationFrame(() => { maskShow.value = true; panelOpen.value = true })
  } else {
    maskShow.value = false
    panelOpen.value = false
  }
})

function close() { emit('update:open', false) }
function pick(key: HomeSortKey) {
  emit('select', key)
  emit('update:open', false)
}
</script>

<style scoped lang="scss">
/* 顶部下拉容器：锚定在筛选条（position:relative）正下方 */
.ss-root {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 90;
}
.ss-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 100vh;
  background: var(--overlay-scrim);
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-out);
}
.ss-mask.show { opacity: 1; }
.ss-panel {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  background: var(--bg-card);
  color: var(--text-primary);
  padding: var(--spacing-sm) 0;
  box-shadow: var(--shadow-bar-primary);
  border-bottom-left-radius: var(--radius-card);
  border-bottom-right-radius: var(--radius-card);
  opacity: 0;
  transform: translateY(-8px);
  transition: opacity var(--duration-base) var(--ease-out), transform var(--duration-base) var(--ease-out);
  z-index: 1;
}
.ss-panel.open { opacity: 1; transform: translateY(0); }
.ss-title {
  padding: var(--spacing-xs) var(--spacing-lg);
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
.ss-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  height: 88rpx;
  padding: 0 var(--spacing-lg);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.ss-item:active { background-color: var(--bg-soft); }
.ss-item.active .ss-item-text { color: var(--color-primary); font-weight: var(--weight-bold); }
.ss-item-icon { flex-shrink: 0; line-height: 1; }
.ss-item-text { flex: 1; font-size: var(--font-body); color: var(--text-primary); }
.ss-item-check { flex-shrink: 0; }
</style>
