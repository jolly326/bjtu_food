<template>
  <CardSection>
    <!-- 菜品信息卡（UI 统一 Loop Round 22 重排 + Round 23 属性块还原）：
         ① 名称 + 价格组 → ② 评分（左）/ 位置（右）同行 → ③ 简介（`description` 为空则整块隐藏）
         → ④ **全卡唯一一条浅灰分隔线**（仅渲染在简介与属性容器之间，简介隐藏时一并消失）
         → ⑤ 描述四维（**Round 23 还原 Round 22 之前的样式**：无底色 / 无边框 / 四列等分居中）。
         · 模块之间**靠垂直留白区分**（`--spacing-md`），除第 ④ 条外**不加任何分隔线**；
         · 交互文字（本卡仅「展开 / 收起」）只用文字配色，**禁止实色填充按钮**；
         · 卡片内不做分享按钮（复用微信原生右上角分享）；
         · **纠错入口已从本卡移除**（R23 移出本卡 → R25 定为**底栏「反馈错误」**，全页入口唯一）；
         · **不绘制评分进度条、不展示评价人数**（`ratingCount` 仍参与「有无评分」判定，仅不渲染）。 -->
    <view class="dish-info">
      <!-- ① 名称 + 价格组：价格唯一数据源 = price（现价）；仅 originalPrice > price 时并列划线原价 -->
      <view class="title-row">
        <text class="dish-name" aria-label="菜品名称">{{ dish.name }}</text>
        <view class="price-group">
          <text class="price-text">¥{{ formatPrice(dish.price) }}</text>
          <text v-if="hasPromo" class="origin-price">¥{{ formatPrice(dish.originalPrice) }}</text>
        </view>
      </view>

      <!-- ② 评分（左）+ 位置（右）同行：有评分 → 黄星 + 均分；零评价 → 隐藏星、浅灰「暂无评分」 -->
      <view class="meta-row">
        <view v-if="ratingCount > 0" class="rating-group" role="img" :aria-label="`评分 ${ratingText} 分`">
          <!-- 星色须传**实色**：IconSvg 的 color 不解析 var()（data-uri 内为字面量），传 var(...) 恒落近黑 -->
          <IconSvg name="star-filled" :size="30" :color="COLOR_MAP['star']" class="rating-star" />
          <text class="rating-num">{{ ratingText }}</text>
        </view>
        <text v-else class="rating-empty">暂无评分</text>
        <view class="loc-group" role="group" :aria-label="`位置：${locationText}`">
          <IconSvg name="location" :size="26" :color="COLOR_MAP['primary']" class="loc-icon" />
          <text class="loc-text">{{ locationText }}</text>
        </view>
      </view>

      <!-- ③ 简介：默认两行 + 右下角「展开 / 收起」；`description` 为空则整块不渲染（不占页面空间） -->
      <view v-if="dish.description" class="desc-row">
        <text class="desc-content" :class="{ 'desc-content--collapsed': !descExpanded }">{{ dish.description }}</text>
        <text
          class="desc-toggle"
          role="button"
          aria-label="展开或收起简介"
          :aria-expanded="descExpanded ? 'true' : 'false'"
          @tap="descExpanded = !descExpanded"
        >{{ descExpanded ? '收起' : '展开' }}</text>
      </view>

      <!-- ④ 全卡**唯一**一条浅灰分隔线：只在简介存在时渲染（简介隐藏 → 这条线一并消失） -->
      <view v-if="dish.description" class="divider" />

      <!-- ⑤ 描述四维（荤素 / 主料 / 口味 / 冷热）：**还原 Round 22 之前的样式** ——
           无底色 / 无边框 / 无入口，四列水平等分居中；上：字段值（主字号），下：固定标签（浅灰小字）；
           逐维渲染、缺项不占位，多值已用「、」拼接。
           纠错入口（原本卡右上角）已移出 —— 全页唯一落点为**底栏「反馈错误」**（Round 25）。 -->
      <view v-if="dims.length > 0" class="dims">
        <view class="dim-col" v-for="d in dims" :key="d.label">
          <text class="dim-val">{{ d.value }}</text>
          <text class="dim-label">{{ d.label }}</text>
        </view>
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
import { hasDiscount, formatRating } from '@/utils/dish'

const props = defineProps<{
  dish: DishDetail
  /** 位置文案（页面派生：「食堂 · 楼层 · 档口」，缺项兜底「未知位置」） */
  locationText: string
  /** 平均评分（实时聚合；零评价时为 0）—— 与 `ratingCount` 同源同刻 */
  rating: number
  /** 评价人数：**仅用于判定「有无评分」**，按规格不渲染（Round 22） */
  ratingCount: number
}>()

/** 均分文案（恒一位小数；走公共口径 `utils/dish.formatRating`） */
const ratingText = computed(() => formatRating(props.rating))

/** 简介展开/收起（换菜品时复位） */
const descExpanded = ref(false)
watch(() => props.dish.name, () => { descExpanded.value = false })

