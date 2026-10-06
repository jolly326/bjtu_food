<template>
  <CardSection>
    <!-- 菜品信息卡：
         ① **名称行**（菜名 ≤2 行 + **「菜品有问题?」入口** + 价格组）→ ② 评分（左）/ 位置（右）同行
         → ③ 简介（**固定 ≤2 行截断、无展开**；`description` 为空则整块隐藏）
         → ④ **全卡唯一一条浅灰分隔线**（仅渲染在简介与属性容器之间，简介隐藏时一并消失）
         → ⑤ 描述四维（无底色 / 无边框 / 四列等分居中）。
         · 模块之间**靠垂直留白区分**（`--spacing-md`），除第 ④ 条外**不加任何分隔线**；
         · 卡内唯一可点件 = **「菜品有问题?」**（文字 + 小图标，**禁实色填充按钮**）；
         · 卡片内不做分享按钮（复用微信原生右上角分享）；
         · **「菜品有问题?」= 全页唯一问题反馈入口**（位于本卡名称行、价格左侧）；
         · **不绘制评分进度条、不展示评价人数**（「有无评分」的唯一判据 = `avgRating` 是否为 `null`）。 -->
    <view class="dish-info">
      <!-- ① 名称行（flex 横向、垂直居中）：菜名（**≤2 行截断**）→「**菜品有问题?**」入口（价格左侧、视觉权重压低、图标与文字同色 `--text-tertiary`）→ 价格组
           价格唯一数据源 = price（现价）；仅 originalPrice > price 时并列划线原价 -->
      <view class="title-row">
        <text class="dish-name" aria-label="菜品名称">{{ dish.name }}</text>
        <view
          class="correct-entry"
          role="button"
          aria-label="菜品有问题，前往提交反馈"
          hover-class="correct-entry--pressed"
          hover-stay-time="80"
          @tap="emit('correct')"
        >
          <IconSvg name="alert" :size="24" :color="COLOR_MAP['text-tertiary']" class="correct-icon" />
          <text class="correct-text">菜品有问题?</text>
        </view>
        <view class="price-group">
          <text class="price-text">¥{{ formatPrice(dish.price) }}</text>
          <text v-if="hasPromo" class="origin-price">¥{{ formatPrice(dish.originalPrice) }}</text>
        </view>
      </view>

      <!-- ② 评分（左）+ 位置（右）同行：🔴 **恒渲染**黄星 + 均分 ——
    出参 `avgRating` 零评价已兜底为 `5.0`（见 docs/api/client/dishes.md「零评价展示口径」），
       故端上不再有「有无评分」分支；只显示均分、**不显示评价条数**（保持简洁）。 -->
      <view class="meta-row">
        <view class="rating-group" role="img" :aria-label="`评分 ${ratingText} 分`">
          <!-- 星色须传**实色**：IconSvg 的 color 不解析 var()（data-uri 内为字面量），传 var(...) 恒落近黑 -->
          <IconSvg name="star-filled" :size="30" :color="COLOR_MAP['star']" class="rating-star" />
          <text class="rating-num">{{ ratingText }}</text>
        </view>
        <view class="loc-group" role="group" :aria-label="`位置：${locationText}`">
          <IconSvg name="location" :size="26" :color="COLOR_MAP['primary']" class="loc-icon" />
          <text class="loc-text">{{ locationText }}</text>
        </view>
      </view>

      <!-- ③ 简介：**固定最多 2 行截断、超出省略**，文本独占卡片整宽（不存在文字与按钮的排版冲突）；
           `description` 为空则整块不渲染（不占页面空间） -->
      <view v-if="dish.description" class="desc-row">
        <text class="desc-content">{{ dish.description }}</text>
      </view>

      <!-- ④ 全卡**唯一**一条浅灰分隔线：只在简介存在时渲染（简介隐藏 → 这条线一并消失） -->
      <view v-if="dish.description" class="divider" />

      <!-- ⑤ 描述属性（`dish.attributes`）：**值即中文 ⇒ 端上直渲 `value`** ——
           无底色 / 无边框 / 无入口，各列水平等分居中；上：中文值（主字号），下：维度名（浅灰小字）；
           按后端返回顺序逐维渲染、缺项不占位，多值维已用「、」拼接。 -->
      <view v-if="dims.length > 0" class="dims">
        <view class="dim-col" v-for="d in dims" :key="d.name">
          <text class="dim-val">{{ d.value }}</text>
          <text class="dim-label">{{ d.name }}</text>
        </view>
      </view>
    </view>
  </CardSection>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DishDetail } from '@/types/dish'
import CardSection from '@/components/CardSection.vue'
import IconSvg from '@/components/IconSvg.vue'
// 图标色须传**实色**（IconSvg 的 color 不解析 var()，data-uri 内为字面量，传 var(...) 恒落近黑）
import { COLOR_MAP } from '@/theme/tokens'
import { formatPrice } from '@/utils/money'
import { hasDiscount, formatRating } from '@/utils/dish'

