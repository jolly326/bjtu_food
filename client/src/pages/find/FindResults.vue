<template>
  <view class="filter-result">
    <!-- 结果态滚动容器：find-page-layout-restructure —— 滚动随结果内容区（FindResults）走，
         不再由 find/index 页根层包裹两态共用滚动。结果为空或请求失败时静默（无占位） -->
    <scroll-view
      class="results-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="emit('refresh')"
    >
      <!-- 搜索结果：一行一个菜品（find-result-card-polish：单卡内联于 FindResults，DishResultRow 已合并） -->
      <view class="mixed-list" :class="{ single: items.length === 1 }">
        <view
          v-for="item in items"
          :key="`${item.type}-${item.id}`"
          class="mixed-item"
          @tap="selectRow(item)"
        >
          <view class="mixed-thumb">
            <image
              v-if="item.image"
              :src="thumbSrc(item.image)"
              mode="aspectFill"
              class="mixed-thumb-img"
              :class="{ loaded: loadedSet.has(thumbSrc(item.image)) }"
              lazy-load
              @load="loadedSet.add(thumbSrc(item.image))"
            />
            <view v-else class="mixed-thumb-ph">
              <IconSvg name="dish" :size="48" color="var(--text-tertiary)" />
            </view>
          </view>
          <view class="mixed-info">
            <!-- 第一行：菜名（命中不再上红，主色仅留给价格）+ 评分 + 价格，基线对齐 -->
            <view class="mixed-title-row">
              <view class="mixed-name-group">
                <text class="mixed-name">
                  <text
                    v-for="(seg, si) in splitHighlight(item.name)"
                    :key="si"
                  >{{ seg.text }}</text>
                </text>
                <view v-if="item.rating != null" class="mixed-rating-group">
                  <IconSvg name="star-filled" :size="26" color="var(--color-primary)" class="mixed-rating-star" />
                  <text class="mixed-rating-num">{{ Number(item.rating).toFixed(1) }}</text>
                </view>
              </view>
              <view v-if="item.price != null" class="mixed-price-group">
                <view v-if="item.promoPrice != null" class="mixed-promo-badge">促销</view>
                <text class="mixed-price" v-if="item.promoPrice != null"><text class="mixed-price-sym">¥</text>{{ formatPrice(item.promoPrice) }}</text>
                <text class="mixed-price" v-else><text class="mixed-price-sym">¥</text>{{ formatPrice(item.price) }}</text>
                <text v-if="item.promoPrice != null && item.originalPrice != null" class="mixed-original">¥{{ formatPrice(item.originalPrice) }}</text>
              </view>
            </view>
            <!-- 标签行：复用全站 TagLabel（variant="plain" = 统一主色软底 chip，不按语义分色），
                 与首页卡片同源，不再本地自绘 -->
            <view v-if="item.tagLabels && item.tagLabels.length" class="mixed-tags">
              <TagLabel v-for="t in item.tagLabels" :key="t" :text="t" variant="plain" />
            </view>
            <!-- 底部：位置左 + 距你右，两级浅灰弱化 -->
            <view class="mixed-sub">
              <text class="mixed-sub-text">
                <text
                  v-for="(seg, si) in splitHighlight(item.sub || '')"
                  :key="si"
                >{{ seg.text }}</text>
              </text>
              <text v-if="item.distance != null" class="mixed-dist-seg">距你 {{ formatDistance(item.distance) }}</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { formatPrice } from '@/utils/money'
import { formatDistance } from '@/utils/format'
import TagLabel from '@/components/TagLabel.vue'
import { getImageUrl, getThumbUrl } from '@/utils/image'

/** 搜索混合结果项（仅菜品）；与 find 页 MixedResult 结构兼容 */
interface MixedResultItem {
  type: 'dish'
  id?: number
  name: string
  image?: string
  sub?: string
  price?: number
  rating?: number
  ratingCount?: number
  stall?: string
  tags?: string
  tagLabels?: string[]
  promoPrice?: number
  originalPrice?: number
  lat?: number
  lng?: number
  distance?: number
}

const props = defineProps<{
  items: MixedResultItem[]
  keyword?: string
  /** 结果态下拉刷新触发态：由宿主页面维护并复位 */
  refresherTriggered?: boolean
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
  /** 结果态下拉刷新：宿主页面据此重跑当前搜索/筛选 */
  (e: 'refresh'): void
}>()

