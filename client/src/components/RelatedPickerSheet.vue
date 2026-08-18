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
    <view class="sheet-head">
      <text class="sheet-title">选择关联对象</text>
      <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @tap="$emit('close')" />
    </view>

    <view class="sheet-tabs">
      <view class="sheet-tab" :class="{ active: tab === 'dish' }" @tap="tab = 'dish'">菜品</view>
      <view class="sheet-tab" :class="{ active: tab === 'stall' }" @tap="tab = 'stall'">档口</view>
    </view>

    <view class="sheet-search">
      <SearchBar
        v-model="keyword"
        input-mode
        :placeholder="tab === 'dish' ? '搜索菜品' : '搜索档口'"
        :margin="'0'"
        @update:model-value="onKeyword"
      />
    </view>

    <scroll-view class="sheet-list" scroll-y>
      <view v-if="loading" class="sheet-empty">
        <view class="footer-spinner" />
      </view>
      <view v-else-if="candidates.length === 0" class="sheet-empty">
        <text class="sheet-empty-text">{{ keyword ? '没有找到相关结果' : '输入关键词搜索' }}</text>
      </view>
      <view
        v-for="item in candidates"
        :key="`${item.type}-${item.id}`"
        class="sheet-item"
        :class="{ on: isSelected(item) }"
        @tap="select(item)"
      >
        <image v-if="item.image" class="sheet-item-img" :src="item.image" mode="aspectFill" />
        <view v-else class="sheet-item-img sheet-item-img-empty">
          <IconSvg :name="item.type === 'stall' ? 'list' : 'dish'" :size="36" color="var(--text-tertiary)" />
        </view>
        <text class="sheet-item-name">{{ item.name }}</text>
        <IconSvg v-if="isSelected(item)" name="check" :size="32" color="var(--color-primary)" />
      </view>
    </scroll-view>

    <view class="sheet-footer">
      <view class="sheet-clear" @tap="$emit('clear')">不关联</view>
      <view class="sheet-confirm" @tap="confirm">确定</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick, onUnmounted } from 'vue'
import IconSvg from './IconSvg.vue'
import SearchBar from './SearchBar.vue'
import * as dishApi from '@/api/dish'
import { getImageUrl } from '@/utils/image'
import { getCanteensWithStalls } from '@/api/canteen'

/**
 * RelatedPickerSheet —— 关联对象选择弹层（task-14 W2/W5 / task-13 T20）
 * 修复 V3：档口联想走正式 API（getCanteensWithStalls 全量档口，含真实 id），
 * 返回真实 stallId，禁止伪造 id。菜品走 searchDishesPage 正式接口。
 */
import type { RelatedItem } from './related-item'

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

// 下拉关闭手势（与 ApplySheet / ContributeSheet 等底部弹层保持一致）
const dragOffset = ref(0)
const dragging = ref(false)

