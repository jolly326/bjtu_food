<template>
  <!-- 搜索结果菜品条目（页内私有；UI 统一 Loop Round 21 按用户规格设计，Round 21b 由 DishResultRow 更名）：
        横向 flex = 左「正方形图片」+ 右「纵向三行信息」，整条可点跳菜品详情。
        · 极简无卡片：**每条结果各自一块 `.module-wrap`**（暖奶米半透 + 圆角 + 极淡暖棕阴影），
          只包裹这一条菜品信息；条目之间靠列表容器（find/index 的 `.mixed-list`）的 flex gap 留白分组。
        · 左图：固定 160rpx 正方形，aspectFill 铺满（即容器内居中），无图 → 统一占位（`ImagePlaceholder`）；
       · 右信息：flex:1 且 align-self:stretch（上下边界与左图对齐），三行**垂直居中**（justify-content:center），
         行距 --spacing-sm ——
         ① 菜名（两行省略、命中**加粗**）；
         ② 档口位置（小字号、弱灰、单行省略、命中加粗）；
         ③ 底行：左侧评分（★ + 数字，与价格**同字号**）—— 右侧价格（主色带 ¥，仅 originalPrice > price 追加灰色删除线原价）；
       · `id` 仅用于 key / 跳转，零渲染；**不渲染**标签、描述、评价数等详情页字段；
       · 命中片段只**加粗**不上主色（主色是价格专用强调色 —— UI 文档 §1 第 5 条）。
       ⚠️ 卡间纵向间距由列表容器（find/index 的 .mixed-list）用 flex gap 承担 —— 本组件不用 `+` 兄弟选择器
       （mp-weixin WXSS 不保证支持，见 find/index 样式区登记）。 -->
  <view
    class="dish-result-card module-wrap"
    role="button"
    :aria-label="`查看 ${item.name}`"
    hover-class="card-pressed"
    hover-stay-time="80"
    @tap="onTap"
  >
    <view class="thumb">
      <!-- 破图兜底（与首页 `DishCard` 同源同写法）：`@error` 置 `imgOk = false` → 落 `v-else`
           的统一餐具占位，不留灰底裂图 -->
      <image
        v-if="item.image && imgOk"
        :src="thumbSrc(item.image)"
        mode="aspectFill"
        class="thumb-img"
        :class="{ loaded }"
        lazy-load
        @load="loaded = true"
        @error="imgOk = false"
      />
      <view v-else class="thumb-ph">
        <ImagePlaceholder :size="48" />
      </view>
    </view>

    <view class="info">
      <!-- ① 菜名（两行省略，命中加粗） -->
      <text class="name">
        <text
          v-for="(seg, si) in nameSegs"
          :key="si"
          :class="{ hit: seg.hit }"
        >{{ seg.text }}</text>
      </text>

      <!-- ② 位置：食堂 · 档口（joinLocation 单点拼接），小字号弱灰、单行省略、命中加粗 -->
      <view class="loc-row">
        <text class="sub-text">
          <text
            v-for="(seg, si) in subSegs"
            :key="si"
            :class="{ hit: seg.hit }"
          >{{ seg.text }}</text>
        </text>
      </view>

      <!-- ③ 底行：左 = 评分（★ + 数字，与价格同字号）；右 = 价格（主色带 ¥，仅 originalPrice > price 追加划线原价）。
           🔴 零评价时服务端已兜底 `5.0` ⇒ 评分组**恒渲染**，不再有「无评分不渲染」分支。 -->
      <view class="meta-row">
        <view class="rating-group">
          <!-- 星色 = 独立语义色（黄），不随主色换肤；必须传实色（data-uri 不解析 var()） -->
          <AppIcon name="star-filled" :size="36" :color="COLOR_MAP['star']" class="rating-star" />
          <text class="rating-num">{{ formatRating(item.rating) }}</text>
        </view>
        <view v-if="item.price != null" class="price-group">
          <text class="price"><text class="price-sym">¥</text>{{ formatPrice(item.price) }}</text>
          <text v-if="hasDiscount(item.price, item.originalPrice)" class="original">¥{{ formatPrice(item.originalPrice) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
// 星色须传**实色**：AppIcon 的 color 不解析 var()（data-uri 内为字面量），传 var(...) 恒落近黑
import { COLOR_MAP } from '@/theme/tokens'
import { formatPrice } from '@/utils/money'
import { getThumbImageUrl as thumbSrc } from '@/utils/image'
import { hasDiscount, formatRating } from '@/utils/dish'
import type { MixedResultItem } from '@/types/dish'

const props = defineProps<{
  /** 单条结果（image = coverImage；sub = 食堂 · 档口；id 仅用于跳转） */
  item: MixedResultItem
  /** 搜索关键词：命中片段**加粗**（不带上色，主色留给价格） */
  keyword?: string
}>()

const emit = defineEmits<{
  /** 整卡点击 → 宿主页跳转菜品详情（载荷 = 菜品 id；id 缺失时不派发） */
  (e: 'select', id: number): void
}>()

/** 图片加载完成 → 淡入（每行自持；行以 item 为 key 稳定复用，无需跨行去重集合） */
const loaded = ref(false)

/** 图片可用性：加载失败（`@error`）即置 false → 回退统一占位，禁止裂图 */
const imgOk = ref(true)

/** 关键词拆段：命中片段仅**加粗**不上主色（主色是价格专用强调色 —— UI 文档 §1 第 5 条） */
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

/** 菜名 / 位置行的命中拆段结果：用 `computed` 缓存，仅在 `item` / `keyword` 变化时重算
    （行以 item 为 key 稳定复用）—— 避免每次渲染重跑拆段与 `toLowerCase`。 */
const nameSegs = computed(() => splitHighlight(props.item.name))
const subSegs = computed(() => splitHighlight(props.item.sub || ''))

/** 整卡点击：id 缺失（脏数据）时不派发，避免宿主跳进「菜品不存在」 */
function onTap() {
  if (props.item.id != null) emit('select', props.item.id)
}
</script>

<style scoped>
/* 极简无卡片：底色 / 圆角 / 阴影 / 内距全部由全局 `.module-wrap` 承担，本类只负责内部排版 */
.dish-result-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}
/* 整条按压反馈：底色加深一档（bg-soft），与全站按压语言一致 */
.dish-result-card.card-pressed { background: var(--bg-soft); }
/* 注：卡间纵向间距由列表容器（find/index .mixed-list）的 flex gap 承担 ——
   本组件不用 `+` 兄弟选择器（mp-weixin WXSS 不保证支持，见 find/index 样式区登记）。 */

/* 左：固定正方形图片容器（aspectFill 铺满即容器内居中；无图 → 统一占位 `ImagePlaceholder`）。
   底色与占位同源（`--bg-placeholder`，Round 31 统一）—— 图片未就绪的一瞬也是同一灰底，不闪白。 */
.thumb {
  width: 160rpx;
  height: 160rpx;
  flex-shrink: 0;
  border-radius: var(--radius-icon);
  overflow: hidden;
  background: var(--bg-placeholder);
}
/* 淡入时长走 token（原裸值 0.32s）—— 与 DishCard 缩略图淡入同档（--duration-slow） */
.thumb-img { width: 100%; height: 100%; display: block; opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); }
.thumb-img.loaded { opacity: 1; }
.thumb-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }

