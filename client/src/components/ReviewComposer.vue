<template>
  <!-- 写评价底部抽屉（dish-detail-visual-polish 扩展：底栏「写评价」入口的真实功能）。
       与 ReviewActionSheet / AuthSheet 同一底部抽屉范式：遮罩淡入 + grabber + 右上角关闭 + 底部安全区。
       注意：组件须挂在 scroll-view 之外（小程序 scroll-view 内 fixed 层级会被压扁/裁剪）。 -->
  <view v-if="open" class="rc-root">
    <view class="rc-mask" :class="{ show: maskShow }" @tap="close" @touchmove.stop.prevent="noop" />
    <view class="rc-sheet" :class="{ open: sheetOpen }">
      <view class="rc-grabber" />
      <!-- 头部：标题 + 菜名 + 右上角关闭 -->
      <view class="rc-head">
        <view class="rc-head-titles">
          <view class="rc-title">写评价</view>
          <text class="rc-sub">{{ dishName }}</text>
        </view>
        <view class="rc-close" role="button" aria-label="关闭" @tap.stop="close">
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
        </view>
      </view>

      <!-- 星级：1-5 必填；未选 outline 浅灰、已选填充主色 -->
      <view class="rc-field">
        <view class="rc-stars">
          <view
            v-for="i in 5"
            :key="i"
            class="rc-star"
            role="button"
            :aria-label="`${i} 星`"
            :aria-checked="rating === i ? 'true' : 'false'"
            @tap="rating = i"
          >
            <IconSvg
              :name="i <= rating ? 'star-filled' : 'star'"
              :size="56"
              :color="i <= rating ? 'var(--color-primary)' : 'var(--border-bold)'"
            />
          </view>
        </view>
        <text class="rc-star-tip">{{ rating > 0 ? `已评 ${rating} 星` : '点击星星打分（必填）' }}</text>
      </view>

      <!-- 正文（选填 ≤500）：与写评价/反馈弹窗 textarea 同款浅底圆角无边框 -->
      <view class="rc-input-wrap">
        <textarea
          class="rc-input"
          v-model="content"
          maxlength="500"
          auto-height
          placeholder="说说味道、分量、性价比…（选填）"
          placeholder-class="rc-ph"
          :disabled="submitting"
        />
        <view class="rc-count">{{ content.length }}/500</view>
      </view>

      <!-- 提交：主色实底；未选星或提交中禁用 -->
      <view
        class="rc-submit"
        :class="{ disabled: !rating || submitting }"
        role="button"
        :aria-disabled="(!rating || submitting) ? 'true' : 'false'"
        @tap="onSubmit"
      >
        <text class="rc-submit-text">{{ submitting ? '提交中…' : '发布评价' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import IconSvg from './IconSvg.vue'
import { createReview } from '@/api/review'

const props = defineProps<{
  open: boolean
  /** 菜品 ID（提交目标） */
  dishId: number
  /** 菜名（标题下展示） */
  dishName: string
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  /** 提交成功后通知父级刷新（父级重拉评价列表 + 综合评分） */
  (e: 'submitted'): void
}>()

/** 空处理器：遮罩 touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}

/* 开合动画（与 ReviewActionSheet 一致：遮罩淡入 + 抽屉上滑） */
const maskShow = ref(false)
const sheetOpen = ref(false)
watch(() => props.open, (v) => {
  if (v) {
    // 每次打开重置表单
    rating.value = 0
    content.value = ''
    submitting.value = false
    nextTick(() => {
      maskShow.value = true
      sheetOpen.value = true
    })
  } else {
    maskShow.value = false
    sheetOpen.value = false
  }
})

/* 表单状态 */
const rating = ref(0)
const content = ref('')
const submitting = ref(false)

function close() {
  emit('update:open', false)
}

async function onSubmit() {
  if (submitting.value) return
  if (rating.value < 1) {
    uni.showToast({ title: '请先选择评分', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await createReview({
      dishId: props.dishId,
      rating: rating.value,
      content: content.value.trim() || undefined,
    })
    uni.showToast({ title: '评价成功', icon: 'success' })
    emit('submitted')
    close()
  } catch (e: any) {
    uni.showToast({ title: e.message || '发布失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.rc-root { z-index: var(--z-actionsheet); }
.rc-mask {
  position: fixed;
  inset: 0;
  background: var(--overlay-scrim);
  opacity: 0;
  transition: opacity var(--duration-slow) var(--ease-out);
  z-index: calc(var(--z-actionsheet) - 10);
}
.rc-mask.show { opacity: 1; }
.rc-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: var(--z-actionsheet);
  transform: translateY(100%);
  padding: var(--spacing-sm) var(--spacing-lg) calc(var(--spacing-lg) + env(safe-area-inset-bottom));
  will-change: transform;
}
.rc-sheet.open { transform: translateY(0); }
.rc-grabber { width: 72rpx; height: 8rpx; border-radius: var(--radius-pill); background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.rc-head { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); padding: var(--spacing-sm) 0 var(--spacing-md); }
.rc-head-titles { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.rc-title { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); }
.rc-sub { font-size: var(--font-small); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rc-close { padding: 0 var(--spacing-xs); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.rc-close:active { opacity: 0.5; }

.rc-field { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-xs) 0 var(--spacing-sm); }
.rc-stars { display: flex; align-items: center; }
.rc-star { padding: 0 var(--spacing-2xs); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.rc-star:active { opacity: 0.6; }
.rc-star-tip { font-size: var(--font-small); color: var(--text-tertiary); }

.rc-input-wrap { position: relative; }
.rc-input { width: 100%; box-sizing: border-box; min-height: 160rpx; padding: var(--spacing-sm) var(--spacing-md) var(--spacing-lg); background: var(--bg-input); border-radius: var(--radius-card); font-size: var(--font-body); color: var(--text-primary); line-height: 1.5; }
.rc-ph { color: var(--text-hint); }
.rc-count { position: absolute; right: var(--spacing-sm); bottom: var(--spacing-sm); font-size: var(--font-aux); color: var(--text-tertiary); }

.rc-submit { display: flex; align-items: center; justify-content: center; height: 88rpx; margin-top: var(--spacing-lg); border-radius: 24rpx; background: var(--color-primary); box-shadow: var(--shadow-float); -webkit-tap-highlight-color: transparent; }
.rc-submit.disabled { opacity: 0.5; }
.rc-submit-text { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--color-on-primary); }

@media (prefers-reduced-motion: reduce) {
  .rc-mask { transition: opacity 0.2s ease; }
  .rc-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
