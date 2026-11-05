<script setup lang="ts">
/**
 * 图片字段（菜品 / 食堂 / 档口三处共用）：16:9 预览 + 选择 + 移除。
 *
 * 🔴 延迟上传：选图只做前端压缩与本地预览（blob），**不上传**；由父组件在「保存」时
 * 取 `pending` 事件抛出的文件再上传 COS，避免「选了又取消」也落桶。关闭弹窗即释放 blob。
 *
 * 🔴 回滚机制：选图后 modelValue 临时变为本地 blob URL。若用户取消或保存失败，
 * 父组件将 modelValue 恢复为服务器 URL，本组件监听到该回填即丢弃本地预览、释放 blob 并清空 pending。
 */
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { compressImage } from '@/utils/image'

const props = withDefaults(defineProps<{ modelValue: string; label?: string }>(), {
  label: '图片',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  /** 待上传文件（选图后为非空，移除 / 回滚为 null） */
  pending: [file: File | null]
}>()

/** 进入组件前的原始服务器 URL，用于回滚 */
const originalValue = ref(props.modelValue || '')
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

// 外部把值清空（如打开编辑重置）或改回服务器 URL（取消 / 保存失败回滚）时，
// 一并丢弃本地预览、释放 blob 并清空待上传文件；
// modelValue 为 blob 时是本地预览自身回填，不作处理。
// 当 modelValue 为非 blob 的服务器 URL 时，持续刷新 originalValue，保证回滚到当前编辑对象的原图。
watch(
  () => props.modelValue,
  (value) => {
    if (value && !value.startsWith('blob:')) {
      originalValue.value = value
    }
    if (objectUrl.value && (!value || !value.startsWith('blob:'))) {
      revoke()
      emit('pending', null)
    }
  },
  { immediate: true },
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
/* 16:9 横向预览：限宽，宽版弹窗下不会按满宽把卡片撑高 */
.image-field__thumb {
  display: block;
  width: 100%;
  max-width: 320px;
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
  max-width: 320px;
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

@media (max-width: 640px) {
  .image-field__ops .btn {
    flex: 1;
  }
}
</style>
