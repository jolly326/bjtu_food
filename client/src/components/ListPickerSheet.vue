<template>
  <!-- 通用「标题 + 可搜索列表 + 项选择（+完成）」底部选择器（component-org-sheet-unify）：
       骨架复用 BaseSheet；searchable 时内部防抖对外 emit('search', kw)，options 由父级按需供给；
       项点击 emit('select', option)，由父级决定关闭/进入下一级；confirmable 时底部渲染「完成」主按钮。 -->
  <BaseSheet
    :visible="open"
    z-token="--z-sheet"
    :title="title"
    closable
    @close="emit('close')"
  >
    <view class="lp-wrap">
      <view v-if="searchable" class="lp-search">
        <input
          v-model="keyword"
          class="lp-search-input"
          placeholder="搜索…"
          placeholder-class="lp-search-ph"
          confirm-type="search"
          aria-label="搜索"
          @input="onSearchInput"
        />
      </view>
      <scroll-view class="lp-list" scroll-y>
        <view
          v-for="opt in leading"
          :key="opt.key"
          class="lp-item"
          :class="{ on: isLeadingOn(opt) }"
          role="button"
          :aria-label="opt.label"
          @tap="pick(opt)"
        >
          <view class="lp-item-img lp-item-img--empty">
            <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
          </view>
          <view class="lp-item-info">
            <text class="lp-item-name">{{ opt.label }}</text>
            <text v-if="opt.sub" class="lp-item-sub">{{ opt.sub }}</text>
          </view>
          <IconSvg v-if="isLeadingOn(opt)" name="check" :size="32" color="var(--color-primary)" />
        </view>
        <view v-if="leading.length > 0" class="lp-divider" />
        <view v-if="options.length === 0 && !keyword.trim()" class="lp-empty">
          <text class="lp-empty-text">输入关键词搜索</text>
        </view>
        <view
          v-for="opt in options"
          :key="opt.key"
          class="lp-item"
          :class="{ on: isOn(opt) }"
          role="button"
          :aria-label="opt.label"
          @tap="pick(opt)"
        >
          <image v-if="opt.image" class="lp-item-img" :src="opt.image" mode="aspectFill" />
          <view v-else class="lp-item-img lp-item-img--empty">
            <IconSvg name="dish" :size="36" color="var(--text-tertiary)" />
          </view>
          <view class="lp-item-info">
            <text class="lp-item-name">{{ opt.label }}</text>
            <text v-if="opt.sub" class="lp-item-sub">{{ opt.sub }}</text>
          </view>
          <IconSvg v-if="isOn(opt)" name="check" :size="32" color="var(--color-primary)" />
        </view>
      </scroll-view>
      <view v-if="confirmable" class="lp-footer">
        <view class="lp-confirm" role="button" :aria-label="confirmText" @tap="emit('confirm')">
          {{ confirmText }}
        </view>
      </view>
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import BaseSheet from './BaseSheet.vue'
import IconSvg from './IconSvg.vue'

interface PickerOption {
  /** 稳定唯一 key（用于高亮/去重） */
  key: string
  label: string
  sub?: string
  image?: string
}

const props = withDefaults(defineProps<{
  open: boolean
  title?: string
  searchable?: boolean
  confirmable?: boolean
  confirmText?: string
  options: PickerOption[]
  /** 列表前置固定项（如「不关联」），选中态与 options 互斥 */
  leading?: PickerOption[]
  /** 当前高亮 key（null 表示高亮 leading 中 key 为 none 的首项） */
  selectedKey?: string | null
}>(), {
  title: '',
  searchable: false,
  confirmable: false,
  confirmText: '完成',
  leading: () => [],
  selectedKey: null,
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'search', kw: string): void
  (e: 'select', option: PickerOption): void
  (e: 'confirm'): void
}>()

const keyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

function isOn(opt: PickerOption): boolean {
  return props.selectedKey != null && props.selectedKey === opt.key
}
function isLeadingOn(opt: PickerOption): boolean {
  return opt.key === '__none__' && props.selectedKey == null
}
function pick(opt: PickerOption) {
  emit('select', opt)
}
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    emit('search', keyword.value.trim())
  }, 300)
}

// 打开时清空关键词（父级在 open 变 true 时按需首拉）
watch(
  () => props.open,
  (v) => {
    if (v) keyword.value = ''
  },
)
</script>

<style scoped>
.lp-wrap { display: flex; flex-direction: column; height: 70vh; max-height: 82vh; }
.lp-search { padding: var(--spacing-md); flex-shrink: 0; }
.lp-search-input { height: 72rpx; padding: 0 var(--spacing-md); background: var(--bg-page); border-radius: var(--radius-btn); font-size: var(--font-small); color: var(--text-primary); box-sizing: border-box; }
.lp-search-ph { color: var(--text-tertiary); }
.lp-list { flex: 1; min-height: 0; padding: 0 var(--spacing-md); box-sizing: border-box; }
.lp-item { display: flex; align-items: center; gap: var(--spacing-sm); min-height: 88rpx; padding: var(--spacing-sm); border-radius: var(--radius-card); transition: background var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
.lp-item.on { background: var(--bg-soft); }
.lp-item-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.lp-item-name { font-size: var(--font-body); color: var(--text-primary); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lp-item-sub { font-size: var(--font-aux); color: var(--text-tertiary); }
.lp-item-img { width: 72rpx; height: 72rpx; border-radius: var(--radius-tag); background: var(--bg-page); flex-shrink: 0; }
.lp-item-img--empty { display: flex; align-items: center; justify-content: center; }
.lp-divider { height: 2rpx; background: var(--border-color); margin: var(--spacing-2xs) var(--spacing-sm) var(--spacing-xs); }
.lp-empty { padding: var(--spacing-xl) 0; text-align: center; }
.lp-empty-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.lp-footer { display: flex; padding: var(--spacing-md); border-top: 2rpx solid var(--border-color); flex-shrink: 0; }
.lp-confirm { flex: 1; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--color-primary); color: var(--color-on-primary); font-weight: var(--weight-bold); font-size: var(--font-subtitle); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.lp-confirm:active { opacity: 0.85; }
</style>
