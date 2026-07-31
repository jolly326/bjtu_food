<template>
  <view class="page publish-page">
    <Header :title="isEdit ? '编辑动态' : '发布动态'" showBack />
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

      <!-- 图片上传 -->
      <view class="block">
        <view class="section-head">
          <text class="section-title">图片</text>
          <text class="section-sub">最多 9 张</text>
        </view>
        <view class="img-grid">
          <view v-for="(img, idx) in images" :key="idx" class="img-cell">
            <image class="img-thumb" :src="img" mode="aspectFill" />
            <view class="img-remove" @tap="removeImage(idx)"><text class="img-remove-text">✕</text></view>
          </view>
          <view v-if="images.length < 9" class="img-cell img-add" @tap="chooseImage">
            <text class="img-add-icon">{{ EMOJI.plus }}</text>
          </view>
        </view>
      </view>

      <!-- 关联对象（可选） -->
      <view class="block">
        <view class="section-head">
          <text class="section-title">关联对象</text>
          <text class="section-sub">选填</text>
        </view>
        <view class="related-picker" @tap="openRelatedSheet">
          <text class="related-label">{{ relatedLabel }}</text>
          <text class="related-arrow">{{ EMOJI.arrowRight }}</text>
        </view>
      </view>

      <view style="height: var(--spacing-xl)" />
    </scroll-view>

    <!-- 底部提交 -->
    <view class="submit-bar">
      <AppButton :text="isEdit ? '保存并重新提交' : '发布'" :loading="submitting" @click="submit" />
    </view>

    <!-- 关联对象选择 Sheet -->
    <view v-if="relatedSheetOpen" class="sheet-mask" @tap="relatedSheetOpen = false" />
    <view class="related-sheet" :class="{ open: relatedSheetOpen }">
      <view class="sheet-head">
        <text class="sheet-title">选择关联对象</text>
        <text class="sheet-close" @tap="relatedSheetOpen = false">✕</text>
      </view>
      <view class="sheet-tabs">
        <view class="sheet-tab" :class="{ active: relatedTab === 'dish' }" @tap="relatedTab = 'dish'">菜品</view>
        <view class="sheet-tab" :class="{ active: relatedTab === 'stall' }" @tap="relatedTab = 'stall'">档口</view>
      </view>
      <view class="sheet-search">
        <text class="sheet-search-icon">{{ EMOJI.search }}</text>
        <input class="sheet-search-input" v-model="relatedKeyword" :placeholder="relatedTab === 'dish' ? '搜索菜品' : '搜索档口'" @input="onRelatedKeyword" />
      </view>
      <scroll-view class="sheet-list" scroll-y>
        <view v-if="relatedCandidates.length === 0" class="sheet-empty">
          <text class="sheet-empty-text">{{ relatedKeyword ? '没有找到相关结果' : '输入关键词搜索' }}</text>
        </view>
        <view
          v-for="item in relatedCandidates"
          :key="item.id"
          class="sheet-item"
          @tap="selectRelated(item)"
        >
          <image v-if="item.image" class="sheet-item-img" :src="item.image" mode="aspectFill" />
          <view v-else class="sheet-item-img sheet-item-img-empty"><text class="sheet-item-fallback">{{ EMOJI.dishPlaceholder }}</text></view>
          <text class="sheet-item-name">{{ item.name }}</text>
          <text class="sheet-item-check" v-if="selectedRelated && selectedRelated.id === item.id && selectedRelated.type === relatedTab">✓</text>
        </view>
      </scroll-view>
      <view class="sheet-footer">
        <view class="sheet-clear" @tap="clearRelated">不关联</view>
        <view class="sheet-confirm" @tap="relatedSheetOpen = false">确定</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import { EMOJI } from '@/utils/emoji'
import { uploadImage } from '@/api/upload'
import { getImageUrl, toAbsoluteImageUrl } from '@/utils/image'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import * as dishApi from '@/api/dish'
import type { Moment } from '@/types/moment'

