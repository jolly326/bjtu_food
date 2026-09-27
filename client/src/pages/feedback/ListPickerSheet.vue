<template>
  <!-- 「标题 + 可搜索列表 + 项选择」底部选择器（component-org-sheet-unify）：
       骨架复用 BaseSheet；`searchable` 时内部防抖对外 emit('search', kw)，`options` 由父级按需供给；
       项点击 emit('select', option)，由父级决定关闭 / 进入下一级。
       注意：不得反向依赖其它分包组件。

       ⚠️ UI 统一 Loop Round 17 收敛（零消费即删）：本组件全仓**只有一个**消费方（反馈页「选择菜品」弹层），
       原为兼容多场景保留的分支经逐一核对**零消费**，遂移除：
       · 行左 `icon` 分支（消费方只用 `image`）与随之无用的 `IconSvg` / `COLOR_MAP` 依赖；
       · 单选高亮 `selectedKey` + `.lp-radio` 指示器（消费方不传选中态）；
       · 底部「完成」主按钮 `confirmable` / `confirmText`；
       · 头部返回箭头 `backable` 与 `back` 事件；
       · 默认尾部槽（承载「其他」自定义输入，无人使用）；
       · `rowStyle` 变体：消费方恒传 `'plain'` ⇒ 通栏行为**唯一形态**（原 `card` 分支为死代码）。
       `#empty` 空态槽保留（父级用公共 `EmptyState` 填充）。 -->
  <BaseSheet :visible="open" z-token="--z-sheet" :title="title" closable @close="emit('close')">
    <view class="lp-wrap">
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
          v-for="opt in options"
          :key="opt.key"
          class="lp-item"
          role="button"
          :aria-label="opt.label"
          @tap="pick(opt)"
        >
          <image v-if="opt.image" class="lp-lead-img" :src="opt.image" mode="aspectFill" />
          <view class="lp-item-info">
            <text class="lp-item-name">{{ opt.label }}</text>
            <text v-if="opt.sub" class="lp-item-sub">{{ opt.sub }}</text>
          </view>
        </view>
        <!-- 列表区内空态：无任何项时**由父级 #empty 槽承载**（唯一消费方恒提供该槽，
             UI 统一 Loop Round 4 起用公共 `EmptyState` 提供文案与排版，本组件不内置任何空态文案）。 -->
        <view v-if="options.length === 0" class="lp-empty">
          <slot name="empty" />
        </view>
      </scroll-view>
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import BaseSheet from '@/components/BaseSheet.vue'

interface PickerOption {
  /** 稳定唯一 key（列表渲染与去重） */
  key: string
  label: string
  /** 次行信息（如菜品「食堂 · 档口」，走 `utils/dish.joinLocation`） */
  sub?: string
  /** 行左缩略图（无图则只渲染文案） */
  image?: string
}

const props = withDefaults(defineProps<{
  open: boolean
  title?: string
  /** 是否渲染搜索框（内部防抖 300ms 后 emit('search')） */
  searchable?: boolean
  options: PickerOption[]
  /** 搜索框占位文案（searchable 时生效） */
  searchPlaceholder?: string
  /** 打开时搜索框预填值（深链预填用，缺省清空） */
  searchInitial?: string
}>(), {
  title: '',
  searchable: false,
  searchPlaceholder: '',
  searchInitial: '',
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'search', kw: string): void
  (e: 'select', option: PickerOption): void
}>()

const keyword = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

function pick(opt: PickerOption) {
  emit('select', opt)
}

/** 输入防抖：300ms 内连续输入只对外发一次搜索（父级自行决定是本地过滤还是请求） */
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    emit('search', keyword.value.trim())
  }, 300)
}

// 打开时（重）预填搜索框：有 searchInitial 则回填（下拉选品深链预填），否则清空
watch(
  () => props.open,
  (v) => {
    if (v) keyword.value = props.searchInitial || ''
  },
)
</script>

<style scoped>
/* Round 26：vh + dvh 双声明（同 BaseSheet 口径）—— H5 地址栏伸缩时 vh 大于真实可视高 ⇒ 弹层超高 */
.lp-wrap { display: flex; flex-direction: column; height: 70vh; height: 70dvh; max-height: 82vh; max-height: 82dvh; }
.lp-search { padding: var(--spacing-md); flex-shrink: 0; }
.lp-search-input { height: 72rpx; padding: 0 var(--spacing-md); background: var(--bg-page); border-radius: var(--radius-btn); font-size: var(--font-small); color: var(--text-primary); box-sizing: border-box; }
.lp-search-ph { color: var(--text-tertiary); }
.lp-list { flex: 1; min-height: 0; padding: 0 var(--spacing-md) var(--spacing-sm); box-sizing: border-box; }
/* 空态容器：只做居中占位，文案与排版由父级经 `#empty` 槽用公共 `EmptyState` 提供 */
.lp-empty { padding: var(--spacing-xl) 0; text-align: center; }

/* 行（通栏分隔观感，唯一形态）：`border-bottom` 做分隔，末行去线 */
.lp-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
  border-bottom: 2rpx solid var(--border-color);
  -webkit-tap-highlight-color: transparent;
}
.lp-item:last-child { border-bottom: none; }
.lp-lead-img { width: 72rpx; height: 72rpx; border-radius: var(--radius-circle); background: var(--bg-placeholder); flex-shrink: 0; }
.lp-item-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.lp-item-name { font-size: var(--font-body); color: var(--text-primary); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 次要说明（如「关联菜品」）再降档弱化，突出主名 */
.lp-item-sub { font-size: var(--font-tiny); color: var(--text-tertiary); }
</style>
