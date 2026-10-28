<script setup lang="ts">
/**
 * 菜品底部弹窗（设计稿 docs/ui/client/美食地图.md §3；功能规则 docs/func/client/B1、B2）。
 *
 * 自上而下：菜品大图 → 信息区（两页横向切换）→ 轻确认。
 * 信息区第 1 页（默认）：菜品名+档口名 / 餐厅名+楼层 两行大字，点赞在右，纠错在第二行右下。
 * 信息区第 2 页：三枚反馈按钮 +「返回菜品信息」；信息左移、反馈从右侧进入。
 * 点赞与纠错提交后轻 Toast，弹窗不自动关闭。
 */
import { computed, ref, watch } from 'vue'

import { dishApi, issueApi } from '@/api'
import type { DishVO, IssueOptionOut } from '@/api'
import AppIcon from '@/components/base/AppIcon.vue'
import BaseSheet from '@/components/base/BaseSheet.vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import { cooldownFrom, useCooldown } from '@/utils/backoff'

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
const issueBusy = ref(false)
const imgFailed = ref(false)
/** 点赞反馈的 Q 弹：切换后置真并在动画时长后复位 */
const popping = ref(false)

/** 信息区当前页：false = 菜品信息（默认），true = 反馈 */
const feedbackOpen = ref(false)
/** 待确认的纠错项（轻确认卡片） */
const confirmOption = ref<IssueOptionOut | null>(null)

/** 429 退避：点赞与纠错各自独立，命中后入口禁用并倒计时恢复 */
const { locked: likeCooling, left: likeLeft } = useCooldown('like')
const { locked: issueCooling, left: issueLeft } = useCooldown('issue')

watch(
  () => [props.modelValue, props.dish] as const,
  ([visible, dish]) => {
    if (!visible || !dish) return
    likeCount.value = dish.likeCount
    liked.value = dish.liked
    feedbackOpen.value = false
    confirmOption.value = null
    imgFailed.value = false
  },
  { immediate: true },
)

function playPop(): void {
  popping.value = false
  setTimeout(() => {
    popping.value = true
    setTimeout(() => {
      popping.value = false
    }, 240)
  }, 0)
}

function toast(title: string): void {
  uni.showToast({ title, icon: 'none', duration: 3000 })
}

function openFeedback(): void {
  feedbackOpen.value = true
}

function resetFeedback(): void {
  feedbackOpen.value = false
}

async function toggleLike(): Promise<void> {
  if (!props.dish || likeBusy.value) return
  if (likeCooling.value) {
    toast(`操作过于频繁，请 ${likeLeft.value} 秒后再试`)
    return
  }
  likeBusy.value = true
  try {
    const result = await dishApi.like(props.dish.id)
    likeCount.value = result.likeCount
    liked.value = result.liked
    emit('change', { id: result.id, likeCount: result.likeCount, liked: result.liked })
    playPop()
    toast(result.liked ? '已点赞' : '已取消')
  } catch (error) {
    const wait = cooldownFrom('like', error)
    toast(
      wait > 0
        ? `操作过于频繁，请 ${wait} 秒后再试`
        : (error as Error).message || '操作失败，请重试',
    )
  } finally {
    likeBusy.value = false
  }
}

function pickOption(option: IssueOptionOut): void {
  if (issueBusy.value) return
  confirmOption.value = option
}

function cancelConfirm(): void {
  confirmOption.value = null
}

async function submitIssue(optionId: number): Promise<boolean> {
  if (!props.dish || issueBusy.value) return false
  if (issueCooling.value) {
    toast(`操作过于频繁，请 ${issueLeft.value} 秒后再试`)
    return false
  }
  issueBusy.value = true
  try {
    await issueApi.submit(props.dish.id, optionId)
    toast('已提交，感谢反馈')
    return true
  } catch (error) {
    const wait = cooldownFrom('issue', error)
    toast(
      wait > 0
        ? `操作过于频繁，请 ${wait} 秒后再试`
        : (error as Error).message || '提交失败，请重试',
    )
    return false
  } finally {
    issueBusy.value = false
  }
}

async function confirmSubmit(): Promise<void> {
  const option = confirmOption.value
  if (!option) return
  const ok = await submitIssue(option.id)
  confirmOption.value = null
  if (ok) feedbackOpen.value = false
}

/** 信息区横向手势：左滑进入反馈（右页），右滑回菜品信息（左页） */
const SWIPE_THRESHOLD = 12
let swipeStartX = 0
let swipeStartY = 0
let swipeMoved = false

function onSwipeStart(event: TouchEvent): void {
  const touch = event.touches[0]
  if (!touch) return
  swipeStartX = touch.clientX
  swipeStartY = touch.clientY
  swipeMoved = false
}

