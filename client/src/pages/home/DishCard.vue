<template>
  <view class="dish-card" :aria-label="`${dish.name}，${dish.price}元`" @tap="handleClick" role="button" tabindex="0">
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
        <IconSvg name="dish" :size="56" color="var(--text-tertiary)" class="placeholder-icon" />
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
      <!-- 第四段：左 = 黄色实心五角星 + 数字评分；右 = 价格（橙色突出）。同一行。
           星尺寸 30rpx：24px 网格的星形自带视觉留白，30rpx 与 24rpx 评分文本视觉等高（光学补偿） -->
      <view class="card-meta">
        <view class="card-rating">
          <IconSvg name="star-filled" :size="30" :color="COLOR_MAP.star" class="star-icon" />
          <text class="rating-text">{{ fmtRating(dish.rating) }}</text>
        </view>
        <text class="card-price">¥{{ formatPrice(dish.price) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Dish } from '@/types/dish'
import { getImageUrl, getThumbUrl } from '@/utils/image'
import { formatPrice } from '@/utils/money'
import IconSvg from '@/components/IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'

const props = defineProps<{
  dish: Dish
}>()

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件 @click 编译为原生 bindclick，emit 参数丢失。
const emit = defineEmits<{
  select: [dish: Dish]
}>()

/** 图片 URL：通过 getImageUrl 处理（兼容相对路径与完整 URL） */
// C14 列表缩略图走 _thumb（仅详情大图用原图），弱网下流量/时延显著下降
const imgSrc = computed(() => getImageUrl(getThumbUrl(props.dish.image)))

/** 图片加载状态：加载失败则回退到占位，禁止裂图 */
const imgOk = ref(true)
/** 图片淡入：load 事件触发后置 true，配合 .card-img.loaded 做 opacity 过渡（B.5 降低 CLS） */
const imgLoaded = ref(false)

/** 评分统一保留一位小数（与详情页 toFixed(1) 一致，避免 4 / 4.5 显示不一致） */
function fmtRating(r: number): string {
  return Number(r || 0).toFixed(1)
}

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
.card-image {
  position: relative;
  width: 100%;
  /* 固定 3:2 比例容器（tab-pages-visual-unify：由 4:3 收矮，把视觉重心让给文字信息）；
     未加载（占位）与加载后（图片）高度一致，消除瀑布流滚动重排卡顿（CLS=0） */
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
.placeholder-icon {
  font-size: var(--icon-2xl);
  line-height: 1;
}
/* 星：黄色实心（--color-star，星色不随主色换肤）；取色走 COLOR_MAP 真源实色（data-uri 不解析 var） */
.star-icon {
  line-height: 1;
  flex-shrink: 0;
}
.rating-text {
  color: var(--text-secondary);
  font-size: var(--font-small);
  font-weight: var(--weight-semibold);
  font-variant-numeric: tabular-nums;
}
.card-info {
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
  min-width: 0;
}
/* 菜名：黑色加粗、卡片内最大字号（表格第 2 行） */
.card-name {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  color: var(--text-primary);
  min-width: 0;
}
/* 第三段：食堂 | 档口（浅灰纯文字，无图标；超长省略） */
.card-stall {
  margin-top: var(--spacing-sm);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
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
/* 价格：橙色突出（--color-price = 主色文字档），第四段右端 */
.card-price {
  font-size: var(--font-h3);
  color: var(--color-price);
  font-weight: var(--weight-bold);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
</style>
