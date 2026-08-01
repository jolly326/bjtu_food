<template>
  <view class="feedback-form">
    <CardSection>
      <SectionTitle title="反馈类型" />
      <picker :range="typeOptions" @change="onTypeChange">
        <view class="picker-row">
          <text class="picker-text">{{ typeOptions[selectedType] }}</text>
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" />
        </view>
      </picker>
    </CardSection>

    <CardSection>
      <SectionTitle title="告诉我们你的想法">
        <template #extra>
          <IconSvg name="contact" :size="28" color="var(--text-primary)" />
        </template>
      </SectionTitle>
      <textarea
        v-model="content"
        class="feedback-input"
        placeholder="请描述你的建议或遇到的问题..."
        maxlength="500"
      />
      <text class="char-count">{{ content.length }}/500</text>
    </CardSection>

    <CardSection>
      <SectionTitle title="联系方式（选填）">
        <template #extra>
          <IconSvg name="mail" :size="28" color="var(--text-primary)" />
        </template>
      </SectionTitle>
      <input v-model="contact" class="contact-input" placeholder="邮箱/QQ/微信" maxlength="100" />
    </CardSection>

    <view class="submit-wrap">
      <AppButton text="提交反馈" :disabled="!content.trim()" :loading="submitting" @click="handleSubmit" />
    </view>

    <view v-if="submitted" class="success-msg">
      <IconSvg name="check" :size="32" color="var(--color-success)" />
      <text class="success-text"> 感谢你的反馈！我们会尽快查看。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CardSection from '@/components/CardSection.vue'
import AppButton from '@/components/AppButton.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import IconSvg from '@/components/IconSvg.vue'
import { submitFeedback } from '@/api/feedback'

const typeOptions = ['问题反馈', '功能建议', '其他']
const typeValues = ['error', 'suggestion', 'other'] as const
const selectedType = ref(0)
const content = ref('')
const contact = ref('')
const submitting = ref(false)
const submitted = ref(false)

function onTypeChange(e: any) {
  selectedType.value = e.detail.value
}

async function handleSubmit() {
  if (!content.value.trim() || submitting.value) return
  submitting.value = true
  try {
    await submitFeedback({
      type: typeValues[selectedType.value] ?? 'other',
      content: content.value.trim(),
      contact: contact.value.trim(),
    })
    submitted.value = true
    content.value = ''
    contact.value = ''
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败，请重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.feedback-form { padding: var(--spacing-md); }
.picker-row { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-xs) 0; }
.picker-text { font-size: var(--font-body); color: var(--text-primary); }
.feedback-input { width: 100%; min-height: 200rpx; font-size: var(--font-small); color: var(--text-primary); padding: var(--spacing-sm); border: 2rpx solid var(--border-light); border-radius: var(--radius-icon); box-sizing: border-box; }
.char-count { display: block; text-align: right; font-size: var(--font-tiny); color: var(--text-tertiary); margin-top: var(--spacing-xs); }
.contact-input { width: 100%; font-size: var(--font-small); padding: var(--spacing-sm); border: 2rpx solid var(--border-light); border-radius: var(--radius-icon); box-sizing: border-box; }
.submit-wrap { padding: var(--spacing-sm) 0; }
.success-msg { display: flex; align-items: center; justify-content: center; padding: var(--spacing-xl); font-size: 28rpx; color: var(--color-success); }
.success-text { color: var(--color-success); }
</style>
