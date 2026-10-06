<script setup lang="ts">
import { ref } from 'vue'
import { uploadImage } from '@/api/upload'
import { ElMessage } from 'element-plus'

/**
 * 图片上传（[UI 基线 §1.11 / §2.5 #2](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p>**顺序即语义**：`modelValue` 的顺序就是落库顺序，**首图恒为封面**（带「封面」角标）；
 * 缩略图支持拖拽排序（HTML5 DnD，本地重排后回传，父组件保存时持久化）。
 */
const props = defineProps<{
  modelValue: string[]
  max?: number
  ratioHint?: string
  ariaLabel?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [string[]] }>()

const uploading = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)
/** 起拖的缩略图下标（null = 未在拖拽） */
const dragIndex = ref<number | null>(null)

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

/** 拖拽落位：本地重排后回传（首图自动成为封面），由父组件在保存时落库 */
function onDropThumb(target: number): void {
  const from = dragIndex.value
  dragIndex.value = null
  if (from === null || from === target) return
  const arr = [...props.modelValue]
  const [moved] = arr.splice(from, 1)
  if (!moved) return
  arr.splice(target, 0, moved)
  emit('update:modelValue', arr)
}
</script>

<template>
  <div class="img-upload" role="group" :aria-label="ariaLabel ?? '图片上传'">
    <div
      class="thumb"
      :class="{ dragging: dragIndex === i }"
      v-for="(u, i) in modelValue"
      :key="u"
      draggable="true"
      @dragstart="dragIndex = i"
      @dragend="dragIndex = null"
      @dragover.prevent
      @drop="onDropThumb(i)"
    >
      <img :src="u" alt="" />
      <span class="thumb-cover" v-if="i === 0">封面</span>
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
  cursor: grab;
  transition:
    box-shadow var(--duration-base) var(--ease-out),
    border-color var(--duration-base) var(--ease-out);
}
.thumb.dragging {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-hover);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  /* 拖拽手柄不应被图片的默认可拖行为劫持 */
  -webkit-user-drag: none;
  user-select: none;
}
.thumb-cover {
  position: absolute;
  left: 0;
  bottom: 0;
  padding: 0 var(--space-2);
  border-radius: var(--radius-pill);
  margin: var(--space-1);
  background: var(--bg-soft);
  color: var(--text-secondary);
  font-size: var(--font-xs);
  line-height: 1.6;
}
.thumb-del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  /* 半透黑遮罩为**图片上的浮层底**，设计变量未设该档（非结构尺寸，不硬造 token） */
  background: rgba(0, 0, 0, 0.55);
  /* 半透黑底上的图标 = 实底上的文字（设计变量.md §2.3 `--text-white`） */
  color: var(--text-white);
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
  font-size: var(--font-3xl);
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
