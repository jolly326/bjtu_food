<template>
  <view class="page feedback-page">
    <Header title="意见反馈" showBack />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 类型 -->
      <CardSection title="反馈类型">
        <view class="type-row">
          <view
            v-for="t in types"
            :key="t.value"
            class="type-chip"
            :class="{ active: type === t.value }"
            @tap="type = t.value"
          >
            <text class="type-text">{{ t.label }}</text>
          </view>
        </view>
      </CardSection>

      <!-- 内容 -->
      <CardSection title="反馈内容">
        <textarea
          class="content-input"
          v-model="content"
          placeholder="请描述你遇到的问题或建议…"
          maxlength="1000"
          :auto-height="true"
        />
        <text class="counter">{{ content.length }}/1000</text>
      </CardSection>

      <!-- 联系方式 -->
      <CardSection title="联系方式（选填）">
        <input class="contact-input" v-model="contact" placeholder="邮箱 / 微信，方便我们回复你" />
      </CardSection>

      <view style="height: var(--spacing-xl)" />
    </scroll-view>

    <view class="submit-bar">
      <AppButton text="提交反馈" :loading="submitting" @click="submit" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import CardSection from '@/components/CardSection.vue'
import { useUserStore } from '@/stores/user'
import { submitFeedback } from '@/api/feedback'
import type { FeedbackSubmit } from '@/types/feedback'

const userStore = useUserStore()
const types: { value: FeedbackSubmit['type']; label: string }[] = [
  { value: 'suggestion', label: '功能建议' },
  { value: 'error', label: '内容纠错' },
  { value: 'other', label: '其他' },
]
const type = ref<FeedbackSubmit['type']>('suggestion')
const content = ref('')
const contact = ref('')
const submitting = ref(false)

async function submit() {
  if (!userStore.requireAuth()) return
  const text = content.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写反馈内容', icon: 'none' })
    return
  }
  if (text.length > 1000) {
    uni.showToast({ title: '内容不能超过1000字', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitFeedback({ type: type.value, content: text, contact: contact.value.trim() || undefined })
    uni.showToast({ title: '提交成功，感谢反馈', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 600)
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.feedback-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: var(--spacing-md); padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom)); }
.type-row { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.type-chip { padding: var(--spacing-xs) var(--spacing-lg); border-radius: var(--radius-tag); background: var(--bg-soft); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.type-chip:active { transform: scale(var(--press-scale)); }
.type-chip.active { background: var(--color-primary-soft); }
.type-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; }
.type-chip.active .type-text { color: var(--color-primary); }
.content-input { width: 100%; min-height: 220rpx; font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.counter { display: block; text-align: right; font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); }
.contact-input { width: 100%; height: 88rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); box-sizing: border-box; }
.submit-bar { padding: var(--spacing-md); padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
</style>
