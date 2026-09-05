<template>
  <!-- 通用「标题 + 可搜索列表 + 项选择（+完成）」底部选择器（component-org-sheet-unify）：
       骨架复用 BaseSheet；searchable 时内部防抖对外 emit('search', kw)，options 由父级按需供给；
       项点击 emit('select', option)，由父级决定关闭/进入下一级；confirmable 时底部渲染「完成」主按钮。
       语义扩展（feedback 位置/楼层/菜品三处弹层复用，向后兼容）：
       - option.icon（IconSvg name）→ 行左 icon 方块；无 icon 且有 image 渲染图片；均无则空位占位；
       - backable → 头部标题左侧返回箭头（复用 IconSvg arrow + scaleX(-1)），点击 emit('back')，与 closable 并存；
       - rowStyle='plain' 复刻 feedback 原 .sheet-item 通栏行观感（分隔线/icon/文字/右侧 check）；默认 'card'（发布页不变）；
       - 默认槽渲染于滚动列表之后、底部按钮区之前（承载「其他」自定义输入等尾部内容）；
       - 具名 #empty 槽渲染于列表区内当无任何项时（承载「无关键词引导 / 无结果去补录 CTA」，由父级条件供内容），
         未提供时回退到 searchable 的「输入关键词搜索」内置提示（发布页等旧行为不变）。
       注意：不得引用 SearchBar（其将迁入 pages/publish-content，主包不可反向依赖分包）。 -->
  <BaseSheet
    :visible="open"
    z-token="--z-sheet"
    :title="title"
    closable
    :backable="backable"
    @close="emit('close')"
    @back="emit('back')"
  >
    <view class="lp-wrap" :class="{ 'lp-wrap--plain': rowStyle === 'plain' }">
      <view v-if="searchable" class="lp-search">
        <input
          v-model="keyword"
          class="lp-search-input"
          :placeholder="searchPlaceholder || '搜索…'"
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
          :class="{ on: isLeadingOn(opt), 'lp-item--plain': rowStyle === 'plain' }"
          role="button"
          :aria-label="opt.label"
          @tap="pick(opt)"
        >
          <template v-if="opt.icon">
            <view class="lp-lead lp-lead--icon"><IconSvg :name="opt.icon" :size="plainIconSize" color="var(--text-tertiary)" /></view>
          </template>
          <template v-else-if="opt.image">
            <image class="lp-lead-img" :class="{ 'lp-lead-img--plain': rowStyle === 'plain' }" :src="opt.image" mode="aspectFill" />
          </template>
          <template v-else>
            <view class="lp-lead lp-lead--empty" />
          </template>
          <view class="lp-item-info">
            <text class="lp-item-name">{{ opt.label }}</text>
            <text v-if="opt.sub" class="lp-item-sub">{{ opt.sub }}</text>
          </view>
          <IconSvg v-if="isLeadingOn(opt)" name="check" :size="32" color="var(--color-primary)" />
        </view>
        <view v-if="leading.length > 0" class="lp-divider" />
        <view
          v-for="opt in options"
          :key="opt.key"
          class="lp-item"
          :class="{ on: isOn(opt), 'lp-item--plain': rowStyle === 'plain' }"
          role="button"
          :aria-label="opt.label"
          @tap="pick(opt)"
        >
          <template v-if="opt.icon">
            <view class="lp-lead lp-lead--icon"><IconSvg :name="opt.icon" :size="plainIconSize" color="var(--text-tertiary)" /></view>
          </template>
          <template v-else-if="opt.image">
            <image class="lp-lead-img" :class="{ 'lp-lead-img--plain': rowStyle === 'plain' }" :src="opt.image" mode="aspectFill" />
          </template>
          <template v-else>
            <view class="lp-lead lp-lead--empty" />
          </template>
          <view class="lp-item-info">
            <text class="lp-item-name">{{ opt.label }}</text>
            <text v-if="opt.sub" class="lp-item-sub">{{ opt.sub }}</text>
          </view>
          <IconSvg v-if="isOn(opt)" name="check" :size="32" color="var(--color-primary)" />
        </view>
        <!-- 列表区内空态：无任何项时由父级 #empty 槽承载引导/去补录，未提供则回退内置提示 -->
        <view v-if="leading.length === 0 && options.length === 0" class="lp-empty">
          <slot name="empty">
            <text v-if="searchable" class="lp-empty-text">输入关键词搜索</text>
          </slot>
        </view>
      </scroll-view>
      <!-- 默认槽：滚动列表之后、底部按钮区之前（承载「其他」自定义输入等尾部内容） -->
      <view class="lp-tail"><slot /></view>
      <view v-if="confirmable" class="lp-footer">
        <view class="lp-confirm" role="button" :aria-label="confirmText" @tap="emit('confirm')">
          {{ confirmText }}
        </view>
      </view>
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BaseSheet from './BaseSheet.vue'
import IconSvg from './IconSvg.vue'

