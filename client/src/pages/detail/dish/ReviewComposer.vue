<template>
  <!-- 写评价底部抽屉（component-org-sheet-unify 2.4）：骨架统一复用 BaseSheet（遮罩/grabber/下滑关闭/安全区/焦点还原），
       本组件只承载「写评价」表单语义（星级 + 正文 + 提交状态），不再自持第二套 sheet 骨架/CSS。
       头部标题「写评价」由 BaseSheet title 渲染，右上 X 由 closable 提供；菜名作为表单首行置于内容区。
       注意：组件须挂在 scroll-view 之外（小程序 scroll-view 内 fixed 层级会被压扁/裁剪）。 -->
  <BaseSheet
    :visible="visible"
    title="写评价"
    closable
    z-token="--z-actionsheet"
    @close="onClose"
  >
    <view class="rc-body">
      <!-- 菜名副标题：BaseSheet 头部之下、星级之上 -->
      <text class="rc-dish">{{ dishName }}</text>

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
  </BaseSheet>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import BaseSheet from '@/components/BaseSheet.vue'
import IconSvg from '@/components/IconSvg.vue'
import { createReview } from '@/api/review'

const props = defineProps<{
  /** 受控显隐（由 BaseSheet close 驱动父级更新后回写） */
  visible: boolean
  /** 菜品 ID（提交目标） */
  dishId: number
  /** 菜名（标题下展示） */
  dishName: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  /** 提交成功后通知父级刷新（父级重拉评价列表 + 综合评分） */
  (e: 'submitted'): void
}>()

/* 表单状态 */
const rating = ref(0)
const content = ref('')
const submitting = ref(false)

// 每次打开重置表单（BaseSheet 常驻挂载，由 visible 驱动开合）
watch(
  () => props.visible,
  (v) => {
    if (v) {
      rating.value = 0
      content.value = ''
      submitting.value = false
    }
  },
)

/** 用户主动关闭（BaseSheet 遮罩/下滑/右上 X）或提交成功后关闭 */
function onClose() {
  emit('close')
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
    onClose()
  } catch (e: any) {
    uni.showToast({ title: e.message || '发布失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
/* 内容区仅承载表单语义；水平留白 + 底部常规留白（底部安全区由 BaseSheet 根弹层统一提供 env，此处不重复累加） */
.rc-body {
  padding: var(--spacing-sm) var(--spacing-lg) var(--spacing-lg);
}
/* 菜名副标题：次级浅灰小字，单行省略 */
.rc-dish {
  display: block;
  font-size: var(--font-small);
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-top: var(--spacing-2xs);
}

.rc-field { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-sm) 0; }
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
</style>
