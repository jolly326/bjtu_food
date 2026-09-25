<template>
  <!-- 写评价 / 重新评价底部抽屉（component-org-sheet-unify 2.4）：骨架统一复用 BaseSheet（遮罩/grabber/下滑关闭/安全区/焦点还原），
       本组件只承载表单语义（星级 + 正文 + 配图 + 提交状态），不再自持第二套 sheet 骨架/CSS。
       头部标题由 BaseSheet title 渲染（写评价 / 重新评价），右上 X 由 closable 提供；菜名作为表单首行置于内容区。
       注意：组件须挂在 scroll-view 之外（小程序 scroll-view 内 fixed 层级会被压扁/裁剪）。
       小屏适配（评审 B1-①）：BaseSheet 传 scroll-body 走 scroll-view 分支，内容超 88vh 时内部滚动，提交钮始终可达。
       重新评价：传 `reviewId` + `prefill` 时进入覆盖式重评（PUT /reviews/{id}），表单预填旧评分/文字/配图。 -->
  <BaseSheet
    :visible="visible"
    :title="isEdit ? '重新评价' : '写评价'"
    closable
    scroll-body
    z-token="--z-actionsheet"
    @close="onClose"
  >
    <view class="rc-body">
      <!-- 菜名副标题：BaseSheet 头部之下、星级之上 -->
      <text class="rc-dish">{{ dishName }}</text>

      <!-- 重评提示：判定为已评价（重评模式）时明示覆盖语义，避免用户误以为在发新评价 -->
      <text v-if="isEdit" class="rc-overwrite-tip">你已评价过此菜，本次提交将覆盖原评价</text>

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
              :color="i <= rating ? COLOR_MAP['star'] : COLOR_MAP['star-empty']"
            />
          </view>
        </view>
        <text class="rc-star-tip">{{ rating > 0 ? `已评 ${rating} 星` : '点击星星打分（必填）' }}</text>
      </view>

      <!-- 正文（选填 ≤500）：与写评价/反馈弹窗 textarea 同款浅底圆角无边框；
           cursor-spacing 40 对齐全站输入语言（评审 B1-③/M3），键盘弹起不贴输入框 -->
      <view class="rc-input-wrap">
        <textarea
          class="rc-input"
          v-model="content"
          maxlength="500"
          auto-height
          :cursor-spacing="40"
          placeholder="说说味道、分量、性价比…（选填）"
          placeholder-class="rc-ph"
          :disabled="submitting"
        />
        <view class="rc-count">{{ content.length }}/500</view>
      </view>

      <!-- 配图（选填 ≤3 张）：统一 ImagePicker（安检上传）；提交中禁选 -->
      <view class="rc-field-images">
        <ImagePicker v-model="images" :max="3" :disabled="submitting" />
      </view>

      <!-- 提交：主色实底；未选星或提交中禁用 -->
      <view
        class="rc-submit"
        :class="{ disabled: !rating || submitting }"
        role="button"
        :aria-disabled="(!rating || submitting) ? 'true' : 'false'"
        @tap="onSubmit"
      >
        <text class="rc-submit-text">{{ submitting ? '提交中…' : submitText }}</text>
      </view>
    </view>
  </BaseSheet>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import BaseSheet from '@/components/BaseSheet.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImagePicker from '@/components/ImagePicker.vue'
// 星色须传**实色**：IconSvg 的 color 不解析 var()（data-uri 内为字面量），传 var(...) 恒落近黑
import { COLOR_MAP } from '@/theme/tokens'
import { createReview, updateReview } from '@/api/review'
// 提交成功载荷类型唯一声明处为 types/review.ts（与 useDishPage.onReviewSubmitted 共用，避免重复声明）
import type { ReviewSubmittedPayload } from '@/types/review'

/** 重评预填值（来自「我的评价」GET /my/reviews?dishId=） */
interface ReviewPrefill {
  rating: number
  content: string
  images: string[]
}

const props = withDefaults(defineProps<{
  /** 受控显隐（由 BaseSheet close 驱动父级更新后回写） */
  visible: boolean
  /** 菜品 ID（首次发表提交目标） */
  dishId: number
  /** 菜名（标题下展示） */
  dishName: string
  /** 重评目标评价 ID；非空时走覆盖式 PUT /reviews/{id} */
  reviewId?: number | null
  /** 重评预填（评分 / 文字 / 配图）；未评价时为空 */
  prefill?: ReviewPrefill | null
}>(), {
  reviewId: null,
  prefill: null,
})