const props = defineProps<{
  dish: DishDetail
  /** 位置文案（页面派生：「食堂 · 楼层 · 档口」，缺项兜底「未知位置」） */
  locationText: string
  /**
   * 平均评分（`DishDetailVO.avgRating`，口径 = 仅未隐藏评价）。
   * 🔴 **零评价时服务端已兜底为 `5.0`**（不返回 null）⇒ 端上恒渲染评分位，
   * 无「有无评分」分支；只显示均分、不显示评价条数（保持简洁）。
   */
  rating: number
}>()

/** 均分文案（恒一位小数；走公共口径 `utils/dish.formatRating`） */
const ratingText = computed(() => formatRating(props.rating))

const emit = defineEmits<{
  /** 「菜品有问题?」入口：页面侧跳独立反馈页（`correctionUrl(dishId)`，免认证） */
  (e: 'correct'): void
}>()

/** 划线原价显隐：判据统一走 `utils/dish.hasDiscount` */
const hasPromo = computed(() => hasDiscount(props.dish.price, props.dish.originalPrice))

/**
 * 描述属性列（按后端返回顺序逐维渲染，缺项不占位）。
 *
 * **R4（值即中文、端上零翻译）**：`DishDetailVO.attributes[].value` 就是中文文本，
 * 端上**直接渲染、不拉字典**。
 * `value`：`single` 维为字符串、`multi` 维为字符串数组（多值以「、」拼接）。
 */
const dims = computed(() => {
  const list: { name: string; value: string }[] = []
  for (const item of props.dish.attributes || []) {
    const text = Array.isArray(item.value) ? item.value.filter(Boolean).join('、') : String(item.value || '')
    if (!text) continue
    list.push({ name: item.name, value: text })
  }
  return list
})

/* 注：「菜品有问题?」点击上抛 `correct`，由页面编排
   `useDishPage.onCorrectDishInfo` 跳独立反馈页（`correctionUrl(dishId)`，免认证、游客可直达）。 */
</script>

<style scoped>
/* ===== 模块垂直节奏（唯一来源）：除简介下方那条分隔线外，全部用留白区分 —— 不加任何额外分割线 ===== */

/* ① 名称行（flex 横向、**垂直居中**）：菜名（≤2 行截断）+「菜品有问题?」入口（价格左侧）+ 价格组 */
.title-row { display: flex; align-items: center; gap: var(--spacing-sm); }
.dish-name {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-title);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-h2);
  line-height: 1.2;
  color: var(--text-primary);
  /* 最长 2 行、超出省略；不与右侧入口 / 价格抢占空间 */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  /* 断行口径（全站唯一写法）：超长无空格串（长英文 / URL）在 2 行折叠内换行，
     不落到「无省略号硬裁」；`anywhere` 同时参与 min-content 计算，窄容器下也不会撑破 */
  overflow-wrap: anywhere;
}
/* 「菜品有问题?」入口：文字 + 线性小图标、**视觉权重压低**（图标与文字同色 `--text-tertiary`），
   位于价格左侧；命中区经 ::after **仅纵向**扩至 ≥88rpx（a11y 44pt 下限）。
   按压反馈 = opacity 微降（**禁 `transform: scale`** —— 全站红线）。 */
.correct-entry {
  position: relative;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-3xs);
  -webkit-tap-highlight-color: transparent;
}
.correct-entry::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: var(--tap-target-size);
  transform: translateY(-50%);
}
.correct-icon { flex: none; width: 24rpx; height: 24rpx; display: flex; align-items: center; justify-content: center; }
.correct-text { font-size: var(--font-aux); color: var(--text-tertiary); line-height: 1.2; white-space: nowrap; }
.correct-entry--pressed { opacity: 0.6; }
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

/* ③ 简介：**固定最多 2 行截断、超出省略** */
.desc-row { display: flex; margin-top: var(--spacing-md); }
.desc-content {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--font-small);
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  /* 断行口径同上（`--font-*` 长简介里的长英文 / 长串不硬裁） */
  overflow-wrap: anywhere;
}

/* ④ 全卡唯一分隔线：简介 ↔ 属性容器之间。
   下侧不再留白：分隔线 → 属性块的 --spacing-md 间距改由 `.dims` 自持，
   这样「简介缺失、分隔线同步消失」时同样的间距仍然成立（不会少掉一段留白）。 */
.divider { border-top: 1rpx solid var(--border-color); margin: var(--spacing-sm) 0 0; }

/* ⑤ 描述四维：水平等分、逐维渲染（缺项不渲染该列）、
   无竖线分隔；**无底色 / 无边框 / 无内边距**（采用无容器方案）。
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
  /* 断行口径同上：属性值可能是一长串无空格文本（与名称 / 简介统一） */
  overflow-wrap: anywhere;
}
.dim-label { font-size: var(--font-aux); color: var(--text-tertiary); line-height: 1; }
</style>
