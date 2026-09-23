<template>
  <CardSection>
    <!-- 信息卡条目顺序（spec contribution-entry 权威）：① 名称 + 价格 → ② 位置行（右侧并入「信息有误？」
         纠错入口，**不新增整行**）→ ③ 描述 → ④ 描述四维。卡内分隔线语言统一为一条（段落间 border-top）。
         已删除：标签 chips、信息更新时间、评分 / 评价数、窗口号、距离、独立纠错行（2026-09-20 dish-detail-remediation）。 -->

    <!-- ① 名称 + 价格：展示唯一数据源 = price（常显现价）；originalPrice > price 时并列划线原价 -->
    <view class="title-row">
      <text class="dish-name" aria-label="菜品名称">{{ dish.name }}</text>
      <view class="price-row">
        <text class="price-text">¥{{ formatPrice(dish.price) }}</text>
        <text v-if="hasPromo" class="origin-price">¥{{ formatPrice(dish.originalPrice) }}</text>
      </view>
    </view>

    <!-- ② 位置行：食堂 · 楼层 · 档口名（左）+「信息有误？」纠错入口（右，并入同一信息行）。
         纠错采用与「简介展开」同档的轻量文本样式（主色 / 同字号字重），非按钮 / chip / 独立卡片；
         位置文案恒有（`未知位置` 兜底），故该行恒在、入口恒可达。
         行 aria-label 同时覆盖「位置」与「信息有误？」两个语义。 -->
    <view
      class="card-block loc-row"
      role="group"
      :aria-label="`位置：${locationText}；信息有误？可点击前往反馈纠错`"
    >
      <IconSvg name="location" :size="26" :color="COLOR_MAP['primary']" class="loc-icon" />
      <text class="loc-text">{{ locationText }}</text>
      <text
        class="correct-link"
        role="button"
        aria-label="信息有误，点击前往反馈纠错"
        hover-class="pressed"
        @tap="goCorrect"
      >信息有误？</text>
    </view>

    <!-- ③ 描述（默认两行 + 展开 / 收起） -->
    <view class="card-block desc-row" v-if="dish.description">
      <text class="desc-content" :class="{ 'desc-content--collapsed': !descExpanded }">{{ dish.description }}</text>
      <text
        class="desc-toggle"
        role="button"
        aria-label="展开或收起简介"
        :aria-expanded="descExpanded ? 'true' : 'false'"
        @tap="descExpanded = !descExpanded"
      >{{ descExpanded ? '收起' : '展开' }}</text>
    </view>

    <!-- ④ 描述四维（荤素 / 主料 / 口味 / 冷热）：逐维渲染，缺项不占位，不用 `-` 凑列 -->
    <view class="card-block dims" v-if="dims.length > 0">
      <view class="dim-col" v-for="d in dims" :key="d.label">
        <text class="dim-val">{{ d.value }}</text>
        <text class="dim-label">{{ d.label }}</text>
      </view>
    </view>
  </CardSection>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { DishDetail } from '@/types/dish'
import CardSection from '@/components/CardSection.vue'
import IconSvg from '@/components/IconSvg.vue'
// 图标色须传**实色**（IconSvg 的 color 不解析 var()，data-uri 内为字面量，传 var(...) 恒落近黑）
import { COLOR_MAP } from '@/theme/tokens'
import { useDishAttributeStore } from '@/stores/dish-attribute'
import { formatPrice } from '@/utils/money'
import { feedbackEntryUrl } from '@/utils/routes'

const props = defineProps<{
  dish: DishDetail
  locationText: string
}>()

/** 简介展开/收起（换菜品时复位） */
const descExpanded = ref(false)
watch(() => props.dish.name, () => { descExpanded.value = false })

/** 「有折扣」唯一判据：originalPrice 有值且大于 price（不引入第三个价格字段） */
const hasPromo = computed(() => {
  const op = props.dish.originalPrice
  return op != null && op > props.dish.price
})

/**
 * 描述四维（逐维渲染，缺项不占位）：荤素 / 主料 / 口味 / 冷热。
 *
 * 菜品出参下发**机器值**，中文一律由**四维字典**翻译（2026-09-23 §7.40 R4）——
 * 端上零硬编码映射表；字典未就绪 / 未命中时该维**不渲染**（沿用「缺项不占位」口径）。
 */
const dishAttr = useDishAttributeStore()
onMounted(() => {
  dishAttr.ensureLoaded()
})

