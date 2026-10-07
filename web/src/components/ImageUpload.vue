<script lang="ts">
/** 上传图片项：`url` 之外为扩展预留字段（本期不启用 alt / caption 等） */
export interface ImageItem {
  url: string
}
</script>

<script setup lang="ts">
import { ref } from 'vue'
import { uploadImage } from '@/api/upload'
import { ElMessage } from 'element-plus'

/**
 * 图片上传（[UI 基线 §1.11 / §2.5 #2](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p>**顺序即语义**：`modelValue: ImageItem[]` 的顺序就是落库顺序，**封面 = 首图（恒）**——
 * 「封面」角标恒在 index 0，非首图提供「设为封面」动作（把该图移到首位），无独立封面标记；
 * 缩略图**仅手柄可拖**（每格左上角 `DragHandle` 发起 HTML5 DnD，无禁用条件，
 * 提交中的并发守卫由表单层 busy 统一控制）；父组件保存时按数组顺序映射为 `imageUrls: string[]` 上送。
 */
const props = defineProps<{
  modelValue: ImageItem[]
  max?: number
  ratioHint?: string
  ariaLabel?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [ImageItem[]] }>()

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
    emit('update:modelValue', [...props.modelValue, { url }])
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

/** 设为封面 = 把该图移到首位（首图恒为封面，不做独立封面标记） */
function makeCover(i: number): void {
  if (i === 0) return
  const arr = [...props.modelValue]
  const [moved] = arr.splice(i, 1)
  if (!moved) return
  arr.unshift(moved)
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
    <!-- 整格退化为放置目标：仅手柄 draggable（dragstart / dragend 自手柄冒泡到格） -->
    <div
      class="thumb"
      :class="{ dragging: dragIndex === i }"
      v-for="(img, i) in modelValue"
      :key="img.url"
      @dragover.prevent
      @drop="onDropThumb(i)"
      @dragend="dragIndex = null"
    >
      <img :src="img.url" alt="" />
      <DragHandle
        class="thumb-handle"
        :label="`拖拽排序${ariaLabel ?? '图片'} ${i + 1}`"
        @dragstart="dragIndex = i"
      />
      <span class="thumb-cover" v-if="i === 0">封面</span>
      <button
        class="thumb-del"
        type="button"
        :aria-label="`删除${ariaLabel ?? '图片'} ${i + 1}`"
        @click="remove(i)"
      >
        ×
      </button>
      <button
        class="thumb-cover-btn"
        v-if="i !== 0"
        type="button"
        :aria-label="`将${ariaLabel ?? '图片'} ${i + 1} 设为封面`"
        @click="makeCover(i)"
      >
        设为封面
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
/* DragHandle（子组件根）落位与可见性：半透黑底同删除钮（图片上的浮层底，设计变量未设该档，
   非结构尺寸，不硬造 token）；双类选择器确保覆盖组件内 `--text-muted` 缺省色 */
.thumb .thumb-handle {
  position: absolute;
  top: 0;
  left: 0;
  color: var(--text-white);
  background: rgba(0, 0, 0, 0.55);
  border-radius: 0 0 var(--radius) 0;
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
/* 「设为封面」= 移到首位；hover 过渡与缩略图同档（--duration-base + --ease-out） */
.thumb-cover-btn {
  position: absolute;
  right: 0;
  bottom: 0;
  margin: var(--space-1);
  padding: 0 var(--space-2);
  border: none;
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  color: var(--text-secondary);
  font-size: var(--font-xs);
  line-height: 1.6;
  cursor: pointer;
  transition:
    background-color var(--duration-base) var(--ease-out),
    color var(--duration-base) var(--ease-out);
}
.thumb-cover-btn:hover {
  background: var(--color-primary-bg);
  color: var(--color-primary-text);
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
/* 禁用态不上提 shared.css：全站仅此一处「添加格禁用」形态，无公共等价选择器
   （.btn-*:disabled 均绑定在各自按钮类上），硬提炼需新造类，收益不抵成本 */
.thumb.add:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.hint {
  font-size: var(--font-xs);
  color: var(--text-muted);
}
</style>