interface PickerOption {
  /** 稳定唯一 key（用于高亮/去重） */
  key: string
  label: string
  sub?: string
  image?: string
  /** 行左 icon（IconSvg name，优先于 image 渲染）；无 icon 无 image 时渲染空位占位 */
  icon?: string
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
  /** 行样式：'card'（默认，发布页）| 'plain'（feedback 原 .sheet-item 通栏行观感） */
  rowStyle?: 'card' | 'plain'
  /** 头部标题左侧返回箭头（层级回退），与 closable 关闭钮并存 */
  backable?: boolean
  /** 搜索框占位文案（searchable 时生效） */
  searchPlaceholder?: string
  /** 打开时搜索框预填值（层级预置/深链预填用，缺省清空） */
  searchInitial?: string
}>(), {
  title: '',
  searchable: false,
  confirmable: false,
  confirmText: '完成',
  leading: () => [],
  selectedKey: null,
  rowStyle: 'card',
  backable: false,
  searchPlaceholder: '',
  searchInitial: '',
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'back'): void
  (e: 'search', kw: string): void
  (e: 'select', option: PickerOption): void
  (e: 'confirm'): void
}>()

const keyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

/** plain 行左 icon 方块内图标尺寸 */
const plainIconSize = computed(() => (props.rowStyle === 'plain' ? 32 : 36))

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

// 打开时（重）预填搜索框：有 searchInitial 则回填（feedback 菜品深链预填），否则清空
watch(
  () => props.open,
  (v) => {
    if (v) keyword.value = props.searchInitial || ''
  },
)
</script>

<style scoped>
.lp-wrap { display: flex; flex-direction: column; height: 70vh; max-height: 82vh; }
.lp-search { padding: var(--spacing-md); flex-shrink: 0; }
.lp-search-input { height: 72rpx; padding: 0 var(--spacing-md); background: var(--bg-page); border-radius: var(--radius-btn); font-size: var(--font-small); color: var(--text-primary); box-sizing: border-box; }
.lp-search-ph { color: var(--text-tertiary); }
.lp-list { flex: 1; min-height: 0; padding: 0 var(--spacing-md); box-sizing: border-box; }
.lp-empty { padding: var(--spacing-xl) 0; text-align: center; }
.lp-empty-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.lp-tail { flex-shrink: 0; }

/* ===== 行：card 默认（发布页） ===== */
.lp-item { display: flex; align-items: center; gap: var(--spacing-sm); min-height: 88rpx; padding: var(--spacing-sm); border-radius: var(--radius-card); transition: background var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
.lp-item.on { background: var(--bg-soft); }
.lp-item-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.lp-item-name { font-size: var(--font-body); color: var(--text-primary); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lp-item-sub { font-size: var(--font-aux); color: var(--text-tertiary); }
.lp-lead--icon { width: 72rpx; height: 72rpx; border-radius: var(--radius-tag); background: var(--bg-page); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.lp-lead--empty { width: 72rpx; height: 72rpx; border-radius: var(--radius-tag); background: var(--bg-page); flex-shrink: 0; }
.lp-lead-img { width: 72rpx; height: 72rpx; border-radius: var(--radius-tag); background: var(--bg-page); flex-shrink: 0; }
.lp-divider { height: 2rpx; background: var(--border-color); margin: var(--spacing-2xs) var(--spacing-sm) var(--spacing-xs); }

/* ===== 行：plain（feedback 原 .sheet-item 通栏分隔观感） ===== */
.lp-wrap--plain .lp-list { padding: 0 var(--spacing-md) var(--spacing-sm); }
.lp-item--plain { min-height: 0; padding: var(--spacing-sm) 0; border-radius: 0; border-bottom: 2rpx solid var(--border-color); }
.lp-item--plain:last-child { border-bottom: none; }
.lp-item--plain .lp-lead--icon { width: 64rpx; height: 64rpx; border-radius: var(--radius-icon); background: var(--bg-soft); }
.lp-item--plain .lp-lead--empty { width: 64rpx; height: 64rpx; border-radius: var(--radius-icon); background: var(--bg-soft); }
.lp-item--plain .lp-lead-img { width: 72rpx; height: 72rpx; border-radius: 50%; background: var(--bg-placeholder); }
.lp-item--plain .lp-item-name { font-size: var(--font-body); font-weight: var(--weight-medium); }
.lp-item--plain.on { background: var(--bg-soft); }

.lp-footer { display: flex; padding: var(--spacing-md); border-top: 2rpx solid var(--border-color); flex-shrink: 0; }
.lp-confirm { flex: 1; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--color-primary); color: var(--color-on-primary); font-weight: var(--weight-bold); font-size: var(--font-subtitle); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.lp-confirm:active { opacity: 0.85; }
</style>