const dims = computed(() => {
  const d = props.dish
  const list: { label: string; value: string }[] = []
  const diet = dishAttr.labelOf('dietType', d.dietType)
  const ing = dishAttr.labelsOf('ingredients', d.ingredients)
  const fla = dishAttr.labelsOf('flavorTags', d.flavorTags)
  const serve = dishAttr.labelOf('serveTemp', d.serveTemp)
  if (diet) list.push({ label: '荤素', value: diet })
  if (ing.length) list.push({ label: '主料', value: ing.join('、') })
  if (fla.length) list.push({ label: '口味', value: fla.join('、') })
  if (serve) list.push({ label: '冷热', value: serve })
  return list
})

/** 纠错入口 → 反馈页预选「信息不对」并关联本菜品（落点唯一构造函数，from=dish，见 contribution-entry） */
function goCorrect() {
  uni.navigateTo({
    url: feedbackEntryUrl({ type: 'error', from: 'dish', dishId: props.dish.id, dishName: props.dish.name }),
  })
}
</script>

<style scoped>
/* ① 名称 + 价格（同一行、基线对齐） */
.title-row { display: flex; align-items: baseline; gap: var(--spacing-sm); }
/* 菜名 / 价格字重按 content-flow-visual delta 统一 600 档（--weight-semibold，与卡片菜名同档） */
.dish-name { font-size: var(--font-title); font-weight: var(--weight-semibold); letter-spacing: var(--tracking-h2); line-height: 1.2; color: var(--text-primary); flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.price-row { display: flex; align-items: baseline; gap: var(--spacing-xs); flex: 0 0 auto; }
.price-text { font-size: var(--font-h2); font-weight: var(--weight-semibold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.origin-price { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }

/* 统一分隔线语言：段落之间仅用 border-top（一条语义），不再混用竖线 / border-bottom */
.card-block { margin-top: var(--spacing-md); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }

/* ② 位置行（右侧并入纠错入口） */
.loc-row { display: flex; align-items: center; gap: var(--spacing-xs); }
.loc-icon { width: 26rpx; height: 26rpx; line-height: 1; flex-shrink: 0; }
.loc-text { flex: 1 1 auto; min-width: 0; font-size: var(--font-small); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 纠错入口：与「简介展开」同档轻量文本（主色 / 同字号字重），非按钮 / chip；命中区 ::after 扩至 ≥88rpx */
.correct-link { position: relative; flex: 0 0 auto; margin-left: var(--spacing-sm); font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-medium); line-height: 1.4; padding: 2rpx var(--spacing-xs); -webkit-tap-highlight-color: transparent; }
.correct-link::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 88rpx;
  height: 88rpx;
  transform: translate(-50%, -50%);
}
.correct-link.pressed { opacity: 0.7; }

/* ③ 描述（默认两行 + 展开 / 收起；展开钮命中区经 ::after 扩至 ≥88rpx） */
.desc-row { display: flex; align-items: flex-start; gap: var(--spacing-sm); }
.desc-content { flex: 1 1 auto; min-width: 0; font-size: var(--font-small); color: var(--text-secondary); line-height: 1.5; display: -webkit-box; -webkit-box-orient: vertical; overflow: hidden; word-break: break-all; }
.desc-content--collapsed { -webkit-line-clamp: 2; }
.desc-toggle { position: relative; flex: 0 0 auto; align-self: flex-start; font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-medium); padding: 2rpx var(--spacing-xs); line-height: 1.4; -webkit-tap-highlight-color: transparent; }
.desc-toggle::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 88rpx;
  height: 88rpx;
  transform: translate(-50%, -50%);
}

/* ④ 四维：逐维渲染（缺项不渲染该列），水平等分、单行不折行、无竖线分隔 */
.dims { display: flex; align-items: stretch; }
/* 标签贴底对齐（justify-content: flex-end）：长值换行时各列标签仍在同一基线上 */
.dim-col { flex: 1 1 0; min-width: 0; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; gap: var(--spacing-xs); padding: 0 var(--spacing-sm); }
/* 数字 500 档（--weight-medium）、标签三级浅灰；长值不硬截断——最多两行自动换行收起 */
.dim-val { font-size: var(--font-subtitle); font-weight: var(--weight-medium); color: var(--text-primary); line-height: 1.2; text-align: center; max-width: 100%; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; white-space: normal; word-break: break-word; }
.dim-label { font-size: var(--font-aux); color: var(--text-tertiary); line-height: 1; }
</style>