const userStore = useUserStore()
const content = ref('')
const images = ref<string[]>([])
const submitting = ref(false)
const editId = ref<number | null>(null)
const isEdit = computed(() => editId.value != null)

// 关联对象
const relatedSheetOpen = ref(false)
const relatedTab = ref<'dish' | 'stall'>('dish')
const relatedKeyword = ref('')
const relatedCandidates = ref<{ id: number; name: string; image: string; type: 'dish' | 'stall' }[]>([])
const selectedRelated = ref<{ id: number; name: string; type: 'dish' | 'stall' } | null>(null)

const relatedLabel = computed(() => {
  if (!selectedRelated.value) return '不关联（自由动态）'
  const prefix = selectedRelated.value.type === 'dish' ? '菜品' : '档口'
  return `${prefix}·${selectedRelated.value.name}`
})

let relatedTimer: ReturnType<typeof setTimeout> | null = null

function onRelatedKeyword() {
  if (relatedTimer) clearTimeout(relatedTimer)
  relatedTimer = setTimeout(async () => {
    await searchRelated()
  }, 300)
}

async function searchRelated() {
  relatedCandidates.value = []
  const kw = relatedKeyword.value.trim()
  try {
    if (relatedTab.value === 'dish') {
      const res = await dishApi.searchDishesPage({ keyword: kw, page: 1, pageSize: 10 })
      relatedCandidates.value = res.list.map(d => ({ id: d.id, name: d.name, image: getImageUrl(d.image), type: 'dish' as const }))
    } else {
      const res = await dishApi.searchDishesPage({ keyword: kw, page: 1, pageSize: 10 })
      // 档口名联想：以 keyword 搜档口（后端 /dishes 按 keyword 模糊匹配 name/stall）
      const stallNames = new Set<string>()
      const stalls = res.list
        .filter(d => d.stallName && !stallNames.has(d.stallName))
        .map(d => { stallNames.add(d.stallName); return { id: Number(d.id), name: d.stallName!, image: getImageUrl(d.image), type: 'stall' as const } })
      relatedCandidates.value = stalls
    }
  } catch {
    relatedCandidates.value = []
  }
}

function openRelatedSheet() {
  relatedSheetOpen.value = true
  searchRelated()
}

function selectRelated(item: { id: number; name: string; image: string; type: 'dish' | 'stall' }) {
  if (selectedRelated.value && selectedRelated.value.id === item.id && selectedRelated.value.type === item.type) {
    selectedRelated.value = null
  } else {
    selectedRelated.value = { id: item.id, name: item.name, type: item.type }
  }
}

function clearRelated() {
  selectedRelated.value = null
  relatedSheetOpen.value = false
}

function chooseImage() {
  const remain = 9 - images.value.length
  if (remain <= 0) return
  uni.chooseMedia({
    count: remain,
    mediaType: ['image'],
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      for (const f of res.tempFiles) {
        try {
          const url = await uploadImage(f.tempFilePath)
          images.value.push(url)
        } catch {
          uni.showToast({ title: '图片上传失败', icon: 'none' })
        }
      }
    },
  })
}

function removeImage(idx: number) {
  images.value.splice(idx, 1)
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
  const payload = {
    content: text,
    images: images.value,
    relatedType: selectedRelated.value ? selectedRelated.value.type : 'none',
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
        selectedRelated.value = { id: m.relatedId, name: m.relatedName || '', type: m.relatedType as 'dish' | 'stall' }
        relatedTab.value = m.relatedType as 'dish' | 'stall'
      }
    } catch {
      uni.showToast({ title: '加载动态失败', icon: 'none' })
    }
  }
})
</script>

