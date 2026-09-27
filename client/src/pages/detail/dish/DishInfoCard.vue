<template>
  <CardSection>
    <!-- 信息卡条目顺序（UI 统一 Loop Round 19 起为**五段**）：① 名称 + 价格 → ①b 评分行
         （均分 / 人数 / 迷你分布条；原「综合评分」独立卡已取消）→ ② 位置行（右侧并入「信息有误？」纠错入口，
         **不新增整行**，权威条款见 spec contribution-entry）→ ③ 描述 → ④ 描述四维。
         卡内分隔线统一为一条（段落间 border-top）；仍不含：标签 chips、信息更新时间、窗口号、距离、独立纠错行。 -->

    <!-- ① 名称 + 价格：展示唯一数据源 = price（常显现价）；originalPrice > price 时并列划线原价 -->
    <view class="title-row">
      <text class="dish-name" aria-label="菜品名称">{{ dish.name }}</text>
      <view class="price-row">
        <text class="price-text">¥{{ formatPrice(dish.price) }}</text>
        <text v-if="hasPromo" class="origin-price">¥{{ formatPrice(dish.originalPrice) }}</text>
      </view>
    </view>

    <!-- ①b 评分行（UI 统一 Loop Round 19：原「综合评分」独立卡**取消**，均分 / 人数 / 分布并入信息卡一行）：
         均分 + 人数 + 5 段迷你分布条，三者**同源同刻**（均取实时聚合，一次查询算出）；
         分布条只表达「形状」（各星占比），不逐星列数字，避免再占一块版面。
         零评价（`ratingCount = 0`）→ 轻文案一行「暂无评分」，不占位成块、不误报为 0 分。 -->
    <view
      class="card-block rate-row"
      v-if="ratingCount > 0"
      role="group"
      :aria-label="`评分 ${ratingText} 分，${ratingCount} 人评分`"
    >
      <view class="rate-score">
        <IconSvg name="star-filled" :size="24" :color="COLOR_MAP['star']" class="rate-star" />
        <text class="rate-num">{{ ratingText }}</text>
      </view>
      <text class="rate-count">{{ ratingCount }} 人评</text>
      <view class="rate-bar" role="img" :aria-label="`评分分布：${distAriaLabel}`">
        <view
          v-for="seg in distSegments"
          :key="seg.star"
          class="rate-seg"
          :style="{ flexGrow: seg.count, opacity: seg.opacity }"
        />
      </view>
    </view>
    <text v-else class="card-block rate-empty">暂无评分</text>

    <!-- ② 位置行：食堂 · 楼层 · 档口名（左）+「信息有误？」纠错入口（右，并入同一信息行）。
         纠错采用与「简介展开」同档的轻量文本样式（主色 / 同字号字重），非按钮 / chip / 独立卡片；
         位置文案恒有（`未知位置` 兜底），故该行恒在、入口恒可达。
         行 aria-label 同时覆盖「位置」与「信息有误？」两个语义。 -->
    <view
      class="card-block loc-row"
      role="group"
      :aria-label="`位置：${locationText}；信息有误？可点击前往更新菜品信息`"
    >
      <IconSvg name="location" :size="26" :color="COLOR_MAP['primary']" class="loc-icon" />
      <text class="loc-text">{{ locationText }}</text>
      <!-- 「信息有误？」入口：视觉低调（三级灰小字 + arrow 图标），
           整体一行热区（::after 扩至 ≥88rpx），按压反馈与全站一致（opacity）；落点 = 反馈页 update 模式 -->
      <view
        class="correct-link"
        role="button"
        aria-label="信息有误，点击前往更新菜品信息"
        hover-class="pressed"
        hover-stay-time="80"
        @tap="goCorrect"
      >
        <text class="correct-link-text">信息有误？</text>
        <IconSvg name="arrow" :size="20" :color="COLOR_MAP['text-tertiary']" />
      </view>
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
import type { RatingDistribution } from '@/types/dish'
import { formatPrice } from '@/utils/money'
import { feedbackUrl } from '@/utils/routes'
import { hasDiscount, formatRating } from '@/utils/dish'

const props = defineProps<{
  dish: DishDetail
  locationText: string
  /** 平均评分（实时聚合口径；零评价时为 0）—— 与 `ratingCount` / `distribution` **同源同刻** */
  rating: number
  /** 评价人数（实时聚合；恒 = 分布各星条数之和） */
  ratingCount: number
  /** 评分分布（固定 5 项、按 `star` 降序下发，端上不排序）—— 渲染为「一行迷你分布条」 */
  distribution: RatingDistribution[]
}>()

