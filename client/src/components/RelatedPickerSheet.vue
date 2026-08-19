<template>
  <view v-if="open" class="sheet-mask" :class="{ show: maskShow }" @tap="$emit('close')" @touchmove.stop.prevent="noop" />
  <view
    class="related-sheet"
    :class="{ open }"
    :style="sheetStyle"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchEnd"
  >
    <view class="sheet-grabber" />
    <!-- 头部：与全站底部 Sheet 统一（标题 + 右上角关闭 + 分隔线；不显示「操作」冗余标题） -->
    <view class="sheet-head">
      <text class="sheet-title">选择关联菜品</text>
      <view class="sheet-close" role="button" aria-label="关闭" @tap="$emit('close')">
        <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
      </view>
    </view>

    <view class="sheet-search">
      <SearchBar v-model="keyword" input-mode placeholder="搜索菜品" :margin="'0'" @update:model-value="onKeyword" />
    </view>

    <scroll-view class="sheet-list" scroll-y>
      <!-- 不关联：列表首项，语义与结果项并列（选中态高亮） -->
      <view
        class="sheet-item sheet-item--none"
        :class="{ on: !selected }"
        role="button"
        aria-label="不关联"
        @tap="$emit('clear')"
      >
        <view class="sheet-item-img sheet-item-img-empty">
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
        </view>
        <view class="sheet-item-info">
          <text class="sheet-item-name">不关联</text>
          <text class="sheet-item-sub">自由动态，不带菜品标签</text>
        </view>
        <IconSvg v-if="!selected" name="check" :size="32" color="var(--color-primary)" />
      </view>

      <view class="sheet-divider" />

      <view v-if="loading" class="sheet-empty">
        <view class="footer-spinner" />
      </view>
      <view v-else-if="candidates.length === 0" class="sheet-empty">
        <text class="sheet-empty-text">{{ keyword ? '没有找到相关菜品' : '输入关键词搜索菜品' }}</text>
      </view>
      <view
        v-for="item in candidates"
        :key="item.id"
        class="sheet-item"
        :class="{ on: isSelected(item) }"
        role="button"
        :aria-label="`关联菜品 ${item.name}`"
        @tap="select(item)"
      >
        <image v-if="item.image" class="sheet-item-img" :src="item.image" mode="aspectFill" />
        <view v-else class="sheet-item-img sheet-item-img-empty">
          <IconSvg name="dish" :size="36" color="var(--text-tertiary)" />
        </view>
        <view class="sheet-item-info">
          <text class="sheet-item-name">{{ item.name }}</text>
          <text class="sheet-item-sub">关联菜品</text>
        </view>
        <IconSvg v-if="isSelected(item)" name="check" :size="32" color="var(--color-primary)" />
      </view>
    </scroll-view>

    <!-- 底部：单一「完成」主按钮（应用当前选中并关闭，Apple 少一步操作） -->
    <view class="sheet-footer">
      <view class="sheet-confirm" role="button" aria-label="完成" @tap="confirm">完成</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick, onUnmounted } from 'vue'
import IconSvg from './IconSvg.vue'
import SearchBar from './SearchBar.vue'
import * as dishApi from '@/api/dish'
import { getImageUrl } from '@/utils/image'
import type { RelatedItem } from './related-item'

/**
 * RelatedPickerSheet —— 关联菜品选择弹层（task-14 W2/W5 / task-13 T20）
 * V5 简化：发表动态仅允许关联菜品（产品决策：不支持关联档口），
 * 保留「不关联」列表首项 + 单一「完成」按钮的 Apple 底部 Sheet 范式（与登录弹窗头部结构一致）。
 */
const props = defineProps<{
  open: boolean
  /** 当前已选（用于高亮） */
  selected?: RelatedItem | null
}>()

const emit = defineEmits<{
  close: []
  clear: []
  select: [item: RelatedItem]
  confirm: [item: RelatedItem | null]
}>()

// 遮罩淡入（与兄弟弹层一致）
const maskShow = ref(false)
watch(() => props.open, (v) => {
  if (v) {
    nextTick(() => { maskShow.value = true })
  } else {
    maskShow.value = false
  }
})

// 下拉关闭手势（与 ApplySheet / AuthSheet 等底部弹层保持一致）
const dragOffset = ref(0)
const dragging = ref(false)