function onSwipeMove(event: TouchEvent): void {
  const touch = event.touches[0]
  if (!touch) return
  const dx = touch.clientX - swipeStartX
  const dy = touch.clientY - swipeStartY
  if (!swipeMoved && Math.abs(dx) > SWIPE_THRESHOLD && Math.abs(dx) > Math.abs(dy)) {
    swipeMoved = true
  }
}

function onSwipeEnd(event: TouchEvent): void {
  if (!swipeMoved) return
  const touch = event.changedTouches[0]
  if (!touch) return
  const dx = touch.clientX - swipeStartX
  if (dx < -SWIPE_THRESHOLD) feedbackOpen.value = true
  else if (dx > SWIPE_THRESHOLD) feedbackOpen.value = false
}
</script>

<template>
  <BaseSheet v-model="open" :mask="false">
    <view v-if="dish" class="dish">
      <view class="dish__media" @tap="resetFeedback">
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

      <view
        class="info-viewport"
        @touchstart="onSwipeStart"
        @touchmove.stop="onSwipeMove"
        @touchend="onSwipeEnd"
      >
        <view class="info-strip" :class="{ 'info-strip--feedback': feedbackOpen }">
          <!-- 第 1 页（默认）：菜品名+档口名 / 餐厅名+楼层 -->
          <view class="info-page">
            <view class="dish__head">
              <view class="dish__head-main">
                <text class="dish__name">{{ dish.name }}</text>
                <text class="dish__stall">{{ dish.stallName }}</text>
              </view>
              <view
                class="like"
                :class="{
                  'like--on': liked,
                  'like--busy': likeBusy || likeCooling,
                  'like--pop': popping,
                }"
                hover-class="like--pressed"
                aria-role="button"
                :aria-label="liked ? '取消点赞' : '点赞'"
                @tap.stop="toggleLike"
              >
                <AppIcon
                  :name="liked ? 'heart-fill' : 'heart'"
                  :size="40"
                  :tone="liked ? 'primary' : 'default'"
                />
                <text class="like__count">{{ likeCooling ? likeLeft : likeCount }}</text>
              </view>
            </view>

            <view class="dish__loc-row">
              <view class="dish__loc-left">
                <text class="dish__canteen">{{ dish.canteenName }}</text>
                <text v-if="dish.floor" class="dish__floor">{{ dish.floor }}</text>
              </view>
              <view
                class="issue-entry"
                aria-role="button"
                aria-label="纠错"
                @tap.stop="openFeedback"
              >
                <AppIcon name="flag" :size="22" tone="muted" />
                <text class="issue-entry__text">纠错</text>
              </view>
            </view>
          </view>

          <!-- 第 2 页（右侧）：反馈按钮 + 返回 -->
          <view class="info-page">
            <view class="feedback-actions">
              <view
                v-for="option in options"
                :key="option.id"
                class="feedback-btn"
                :class="{ 'feedback-btn--cooling': issueCooling }"
                hover-class="feedback-btn--pressed"
                aria-role="button"
                :aria-label="option.name"
                @tap.stop="pickOption(option)"
              >
                <text class="feedback-btn__text">{{ option.name }}</text>
              </view>
            </view>
            <view
              class="feedback-back"
              aria-role="button"
              aria-label="返回菜品信息"
              @tap.stop="resetFeedback"
            >
              <text class="feedback-back__text">返回菜品信息</text>
              <text class="caret-doodle">›</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </BaseSheet>

  <!-- 轻确认卡片：同风格、轻遮罩；提交成功后回到菜品信息页 -->
  <view v-if="confirmOption" class="confirm" @tap="cancelConfirm">
    <view class="confirm__card" @tap.stop>
      <text class="confirm__label">提交反馈</text>
      <text class="confirm__option">{{ confirmOption.name }}</text>
      <view class="confirm__actions">
        <view
          class="confirm__btn"
          hover-class="confirm__btn--pressed"
          aria-role="button"
          aria-label="取消"
          @tap="cancelConfirm"
        >
          <text class="confirm__btn-text">取消</text>
        </view>
        <view
          class="confirm__btn confirm__btn--primary"
          hover-class="confirm__btn--pressed"
          aria-role="button"
          aria-label="提交"
          @tap="confirmSubmit"
        >
          <text class="confirm__btn-text confirm__btn-text--primary">提交</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.dish {
  display: flex;
  flex-direction: column;
}

/* 大图：大圆角 + 极细浅棕描边（不靠重投影） */
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

