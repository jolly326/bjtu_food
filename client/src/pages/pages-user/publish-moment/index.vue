<template>
  <view class="page publish-page" :class="{ 'theme-dark': theme.isDark }">
    <Header :title="isEdit ? '编辑动态' : '发布动态'" @back="backToHome" />
    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 正文 -->
      <view class="block">
        <textarea
          class="content-input"
          v-model="content"
          placeholder="分享你的美食体验、探店灵感…"
          maxlength="500"
          :auto-height="true"
        />
        <text class="counter">{{ content.length }}/500</text>
      </view>

      <!-- 关联对象（可选） -->
      <view class="block">
        <SectionTitle title="关联对象">
          <template #extra><text class="section-sub">选填</text></template>
        </SectionTitle>
        <view class="related-picker" @tap="relatedSheetOpen = true">
          <text class="related-label">{{ relatedLabel }}</text>
          <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" />
        </view>
      </view>

      <!-- 图片上传 -->
      <view class="block">
        <SectionTitle title="图片">
          <template #extra><text class="section-sub">最多 9 张</text></template>
        </SectionTitle>
        <ImageUploader v-model="images" :max="9" />
      </view>

      <view style="height: var(--spacing-xl)" />
    </scroll-view>

    <!-- 底部提交 -->
    <view class="submit-bar">
      <AppButton :text="isEdit ? '保存并重新提交' : '发布'" :loading="submitting" @click="submit" />
    </view>

    <!-- 关联对象选择 Sheet（W5：走正式 API，返回真实 stallId） -->
    <RelatedPickerSheet
      :open="relatedSheetOpen"
      :selected="selectedRelated"
      @close="relatedSheetOpen = false"
      @clear="clearRelated"
      @select="onRelatedSelect"
      @confirm="onRelatedConfirm"
    />

    <!-- 认证弹层（未登录发布 requireAuth 统一在此弹出） -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import type { Moment, RelatedType } from '@/types/moment'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import RelatedPickerSheet from '@/components/RelatedPickerSheet.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import type { RelatedItem } from '@/components/RelatedPickerSheet.vue'

const theme = useThemeStore()
const userStore = useUserStore()
const content = ref('')
const images = ref<string[]>([])
const submitting = ref(false)
const editId = ref<number | null>(null)
const isEdit = computed(() => editId.value != null)

// 关联对象（由 RelatedPickerSheet 走正式 API 返回真实 id）
const relatedSheetOpen = ref(false)
const selectedRelated = ref<RelatedItem | null>(null)

const relatedLabel = computed(() => {
  if (!selectedRelated.value) return '不关联（自由动态）'
  const prefix = selectedRelated.value.type === 'dish' ? '菜品' : '档口'
  return `${prefix}·${selectedRelated.value.name}`
})

function onRelatedSelect(item: RelatedItem) {
  // 二次点击同一项取消关联（toggle）
  if (selectedRelated.value && selectedRelated.value.id === item.id && selectedRelated.value.type === item.type) {
    selectedRelated.value = null
  } else {
    selectedRelated.value = item
  }
}

function clearRelated() {
  selectedRelated.value = null
  relatedSheetOpen.value = false
}

/** 确定：组件回传当前选中项（selected），关闭弹层 */
function onRelatedConfirm(item: RelatedItem | null) {
  selectedRelated.value = item
  relatedSheetOpen.value = false
}

async function submit() {
  if (!userStore.requireAuth()) return
  const text = content.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写动态内容', icon: 'none' })
    return
  }
  if (text.length > 500) {
    uni.showToast({ title: '内容不能超过500字', icon: 'none' })
    return
  }
  submitting.value = true
  const relatedType: RelatedType = selectedRelated.value ? selectedRelated.value.type : 'none'
  const payload = {
    content: text,
    images: images.value,
    relatedType,
    relatedId: selectedRelated.value ? selectedRelated.value.id : null,
  }
  try {
    if (isEdit.value && editId.value != null) {
      await momentApi.updateMoment(editId.value, payload)
    } else {
      await momentApi.publishMoment(payload)
    }
    uni.showToast({ title: isEdit.value ? '已重新提交审核' : '发布成功，审核中', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 600)
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad(async (query) => {
  if (query?.id) {
    editId.value = Number(query.id)
    try {
      const m: Moment = await momentApi.getMomentDetail(Number(query.id))
      content.value = m.content
      images.value = [...m.images]
      if (m.relatedType && m.relatedType !== 'none' && m.relatedId) {
        selectedRelated.value = { id: m.relatedId, name: m.relatedName || '', image: '', type: m.relatedType as 'dish' | 'stall' }
      }
    } catch {
      uni.showToast({ title: '加载动态失败', icon: 'none' })
    }
  }
})
</script>

<style scoped>
.publish-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: var(--spacing-md); padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom)); }
.block { background: var(--bg-card); padding: var(--spacing-md); margin: 0 var(--spacing-md) var(--spacing-md); box-shadow: var(--shadow-card); border-radius: var(--radius-card); }
/* 正文输入：与写评价/反馈弹窗 textarea 同款（bg-page 浅底 + radius-card + 无边框） */
.content-input { width: 100%; min-height: 220rpx; font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; padding: var(--spacing-sm); background: var(--bg-page); border-radius: var(--radius-card); border: none; box-sizing: border-box; }
.counter { display: block; text-align: right; font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }
.section-sub { font-size: var(--font-aux); color: var(--text-tertiary); margin-left: var(--spacing-xs); }
.related-picker { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-sm) var(--spacing-md); background: var(--bg-soft); border-radius: var(--radius-tag); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.related-picker:active { transform: scale(var(--press-scale)); }
.related-label { font-size: var(--font-body); color: var(--text-secondary); }
.related-arrow { font-size: 28rpx; color: var(--text-tertiary); }
.submit-bar { padding: var(--spacing-md); padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
</style>