/** 空处理器：mask touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}

const sheetStyle = computed(() => ({
  transform: `translateY(calc(${props.open ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  transition: dragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
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
  // 记录瞬时速度（apple-design §5 velocity handoff）
  const dt = Math.max(now - lastTime, 1)
  velocity = ((y - lastY) / dt) * 1000 // px/s
  lastY = y
  lastTime = now
  const delta = y - startY
  // 仅允许向下拖拽
  dragOffset.value = delta > 0 ? delta : 0
}
function onTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  // 松手速度 > 480px/s 视为向下甩动直接关闭，或位移 > 120rpx 关闭（apple-design §5 手势阈值），否则回弹
  if (velocity > 480 || dragOffset.value > 120) emit('close')
  dragOffset.value = 0
}

const tab = ref<'dish' | 'stall'>('dish')
const keyword = ref('')
const loading = ref(false)
const candidates = ref<RelatedItem[]>([])

let timer: ReturnType<typeof setTimeout> | null = null

function isSelected(item: RelatedItem): boolean {
  return !!props.selected && props.selected.id === item.id && props.selected.type === item.type
}

/** 请求序号守卫：open/tab/keyword 频繁变化时的多次 loadCandidates，丢弃过期响应，避免重复渲染/竞态（P1 筛选去重） */
let candidateSeq = 0
async function loadCandidates() {
  const seq = ++candidateSeq
  const kw = keyword.value.trim()
  loading.value = true
  candidates.value = []
  try {
    if (tab.value === 'dish') {
      const res = await dishApi.searchDishesPage({ keyword: kw, page: 1, pageSize: 10 })
      candidates.value = res.list.map(d => ({
        id: d.id,
        name: d.name,
        image: getImageUrl(d.image),
        type: 'dish' as const,
      }))
    } else {
      // 正式档口列表：全量档口（后端返回真实 id），前端按关键词过滤
      const canteens: any[] = await getCanteensWithStalls()
      const stalls: { id: number; name: string; images?: any }[] = []
      const seen = new Set<number>()
      for (const c of canteens) {
        for (const s of (c.stalls || []) as any[]) {
          const id = Number(s.id || 0)
          if (id && !seen.has(id)) {
            seen.add(id)
            stalls.push({ id, name: s.name || '', images: s.images })
          }
        }
      }
      const filtered = kw
        ? stalls.filter(s => s.name.includes(kw))
        : stalls
      candidates.value = filtered.slice(0, 20).map(s => ({
        id: s.id,
        name: s.name,
        image: Array.isArray(s.images) ? getImageUrl(s.images[0] || '') : getImageUrl((s.images as any) || ''),
        type: 'stall' as const,
      }))
    }
    // 过期响应（期间又切换了 tab/打开状态/关键词）直接丢弃，不覆盖最新结果
    if (seq !== candidateSeq) return
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

/** 确定：回传当前选中项（props.selected），由页面统一关闭弹层 */
function confirm() {
  emit('confirm', props.selected ?? null)
}

// 打开时或切换 tab 时重置并加载
watch(() => props.open, (v) => {
  if (v) {
    keyword.value = ''
    loadCandidates()
  }
})
watch(tab, () => {
  keyword.value = ''
  loadCandidates()
})

// N05 修复：卸载时清理防抖定时器，避免组件销毁后回调仍触发
onUnmounted(() => {
  if (timer) clearTimeout(timer)
  timer = null
})
</script>

<style scoped>
.sheet-mask { position: fixed; inset: 0; background: var(--overlay-scrim); z-index: 90; opacity: 0; transition: opacity 0.3s ease; }
.sheet-mask.show { opacity: 1; }
.related-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 100;
  transform: translateY(100%);
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);
  display: flex; flex-direction: column;
  max-height: 80vh;
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
}
.related-sheet.open { transform: translateY(0); }
.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-title { font-size: var(--font-h3); font-weight: var(--weight-bold); color: var(--text-primary); }
.sheet-close { font-size: var(--font-body); color: var(--text-tertiary); padding: 0 var(--spacing-xs); }
.sheet-tabs { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md) var(--spacing-md) 0; }
.sheet-tab { padding: var(--spacing-xs) var(--spacing-lg); border-radius: var(--radius-tag); background: var(--bg-soft); font-size: var(--font-aux); color: var(--text-secondary); font-weight: var(--weight-semibold); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.sheet-tab:active { transform: scale(var(--press-scale)); }
.sheet-tab.active { background: var(--color-primary-soft); color: var(--color-primary); }
.sheet-search { padding: var(--spacing-md); }
.sheet-list { flex: 1; overflow-y: auto; padding: 0 var(--spacing-md); }
.sheet-empty { padding: var(--spacing-xl) 0; text-align: center; }
.sheet-empty-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.sheet-item { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); transition: background 0.12s ease; -webkit-tap-highlight-color: transparent; }
.sheet-item.on { background: var(--bg-soft); }
.sheet-item-img { width: 72rpx; height: 72rpx; border-radius: var(--radius-tag); background: var(--bg-page); flex-shrink: 0; }
.sheet-item-img-empty { display: flex; align-items: center; justify-content: center; }
.sheet-item-name { flex: 1; font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sheet-footer { display: flex; gap: var(--spacing-md); padding: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.sheet-clear { flex: 1; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--bg-soft); color: var(--text-secondary); font-weight: var(--weight-semibold); -webkit-tap-highlight-color: transparent; }
.sheet-confirm { flex: 2; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--color-primary); color: var(--color-on-primary); font-weight: var(--weight-bold); -webkit-tap-highlight-color: transparent; }

.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; margin: 0 auto; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .related-sheet { transition: opacity 0.2s ease; transform: none !important; }
  .sheet-mask { transition: none !important; }
  .footer-spinner { animation: none; }
}
</style>
