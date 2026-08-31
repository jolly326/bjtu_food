<template>
  <!-- 食堂筛选下拉：红色背景面板，与 header 同一红色块、视觉衔接无间隙；点击面板外遮罩关闭 -->
  <view class="cf-mask" @tap="$emit('close')">
    <view class="cf-panel" :class="{ 'theme-dark': theme.isDark }" @tap.stop>
      <view class="cf-title">选择食堂</view>
      <scroll-view scroll-y class="cf-list">
        <view
          class="cf-item press"
          :class="{ active: selectedId === null }"
          hover-class="pressed"
          @tap="$emit('select', null)"
        >
          <text class="cf-name">全部</text>
          <IconSvg v-if="selectedId === null" name="check" :size="32" color="var(--color-on-primary-surface)" />
        </view>
        <view
          v-for="c in canteens"
          :key="c.id"
          class="cf-item press"
          :class="{ active: selectedId === c.id }"
          hover-class="pressed"
          @tap="$emit('select', c.id ?? null)"
        >
          <text class="cf-name">{{ c.name }}</text>
          <IconSvg v-if="selectedId === c.id" name="check" :size="32" color="var(--color-on-primary-surface)" />
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
import IconSvg from '@/components/IconSvg.vue'
import type { CanteenInfo } from '@/types/canteen'

defineProps<{
  canteens: CanteenInfo[]
  /** 当前选中食堂 id（null = 全部） */
  selectedId: number | null
}>()

defineEmits<{
  (e: 'select', id: number | null): void
  (e: 'close'): void
}>()

const theme = useThemeStore()
</script>

<style scoped lang="scss">
/* 遮罩：自 header 底部向下铺满，承接面板外点击关闭；下方内容轻微压暗 */
.cf-mask {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  /* 向下延伸一屏，覆盖首页内容区 */
  height: 100vh;
  background: var(--overlay-scrim);
  z-index: 90;
}
/* 红色面板：与 header 共用 --color-primary（同源 token），紧贴 header 无间隙，亮/暗模式均无缝 */
.cf-panel {
  background: var(--color-primary);
  color: var(--color-on-primary-surface);
  padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + env(safe-area-inset-bottom));
  box-shadow: var(--shadow-bar-primary);
  /* 仅透明度交叉淡入，无位移过冲（红线 §4.9） */
  animation: cfIn var(--duration-base) ease both;
}
@keyframes cfIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.cf-title {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--color-on-primary-surface);
  padding: var(--spacing-xs) var(--spacing-sm) var(--spacing-md);
}
.cf-list {
  max-height: 60vh;
}
.cf-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-sm);
  border-radius: var(--radius-card);
  -webkit-tap-highlight-color: transparent;
}
.cf-item.active {
  background: rgba(255, 255, 255, 0.16);
}
.cf-name {
  font-size: var(--font-subtitle);
  color: var(--color-on-primary-surface);
  min-width: 0;
}
</style>
