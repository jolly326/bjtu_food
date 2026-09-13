<template>
  <CardSection>
    <!-- 第一行：名称 + 价格（标徽独立成行） -->
    <view class="title-row" @longpress="emit('dish-longpress')">
      <text class="dish-name" aria-label="菜品名称">{{ dish.name }}</text>
      <view class="price-row">
        <block v-if="hasPromo">
          <text class="promo-price">¥{{ dish.promoPrice }}</text>
          <text class="origin-price">¥{{ dish.originalPrice }}</text>
          <text class="promo-tag"><IconSvg name="clock" :size="22" color="var(--color-primary)" /> 限时优惠</text>
        </block>
        <text v-else class="price-text">¥{{ dish.price }}</text>
      </view>
    </view>

    <!-- 第二行：标徽（独立成行） -->
    <view class="tag-row tag-row--standalone" v-if="tagList.length > 0">
      <TagLabel v-for="tag in tagList" :key="tag" :text="tag" />
    </view>

    <!-- 位置 + 右侧距你 -->
    <view class="loc-row" aria-label="所在位置">
      <view class="loc-left">
        <IconSvg name="location" :size="26" color="var(--color-primary)" class="loc-icon" />
        <text class="loc-text">{{ locationText }}</text>
      </view>
      <view class="loc-dist" :class="distActive ? 'loc-dist--lead' : 'loc-dist--muted'" @tap="emit('dist-tap')" role="button" :aria-label="distActive ? '距你距离' : '开启定位查看距你多远'">
        <IconSvg v-if="distActive" name="location" :size="22" color="var(--color-primary)" class="loc-dist-icon" />
        <text class="loc-dist-text">{{ distText }}</text>
      </view>
    </view>

    <!-- 简介（展开按钮同行右侧，默认两行） -->
    <view class="desc-row" v-if="dish.description">
      <view class="desc-content-wrap">
        <text class="desc-content" :class="{ 'desc-content--collapsed': !descExpanded }">{{ dish.description }}</text>
      </view>
      <text class="desc-toggle" @tap="descExpanded = !descExpanded" role="button" aria-label="展开或收起简介">
        {{ descExpanded ? '收起' : '展开' }}
      </text>
    </view>

    <!-- 关键指标条（评分/评价/口味/地域，固定四列，缺省 - 占位） -->
    <view class="metric-panel" v-if="hasMetrics">
      <view class="metric-col">
        <text class="metric-val">{{ ratingText }}</text>
        <text class="metric-label">评分</text>
      </view>
      <view class="metric-col">
        <text class="metric-val">{{ countText }}</text>
        <text class="metric-label">评价</text>
      </view>
      <view class="metric-col">
        <text class="metric-val metric-val--text">{{ spiceText }}</text>
        <text class="metric-label">口味</text>
      </view>
      <view class="metric-col">
        <text class="metric-val metric-val--text">{{ regionText }}</text>
        <text class="metric-label">地域</text>
      </view>
    </view>

    <!-- 纠错入口（贡献入口 A）：把④主线从「深度 2：我的→反馈→选类型」拉到「内容页原地」。
         点击直达反馈页预选「信息不对」并自动关联本菜品（落点唯一构造函数，from=dish） -->
    <view class="correct-row" role="button" aria-label="信息有误" hover-class="pressed" @tap="goCorrect">
      <view class="correct-left">
        <IconSvg name="report" :size="26" color="var(--text-tertiary)" />
        <text class="correct-text">信息有误？</text>
      </view>
      <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" />
    </view>
  </CardSection>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { Dish } from '@/types/dish'
import CardSection from '@/components/CardSection.vue'
import TagLabel from '@/components/TagLabel.vue'
import IconSvg from '@/components/IconSvg.vue'
import { feedbackEntryUrl } from '@/utils/routes'

const props = defineProps<{
  dish: Dish
  locationText: string
  distText: string
  distActive: boolean
}>()

const emit = defineEmits<{
  (e: 'dish-longpress'): void
  (e: 'dist-tap'): void
}>()

/** 简介展开/收起（换菜品时复位） */
const descExpanded = ref(false)
watch(() => props.dish.name, () => { descExpanded.value = false })

/** 折扣价展示 */
const hasPromo = computed(() => !!props.dish.promoPrice)

/** 标签列表（新品 + 属性标签去重） */
const tagList = computed(() => {
  const d = props.dish
  const list: string[] = []
  if (d.isNew) list.push('新品')
  for (const t of d.tags || []) {
    if (!list.includes(t)) list.push(t)
  }
  return list
})

/** 地域文案 */
const regionText = computed(() => (props.dish.region && props.dish.region.trim()) || '-')

/** 口味文案 */
const spiceText = computed(() => {
  const map: Record<number, string> = { 0: '不辣', 1: '微辣', 2: '中辣', 3: '重辣' }
  const lv = props.dish.spiceLevel
  return lv != null && map[lv] ? map[lv] : '-'
})

/** 指标展示值 */
const ratingText = computed(() => (props.dish.rating > 0 ? props.dish.rating.toFixed(1) : '-'))
const countText = computed(() => {
  const v = props.dish.ratingCount || 0
  return v > 0 ? formatCount(v) : '-'
})

