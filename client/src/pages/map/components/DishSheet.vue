<script setup lang="ts">
/**
 * 菜品底部弹窗（设计稿 docs/ui/client/美食地图.md §3；功能规则 docs/func/client/B1、B2）。
 *
 * 自上而下：菜品大图 → 菜名 / 价格 / 位置 → 两态点赞 → 纠错入口（同层展开选项）。
 * 点赞与纠错提交后轻 Toast，弹窗不自动关闭。
 */
import { computed, ref, watch } from 'vue'

import { dishApi, issueApi } from '@/api'
import type { DishVO, IssueOptionOut } from '@/api'
import AppIcon from '@/components/base/AppIcon.vue'
import BaseSheet from '@/components/base/BaseSheet.vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import { fenToYuan } from '@/utils/price'

const props = defineProps<{
  modelValue: boolean
  dish: DishVO | null
  options: IssueOptionOut[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'change', payload: { id: number; likeCount: number; liked: boolean }): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const likeCount = ref(0)
const liked = ref(false)
const likeBusy = ref(false)
const issueOpen = ref(false)
const issueBusy = ref(false)
const imgFailed = ref(false)
/** 点赞反馈的 Q 弹：切换后置真并在动画时长后复位 */
const popping = ref(false)

function playPop(): void {
  popping.value = false
  setTimeout(() => {
    popping.value = true
    setTimeout(() => {
      popping.value = false
    }, 240)
  }, 0)
}

const priceLabel = computed(() => `¥${fenToYuan(props.dish?.price ?? 0)}`)

watch(
  () => [props.modelValue, props.dish] as const,
  ([visible, dish]) => {
    if (!visible || !dish) return
    likeCount.value = dish.likeCount
    liked.value = dish.liked
    issueOpen.value = false
    imgFailed.value = false
  },
  { immediate: true },
)

function toast(title: string): void {
  uni.showToast({ title, icon: 'none', duration: 3000 })
}

async function toggleLike(): Promise<void> {
  if (!props.dish || likeBusy.value) return
  likeBusy.value = true
  try {
    const result = await dishApi.like(props.dish.id)
    likeCount.value = result.likeCount
    liked.value = result.liked
    emit('change', { id: result.id, likeCount: result.likeCount, liked: result.liked })
    playPop()
    toast(result.liked ? '已点赞' : '已取消')
  } catch (error) {
    toast((error as Error).message || '操作失败，请重试')
  } finally {
    likeBusy.value = false
  }
}

async function submitIssue(optionId: number): Promise<void> {
  if (!props.dish || issueBusy.value) return
  issueBusy.value = true
  try {
    await issueApi.submit(props.dish.id, optionId)
    toast('已提交，感谢反馈')
    issueOpen.value = false
  } catch (error) {
    toast((error as Error).message || '提交失败，请重试')
  } finally {
    issueBusy.value = false
  }
}
</script>

<template>
  <BaseSheet v-model="open">
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

      <view class="dish__info">
        <view class="dish__head">
          <text class="dish__name">{{ dish.name }}</text>
          <text class="dish__price">{{ priceLabel }}</text>
        </view>
        <view class="dish__loc">
          <AppIcon name="map-pin" :size="22" tone="muted" />
          <text class="dish__loc-text">{{ dish.canteenName }} · {{ dish.stallName }}</text>
        </view>
      </view>

      <view class="dish__actions">
        <view
          class="like"
          :class="{ 'like--on': liked, 'like--busy': likeBusy, 'like--pop': popping }"
          hover-class="like--pressed"
          @tap="toggleLike"
        >
          <AppIcon :name="liked ? 'heart-fill' : 'heart'" :size="28" :tone="liked ? 'primary' : 'default'" />
          <text class="like__count">{{ likeCount }}</text>
        </view>
        <view class="issue-toggle" @tap="issueOpen = !issueOpen">
          <text class="issue-toggle__text">纠错反馈</text>
          <AppIcon :name="issueOpen ? 'caret-up' : 'caret-down'" :size="22" tone="default" />
        </view>
      </view>

      <view v-if="issueOpen" class="issue-list">
        <view
          v-for="option in options"
          :key="option.id"
          class="issue-row"
          hover-class="issue-row--pressed"
          @tap="submitIssue(option.id)"
        >
          <text class="issue-row__text">{{ option.name }}</text>
        </view>
      </view>
    </view>
  </BaseSheet>
</template>

<style lang="scss" scoped>
.dish {
  display: flex;
  flex-direction: column;
}

.dish__media {
  position: relative;
  width: 100%;
  padding-bottom: 75%;
  overflow: hidden;
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

.dish__info {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-md) 0 var(--spacing-sm);
}

.dish__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.dish__name {
  font-family: var(--font-sans);
  font-size: var(--font-title);
  font-weight: var(--weight-regular);
  line-height: 1.2;
  color: var(--text-title);
}

.dish__price {
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-body);
}

.dish__loc {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
}

.dish__loc-text {
  font-size: var(--font-subtitle);
  color: var(--text-tertiary);
}

.dish__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-xs) 0 var(--spacing-sm);
}

.like {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
}

.like--on {
  background: var(--color-primary-soft);
}

.like--pressed {
  opacity: 0.72;
}

.like--busy {
  opacity: 0.55;
}

/* 点赞 Q 弹：图标与数字一起弹一下（过冲缓动，不做夸张旋转） */
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
  font-size: var(--font-body);
  color: var(--text-title);
}

.issue-toggle {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  padding: var(--spacing-xs) 0;
}

.issue-toggle__text {
  font-size: var(--font-body);
  color: var(--text-body);
}

.issue-list {
  display: flex;
  flex-direction: column;
}

.issue-row {
  display: flex;
  align-items: center;
  min-height: var(--tap-target-size);
  padding: 0 var(--spacing-sm);
  border-top: 1rpx solid var(--border-color);
}

.issue-row--pressed {
  background: var(--bg-card);
}

.issue-row__text {
  font-size: var(--font-body);
  color: var(--text-title);
}
</style>
