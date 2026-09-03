<template>
  <view class="mixed-item" @tap="onTap">
    <view class="mixed-thumb">
      <image
        v-if="dish.image"
        :src="thumbSrc"
        mode="aspectFill"
        class="mixed-thumb-img"
        :class="{ loaded }"
        lazy-load
        @load="loaded = true"
      />
      <view v-else class="mixed-thumb-ph">
        <IconSvg name="dish" :size="48" color="var(--text-tertiary)" />
      </view>
    </view>
    <view class="mixed-info">
      <!-- 第一行：菜名 + 评分（贴名小号）+ 价格 两端对齐（名称/价格为搜索核心信息） -->
      <view class="mixed-title-row">
        <view class="mixed-name-group">
          <text class="mixed-name">
            <text
              v-for="(seg, si) in splitHighlight(dish.name)"
              :key="si"
              :class="{ hl: seg.hit }"
            >{{ seg.text }}</text>
          </text>
          <!-- 评分：贴近菜名右侧、小一号/两号（星 + 分数，不含评论数；星星放大与菜名字号匹配） -->
          <view v-if="dish.rating != null" class="mixed-rating-group">
            <IconSvg name="star-filled" :size="26" color="var(--color-star)" class="mixed-rating-star" />
            <text class="mixed-rating-num">{{ Number(dish.rating).toFixed(1) }}</text>
          </view>
        </view>
        <!-- 价格组（促销角标 + 促销价/单价 + 原价划线），菜品才有价格 -->
        <view v-if="dish.price != null" class="mixed-price-group">
          <view v-if="dish.promoPrice != null" class="mixed-promo-badge">促销</view>
          <text class="mixed-price" v-if="dish.promoPrice != null"><text class="mixed-price-sym">¥</text>{{ dish.promoPrice.toFixed(2) }}</text>
          <text class="mixed-price" v-else><text class="mixed-price-sym">¥</text>{{ dish.price.toFixed(2) }}</text>
          <text v-if="dish.promoPrice != null && dish.originalPrice != null" class="mixed-original">¥{{ dish.originalPrice.toFixed(2) }}</text>
        </view>
      </view>
      <!-- 第二行：标徽（属性标签 chips，主色软底） -->
      <view v-if="dish.tagLabels && dish.tagLabels.length" class="mixed-tags">
        <text v-for="t in dish.tagLabels" :key="t" class="mixed-tag">{{ t }}</text>
      </view>
      <!-- 第三行：位置（档口·食堂 + 距你，两端对齐） -->
      <view class="mixed-sub">
        <text class="mixed-sub-text">
          <text
            v-for="(seg, si) in splitHighlight(dish.sub || '')"
            :key="si"
            :class="{ hl: seg.hit }"
          >{{ seg.text }}</text>
        </text>
        <text v-if="dish.distance != null" class="mixed-dist-seg">距你 {{ fmtMixedDistance(dish.distance) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { getImageUrl, getThumbUrl } from '@/utils/image'
import IconSvg from '@/components/IconSvg.vue'

/** 搜索结果（仅菜品）单行展示模型；与 find 页 MixedResult 结构兼容 */
interface DishResultItem {
  id?: number
  name: string
  image?: string
  sub?: string
  price?: number
  rating?: number
  tagLabels?: string[]
  promoPrice?: number
  originalPrice?: number
  distance?: number
}

const props = defineProps<{
  dish: DishResultItem
  /** 当前搜索词，用于命中高亮（朱砂红） */
  keyword?: string
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
}>()

/** 图片淡入：load 完成后置 true，配合 .loaded 做 opacity 过渡（C12） */
const loaded = ref(false)
const thumbSrc = computed(() => getImageUrl(getThumbUrl(props.dish.image)))

/** 距你文案：米/公里自适应 */
function fmtMixedDistance(m: number): string {
  if (!Number.isFinite(m) || m < 0) return ''
  if (m > 999000) return '>999km'
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${Math.round(m)}m`
}

/** A1 关键词高亮：将文本按当前 keyword 拆分为 [{text, hit}] 片段，命中段套 .hl（朱砂红），避免 v-html XSS */
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

function onTap() {
  if (props.dish.id != null) emit('select', props.dish.id)
}
</script>

<style scoped>
/* 搜索结果列表行（仅菜品，一行一个，左图右信息）。
   Apple Design 列表行卡：大圆角 + hairline 分隔 + 按下背景高亮（Apple 偏好 highlight 而非 scale） */
.mixed-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  padding: var(--spacing-md);
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
/* C12 图片淡入：初始透明，loaded 后置 1 由 transition 淡入 */
.mixed-thumb-img { width: 100%; height: 100%; opacity: 0; transition: opacity 0.32s var(--ease-out); }
.mixed-thumb-img.loaded { opacity: 1; }
.mixed-thumb-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.mixed-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); min-height: 160rpx; justify-content: center; }
/* 第一行：菜名（加大）+ 评分（贴名小号）+ 价格 两端对齐（名称/价格为搜索核心，价格不换行防挤占菜名） */
.mixed-title-row { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); }
.mixed-name-group { flex: 1; min-width: 0; display: flex; align-items: center; gap: var(--spacing-sm); }
.mixed-name {
  /* 不撑满：评分紧贴菜名右侧（而非被推到行尾贴近价格）；长菜名可收缩省略 */
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  /* A5 菜名两行截断：长菜名不再丢信息 */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
/* A1 关键词高亮：命中段朱砂红 */
.mixed-name .hl, .mixed-sub .hl { color: var(--color-primary); font-weight: var(--weight-bold); }
/* B9 属性标签 chips */
.mixed-tags { display: flex; flex-wrap: wrap; gap: var(--spacing-2xs); margin-top: 2rpx; }
.mixed-tag {
  font-size: var(--font-tiny);
  line-height: 1.4;
  padding: 2rpx 12rpx;
  border-radius: var(--radius-tag);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: var(--weight-medium);
}
/* B10 促销角标 + 原价划线 */
.mixed-promo-badge {
  font-size: var(--font-tiny);
  line-height: 1.4;
  padding: 2rpx 12rpx;
  border-radius: var(--radius-tag);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-weight: var(--weight-bold);
  /* 价格组 baseline 对齐下居中，避免角标因 padding 偏上 */
  align-self: center;
}
.mixed-original { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
/* 价格组：促销角标 + 促销价/单价 + 原价划线；flex-shrink:0 防被菜名挤压，baseline 对齐 */
.mixed-price-group { display: flex; align-items: baseline; gap: var(--spacing-2xs); flex-shrink: 0; }
/* A6 价格视觉强化：¥ 符号缩小、数字放大，统一用专用价色 --color-price */
.mixed-price { font-size: var(--font-title); font-weight: var(--weight-bold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.mixed-price-sym { font-size: var(--font-body); font-weight: var(--weight-medium); }
/* 评分组：贴近菜名右侧、小一号/两号（弱化星级，避免喧宾夺主） */
.mixed-rating-group { display: inline-flex; align-items: center; gap: 2rpx; flex-shrink: 0; }
.mixed-rating-star { flex-shrink: 0; }
.mixed-rating-num { font-size: var(--font-small); font-weight: var(--weight-medium); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
/* 第三行位置：左段档口·食堂可省略、右段「距你 Xm」固定不截断，两端对齐，与标徽行分隔 */
.mixed-sub { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-secondary); }
.mixed-sub-text { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* A4 距离段：主色强调，无定位时不显示 */
.mixed-dist-seg { flex-shrink: 0; color: var(--color-primary); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; }
</style>
