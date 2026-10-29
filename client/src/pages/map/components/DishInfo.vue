<script setup lang="ts">
/**
 * 菜品信息区（DishSheet 私有子组件）。
 * 展示菜品大图、名称、点赞态、档口/位置、纠错入口。
 * 所有状态通过 props 传入，交互通过 emit 上报，由 DishSheet 统一管理。
 */
import { computed, ref, watch } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import type { DishVO } from '@/api'

const props = withDefaults(
  defineProps<{
    dish: DishVO | null
    likeBusy: boolean
    likeCooling: boolean
    /** 点赞 Q 弹（成功后由组合器触发一次） */
    popping: boolean
  }>(),
  {
    dish: null,
    likeBusy: false,
    likeCooling: false,
    popping: false,
  },
)

const emit = defineEmits<{
  (e: 'toggleLike'): void
  (e: 'openFeedback'): void
}>()

/** 图片失败态：内部管理，换菜时重置 */
const imgFailed = ref(false)
watch(
  () => props.dish?.id,
  () => {
    imgFailed.value = false
  },
)

const likeLabel = computed(() => (props.dish?.liked ? '取消点赞' : '点赞'))
/** 🔴 该位恒为点赞数（口径见 docs/func/client/B1）：冷却期也不换成倒计时秒数 */
const displayCount = computed(() => props.dish?.likeCount ?? 0)
</script>

<template>
  <view v-if="dish" class="dish">
    <view class="dish__media">
      <view class="dish__media-inner">
        <image
          v-if="dish.imageUrl && !imgFailed"
          class="dish__img"
          :src="dish.imageUrl"
          mode="aspectFill"
          @error="imgFailed = true"
        />
        <ImagePlaceholder v-else />
      </view>
    </view>

    <view class="sheet-viewport">
      <view class="sheet-page">
        <view class="dish__head">
          <text class="dish__name">{{ dish.name }}</text>
          <view
            class="like"
            :class="{
              'like--on': dish.liked,
              'like--busy': likeBusy || likeCooling,
              'like--pop': popping,
            }"
            hover-class="like--pressed"
            aria-role="button"
            :aria-label="likeLabel"
            @tap.stop="emit('toggleLike')"
          >
            <AppIcon :name="dish.liked ? 'heart-fill' : 'heart'" :size="40" />
            <text class="like__count">{{ displayCount }}</text>
          </view>
        </view>

        <text v-if="dish.stallName" class="dish__stall">{{ dish.stallName }}</text>

        <view class="dashed-rule" aria-hidden="true" />

        <view class="dish__loc-row">
          <view class="dish__loc-left">
            <text class="dish__canteen">{{ dish.canteenName }}</text>
            <text v-if="dish.floor" class="dish__floor">{{ dish.floor }}</text>
          </view>
          <view
            class="issue-entry"
            aria-role="button"
            aria-label="纠错反馈"
            @tap.stop="emit('openFeedback')"
          >
            <text class="issue-entry__text">纠错反馈</text>
            <text class="caret-doodle" aria-hidden="true">›</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 样式与原 DishSheet 信息区一致（拆分组件不改变视觉） */
.dish {
  display: flex;
  flex-direction: column;
}

.dish__media {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  padding-bottom: 75%;
  overflow: hidden;
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  background: var(--bg-placeholder);
}

.dish__media-inner {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}

.dish__img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: var(--radius-card);
}

.sheet-viewport {
  overflow: hidden;
  padding: var(--spacing-md) 0 var(--spacing-sm);
}

.sheet-page {
  display: flex;
  flex: 0 0 100%;
  flex-direction: column;
  justify-content: center;
  min-height: calc(var(--tap-target-size) * 2 + var(--spacing-sm));
}

.dish__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.dish__name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  color: var(--text-title);
  font-family: var(--font-sans);
  font-size: var(--font-title);
  font-weight: var(--weight-regular);
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish__stall {
  display: block;
  margin-top: var(--spacing-2xs);
  overflow: hidden;
  color: var(--text-body);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashed-rule {
  height: 1rpx;
  margin: var(--spacing-sm) 0;
  background-image: repeating-linear-gradient(
    to right,
    var(--sheet-divider) 0,
    var(--sheet-divider) var(--spacing-xs),
    transparent var(--spacing-xs),
    transparent var(--spacing-sm)
  );
}

.dish__loc-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.dish__loc-left {
  display: flex;
  flex: 1;
  align-items: baseline;
  gap: var(--spacing-xs);
  min-width: 0;
}

.dish__canteen,
.dish__floor {
  overflow: hidden;
  color: var(--text-body);
  font-family: var(--font-sans);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish__canteen {
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-body);
  line-height: 1.3;
}

.dish__floor {
  flex: 0 0 auto;
  color: var(--text-body);
  font-size: var(--font-subtitle);
  line-height: 1.3;
}

.issue-entry {
  position: relative;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--spacing-2xs);
  min-height: calc(var(--tap-target-size) * 0.66);
  margin-right: calc(-1 * var(--spacing-xs));
  padding: 0 var(--spacing-xs);
}

.issue-entry::after {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  height: var(--tap-target-size);
  transform: translateY(-50%);
}

.issue-entry__text {
  color: var(--text-body);
  font-family: var(--font-sans);
  font-size: var(--font-subtitle);
  line-height: 1.3;
}

.caret-doodle {
  color: var(--color-muted);
  font-family: var(--font-sans);
  font-size: var(--font-h3);
  line-height: 1;
}

.like {
  position: relative;
  display: flex;
  flex-shrink: 0;
  align-items: baseline;
  gap: var(--spacing-xs);
  padding: 0 var(--spacing-2xs);
}

.like::after {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  height: var(--tap-target-size);
  transform: translateY(-50%);
}

.like--pressed {
  opacity: 0.7;
}

.like--busy {
  opacity: 0.5;
}

.like--pop {
  animation: like-pop var(--duration-base) var(--ease-bounce);
}

@keyframes like-pop {
  0% {
    transform: scale(1);
  }

  45% {
    transform: scale(1.16);
  }

  100% {
    transform: scale(1);
  }
}

.like__count {
  color: var(--text-title);
  font-size: var(--font-title);
  line-height: 1;
}
</style>
