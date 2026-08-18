<script setup lang="ts">
/**
 * ImageUpload：图片上传（§4.2 自封装组件）。
 * - max 控制最多张数（默认 3，符合评价图约束）
 * - v-model 绑定以 "|||" 分隔的相对路径字符串，兼容现有 canteen/stall/dish 约定
 * - 单文件 ≤5MB，jpg/jpeg/png/webp（§4.2）
 * - 上传/失败走 useToastStore
 */
import { ref, computed, watch } from 'vue'
import { uploadImage } from '@/api/upload'
import { useToastStore } from '@/stores/toastStore'
import { icon } from '@/utils/icon'

const props = withDefaults(
  defineProps<{
    modelValue: string
    max?: number
    single?: boolean
  }>(),
  { max: 3, single: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const toast = useToastStore()
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

const images = computed(() => (props.modelValue || '').split('|||').map((s) => s.trim()).filter(Boolean))

function sync(val: string[]) {
  emit('update:modelValue', val.join('|||'))
}

function handleAdd() {
  if (props.single ? images.value.length >= 1 : images.value.length >= props.max) return
  fileInput.value?.click()
}

async function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    input.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    toast.error('图片大小不能超过 5MB')
    input.value = ''
    return
  }
  uploading.value = true
  try {
    const result = await uploadImage(file)
    const next = props.single ? [result.relativeUrl] : [...images.value, result.relativeUrl]
    sync(next)
    toast.success('图片上传成功')
  } catch (err: any) {
    toast.error(err.message || '图片上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function removeImage(idx: number) {
  const next = [...images.value]
  next.splice(idx, 1)
  sync(next)
}

// 占位：避免 unused watch 告警（保持 v-model 受控）
watch(() => props.modelValue, () => {})
</script>

<template>
  <div class="image-upload">
    <div v-for="(img, idx) in images" :key="idx" class="image-item">
      <img :src="img" alt="预览" />
      <span class="image-remove" v-press role="button" tabindex="0" :aria-label="`删除图片 ${idx + 1}`" @click="removeImage(idx)" @keydown.enter.prevent="removeImage(idx)" @keydown.space.prevent="removeImage(idx)">
        <img :src="icon.close" class="icon-x" alt="" />
      </span>
      <span v-if="idx === 0" class="cover-badge">封面</span>
    </div>
    <div
      v-if="(single ? images.length < 1 : images.length < max)"
      class="image-add"
      :class="{ disabled: uploading }"
      v-press
      role="button"
      tabindex="0"
      aria-label="添加图片"
      @click="handleAdd"
      @keydown.enter.prevent="handleAdd"
      @keydown.space.prevent="handleAdd"
    >
      <span class="add-icon">
        <img v-if="!uploading" :src="icon.plus" class="add-svg" alt="" />
      </span>
      <span class="add-text">{{ uploading ? '上传中' : single ? '上传图片' : '添加图片' }}</span>
    </div>
    <input ref="fileInput" type="file" accept="image/*" class="file-hidden" @change="handleFileChange" />
  </div>
</template>

<style scoped>
.image-upload {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}
.image-item {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--border-color);
  flex-shrink: 0;
}
.image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.image-remove {
  position: absolute;
  top: var(--space-1);
  right: var(--space-1);
  width: 28px;   /* 触控区域 ≥28px（Apple 触控规范），替代过小的 20px */
  height: 28px;
  border-radius: 50%;
  background: var(--el-mask-color, rgba(0, 0, 0, 0.5));
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
.image-remove:hover {
  background: rgba(0, 0, 0, 0.7);
}
.image-remove:active {
  transform: scale(var(--press-scale));
}
.image-remove:focus-visible,
.image-add:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}
.image-remove .icon-x {
  width: 14px;
  height: 14px;
  display: block;
  filter: brightness(10);
}
.cover-badge {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: var(--font-xs);
  text-align: center;
  padding: var(--space-1) 0;
  opacity: 0.85;
}
.image-add {
  width: 100px;
  height: 100px;
  border: 1px dashed var(--border-soft);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  cursor: pointer;
  transition: border-color 0.2s var(--ease-out), background 0.2s var(--ease-out);
  background: var(--bg-soft);
  flex-shrink: 0;
}
.image-add:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-bg);
}
.image-add.disabled {
  cursor: default;
  opacity: 0.7;
}
.add-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-light);
}
.add-svg {
  width: 24px;
  height: 24px;
  display: block;
}
.add-text {
  font-size: var(--font-xs);
  color: var(--text-light);
}
.file-hidden {
  display: none;
}
</style>