<style scoped>
.publish-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.block { background: var(--bg-card); padding: var(--spacing-md); margin-bottom: var(--spacing-md); }
.content-input { width: 100%; min-height: 220rpx; font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.counter { display: block; text-align: right; font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--spacing-sm); }
.section-title { font-size: var(--font-body); font-weight: 700; color: var(--text-primary); }
.section-sub { font-size: var(--font-aux); color: var(--text-tertiary); }
.img-grid { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.img-cell { width: 200rpx; height: 200rpx; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); position: relative; flex-shrink: 0; }
.img-thumb { width: 100%; height: 100%; }
.img-remove { position: absolute; top: 4rpx; right: 4rpx; width: 40rpx; height: 40rpx; border-radius: 50%; background: var(--badge-dark-bg); display: flex; align-items: center; justify-content: center; }
.img-remove-text { font-size: 24rpx; line-height: 1; color: var(--badge-dark-text); }
.img-add { display: flex; align-items: center; justify-content: center; border: 2rpx dashed var(--border-bold); background: var(--bg-soft); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.img-add:active { transform: scale(0.97); }
.img-add-icon { font-size: 60rpx; line-height: 1; color: var(--text-tertiary); }
.related-picker { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-sm) var(--spacing-md); background: var(--bg-soft); border-radius: var(--radius-tag); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.related-picker:active { transform: scale(0.97); }
.related-label { font-size: var(--font-body); color: var(--text-secondary); }
.related-arrow { font-size: 28rpx; color: var(--text-tertiary); }
.submit-bar { padding: var(--spacing-md); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }

/* 关联 Sheet */
.sheet-mask { position: fixed; inset: 0; background: var(--overlay-scrim); z-index: 90; }
.related-sheet { position: fixed; left: 0; right: 0; bottom: 0; background: var(--bg-card); border-radius: var(--radius-modal) var(--radius-modal) 0 0; box-shadow: var(--shadow-modal); z-index: 100; transform: translateY(100%); transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); display: flex; flex-direction: column; max-height: 80vh; padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom)); }
.related-sheet.open { transform: translateY(0); }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.sheet-close { font-size: var(--font-body); color: var(--text-tertiary); padding: 0 var(--spacing-xs); }
.sheet-tabs { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md) var(--spacing-md) 0; }
.sheet-tab { padding: var(--spacing-xs) var(--spacing-lg); border-radius: var(--radius-tag); background: var(--bg-soft); font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; }
.sheet-tab.active { background: var(--color-primary-soft); color: var(--color-primary); }
.sheet-search { display: flex; align-items: center; gap: var(--spacing-sm); margin: var(--spacing-md); padding: var(--spacing-xs) var(--spacing-md); background: var(--bg-soft); border-radius: var(--radius-btn); }
.sheet-search-icon { font-size: 28rpx; line-height: 1; }
.sheet-search-input { flex: 1; font-size: var(--font-body); color: var(--text-primary); }
.sheet-list { flex: 1; overflow-y: auto; padding: 0 var(--spacing-md); }
.sheet-empty { padding: var(--spacing-xl) 0; text-align: center; }
.sheet-empty-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.sheet-item { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.sheet-item:active { transform: scale(0.97); }
.sheet-item-img { width: 72rpx; height: 72rpx; border-radius: var(--radius-tag); background: var(--bg-page); flex-shrink: 0; }
.sheet-item-img-empty { display: flex; align-items: center; justify-content: center; }
.sheet-item-fallback { font-size: 36rpx; line-height: 1; }
.sheet-item-name { flex: 1; font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sheet-item-check { font-size: var(--font-body); color: var(--color-primary); font-weight: 800; }
.sheet-footer { display: flex; gap: var(--spacing-md); padding: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.sheet-clear { flex: 1; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--bg-soft); color: var(--text-secondary); font-weight: 600; -webkit-tap-highlight-color: transparent; }
.sheet-confirm { flex: 2; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--color-primary); color: var(--text-white); font-weight: 700; -webkit-tap-highlight-color: transparent; }

@media (prefers-reduced-motion: reduce) {
  .related-sheet { transition: opacity 0.2s ease; }
}
</style>