/** 均分文案（恒一位小数；走公共口径 `utils/dish.formatRating`） */
const ratingText = computed(() => formatRating(props.rating))

/**
 * 迷你分布条分段：按条数比例分宽（`flexGrow = count`），5★→1★ 由深到浅。
 * 深浅只在**透明度**上分档（0.3 ~ 1.0）—— 星色恒为 `--color-star`，不引入任何裸色值。
 */
const distSegments = computed(() =>
  props.distribution.map((item) => ({
    star: item.star,
    count: item.count,
    opacity: [1, 0.8, 0.62, 0.45, 0.3][5 - item.star] ?? 0.3,
  })),
)

/** 分布条无障碍描述（条数逐星读出，纯视觉条本身不携带数字） */
const distAriaLabel = computed(() =>
  props.distribution.map((d) => `${d.star} 星 ${d.count} 条`).join('，'),
)

/** 简介展开/收起（换菜品时复位） */
const descExpanded = ref(false)
watch(() => props.dish.name, () => { descExpanded.value = false })

/** 划线原价显隐：判据统一走 `utils/dish.hasDiscount`（UI 统一 Loop Round 17，与 find 结果同口径） */
const hasPromo = computed(() => hasDiscount(props.dish.price, props.dish.originalPrice))

/**
 * 描述四维（逐维渲染，缺项不占位）：荤素 / 主料 / 口味 / 冷热。
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

/** 纠错入口 → 反馈页「更新信息」模式并预选本菜品（跳过搜索，进页即拉详情预填；落点唯一构造函数） */
function goCorrect() {
  uni.navigateTo({
    url: feedbackUrl('update', props.dish.id),
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

/* ①b 评分行（信息卡内一行，替代原「综合评分」独立卡）：
   均分（主色数字）+ 人数（三级灰）+ 迷你分布条（占满剩余宽度，按条数比例分段） */
.rate-row { display: flex; align-items: center; gap: var(--spacing-xs); }
.rate-score { flex: 0 0 auto; display: flex; align-items: center; gap: var(--spacing-3xs); }
.rate-star { width: 24rpx; height: 24rpx; line-height: 1; }
.rate-num { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); line-height: 1; font-variant-numeric: tabular-nums; }
.rate-count { flex: 0 0 auto; font-size: var(--font-aux); color: var(--text-tertiary); }
/* 迷你分布条：底槽 = 星级空槽色，5 段以 flexGrow（= 条数）比例分宽；段间 2rpx 细缝（--spacing-3xs） */
.rate-bar {
  flex: 1 1 auto;
  min-width: 0;
  height: 12rpx;
  display: flex;
  gap: var(--spacing-3xs);
  border-radius: var(--radius-pill);
  background: var(--color-star-empty);
  overflow: hidden;
}
.rate-seg { flex-grow: 0; flex-basis: 0; background: var(--color-star); }
/* 零评价：轻文案一行（不占位成块、不误报 0 分） */
.rate-empty { display: block; font-size: var(--font-aux); color: var(--text-tertiary); }

/* 统一分隔线语言：段落之间仅用 border-top（一条语义），不再混用竖线 / border-bottom */
.card-block { margin-top: var(--spacing-md); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }

/* ② 位置行（右侧并入纠错入口） */
.loc-row { display: flex; align-items: center; gap: var(--spacing-xs); }
.loc-icon { width: 26rpx; height: 26rpx; line-height: 1; flex-shrink: 0; }
.loc-text { flex: 1 1 auto; min-width: 0; font-size: var(--font-small); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 纠错入口：视觉低调 = 三级灰小字 + arrow 图标（非主色、非按钮 / chip），
   横排 icon+文字居中；命中区 ::after 扩至 ≥88rpx（Apple 44pt 触达下限模式），按压 opacity 与全站一致 */
.correct-link { position: relative; flex: 0 0 auto; margin-left: var(--spacing-sm); display: flex; align-items: center; gap: var(--spacing-3xs); padding: var(--spacing-3xs) var(--spacing-xs); -webkit-tap-highlight-color: transparent; }
.correct-link-text { font-size: var(--font-aux); color: var(--text-tertiary); font-weight: var(--weight-medium); line-height: 1.4; }
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
.desc-toggle { position: relative; flex: 0 0 auto; align-self: flex-start; font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-medium); padding: var(--spacing-3xs) var(--spacing-xs); line-height: 1.4; -webkit-tap-highlight-color: transparent; }
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