/** 评价数格式化：<1万显原数，≥1万显 x.x万 */
function formatCount(n: number): string {
  if (n >= 10000) return `${(n / 10000).toFixed(1)}万`
  return String(n)
}

const hasMetrics = computed(() => {
  const d = props.dish
  return (d.rating || 0) > 0 || (d.ratingCount || 0) > 0 || spiceText.value !== '-' || regionText.value !== '-'
})

/** 纠错入口 → 反馈页预选「信息不对」并关联本菜品（落点唯一构造函数，from=dish，见 contribution-entry） */
function goCorrect() {
  uni.navigateTo({
    url: feedbackEntryUrl({ type: 'error', from: 'dish', dishId: props.dish.id, dishName: props.dish.name }),
  })
}
</script>

<style scoped>
.title-row { display: flex; align-items: flex-start; flex-wrap: wrap; gap: var(--spacing-xs); }
.dish-name { font-size: var(--font-title); font-weight: var(--weight-bold); letter-spacing: var(--tracking-h2); line-height: 1.2; color: var(--text-primary); flex: 0 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tag-row { display: flex; flex-wrap: nowrap; gap: var(--spacing-xs); flex: 0 0 auto; align-items: center; }
.tag-row--standalone { margin-top: var(--spacing-md); }
.price-row { display: flex; align-items: baseline; gap: var(--spacing-xs); flex-wrap: wrap; flex: 0 0 auto; margin-left: auto; }
.price-text { font-size: var(--font-h2); font-weight: var(--weight-bold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.promo-price { font-size: var(--font-h2); font-weight: var(--weight-semibold); color: var(--color-error); font-variant-numeric: tabular-nums; }
.origin-price { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
/* dish-detail-visual-polish：促销角标弱化为浅底细字，不抢价格 */
.promo-tag { font-size: var(--font-tiny); font-weight: var(--weight-bold); color: var(--color-primary); background: var(--color-primary-soft); padding: 0 var(--spacing-xs); border-radius: var(--radius-icon); display: inline-flex; align-items: center; gap: var(--spacing-xs); }
.loc-row { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); margin-top: var(--spacing-md); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); }
.loc-left { display: flex; align-items: center; gap: var(--spacing-xs); min-width: 0; flex: 1 1 auto; }
.loc-icon { width: 26rpx; height: 26rpx; line-height: 1; flex-shrink: 0; }
.loc-text { flex: 1; min-width: 0; font-size: var(--font-small); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.loc-dist { display: flex; align-items: center; gap: 6rpx; flex: 0 0 auto; margin-left: var(--spacing-sm); }
.loc-dist-icon { width: 22rpx; height: 22rpx; line-height: 1; flex-shrink: 0; }
.loc-dist-text { font-size: var(--font-small); font-weight: var(--weight-medium); }
.loc-dist--lead, .loc-dist--muted { color: var(--text-tertiary); }
.desc-row { display: flex; align-items: flex-start; gap: var(--spacing-sm); margin-top: var(--spacing-md); }
.desc-content-wrap { flex: 1 1 auto; min-width: 0; }
.desc-content { font-size: var(--font-small); color: var(--text-secondary); line-height: 1.5; display: -webkit-box; -webkit-box-orient: vertical; overflow: hidden; word-break: break-all; }
.desc-content--collapsed { -webkit-line-clamp: 2; }
/* dish-detail-visual-polish：展开主色 500 档 */
.desc-toggle { flex: 0 0 auto; align-self: flex-start; font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-medium); padding: 2rpx var(--spacing-xs); line-height: 1.4; -webkit-tap-highlight-color: transparent; }
.metric-panel { display: flex; align-items: stretch; margin-top: var(--spacing-md); padding: var(--spacing-sm) 0; }
.metric-col { flex: 1 1 0; min-width: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--spacing-xs); padding: 0 var(--spacing-sm); position: relative; }
/* dish-detail-visual-polish：分隔竖线极浅化（placeholder 灰），数字 500 档与标签拉开 */
.metric-col + .metric-col::before { content: ''; position: absolute; left: 0; top: 20%; bottom: 20%; width: 2rpx; background: var(--bg-placeholder); }
.metric-val { font-size: var(--font-subtitle); font-weight: var(--weight-medium); color: var(--text-primary); line-height: 1; font-variant-numeric: tabular-nums; display: inline-flex; align-items: baseline; gap: 4rpx; }
.metric-val--text { font-size: var(--font-subtitle); font-weight: var(--weight-medium); color: var(--text-primary); display: inline-block; line-height: 1; max-width: 100%; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.metric-label { font-size: var(--font-aux); color: var(--text-tertiary); line-height: 1; }
/* 纠错入口：与位置行同分隔线语言，浅灰弱引导，不抢主信息 */
.correct-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 2rpx solid var(--border-color);
  -webkit-tap-highlight-color: transparent;
}
.correct-row.pressed { opacity: 0.7; }
.correct-left { display: flex; align-items: center; gap: var(--spacing-xs); min-width: 0; }
.correct-text { font-size: var(--font-small); color: var(--text-tertiary); }
</style>
