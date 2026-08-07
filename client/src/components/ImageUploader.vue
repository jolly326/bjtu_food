<template>
  <view class="img-uploader">
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
        <IconSvg name="plus" :size="60" color="var(--text-tertiary)" />
      </view>
    </view>
    <text v-if="showCounter" class="img-counter">{{ props.modelValue.length }}/{{ max }}</text>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import IconSvg from './IconSvg.vue'
import { uploadImage } from '@/api/upload'

/**
 * ImageUploader —— 图片上传网格（task-14 W2/W5 / task-13 T11）
 * 复用：publish-dish / submit-stall / review / publish-moment
 * 受控：v-model 绑定 string[]（已上传的相对/绝对路径）。
 */
const props = withDefaults(defineProps<{
  modelValue: string[]
  max?: number
  showCounter?: boolean
}>(), {
  modelValue: () => [],
  max: 9,
  showCounter: true,
})

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
  const next = props.modelValue.slice()
  next.splice(idx, 1)
  sync(next)
}

function chooseImage() {
  if (uploading.value) return
  const remain = props.max - props.modelValue.length
  if (remain <= 0) return
  uni.chooseMedia({
    count: remain,
    mediaType: ['image'],
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      uploading.value = true
      const next = props.modelValue.slice()
      try {
        for (const f of res.tempFiles) {
          const url = await uploadImage(f.tempFilePath)
          next.push(url)
          sync(next)
        }
      } catch {
        uni.showToast({ title: '图片上传失败', icon: 'none' })
      } finally {
        uploading.value = false
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
</style>
