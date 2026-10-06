<template>
  <view class="dish-card" :aria-label="`${dish.name}，${dish.price}元`" hover-class="dish-card-pressed" @tap="handleClick" role="button" tabindex="0">
    <view class="card-image">
      <image
        v-if="imgSrc && imgOk"
        :src="imgSrc"
        mode="aspectFill"
        class="card-img"
        :class="{ loaded: imgLoaded }"
        lazy-load
        @load="imgLoaded = true"
        @error="imgOk = false"
      />
      <view v-else class="image-placeholder">
        <ImagePlaceholder :size="56" />
      </view>
    </view>
    <view class="card-info">
      <!-- 第二段：菜名（黑色加粗、卡片内最大字号） -->
      <text class="card-name">{{ dish.name }}</text>
      <!-- 第三段：`食堂名称 | 档口名称`（浅灰常规小字，纯文字——无图标、无彩色背景标签；
           分隔符 `|` 为 G3 决议） -->
      <view class="card-stall">
        <text class="stall-text">{{ dish.canteen }} | {{ dish.stallName }}</text>
      </view>
      <!-- 第四段：左 = 黄色实心五角星 + 数字评分（🔴 **恒渲染** —— 零评价时服务端已兜底 `5.0`，
           故不再有「有无评分」分支；只显示均分、**不显示评价条数**），价格靠右；右 = 价格（橙色突出）。同一行。
           星尺寸 34rpx：星形自带视觉留白，口径 = 评分文字（28rpx）+ 6rpx 光学补偿 -->
      <view class="card-meta">
        <view class="card-rating" role="img" :aria-label="`评分 ${formatRating(dish.rating)} 分`">
          <IconSvg name="star-filled" :size="34" :color="COLOR_MAP.star" class="star-icon" />
          <text class="rating-text">{{ formatRating(dish.rating) }}</text>
        </view>
        <text class="card-price">¥{{ formatPrice(dish.price) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { DishListItem } from '@/types/dish'
import { getThumbImageUrl } from '@/utils/image'
import { formatPrice } from '@/utils/money'
import { formatRating } from '@/utils/dish'
import IconSvg from '@/components/IconSvg.vue'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import { COLOR_MAP } from '@/theme/tokens'

const props = defineProps<{
  /** 列表行（`GET /dishes` → `DishListItemVO` 8 字段；列表不含详情专属字段） */
  dish: DishListItem
}>()

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 模板侧会被编译成原生 bind 前缀绑定，emit 参数丢失。
const emit = defineEmits<{
  select: [dish: DishListItem]
}>()

/**
 * 图片 URL：列表**唯一图片字段** `coverImage`（后端已给绝对 URL；无图空串 → 占位空态）。
 * C14 列表缩略图走 _thumb（仅详情大图用原图），弱网下流量/时延显著下降。
 */
const imgSrc = computed(() => getThumbImageUrl(props.dish.coverImage))

/** 图片加载状态：加载失败则回退到占位，禁止裂图 */
const imgOk = ref(true)
/** 图片淡入：load 事件触发后置 true，配合 .card-img.loaded 做 opacity 过渡（B.5 降低 CLS） */
const imgLoaded = ref(false)

/* 评分格式化（恒一位小数）已上提为公共 `utils/dish.formatRating` */

function handleClick() {
  emit('select', props.dish)
}
</script>

<style scoped>
.dish-card {
  width: 100%;
  min-width: 0;
  /* 表面：白卡 + 品牌淡色柔和投影（tab-pages-visual-unify）——
     页面层级由「浅米灰底 — 白卡 — 内容 — 强调」四层结构承担。
     overflow:hidden 保留：顶部图片贴齐卡片上缘，需裁进圆角；
     同时规避微信 WXSS「border-radius + background」圆角外侧背景方角残留。 */
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  transition: opacity var(--duration-base) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
/* 按压反馈（统一按压语言：小程序端「透明度微降」，与 mt-tab / 搜索卡同族；
   不用 transform: scale，避免与卡片内图片淡入的合成层叠加抖动） */
.dish-card-pressed { opacity: 0.88; }
.card-image {
  position: relative;
  width: 100%;
  /* 固定 3:2 比例容器（由 4:3 收矮，把视觉重心让给文字信息；§6.2 第 1 段：占卡片高 ≈52–55%，
     介于设计建议的 4:3 与 16:10 之间）；未加载（占位）与加载后（图片）高度一致，消除瀑布流重排卡顿（CLS=0） */
  aspect-ratio: 3 / 2;
  background: var(--bg-soft);
  overflow: hidden;
}
.card-img {
  width: 100%;
  height: 100%;
  display: block;
  /* B.5 图片加载淡入：默认透明，load 完成后淡入，避免硬切/跳变（CLS<0.1） */
  opacity: 0;
  transition: opacity var(--duration-slow) var(--ease-out);
}
.card-img.loaded { opacity: 1; }
.image-placeholder {
  width: 100%;
  aspect-ratio: 3 / 2;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 星：黄色实心（--color-star，星色不随主色换肤）；取色走 COLOR_MAP 真源实色（data-uri 不解析 var）。
   `flex-shrink: 0` 必须有（flex 行内不被压缩）；图标尺寸/行高由 IconSvg 自持，此处不再重复声明。 */
.star-icon {
  flex-shrink: 0;
}
.rating-text {
  /* 评分文字：正文档（#4A3520）**常规字重**——§8「评分 28rpx 常规」。
     本卡**重字重只留两处**（菜名 --weight-semibold / 价格 --weight-bold）：一屏 4 行文字若 3 行都在加深加粗，
     第一眼就不知道该看什么（设计稿「信息密度偏高」那条）；评分语义已由暖黄星形承担，数字无需再加粗。 */
  color: var(--text-body);
  /* --font-body(14px)：旧口径 24rpx 在窄屏折合 ≈10px，低于 12px 可读下限 */
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  font-variant-numeric: tabular-nums;
}
.card-info {
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
  min-width: 0;
}
/* 菜名：标题档加粗（#2D1F14）、卡片文字层级第一级（§6.2 第 2 段；食堂行 / 评分行不得抢菜名） */
.card-name {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  color: var(--text-title);
  min-width: 0;
}
/* 第三段：食堂 | 档口（辅助档 #7F6A55 纯文字，无图标；超长单行省略，§6.2 第 3 段）
   组内间距：与菜名同属「文字组」→ 紧（--spacing-xs 4px） */
.card-stall {
  margin-top: var(--spacing-xs);
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  color: var(--text-subtitle);
  min-width: 0;
}
.stall-text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 第四段：星 + 评分居左，价格居右（同一行） */
.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
.card-rating {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  min-width: 0;
}
/* 价格：橙色突出（--color-price = 主色文字档），第四段右端。
   `margin-left: auto` 保证**零评价（评分组不渲染）时价格仍靠右**（不贴到左侧） */
.card-price {
  font-size: var(--font-h3);
  color: var(--color-price);
  font-weight: var(--weight-bold);
  flex-shrink: 0;
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}
</style>