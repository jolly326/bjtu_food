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
      <!-- 评分徽标：图片右上角（黑底白字，浅色图上也能看清） -->
      <view class="card-rating-badge">
        <IconSvg name="star-filled" :size="22" color="var(--color-star)" class="star-icon" />
        <text class="rating-text">{{ fmtRating(dish.rating) }}</text>
      </view>
    </view>
    <view class="card-info">
      <!-- 第一行：菜名（左，一级标题） + 价格（右上，主色大号加粗，第一眼可见） -->
      <view class="title-row">
        <text class="card-name">{{ dish.name }}</text>
        <text class="card-price">¥{{ dish.price }}</text>
      </view>
      <!-- 第二行：菜品标签（菜名下方，浅红底深红字，尺寸收小不抢主信息） -->
      <view class="card-tags" v-if="displayTags.length > 0">
        <TagLabel v-for="tag in displayTags" :key="tag" :text="tag" />
        <text v-if="(dish.tags || []).length > 2" class="tag-plus">+{{ (dish.tags || []).length - 2 }}</text>
      </view>
      <!-- 第三行（卡片底部）：食堂·档口 + 距离（缩小字号、降为三级文字弱化） -->
      <view class="card-foot">
        <view class="card-stall">
          <IconSvg name="location" :size="22" color="var(--text-tertiary)" class="stall-icon" />
          <text class="stall-text">{{ dish.canteen }} · {{ dish.stallName }}</text>
        </view>
        <text v-if="dish.distance != null" class="card-distance">{{ fmtDistance(dish.distance) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Dish } from '@/types/dish'
import { getImageUrl, getThumbUrl } from '@/utils/image'
import IconSvg from '@/components/IconSvg.vue'
import TagLabel from '@/components/TagLabel.vue'

const props = defineProps<{
  dish: Dish
}>()

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件 @click 编译为原生 bindclick，emit 参数丢失（同 MomentCard 坑）。
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

/** 标签展示：最多 2 个 +「+N」（B.6 卡片信息区规整）；tags 可能为 undefined（旧数据/占位），空数组兜底防 length 报错 */
const displayTags = computed(() => (props.dish.tags || []).slice(0, 2))

/** 距你文案：米/公里自适应（distance 由前端基于定位本地算，服务器不算） */
function fmtDistance(m: number): string {
  // 脏数据（NaN/负数/非有限）隐藏；超大距离（>999km，应为异常坐标）截断显示
  if (!Number.isFinite(m) || m < 0) return ''
  if (m > 999000) return '>999km'
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${Math.round(m)}m`
}

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
/* 评分徽标：图片层右上角，深灰小胶囊 + 白字（浅色图上也能看清） */
.card-rating-badge {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  background: var(--overlay-dark-strong);
  border-radius: var(--radius-pill);
  padding: var(--spacing-xs) var(--spacing-sm);
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  box-shadow: var(--shadow-float);
  border: 1rpx solid rgba(255, 255, 255, 0.28);
}
.star-icon {
  font-size: var(--font-aux);
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
/* 菜名：一级标题档（32rpx / 600），卡片内信息优先级最高 */
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
  flex: 1;
  min-width: 0;
}
.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-sm);
}
/* 卡片底部行：食堂·档口（左） + 距离（右），同为辅助信息档（24rpx / 400 / 三级文字） */
.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
/* 食堂·档口：卡片底部，超长省略 */
.card-stall {
  display: flex;
  align-items: center;
  gap: 2rpx;
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
.stall-icon { flex-shrink: 0; }
.stall-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 第二行标签 chips：菜名下方（浅红底深红字），独立成行不挤压底部位置信息 */
.card-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-xs);
  min-width: 0;
  margin-top: var(--spacing-sm);
}
/* 标签超出 2 个时的「+N」徽标：与标签同为小元素档圆角（16rpx） */
.tag-plus {
  font-size: var(--font-tiny);
  line-height: 1.4;
  padding: var(--spacing-2xs) var(--spacing-xs);
  border-radius: var(--radius-tag);
  background: var(--bg-placeholder);
  color: var(--text-secondary);
  font-weight: var(--weight-semibold);
}
/* 距离：卡片底部右侧，仅数字+单位，辅助信息档弱化 */
.card-distance {
  flex-shrink: 0;
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
/* 价格：强调信息档（大号加粗 + 主色），位于卡片右上角第一眼可见 */
.card-price {
  font-size: var(--font-h3);
  color: var(--color-price);
  font-weight: var(--weight-bold);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
</style>
