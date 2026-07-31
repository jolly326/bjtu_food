<template>
  <view class="page">
    <Header title="联系开发者" show-back />
    <view class="content">
      <view class="section-card">
        <text class="section-title">反馈类型</text>
        <picker :range="typeOptions" @change="onTypeChange">
          <text class="picker-text">{{ typeOptions[selectedType] }}</text>
        </picker>
      </view>
      <view class="section-card">
        <text class="section-title">{{ EMOJI.contact }} 告诉我们你的想法</text>
        <textarea
          v-model="content"
          class="feedback-input"
          placeholder="请描述你的建议或遇到的问题..."
          maxlength="500"
        />
        <text class="char-count">{{ content.length }}/500</text>
      </view>

      <view class="section-card">
        <text class="section-title">{{ EMOJI.email }} 联系方式（选填）</text>
        <input v-model="contact" class="contact-input" placeholder="邮箱/QQ/微信" maxlength="100" />
      </view>

      <view class="submit-wrap">
        <AppButton text="提交反馈" :disabled="!content.trim()" @click="handleSubmit" />
      </view>

      <view v-if="submitted" class="success-msg">
        <text>{{ EMOJI.success }} 感谢你的反馈！我们会尽快查看。</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { EMOJI } from '@/utils/emoji'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import { post } from '@/api/http'

const typeOptions = ['问题反馈', '功能建议', '其他']
const selectedType = ref(0)
const content = ref('')
const contact = ref('')
const submitted = ref(false)

function onTypeChange(e: any) {
  selectedType.value = e.detail.value
}

async function handleSubmit() {
  if (!content.value.trim()) return
  try {
    await post('/feedback', { type: typeOptions[selectedType.value], content: content.value.trim(), contact: contact.value.trim() })
    submitted.value = true
    content.value = ''
    contact.value = ''
  } catch (e: any) {
    uni.showToast({ title: '提交失败，请重试', icon: 'none' })
  }
}
</script>

<style scoped>
.picker-text { font-size: 28rpx; color: var(--text-primary); padding: var(--spacing-xs) 0; display: block; }
</style>

<style scoped>
.page { min-height: 100vh; background: var(--bg-page); }
.content { padding: var(--spacing-md); }
.section-card { background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); margin-bottom: var(--spacing-sm); box-shadow: var(--shadow-card); }
.section-title { display: block; font-size: var(--font-body); font-weight: 900; color: var(--text-primary); margin-bottom: var(--spacing-sm); }
.feedback-input { width: 100%; min-height: 200rpx; font-size: var(--font-small); color: var(--text-primary); padding: var(--spacing-sm); border: 2rpx solid var(--border-light); border-radius: var(--radius-icon); box-sizing: border-box; }
.char-count { display: block; text-align: right; font-size: var(--font-tiny); color: var(--text-tertiary); margin-top: var(--spacing-xs); }
.contact-input { width: 100%; font-size: var(--font-small); padding: var(--spacing-sm); border: 2rpx solid var(--border-light); border-radius: var(--radius-icon); box-sizing: border-box; }
.submit-wrap { padding: var(--spacing-sm) 0; }
.success-msg { text-align: center; padding: var(--spacing-xl); font-size: 28rpx; color: var(--color-success); }
</style>