/* ===== 信息区：两页横向切换（反馈在左、菜品信息在右，默认右页） ===== */
/* 横向切换手势只作用于信息区；touchmove.stop 会阻断该区域纵向滚动 ——
   两页均为短内容，勿把长列表塞进这里 */
.info-viewport {
  overflow: hidden;
  padding: var(--spacing-md) 0 var(--spacing-sm);
}

.info-strip {
  display: flex;
  width: 200%;
  transform: translateX(0);
  transition: transform var(--duration-slow) var(--ease-bounce);
}

.info-strip--feedback {
  transform: translateX(-50%);
}

.info-page {
  display: flex;
  box-sizing: border-box;
  width: 50%;
  flex-direction: column;
  justify-content: center;
}

/* 第一行：菜品名 + 档口名（同为大字，间距分隔，不用 ·） */
.dish__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.dish__head-main {
  display: flex;
  flex: 1;
  align-items: baseline;
  gap: var(--spacing-sm);
  min-width: 0;
}

.dish__name,
.dish__stall,
.dish__canteen,
.dish__floor {
  font-family: var(--font-sans);
  font-size: var(--font-title);
  font-weight: var(--weight-regular);
  line-height: 1.25;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dish__name {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--text-title);
}

.dish__stall {
  flex: 0 1 auto;
  min-width: 0;
  color: var(--text-body);
}

/* 点赞：与菜品名同大字 */
.like {
  display: flex;
  flex-shrink: 0;
  align-items: baseline;
  gap: var(--spacing-xs);
  padding: 0 var(--spacing-2xs);
}

.like--on {
  color: var(--color-primary-text);
}

.like--pressed {
  opacity: 0.72;
}

.like--busy {
  opacity: 0.55;
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
  font-size: var(--font-title);
  line-height: 1;
  color: var(--text-title);
}

.like--on .like__count {
  color: var(--color-primary-text);
}

/* 第二行：餐厅名 + 楼层（同为大字），右侧纠错缩小并右下错开 */
.dish__loc-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
}

.dish__loc-left {
  display: flex;
  flex: 1;
  align-items: baseline;
  gap: var(--spacing-sm);
  min-width: 0;
}

.dish__canteen {
  flex: 0 1 auto;
  min-width: 0;
  color: var(--text-body);
}

.dish__floor {
  flex: 0 0 auto;
  color: var(--text-tertiary);
}

.issue-entry {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--spacing-2xs);
  min-height: calc(var(--tap-target-size) * 0.66);
  margin-right: calc(-1 * var(--spacing-xs));
  padding: 0 var(--spacing-xs);
}

.issue-entry__text {
  font-size: var(--font-subtitle);
  color: var(--text-body);
}

.caret-doodle {
  font-size: var(--font-h3);
  line-height: 1;
  color: var(--text-tertiary);
}

/* ===== 反馈页 ===== */
.feedback-actions {
  display: flex;
  gap: var(--spacing-xs);
}

.feedback-btn {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: var(--tap-target-size);
  border-radius: var(--radius-btn);
  background: var(--color-primary-soft);
}

.feedback-btn--pressed {
  opacity: 0.72;
}

.feedback-btn--cooling {
  opacity: 0.55;
}

.feedback-btn__text {
  font-size: var(--font-subtitle);
  color: var(--text-title);
  text-align: center;
}

.feedback-back {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--tap-target-size);
  /* 无分割线：用间距区分按钮组与返回 */
  margin-top: var(--spacing-sm);
}

.feedback-back__text {
  font-size: var(--font-body);
  color: var(--text-body);
}

/* ===== 轻确认卡片 ===== */
.confirm {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: calc(var(--z-sheet) + 100);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-scrim);
}

.confirm__card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  width: 78%;
  padding: var(--spacing-lg);
  border-radius: var(--radius-card);
  background: var(--overlay-panel-bg);
  box-shadow: var(--shadow-modal);
}

.confirm__label {
  font-size: var(--font-subtitle);
  color: var(--text-tertiary);
}

.confirm__option {
  font-size: var(--font-h3);
  font-weight: var(--weight-medium);
  color: var(--text-title);
}

.confirm__actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-xs);
}

.confirm__btn {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: var(--tap-target-size);
  border-radius: var(--radius-btn);
  background: var(--bg-page);
}

.confirm__btn--primary {
  background: var(--color-primary);
}

.confirm__btn--pressed {
  opacity: 0.8;
}

.confirm__btn-text {
  font-size: var(--font-body);
  color: var(--text-body);
}

.confirm__btn-text--primary {
  color: var(--text-on-primary);
}

/* 🔴 尊重 reduced-motion：横向切换降级为即时 */
@media (prefers-reduced-motion: reduce) {
  .info-strip {
    transition: none;
  }
}
</style>
