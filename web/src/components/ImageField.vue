<script setup lang="ts">
/**
 * 图片字段（菜品 / 食堂 / 档口三处共用）：16:9 预览 + 选择 + 移除。
 *
 * 🔴 延迟上传：选图只做前端压缩与本地预览（blob），**不上传**；由父组件在「保存」时
 * 取 `pending` 事件抛出的文件再上传 COS，避免「选了又取消」也落桶。关闭弹窗即释放 blob。
 */
import { onBeforeUnmount, watch } from 'vue'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { compressImage } from '@/utils/image'

const props = withDefaults(defineProps<{ modelValue: string; label?: string }>(), {
  label: '图片',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  /** 待上传文件（选图后为非空，移除 / 重置为 null） */
  pending: [file: File | null]
}>()

/** 本地预览 blob URL（仅当本次选了新图时存在） */
const objectUrl = ref('')

function revoke() {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

async function onPick(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const compressed = await compressImage(file)
    revoke()
    objectUrl.value = URL.createObjectURL(compressed)
    emit('pending', compressed)
    emit('update:modelValue', objectUrl.value)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function onClear() {
  revoke()
  emit('pending', null)
  emit('update:modelValue', '')
}

// 外部把值清空（如打开编辑重置）时，一并释放本地预览
watch(
  () => props.modelValue,
  (value) => {
    if (!value) revoke()
  },
)

onBeforeUnmount(revoke)
</script>

<template>
  <div class="image-field">
    <img v-if="modelValue" v-zoomimg :src="modelValue" class="image-field__thumb" :alt="label" />
    <span v-else class="image-field__empty">未上传</span>
    <div class="image-field__ops">
      <label class="btn btn--sm">
        {{ modelValue ? '更换图片' : '上传图片' }}
        <input type="file" accept="image/*" hidden @change="onPick" />
      </label>
      <button v-if="modelValue" class="btn btn--sm" @click="onClear">移除</button>
    </div>
  </div>
</template>

<style scoped>
.image-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
/* 16:9 横向预览：与列表封面统一 */
.image-field__thumb {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
}
.image-field__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 10px;
  border: 1px dashed var(--el-border-color-light);
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
.image-field__ops {
  display: flex;
  gap: 8px;
}
</style>
