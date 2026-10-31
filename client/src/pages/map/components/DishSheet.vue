<script setup lang="ts">
/**
 * 菜品底部弹窗（组合器）。
 *
 * 结构：固定菜品大图 + 横向滑动区（信息页 ↔ 纠错页）。
 * 纠错页在 **Sheet 内部**完成 pick → confirm → success 三态闭环，不弹独立确认框、不加深色遮罩。
 * 🔴 虚线全程固定、两个滑块的两块高度一致 ⇒ 切换/换态时虚线位置不动。
 * 业务状态由 useDishFeedback 统一管理，定时器由 useTimers 统一清理。
 */
import { computed, ref, watch } from 'vue'

import { useDishFeedback, useTimers } from '@/hooks'

import type { DishVO, IssueOptionOut } from '@/api'
import BaseSheet from '@/components/BaseSheet.vue'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import { SHEET } from '@/constants'
import DishInfo from './DishInfo.vue'
import FeedbackPanel from './FeedbackPanel.vue'
import type { FeedbackStage } from './FeedbackPanel.vue'

const props = defineProps<{
  modelValue: boolean
  dish: DishVO | null
  options: IssueOptionOut[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'change', payload: { id: number; likeCount: number; liked: boolean }): void
  (e: 'measured', height: number): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

/** 纠错页当前态：选择 → 确认 → 成功（成功为单向收尾） */
const feedbackOpen = ref(false)
const feedbackStage = ref<FeedbackStage>('pick')
const selectedOption = ref<IssueOptionOut | null>(null)
const popping = ref(false)
/** 大图加载失败：切菜时重置 */
const imgFailed = ref(false)

const {
  likeBusy,
  likeCooling,
  issueBusy,
  toggleLike,
  submitIssue,
  syncFromDish,
} = useDishFeedback({
  dish: () => props.dish,
  onLikeChange: (payload) => {
    emit('change', payload)
  },
})

const { setTimer, clearAll } = useTimers()

watch(
  () => [props.modelValue, props.dish] as const,
  ([visible, dish]) => {
    if (!visible || !dish) {
      backToInfo()
      clearAll()
      return
    }
    imgFailed.value = false
    syncFromDish(dish)
    backToInfo()
  },
  { immediate: true },
)

function openFeedback(): void {
  feedbackStage.value = 'pick'
  selectedOption.value = null
  feedbackOpen.value = true
}

function pickOption(option: IssueOptionOut): void {
  selectedOption.value = option
  feedbackStage.value = 'confirm'
}

function cancelPick(): void {
  selectedOption.value = null
  feedbackStage.value = 'pick'
}

/** 返回：滑回菜品信息页（Sheet 保持打开） */
function backToInfo(): void {
  selectedOption.value = null
  feedbackStage.value = 'pick'
  feedbackOpen.value = false
}

async function confirmSubmit(): Promise<void> {
  const option = selectedOption.value
  if (!option || issueBusy.value) return
  const ok = await submitIssue(option.id)
  if (ok) feedbackStage.value = 'success'
}

function playPop(): void {
  popping.value = false
  setTimer(() => {
    popping.value = true
    setTimer(() => {
      popping.value = false
    }, SHEET.POP_MS)
  }, 0)
}

function onToggleLike(): void {
  void toggleLike().then((likedNow) => {
    if (likedNow) playPop()
  })
}
</script>

<template>
  <BaseSheet v-model="open" :mask="false" @measured="emit('measured', $event)">
    <!-- 菜品大图：固定不滑动；圆角与弹层顶角同半径 -->
    <view class="dish-hero">
      <image
        v-if="dish?.imageUrl && !imgFailed"
        class="dish-hero__img"
        :src="dish.imageUrl"
        mode="aspectFill"
        @error="imgFailed = true"
      />
      <view v-else class="dish-hero__empty">
        <ImagePlaceholder />
      </view>
    </view>

    <!-- 信息 / 纠错：横向滑动（两页等高，切换不改变弹层高度） -->
    <view class="dish-slider">
      <view
        class="dish-slider__track"
        :style="{ transform: feedbackOpen ? 'translateX(-100%)' : 'translateX(0)' }"
      >
        <view class="dish-slider__page">
          <DishInfo
            :dish="dish"
            :like-busy="likeBusy"
            :like-cooling="likeCooling"
            :popping="popping"
            @toggle-like="onToggleLike"
            @open-feedback="openFeedback"
          />
        </view>

        <view class="dish-slider__page" :aria-hidden="!feedbackOpen">
          <FeedbackPanel
            :options="options"
            :stage="feedbackStage"
            :selected="selectedOption"
            :busy="issueBusy"
            @pick="pickOption"
            @cancel="cancelPick"
            @confirm="confirmSubmit"
            @back="backToInfo"
          />
        </view>
      </view>
    </view>
  </BaseSheet>
</template>

<style lang="scss" scoped>
/* 大图：圆角矩形，与弹层边缘留等距 gap；圆角与弹层顶角同半径 ⇒ 同心圆弧 */
.dish-hero {
  position: relative;
  box-sizing: border-box;
  flex: 0 0 auto; /* 图片区不参与纵向压缩 */
  margin: 0 var(--spacing-lg) var(--spacing-xs);
  /* 16:9：padding 百分比以父容器宽度为基准，先扣掉左右 margin 才是图片自身宽度 */
  padding-bottom: calc((100% - var(--spacing-lg) * 2) * 9 / 16);
  overflow: hidden;
  background: var(--bg-placeholder);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-modal);
}

.dish-hero__img {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: block;
  width: 100%;
  height: 100%;
}

.dish-hero__empty {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 滑动视口：flex 容器高度取两页中较高者 ⇒ 切换时弹层高度不变（不用 grid，小程序兼容性更好） */
.dish-slider {
  flex: 0 0 auto; /* 滑块区也不参与压缩，避免与图片互相挤 */
  overflow: hidden;
}

/* 轮播轨：宽 100%，两页各占 100% 并排，靠位移切换（比 width:200% + 50% 在小程序里更稳） */
.dish-slider__track {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: stretch;
  width: 100%;
  transition: transform var(--duration-slow) var(--ease-out);
  will-change: transform;
}

/* 每个滑块固定为「上块 / 虚线 / 下块」三段且高度写死：
   两页同高、上下两块等分 ⇒ 虚线垂直居中，切换时位置完全一致。
   左右 padding 与图片 gap 相同 ⇒ 文本左边缘与图片卡片左边缘对齐 */
.dish-slider__page {
  box-sizing: border-box;
  flex-grow: 0;
  flex-shrink: 0;
  flex-basis: 100%;
  width: 100%;
  min-width: 0;
  /* 🔴 不用 flex 纵向分配：小程序里父高由内容决定时 `flex:1` 不分配空间，
     会让两块内容贴在一起。改为自然流 + 各块自身 min-height 对齐。 */
  padding: var(--spacing-sm) var(--spacing-lg);
}

@media (prefers-reduced-motion: reduce) {
  .dish-slider__track {
    transition: none;
  }
}
</style>
