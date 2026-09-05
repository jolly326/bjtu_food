<template>
  <!-- 通用动作菜单（component-org-sheet-unify）：骨架复用 BaseSheet；
       items 驱动竖排「图标 + 文字」动作项，share 项渲染为 <button open-type="share">（微信原生分享必须 button 形态）。
       页面根级挂载（scroll-view 外，fixed 遮罩才能正确覆盖全屏）。 -->
  <BaseSheet :visible="open" z-token="--z-actionsheet" @close="emit('close')">
    <view class="as-items">
      <template v-for="item in items" :key="item.key">
        <button
          v-if="item.share"
          class="as-item"
          :style="rowStyle(item)"
          open-type="share"
          @tap="pick(item)"
        >
          <IconSvg v-if="item.icon" :name="item.icon" :size="34" :color="iconColor(item)" class="as-item-icon" />
          <text class="as-item-text">{{ item.label }}</text>
        </button>
        <view
          v-else
          class="as-item"
          role="button"
          :style="rowStyle(item)"
          @tap="pick(item)"
        >
          <IconSvg v-if="item.icon" :name="item.icon" :size="34" :color="iconColor(item)" class="as-item-icon" />
          <text class="as-item-text">{{ item.label }}</text>
        </view>
      </template>
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
import BaseSheet from './BaseSheet.vue'
import IconSvg from './IconSvg.vue'

/** 动作项：share=true 走原生分享 button；颜色可用 iconColor/textColor 显式指定（默认次级浅灰） */
interface ActionSheetItem {
  key: string
  label: string
  icon?: string
  iconColor?: string
  textColor?: string
  /** 分享项：渲染为 <button open-type="share">（原生分享必需），点击同时 emit select(key) */
  share?: boolean
}

const props = defineProps<{
  open: boolean
  items: ActionSheetItem[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select', key: string): void
}>()

function iconColor(item: ActionSheetItem): string {
  return item.iconColor || 'var(--text-secondary)'
}
function rowStyle(item: ActionSheetItem) {
  return { color: item.textColor || 'var(--text-secondary)' }
}
function pick(item: ActionSheetItem) {
  emit('select', item.key)
  emit('close')
}
</script>

<style scoped>
/* 选项区：整行通栏热区；分割线仅相邻项之间（各 row 统一 border-top、首行清除；末行下方无线） */
.as-items { width: 100%; padding: var(--spacing-sm) 0; }
.as-item {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--spacing-md);
  width: 100%;
  height: 104rpx;
  padding: 0 var(--spacing-lg);
  font-size: var(--font-body);
  border-top: 1rpx solid var(--border-color);
  background: transparent;
  /* 原生 button 默认样式重置 */
  margin: 0;
  line-height: normal;
  border-radius: var(--radius-none);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.as-items > .as-item:first-child { border-top: none; }
.as-item::after { border: none; }
.as-item:active { opacity: 0.7; }
.as-item-icon { flex-shrink: 0; }
.as-item-text { font-weight: var(--weight-medium); }
</style>
