<template>
  <!-- 动态主卡（合并卡）：发布者 + 正文 + 九宫格 + 关联对象 + 互动 全部一张卡 -->
  <view class="m-card">
    <view class="m-head">
      <image v-if="moment.userAvatar" class="m-avatar" :src="getImageUrl(moment.userAvatar)" mode="aspectFill" />
      <view v-else class="m-avatar m-avatar-empty">
        <IconSvg name="user" :size="36" color="var(--text-tertiary)" />
      </view>
      <view class="m-head-right">
        <text class="m-nickname">{{ moment.userNickname || '匿名用户' }}</text>
        <text class="m-time">{{ formatDateTime(moment.createdAt) }}</text>
      </view>
      <view v-if="isAuthor && moment.auditStatus && moment.auditStatus !== 'approved'" class="m-audit" :class="auditClass">
        <text class="m-audit-text">{{ auditLabel }}</text>
      </view>
    </view>

    <text class="m-content">{{ moment.content }}</text>

    <!-- 九宫格大图（内联，detail-modular-review-cleanup：MomentImageGrid 不再独立文件） -->
    <view v-if="moment.images && moment.images.length" class="m-images">
      <view
        v-for="(img, idx) in moment.images"
        :key="idx"
        class="m-image-wrap"
        @tap="previewImage(idx)"
      >
        <image
          class="m-image"
          :class="{ loaded: loadedSet.has(idx) }"
          :src="getImageUrl(getThumbUrl(img))"
          mode="aspectFill"
          lazy-load
          @load="loadedSet.add(idx)"
        />
      </view>
    </view>

    <!-- 关联对象 -->
    <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="related-card" @tap="emit('related')">
      <image v-if="moment.relatedImage" class="related-thumb" :src="getImageUrl(moment.relatedImage)" mode="aspectFill" lazy-load />
      <view v-else class="related-thumb related-thumb--empty">
        <IconSvg :name="relatedIconName" :size="28" color="var(--text-tertiary)" class="related-icon" />
      </view>
      <view class="related-body">
        <text class="related-type">{{ relatedTypeLabel }}</text>
        <view class="related-name-row">
          <text class="related-name">{{ moment.relatedName }}</text>
        </view>
      </view>
      <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="related-arrow" />
    </view>

    <!-- 互动区 -->
    <InteractBar
      :comment-count="moment.commentCount"
      :useful-count="moment.usefulCount"
      :useful-active="!!moment.useful"
      :useful-pending="usefulPending"
      @useful="emit('useful')"
      @comment="emit('comment')"
      @report="emit('report')"
    />
  </view>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import type { Moment } from '@/types/moment'
import IconSvg from '@/components/IconSvg.vue'
import InteractBar from './InteractBar.vue'
import { formatDateTime } from '@/utils/time'
import { getImageUrl, getThumbUrl, previewImages } from '@/utils/image'

const props = defineProps<{
  moment: Moment
  isAuthor: boolean
  usefulPending?: boolean
}>()

const emit = defineEmits<{
  (e: 'useful'): void
  (e: 'comment'): void
  (e: 'report'): void
  (e: 'related'): void
}>()

/** 图片淡入记录（B.5） */
const loadedSet = reactive(new Set<number>())
function previewImage(idx: number) {
  previewImages(props.moment.images, idx)
}

const relatedTypeLabel = computed(() => {
  const m = props.moment
  if (!m) return ''
  return m.relatedType === 'dish' ? '关联菜品' : m.relatedType === 'stall' ? '关联档口' : ''
})
const relatedIconName = computed(() => {
  if (!props.moment) return 'dish'
  return props.moment.relatedType === 'stall' ? 'list' : 'dish'
})
const auditLabel = computed(() => props.moment.auditStatus === 'pending' ? '审核中' : '已退回')
const auditClass = computed(() => `audit-${props.moment.auditStatus}`)
</script>

<style scoped>
.m-card { margin: var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card); }
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); }
.m-avatar { width: 72rpx; height: 72rpx; border-radius: var(--radius-circle); background: var(--bg-soft); flex-shrink: 0; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.m-nickname { font-size: var(--font-caption); font-weight: var(--weight-bold); color: var(--text-primary); letter-spacing: var(--tracking-h3); }
.m-time { font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }
.m-audit { padding: 4rpx 12rpx; border-radius: var(--radius-tag); }
.m-audit-text { font-size: var(--font-tiny); font-weight: var(--weight-bold); }
.audit-pending { background: var(--color-warning-soft); }
.audit-pending .m-audit-text { color: var(--color-warning); }
.audit-rejected { background: var(--color-error-soft); }
.audit-rejected .m-audit-text { color: var(--color-error); }
.m-content { display: block; margin-top: var(--spacing-md); font-size: var(--font-body); color: var(--text-secondary); line-height: 1.5; word-break: break-word; }
.m-images { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); padding: var(--spacing-md); background: var(--bg-card); margin-top: var(--spacing-sm); }
.m-image-wrap { width: 220rpx; height: 220rpx; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); flex-shrink: 0; -webkit-tap-highlight-color: transparent; }
.m-image { width: 100%; height: 100%; opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); }
.m-image.loaded { opacity: 1; }
.related-card { display: flex; align-items: center; gap: var(--spacing-sm); margin: var(--spacing-md) 0 0; padding: var(--spacing-sm) var(--spacing-xs) var(--spacing-md); background: transparent; border-radius: var(--radius-none); box-shadow: none; border-bottom: 2rpx solid var(--border-color); -webkit-tap-highlight-color: transparent; }
.related-icon { font-size: var(--font-subtitle); line-height: 1; }
.related-thumb { width: 64rpx; height: 64rpx; border-radius: var(--radius-xs); background: var(--bg-page); flex-shrink: 0; }
.related-thumb--empty { display: flex; align-items: center; justify-content: center; }
.related-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.related-type { font-size: var(--font-aux); color: var(--text-tertiary); }
.related-name-row { display: flex; align-items: center; gap: var(--spacing-sm); min-width: 0; }
.related-name { font-size: var(--font-body); font-weight: var(--weight-bold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.related-arrow { font-size: var(--font-body); color: var(--text-tertiary); }
</style>
