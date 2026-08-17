<template>
  <view
    class="dish-card"
    :class="{ pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @tap="handleClick"
  >
    <view class="card-image">
      <image
        v-if="imgSrc && imgOk"
        :src="imgSrc"
        mode="widthFix"
        class="card-img"
        :class="{ loaded: imgLoaded }"
        lazy-load
        @load="imgLoaded = true"
        @error="imgOk = false"
      />
      <view v-else class="image-placeholder">
        <IconSvg name="dish" :size="64" color="var(--text-tertiary)" class="placeholder-icon" />
      </view>
      <view class="card-rating-badge">
        <IconSvg name="star-filled" :size="22" color="var(--color-star)" class="star-icon" />
        <text class="rating-text">{{ dish.rating }}</text>
      </view>
    </view>
    <view class="card-info">
      <view class="title-row">
        <text class="card-name">{{ dish.name }}</text>
        <text class="card-price">¥{{ dish.price }}</text>
      </view>
      <view class="card-tags" v-if="displayTags.length > 0">
        <TagLabel v-for="tag in displayTags" :key="tag" :text="tag" />
        <text v-if="dish.tags.length > 2" class="tag-plus">+{{ dish.tags.length - 2 }}</text>
      </view>
      <view class="meta-row">
        <text class="card-stall">{{ dish.canteen }} · {{ dish.stallName }}</text>
        <view v-if="dish.distance != null" class="card-distance">
          <IconSvg name="location" :size="20" color="var(--color-primary)" class="distance-icon" />
          <text>距你 {{ fmtDistance(dish.distance) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Dish } from '@/types/dish'
import { getImageUrl } from '@/utils/image'
import IconSvg from './IconSvg.vue'
import TagLabel from './TagLabel.vue'

const props = defineProps<{
  dish: Dish
}>()

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件 @click 编译为原生 bindclick，emit 参数丢失（同 MomentCard 坑）。
const emit = defineEmits<{
  select: [dish: Dish]
}>()

/** 按压反馈：按下时整体缩放到 0.97（跨端兼容，替代小程序不支持的 v-press 指令） */
const pressed = ref(false)

/** 图片 URL：通过 getImageUrl 处理（兼容相对路径与完整 URL） */
const imgSrc = computed(() => getImageUrl(props.dish.image))

/** 图片加载状态：加载失败则回退到占位，禁止裂图 */
const imgOk = ref(true)
/** 图片淡入：load 事件触发后置 true，配合 .card-img.loaded 做 opacity 过渡（B.5 降低 CLS） */
const imgLoaded = ref(false)

/** 标签展示：最多 2 个 +「+N」（B.6 卡片信息区规整） */
const displayTags = computed(() => props.dish.tags.slice(0, 2))

/** 距你文案：米/公里自适应（distance 由前端基于定位本地算，服务器不算） */
function fmtDistance(m: number): string {
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${Math.round(m)}m`
}

function handleClick() {
  emit('select', props.dish)
}
</script>

<style scoped>
.dish-card {
  width: 100%;
  min-width: 0;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  /* 进场仅极轻量淡入 + 按压缩放（红线 §4.9②：位移 ≤0，仅 transform/opacity） */
  transition: transform 0.12s ease, opacity 0.2s ease;
  -webkit-tap-highlight-color: transparent;
}
.dish-card.pressed {
  transform: scale(var(--press-scale));
}
.card-image {
  position: relative;
  width: 100%;
  /* 高度跟随图片原始比例（mode=widthFix），未加载时由占位区 4:3 兜底，避免瀑布流跳变 */
  background: var(--bg-page);
  overflow: hidden;
}
.card-img {
  width: 100%;
  height: auto;
  display: block;
  /* B.5 图片加载淡入：默认透明，load 完成后淡入，避免硬切/跳变（CLS<0.1） */
  opacity: 0;
  transition: opacity 0.3s ease;
}
.card-img.loaded { opacity: 1; }
.image-placeholder {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: flex;
  align-items: center;
  justify-content: center;
}
.placeholder-icon {
  font-size: 64rpx;
  line-height: 1;
}
.card-rating-badge {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  background: var(--overlay-dark-strong);
  border-radius: var(--radius-card);
  padding: var(--spacing-xs) var(--spacing-sm);
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  /* 浅色图上也能与文字拉开层次：加深投影 + 浅描边，避免白底图糊字 */
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.35);
  border: 1rpx solid rgba(255, 255, 255, 0.28);
}
.star-icon {
  font-size: 22rpx;
  line-height: 1;
  flex-shrink: 0;
}
.rating-text {
  color: var(--text-white);
  font-size: var(--font-tiny);
  font-weight: var(--weight-bold);
}
.card-info {
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
  min-width: 0;
}
.card-name {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: var(--font-body);
  font-weight: var(--weight-bold);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  color: var(--text-primary);
  flex: 1;
  min-width: 0;
}
.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-sm);
}
.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
.card-stall {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.card-distance {
  flex-shrink: 0;
  margin-left: var(--spacing-xs);
  display: inline-flex;
  align-items: center;
  gap: 2rpx;
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}
.distance-icon { flex-shrink: 0; }
.card-price {
  font-size: var(--font-caption);
  color: var(--color-price);
  font-weight: var(--weight-bold);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-xs);
  margin-top: var(--spacing-sm);
}
/* 标签超出 2 个时的「+N」徽标 */
.tag-plus {
  font-size: var(--font-tiny);
  line-height: 1.4;
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-icon);
  background: var(--bg-placeholder);
  color: var(--text-secondary);
  font-weight: var(--weight-semibold);
}
</style>