const emit = defineEmits<{
  (e: 'close'): void
  /** 提交成功后通知父级刷新（父级重拉评价列表 + 综合评分 + 本地写回「我的评价」态） */
  (e: 'submitted', payload: ReviewSubmittedPayload): void
}>()

/* 表单状态 */
const rating = ref(0)
const content = ref('')
/** 配图（COS URL，≤3 张；经 ImagePicker 安检上传） */
const images = ref<string[]>([])
const submitting = ref(false)

const isEdit = computed(() => props.reviewId != null)
const submitText = computed(() => (isEdit.value ? '保存修改' : '发布评价'))

// 每次打开重置表单：重评态用 prefill 预填旧值，首次发表态清空
watch(
  () => props.visible,
  (v) => {
    if (v) {
      rating.value = props.prefill?.rating ?? 0
      content.value = props.prefill?.content ?? ''
      images.value = props.prefill?.images ? [...props.prefill.images] : []
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
    const payload = {
      rating: rating.value,
      content: content.value.trim() || undefined,
      // 配图（≤3 张 COS URL）；违规文本/图片后端 400 message 经此处 toast 直透
      images: images.value.length ? [...images.value] : undefined,
    }
    let submittedReviewId: number
    if (props.reviewId != null) {
      await updateReview(props.reviewId, payload)
      submittedReviewId = props.reviewId
      uni.showToast({ title: '已更新评价', icon: 'success' })
    } else {
      // 首次发表：POST 出参返回新评价 ID，随载荷上抛（父级本地写回底栏态，无须回读接口）
      submittedReviewId = await createReview(props.dishId, payload)
      uni.showToast({ title: '评价成功', icon: 'success' })
    }
    emit('submitted', {
      mode: props.reviewId != null ? 'update' : 'create',
      reviewId: submittedReviewId,
      rating: rating.value,
      content: content.value.trim(),
      images: images.value.length ? [...images.value] : [],
    })
    onClose()
  } catch (e: any) {
    uni.showToast({ title: e.message || '发布失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
/* 内容区仅承载表单语义（评审 B1-②）：BaseSheet scroll-body 模式已带
   padding: md lg (lg+safe-area) 四周留白，此处不再叠加横向/底部 padding 避免双重缩进，
   仅补顶部少量间距（菜名与头部之间） */
.rc-body {
  padding-top: var(--spacing-2xs);
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
/* 重评覆盖提示：浅底圆角条，明示「覆盖原评价」语义（仅重评模式呈现） */
.rc-overwrite-tip {
  display: block;
  margin-top: var(--spacing-sm);
  padding: var(--spacing-2xs) var(--spacing-sm);
  font-size: var(--font-small);
  color: var(--text-body);
  background: var(--bg-soft);
  border-radius: 8rpx;
}

.rc-field { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-sm) 0; }
.rc-stars { display: flex; align-items: center; }
/* 单星命中区 ≥88rpx：56rpx 图标 + 上下/side --spacing-sm(16rpx) 内边距 = 88×88rpx（视觉尺寸不变） */
.rc-star { padding: var(--spacing-sm); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.rc-star:active { opacity: 0.6; }
.rc-star-tip { font-size: var(--font-small); color: var(--text-tertiary); }

.rc-input-wrap { position: relative; }
/* auto-height 上限 320rpx（评审 B1-③）：长文不再无限撑高，超出由 BaseSheet scroll-body 滚动承接 */
.rc-input { width: 100%; box-sizing: border-box; min-height: 160rpx; max-height: 320rpx; padding: var(--spacing-sm) var(--spacing-md) var(--spacing-lg); background: var(--bg-input); border-radius: var(--radius-card); font-size: var(--font-body); color: var(--text-primary); line-height: 1.5; }
.rc-ph { color: var(--text-hint); }
.rc-count { position: absolute; right: var(--spacing-sm); bottom: var(--spacing-sm); font-size: var(--font-aux); color: var(--text-tertiary); }

/* 配图区：正文与提交之间留档位间距（ImagePicker 自身网格） */
.rc-field-images { margin-top: var(--spacing-md); }

/* 圆角统一到全局主按钮档位 token（--radius-btn，与 AppButton 一致），不再裸 24rpx */
.rc-submit { display: flex; align-items: center; justify-content: center; height: 88rpx; margin-top: var(--spacing-lg); border-radius: var(--radius-btn); background: var(--color-primary); box-shadow: var(--shadow-float); -webkit-tap-highlight-color: transparent; }
.rc-submit.disabled { opacity: 0.5; }
.rc-submit-text { font-size: var(--font-subtitle); font-weight: var(--weight-medium); color: var(--color-on-primary); }
</style>
