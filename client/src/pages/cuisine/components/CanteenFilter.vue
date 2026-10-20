<script setup lang="ts">
/**
 * 食堂筛选（规格见 docs/ui/client/公共组件与形态基线.md §2.2 CanteenFilter）。
 * 形态：右上角触发器 + **自定义下拉面板**（🔴 不用原生 picker / sheet —— Julienne 同一套浮层语言）。
 * 触发器轻量化（细描边 + 弱化文字）；面板暖白卡底 + 大圆角 + 弹层阴影、🔴 无描边；
 * 选中行浅橙内底 + `check` 图标（低刺激），点选项即选中并收起；透明遮罩捕获外部点击关闭。
 */
import { computed, ref } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'

import type { CanteenOut } from '@/api'

/** 宿主节点虚拟化：组件根直接参与页头 flex 布局 */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    options: CanteenOut[]
    modelValue?: number
  }>(),
  { modelValue: undefined },
)

const emit = defineEmits<{ (e: 'pick', id?: number): void }>()

const ALL = '全部'
const open = ref(false)

const rows = computed<{ id?: number; label: string; active: boolean }[]>(() => [
  { id: undefined, label: ALL, active: props.modelValue == null },
  ...props.options.map((o) => ({ id: o.id, label: o.name, active: o.id === props.modelValue })),
])

const current = computed(() => rows.value.find((r) => r.active)?.label ?? ALL)

function onPick(id?: number) {
  emit('pick', id)
  open.value = false
}
</script>

<template>
  <view class="filter">
    <view
      class="filter__trigger"
      hover-class="filter__trigger--pressed"
      role="button"
      aria-label="选择食堂"
      :aria-expanded="open ? 'true' : 'false'"
      @tap="open = !open"
    >
      <AppIcon name="filter" :size="28" tone="muted" />
      <text class="filter__label">{{ current }}</text>
      <AppIcon :name="open ? 'chevron-up' : 'chevron-down'" :size="28" tone="muted" />
    </view>

    <!-- 透明遮罩：捕获面板外点击关闭 -->
    <view v-if="open" class="filter__mask" @tap="open = false" />

    <!-- 下拉面板：暖白卡底 + 大圆角 + 弹层阴影，无描边 -->
    <view v-if="open" class="filter__panel">
      <view
        v-for="row in rows"
        :key="row.label"
        class="filter__opt"
        :class="{ 'filter__opt--active': row.active }"
        hover-class="filter__opt--pressed"
        role="option"
        :aria-selected="row.active ? 'true' : 'false'"
        @tap="onPick(row.id)"
      >
        <text class="filter__optLabel">{{ row.label }}</text>
        <AppIcon v-if="row.active" name="check" :size="28" tone="primary" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 筛选触发器：细描边 + 弱化文字，无底色，轻量化不抢视觉重心 */
.filter {
  position: relative;
}

.filter__trigger {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  box-sizing: border-box;
  height: var(--tap-target-size);
  padding: 0 var(--spacing-md);
  /* 胶囊（圆角 = 半高）：与全站按钮同一圆角节奏 */
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
}

.filter__trigger--pressed {
  opacity: 0.6;
}

.filter__label {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}

/* 透明遮罩：只负责关面板，无着色 */
.filter__mask {
  position: fixed;
  inset: 0;
  z-index: 40;
}

/* 下拉面板：Julienne 纸片浮层 —— 纯白纸面（--overlay-panel-bg）+ 弹层圆角（--radius-modal）
   + 极弱阴影、无描边 */
.filter__panel {
  position: absolute;
  top: calc(100% + var(--spacing-xs));
  right: 0;
  z-index: 50;
  box-sizing: border-box;
  /* 面板最小宽 = 弹窗宽的一半（280rpx，由令牌派生避免裸值） */
  min-width: calc(var(--sheet-dialog-width) / 2);
  overflow: hidden;
  background: var(--overlay-panel-bg);
  border-radius: var(--radius-modal);
  box-shadow: var(--shadow-modal);
  animation: filter-drop var(--duration-fast) var(--ease-out);
}

/* 选项行：触达下限 + 靠留白分组（无分割线、🔴 无彩色填充块）；
   选中 = 对勾 + 文字提级，低刺激不铺色 */
.filter__opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  height: var(--tap-target-size);
  padding: 0 var(--spacing-xl);
}

.filter__opt--pressed {
  opacity: 0.6;
}

.filter__optLabel {
  font-size: var(--font-body);
  color: var(--text-body);
}

.filter__opt--active .filter__optLabel {
  font-weight: var(--weight-medium);
  color: var(--text-title);
}

@keyframes filter-drop {
  from {
    opacity: 0;
    transform: translateY(-8rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