/* 右：纵向信息容器 —— flex:1 + align-self:stretch（上下边界与左图对齐）+ 三行垂直居中；行距 --spacing-sm */
.info {
  flex: 1;
  min-width: 0;
  align-self: stretch;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--spacing-sm);
}

/* ① 菜名：两行省略，命中加粗 */
.name {
  min-width: 0;
  font-size: var(--font-title);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  /* 断行口径（全站唯一写法）：两行折叠下超长无空格串（长英文 / 长数字串）也换行，
     不让尾部在折叠处无省略号静默消失 */
  overflow-wrap: anywhere;
}
/* 命中片段：仅字重加深，不上主色（主色是价格专用强调色 —— UI 文档 §1 第 5 条） */
.name .hit, .sub-text .hit { font-weight: var(--weight-heavy); }

/* ② 位置行：小字号弱灰、单行省略 */
.loc-row { display: flex; justify-content: flex-start; min-width: 0; }
.sub-text { flex: 1; min-width: 0; font-size: var(--font-aux); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ③ 底行：左评分 / 右价格 —— 两者**同字号**（--font-h3 36rpx，星画布 36rpx）；
   评分组不渲染时价格仍靠右（price-group margin-left:auto） */
.meta-row { display: flex; align-items: center; gap: var(--spacing-sm); }
.rating-group { flex: none; display: inline-flex; align-items: center; gap: var(--spacing-2xs); }
/* 星图标宿主节点（<AppIcon> 自定义组件，未开 virtualHost）显式定为 36rpx 方形 flex 盒 ⇒
   内部图标在宿主内精确居中，与行盒/基线解耦（同 SearchBar .search-bar-icon 的 Round 20b 方案；
   否则星与数字会随继承字体度量上下错位 —— Round 21c 实测）。 */
.rating-star {
  flex: none;
  width: 36rpx;
  height: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rating-num { font-size: var(--font-h3); font-weight: var(--weight-semibold); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.price-group { margin-left: auto; display: flex; align-items: baseline; gap: var(--spacing-2xs); }
.price { font-size: var(--font-h3); font-weight: var(--weight-semibold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.price-sym { font-size: var(--font-body); font-weight: var(--weight-medium); }
.original { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
</style>