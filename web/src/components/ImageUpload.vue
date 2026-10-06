<script setup lang="ts">
import { ref } from 'vue'
import { uploadImage } from '@/api/upload'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  modelValue: string[]
  max?: number
  ratioHint?: string
  ariaLabel?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [string[]] }>()

const uploading = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)

async function onPick(e: Event): Promise<void> {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const limit = props.max ?? 5
  if (props.modelValue.length >= limit) {
    ElMessage.warning(`最多 ${limit} 张`)
    return
  }
  uploading.value = true
  try {
    const { url } = await uploadImage(file)
    emit('update:modelValue', [...props.modelValue, url])
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '上传失败')
  } finally {
    uploading.value = false
    if (inputRef.value) inputRef.value.value = ''
  }
}

function remove(i: number): void {
  const arr = [...props.modelValue]
  arr.splice(i, 1)
  emit('update:modelValue', arr)
}
</script>

<template>
  <div class="img-upload" role="group" :aria-label="ariaLabel ?? '图片上传'">
    <div class="thumb" v-for="(u, i) in modelValue" :key="i">
      <img :src="u" alt="" />
      <button
        class="thumb-del"
        type="button"
        :aria-label="`删除${ariaLabel ?? '图片'} ${i + 1}`"
        @click="remove(i)"
      >
        ×
      </button>
    </div>
    <button
      class="thumb add"
      type="button"
      :disabled="uploading"
      :aria-label="ariaLabel ? `添加${ariaLabel}` : '添加图片'"
      @click="inputRef?.click()"
    >
      <span v-if="uploading" class="spin"></span>
      <span v-else>+</span>
    </button>
    <input ref="inputRef" type="file" accept="image/*" hidden @change="onPick" />
    <span class="hint" v-if="ratioHint">{{ ratioHint }}</span>
  </div>
</template>

<style scoped>
.img-upload {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
}
.thumb {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: var(--radius);
  overflow: hidden;
  border: 1px solid var(--border-light);
  background: var(--bg-soft);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb-del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  line-height: 18px;
}
.thumb.add {
  width: 84px;
  height: 84px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--text-muted);
  font-size: 28px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumb.add:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
}
</style>