/** 图片淡入去重集合（key = 缩略图 url） */
const loadedSet = reactive(new Set<string>())
function thumbSrc(src?: string): string {
  return src ? getImageUrl(getThumbUrl(src)) : ''
}

/** 关键词拆段：find-result-card-polish 后命中片段不再上主色（红只给价格），保留分段语义以备未来弱化 */
function splitHighlight(text: string): { text: string; hit: boolean }[] {
  const kw = (props.keyword || '').trim()
  if (!text || !kw) return [{ text, hit: false }]
  const segs: { text: string; hit: boolean }[] = []
  const lowerText = text.toLowerCase()
  const lowerKw = kw.toLowerCase()
  let start = 0
  let idx = lowerText.indexOf(lowerKw, start)
  while (idx !== -1) {
    if (idx > start) segs.push({ text: text.slice(start, idx), hit: false })
    segs.push({ text: text.slice(idx, idx + kw.length), hit: true })
    start = idx + kw.length
    idx = lowerText.indexOf(lowerKw, start)
  }
  if (start < text.length) segs.push({ text: text.slice(start), hit: false })
  return segs
}

function selectRow(item: MixedResultItem) {
  if (item.id != null) emit('select', item.id)
}
</script>

<style scoped>
/* 结果内容区占满宿主（find-body/results-host flex 链），滚动由内部 scroll-view 承担 */
.filter-result {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}
.results-scroll {
  flex: 1;
  min-height: 0;
  padding-bottom: var(--spacing-lg);
}
/* 搜索结果列表（仅菜品，一行一个，左图右信息）
   顶部间距收紧：首卡贴近 FilterBar，不再叠加 md 大留白（find-result-card-polish 5.1） */
.mixed-list { margin: 0 var(--spacing-md) var(--spacing-md); }
/* 单条结果视觉平衡：保留适度顶部空间但不产生过大空档 */
.mixed-list.single { margin-top: var(--spacing-md); }

/* ===== 单卡样式（原 DishResultRow 内联，find-result-card-polish） ===== */
.mixed-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  /* 卡内边距：上下 28rpx(≈14px)、左右 32rpx(=16px)，对齐 8 基网格 */
  padding: 28rpx var(--spacing-lg);
  box-shadow: var(--shadow-card);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}
.mixed-item + .mixed-item { margin-top: var(--spacing-sm); }
.mixed-thumb {
  width: 160rpx;
  height: 160rpx;
  flex-shrink: 0;
  border-radius: var(--radius-icon);
  overflow: hidden;
  background: var(--bg-page);
}
.mixed-thumb-img { width: 100%; height: 100%; opacity: 0; transition: opacity 0.32s var(--ease-out); }
.mixed-thumb-img.loaded { opacity: 1; }
.mixed-thumb-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.mixed-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); min-height: 160rpx; justify-content: center; }
.mixed-title-row { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); }
.mixed-name-group { flex: 1; min-width: 0; display: flex; align-items: center; gap: var(--spacing-sm); }
.mixed-name {
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-title);
  /* 菜名：600 一级深灰第一阅读落点（find-result-card-polish） */
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
/* 命中片段不再上主色：红仅保留给价格（find-result-card-polish） */
.mixed-tags { display: flex; flex-wrap: wrap; gap: var(--spacing-2xs); margin-top: 2rpx; }
.mixed-promo-badge {
  font-size: var(--font-tiny);
  line-height: 1.4;
  padding: 2rpx 12rpx;
  border-radius: var(--radius-tag);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-weight: var(--weight-bold);
  align-self: center;
}
.mixed-original { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
.mixed-price-group { display: flex; align-items: baseline; gap: var(--spacing-2xs); flex-shrink: 0; }
/* 价格：专用主色 + 600，卡片唯一高饱和强调 */
.mixed-price { font-size: var(--font-title); font-weight: var(--weight-semibold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.mixed-price-sym { font-size: var(--font-body); font-weight: var(--weight-medium); }
.mixed-rating-group { display: inline-flex; align-items: center; gap: 2rpx; flex-shrink: 0; }
.mixed-rating-star { flex-shrink: 0; }
.mixed-rating-num { font-size: var(--font-small); font-weight: var(--weight-medium); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.mixed-sub { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); margin-top: var(--spacing-xs); font-size: var(--font-aux); }
/* 底部位置/距你统一三级浅灰弱化（find-result-card-polish） */
.mixed-sub-text { flex: 1; min-width: 0; color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mixed-dist-seg { flex-shrink: 0; color: var(--text-tertiary); font-variant-numeric: tabular-nums; }
</style>