/** 空处理器：mask touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}

const sheetStyle = computed(() => ({
  transform: `translateY(calc(${props.open ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  transition: dragging.value ? 'none' : 'transform var(--duration-slow) var(--ease-drawer)',
}))

let startY = 0
let lastY = 0
let lastTime = 0
let velocity = 0
function onTouchStart(e: any) {
  startY = e.touches?.[0]?.clientY ?? 0
  lastY = startY
  lastTime = Date.now()
  velocity = 0
  dragging.value = true
}
function onTouchMove(e: any) {
  if (!dragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const now = Date.now()
  const dt = Math.max(now - lastTime, 1)
  velocity = ((y - lastY) / dt) * 1000
  lastY = y
  lastTime = now
  const delta = y - startY
  dragOffset.value = delta > 0 ? delta : 0
}
function onTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (velocity > 480 || dragOffset.value > 120) emit('close')
  dragOffset.value = 0
}

const keyword = ref('')
const loading = ref(false)
const candidates = ref<RelatedItem[]>([])

let timer: ReturnType<typeof setTimeout> | null = null

function isSelected(item: RelatedItem): boolean {
  return !!props.selected && props.selected.id === item.id && props.selected.type === item.type
}

/** 请求序号守卫：open/keyword 频繁变化时的多次 loadCandidates，丢弃过期响应，避免重复渲染/竞态（P1 筛选去重） */
let candidateSeq = 0
async function loadCandidates() {
  const seq = ++candidateSeq
  const kw = keyword.value.trim()
  loading.value = true
  candidates.value = []
  try {
    const res = await dishApi.searchDishesPage({ keyword: kw, page: 1, pageSize: 10 })
    // 过期响应（期间又切换了打开状态/关键词）直接丢弃，不覆盖最新结果
    if (seq !== candidateSeq) return
    candidates.value = res.list.map(d => ({
      id: d.id,
      name: d.name,
      image: getImageUrl(d.image),
      type: 'dish' as const,
    }))
  } catch {
    if (seq !== candidateSeq) return
    candidates.value = []
  } finally {
    if (seq === candidateSeq) loading.value = false
  }
}

function onKeyword() {
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => { loadCandidates() }, 300)
}

function select(item: RelatedItem) {
  emit('select', item)
}

/** 完成：回传当前选中项（props.selected），由页面统一关闭弹层 */
function confirm() {
  emit('confirm', props.selected ?? null)
}

// 打开时重置并加载
watch(() => props.open, (v) => {
  if (v) {
    keyword.value = ''
    loadCandidates()
  }
})

// N05 修复：卸载时清理防抖定时器，避免组件销毁后回调仍触发
onUnmounted(() => {
  if (timer) clearTimeout(timer)
  timer = null
})
</script>

<style scoped>
.sheet-mask { position: fixed; inset: 0; background: var(--overlay-scrim); z-index: 90; opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); }
.sheet-mask.show { opacity: 1; }
.related-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 100;
  transform: translateY(100%);
  transition: transform var(--duration-slow) var(--ease-drawer);
  display: flex; flex-direction: column;
  max-height: 82vh;
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
}
.related-sheet.open { transform: translateY(0); }
.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
/* 头部：与登录弹窗同构（标题 + 右上角关闭 + 底部分隔线） */
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-sm) var(--spacing-md); border-bottom: 2rpx solid var(--border-color); flex-shrink: 0; }
.sheet-title { font-size: var(--font-h2); font-weight: var(--weight-bold); color: var(--text-primary); letter-spacing: var(--tracking-h3); }
.sheet-close { padding: var(--spacing-2xs) var(--spacing-xs); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.sheet-close:active { opacity: 0.5; }
.sheet-search { padding: var(--spacing-md); flex-shrink: 0; }
.sheet-list { flex: 1; overflow-y: auto; padding: 0 var(--spacing-md); }
.sheet-empty { padding: var(--spacing-xl) 0; text-align: center; }
.sheet-empty-text { font-size: var(--font-aux); color: var(--text-tertiary); }
/* 列表项：图 + 主次两行信息 + 选中勾；选中行浅底高亮（iOS 列表选中态） */
.sheet-item { display: flex; align-items: center; gap: var(--spacing-sm); min-height: 88rpx; padding: var(--spacing-sm) var(--spacing-sm); border-radius: var(--radius-card); transition: background var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
.sheet-item.on { background: var(--bg-soft); }
.sheet-item-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.sheet-item-name { font-size: var(--font-body); color: var(--text-primary); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sheet-item-sub { font-size: var(--font-aux); color: var(--text-tertiary); }
.sheet-item-img { width: 72rpx; height: 72rpx; border-radius: var(--radius-tag); background: var(--bg-page); flex-shrink: 0; }
.sheet-item-img-empty { display: flex; align-items: center; justify-content: center; }
.sheet-item--none { margin-bottom: var(--spacing-2xs); }
.sheet-divider { height: 2rpx; background: var(--border-color); margin: var(--spacing-2xs) var(--spacing-sm) var(--spacing-xs); }
/* 底部：单一主色「完成」按钮 */
.sheet-footer { display: flex; padding: var(--spacing-md); border-top: 2rpx solid var(--border-color); flex-shrink: 0; }
.sheet-confirm { flex: 1; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--color-primary); color: var(--color-on-primary); font-weight: var(--weight-bold); font-size: var(--font-card); transition: opacity var(--duration-fast) ease, transform var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
.sheet-confirm:active { opacity: 0.85; transform: scale(var(--press-scale)); }

.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; margin: 0 auto; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .related-sheet { transition: opacity 0.2s ease; transform: none !important; }
  .sheet-mask { transition: none !important; }
  .footer-spinner { animation: none; }
}
</style>