/** 划线原价显隐：判据统一走 `utils/dish.hasDiscount`（UI 统一 Loop Round 17，与 find 结果同口径） */
const hasPromo = computed(() => hasDiscount(props.dish.price, props.dish.originalPrice))

/**
 * 属性四维（逐维渲染，缺项不占位）：荤素 / 主料 / 口味 / 冷热。
 *
 * 菜品出参下发**机器值**，中文一律由**四维字典**翻译（§7.40 R4）——
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

/* 注：纠错入口已从本卡移除（R23 移出本卡，R25 定为底栏「反馈错误」）—— 连同其页面级跳转
   （反馈页 update 模式 + 预选本菜品）一并移到页面编排 `useDishPage.onCorrectDishInfo`。 */
</script>

<style scoped>
/* ===== 模块垂直节奏（唯一来源）：除简介下方那条分隔线外，全部用留白区分 —— 不加任何额外分割线 ===== */

/* ① 名称 + 价格组（同一行、基线对齐）：菜名是卡片内最大字号 */
.title-row { display: flex; align-items: baseline; gap: var(--spacing-sm); }
.dish-name {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-title);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-h2);
  line-height: 1.2;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.price-group { flex: 0 0 auto; display: flex; align-items: baseline; gap: var(--spacing-xs); }
.price-text { font-size: var(--font-h2); font-weight: var(--weight-semibold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.origin-price { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }

/* ② 评分（左）/ 位置（右）同行：左评分靠左、右位置靠右，单行不折 */
.meta-row { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); margin-top: var(--spacing-md); }
.rating-group { flex: none; display: inline-flex; align-items: center; gap: var(--spacing-2xs); }
/* 星图标宿主（<icon-svg> 自定义组件）显式定为 30rpx 方形 flex 盒 ⇒ 与数字精确同行居中
   （同 Round 20b / 21d 的方案；否则星会随继承字体度量上下错位） */
.rating-star {
  flex: none;
  width: 30rpx;
  height: 30rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rating-num { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); font-variant-numeric: tabular-nums; }
/* 零评价：隐藏星，仅浅灰文案（不误报 0 分） */
.rating-empty { flex: none; font-size: var(--font-small); color: var(--text-placeholder); }

/* 右侧位置：定位图标 + 「食堂 · 楼层 · 档口」，超长省略 */
.loc-group { flex: 1 1 auto; min-width: 0; display: flex; align-items: center; justify-content: flex-end; gap: var(--spacing-xs); }
.loc-icon {
  flex: none;
  width: 26rpx;
  height: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.loc-text { min-width: 0; font-size: var(--font-small); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ③ 简介：默认两行 + 右下角「展开 / 收起」（纯文字，无填充） */
.desc-row { display: flex; align-items: flex-end; gap: var(--spacing-sm); margin-top: var(--spacing-md); }
.desc-content {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-small);
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}
.desc-content--collapsed { -webkit-line-clamp: 2; line-clamp: 2; }
.desc-toggle {
  position: relative;
  flex: 0 0 auto;
  align-self: flex-end;
  font-size: var(--font-aux);
  color: var(--color-primary);
  font-weight: var(--weight-medium);
  padding: var(--spacing-3xs) var(--spacing-xs);
  line-height: 1.4;
  -webkit-tap-highlight-color: transparent;
}
/* 触达：展开钮命中区经 ::after 扩至 ≥88rpx（不改变视觉尺寸） */
.desc-toggle::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 88rpx;
  height: 88rpx;
  transform: translate(-50%, -50%);
}

/* ④ 全卡唯一分隔线：简介 ↔ 属性容器之间。
   下侧不再留白（Round 23）：分隔线 → 属性块的 --spacing-md 间距改由 `.dims` 自持，
   这样「简介缺失、分隔线同步消失」时同样的间距仍然成立（不会少掉一段留白）。 */
.divider { height: 2rpx; background: var(--border-color); margin: var(--spacing-sm) 0 0; }

/* ⑤ 描述四维（**Round 23 还原 Round 22 之前的样式**）：水平等分、逐维渲染（缺项不渲染该列）、
   无竖线分隔；**无底色 / 无边框 / 无内边距**（「浅米色标签容器」方案已退役）。
   顶部留白由本块自持（`--spacing-md`）⇒ 简介缺失（分隔线同步消失）时仍有正确间距。 */
.dims { display: flex; align-items: stretch; margin-top: var(--spacing-md); }
/* 标签贴底对齐（justify-content: flex-end）：长值换行时各列标签仍在同一基线上 */
.dim-col {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-xs);
  padding: 0 var(--spacing-sm);
}
/* 上一行：字段值（主字号 500 档，多值已用「、」拼接）；下一行：三级浅灰固定标签
   长值不硬截断 —— 最多两行自动换行收起 */
.dim-val {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-medium);
  color: var(--text-primary);
  line-height: 1.2;
  text-align: center;
  max-width: 100%;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  white-space: normal;
  word-break: break-word;
}
.dim-label { font-size: var(--font-aux); color: var(--text-tertiary); line-height: 1; }
</style>
