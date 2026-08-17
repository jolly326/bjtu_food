<template>
  <view class="img-uploader" :class="{ compact: compact }">
    <view class="img-grid">
      <view
        v-for="(img, idx) in props.modelValue"
        :key="`${img}-${idx}`"
        class="img-cell"
      >
        <image class="img-thumb" :src="img" mode="aspectFill" />
        <view class="img-remove" @tap="removeImage(idx)">
          <IconSvg name="close" :size="24" color="var(--badge-dark-text)" />
        </view>
      </view>
      <view
        v-if="props.modelValue.length < max"
        class="img-cell img-add"
        @tap="chooseImage"
      >
        <IconSvg name="plus" :size="addIconSize" color="var(--text-tertiary)" />
      </view>
    </view>
    <text v-if="showCounter && !compact" class="img-counter">{{ props.modelValue.length }}/{{ max }}</text>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import IconSvg from './IconSvg.vue'
import { uploadImage } from '@/api/upload'

/**
 * ImageUploader —— 图片上传网格（task-14 W2/W5 / task-13 T11）
 * 复用：publish-dish / submit-stall / review / publish-moment / 评论栏（compact）
 * 受控：v-model 绑定 string[]（已上传的相对/绝对路径）。
 */
const props = withDefaults(defineProps<{
  modelValue: string[]
  max?: number
  showCounter?: boolean
  /** compact：单元格与输入框同高、横向单行，用于评论栏同行左侧（2026-08-16） */
  compact?: boolean
}>(), {
  modelValue: () => [],
  max: 9,
  showCounter: true,
  compact: false,
})

const addIconSize = computed(() => (props.compact ? 36 : 60))

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
  change: [value: string[]]
}>()

const uploading = ref(false)

function sync(list: string[]) {
  emit('update:modelValue', list)
  emit('change', list)
}

function removeImage(idx: number) {
  const target = props.modelValue[idx]
  // 记录被删 URL：若等待期上传完成前用户删除了已上传项，合并时排除，避免被还原
  if (target) removedDuringUpload.add(target)
  const next = props.modelValue.slice()
  next.splice(idx, 1)
  sync(next)
}

/** 本次选择会话中已被用户删除的 URL（等待期删除兜底，防止上传完成被还原） */
const removedDuringUpload = new Set<string>()

function chooseImage() {
  if (uploading.value) return
  if (props.modelValue.length >= props.max) return
  const remain = Math.max(0, props.max - props.modelValue.length)
  uni.chooseMedia({
    count: remain,
    mediaType: ['image'],
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      uploading.value = true
      const uploaded: string[] = []
      try {
        for (const f of res.tempFiles) {
          const url = await uploadImage(f.tempFilePath)
          uploaded.push(url)
        }
        // 基于最新 modelValue 合并（保留等待期用户的增删），已删除 URL 不再还原
        const next = props.modelValue.filter((u) => !removedDuringUpload.has(u))
        next.push(...uploaded.filter((u) => !removedDuringUpload.has(u)))
        sync(next)
      } catch {
        uni.showToast({ title: '图片上传失败', icon: 'none' })
      } finally {
        uploading.value = false
        removedDuringUpload.clear()
      }
    },
  })
}
</script>

<style scoped>
.img-uploader { width: 100%; }
.img-grid { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.img-cell {
  width: 200rpx;
  height: 200rpx;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--bg-page);
  position: relative;
  flex-shrink: 0;
}
.img-thumb { width: 100%; height: 100%; }
.img-remove {
  position: absolute;
  top: 0; right: 0;
  width: 48rpx; height: 48rpx;
  border-radius: 50%;
  background: var(--badge-dark-bg);
  display: flex; align-items: center; justify-content: center;
  transition: transform 0.12s var(--ease-out), opacity 0.12s var(--ease-out);
}
.img-remove:active { transform: scale(var(--press-scale)); opacity: 0.85; }
.img-add {
  display: flex; align-items: center; justify-content: center;
  border: 2rpx dashed var(--border-bold);
  background: var(--bg-soft);
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.img-add:active { transform: scale(var(--press-scale)); }
.img-counter { display: block; margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-tertiary); }
/* compact：单元格与输入框同高（72rpx）、横向单行，置于评论栏同行左侧 */
.img-uploader.compact { width: auto; flex-shrink: 0; }
.img-uploader.compact .img-grid { flex-wrap: nowrap; gap: var(--spacing-xs); }
.img-uploader.compact .img-cell { width: 72rpx; height: 72rpx; }
.img-uploader.compact .img-add { width: 72rpx; height: 72rpx; }
.img-uploader.compact .img-add:active { transform: scale(var(--press-scale)); background: var(--bg-soft); transition: transform var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out); }
.img-uploader.compact .img-remove { width: 36rpx; height: 36rpx; border-radius: 50%; }
.img-uploader.compact .img-remove::after { content: ''; position: absolute; inset: -14rpx; }
.img-uploader.compact .img-remove :deep(svg) { width: 18rpx; height: 18rpx; }
</style>
