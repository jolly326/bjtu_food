<script setup lang="ts">
/**
 * 结果卡（docs/ui/client/菜品首页.md `DishResultCard`）。
 *
 * 一卡 = 一道菜：16:9 封面 + 单列四段信息区，该菜全部在售位置在卡内并列。
 * 段 1 头部（菜名 / 售价）→ 段 2 位置列表 → 段 3 描述（2 行）→ 段 4 操作栏（反馈左 / 展开右）。
 * 反馈表单在四段之下就地展开，始终处于卡片正常文档流内。
 */
import { computed } from 'vue'

import type { DishVO } from '@/api'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import FeedbackForm from './FeedbackForm.vue'
import { IMAGE, RESULT } from '@/constants'
import { useImageSource } from '@/hooks'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  dish: DishVO
  expanded: boolean
  /** 本卡反馈表单是否展开（由父级全局唯一控制） */
  feedbackOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'open', dishId: number): void
  (e: 'feedback-open', dishId: number | null): void
}>()

const { src, onError } = useImageSource(() => props.dish.imageUrl, IMAGE.RESULT_THUMB_WIDTH)

const needFull = computed(
  () => props.dish.description.length > RESULT.DESCRIPTION_PREVIEW_CHARS,
)
const priceText = computed(() =>
  props.dish.price === null ? '价格待定' : `¥${(props.dish.price / 100).toFixed(2)}`,
)
/** 每个在售位置一行：食堂 楼层 档口，省去中间分隔符 */
const places = computed(() =>
  props.dish.locations
    .map((loc) => ({
      id: loc.id,
      text: [loc.canteenName, loc.floor, loc.stallName].filter(Boolean).join(' '),
    }))
    .filter((place) => place.text),
)

function toggleFeedback(): void {
  emit('feedback-open', props.feedbackOpen ? null : props.dish.id)
}
</script>

<template>
  <view class="card">
    <view class="card__cover">
      <image v-if="src" class="card__img" :src="src" mode="aspectFill" @error="onError" />
      <view v-else class="card__ph"><ImagePlaceholder /></view>
    </view>

    <view class="card__info">
      <!-- 段 1 · 头部：菜名 + 售价 -->
      <view class="card__head">
        <text class="card__name">{{ props.dish.name }}</text>
        <text class="card__price">{{ priceText }}</text>
      </view>

      <!-- 段 2 · 位置列表：一菜多位置并列 -->
      <view class="card__places">
        <text v-for="place in places" :key="place.id" class="card__place">{{ place.text }}</text>
        <text v-if="!places.length" class="card__place">暂无位置</text>
      </view>

      <!-- 段 3 · 描述：常驻 2 行 -->
      <text
        v-if="props.dish.description"
        class="card__desc"
        :class="{ 'card__desc--clamp': needFull && !props.expanded, 'card__desc--full': props.expanded }"
      >{{ props.dish.description }}</text>

      <!-- 段 4 · 操作栏：反馈左、展开 / 收起右 -->
      <view class="card__meta">
        <view
          class="card__link"
          hover-class="card__link--press"
          aria-role="button"
          :aria-label="props.feedbackOpen ? '收起反馈' : '反馈这道菜'"
          @tap.stop="toggleFeedback"
        >{{ props.feedbackOpen ? '收起反馈' : '反馈' }}</view>
        <view
          v-if="needFull"
          class="card__more"
          hover-class="card__link--press"
          aria-role="button"
          :aria-label="props.expanded ? '收起描述' : '展开描述'"
          @tap.stop="emit('open', props.dish.id)"
        >{{ props.expanded ? '收起' : '展开' }}</view>
      </view>
    </view>

    <FeedbackForm
      v-if="props.feedbackOpen"
      type="dish"
      variant="inline"
      :dish-id="props.dish.id"
      @close="emit('feedback-open', null)"
    />
  </view>
</template>

<style lang="scss" scoped>
.card {
  box-sizing: border-box;
  width: 100%;
  overflow: hidden;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-card);
  background: var(--bg-card);
}

.card__cover {
  position: relative;
  height: 0;
  padding-top: var(--cover-aspect-pad);
  overflow: hidden;
  background: var(--bg-placeholder);
}

.card__img,
.card__ph {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

/* 信息区：单列四段 —— 头部 / 位置列表 / 描述 / 操作栏 */
.card__info {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  box-sizing: border-box;
  padding: var(--spacing-sm);
}

.card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.card__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: var(--font-title);
  color: var(--text-title);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.card__price {
  flex-shrink: 0;
  font-size: var(--font-h3);
  color: var(--brand);
}

.card__places {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xs);
}

/* 位置是「在哪吃」的关键信息：字号与色阶都强于描述 */
.card__place {
  overflow: hidden;
  font-size: var(--font-body);
  color: var(--text-title);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.card__desc {
  font-size: var(--font-subtitle);
  color: var(--text-body);
}

.card__desc--clamp {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.card__desc--full {
  animation: motion-fade var(--duration-base) var(--ease-out);
}

/* 操作栏：反馈左、展开 / 收起右；命中区经 ::after 补到触达下限 */
.card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.card__link,
.card__more {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: var(--control-height-sm);
  padding: 0 var(--spacing-md);
  font-size: var(--font-caption);
  color: var(--text-body);
}

.card__link {
  padding-left: 0;
}

.card__link::after,
.card__more::after {
  position: absolute;
  top: calc((var(--tap-target-size) - var(--control-height-sm)) / -2);
  right: 0;
  left: 0;
  height: var(--tap-target-size);
  content: '';
}

.card__link--press {
  opacity: 0.6;
}
</style>
