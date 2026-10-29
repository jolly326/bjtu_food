<script setup lang="ts">
/**
 * 菜品编辑弹窗（菜品管理 / 纠错处理两页共用，规格见 docs/ui/web/菜品管理.md §2）。
 * 新建（`dish = null`）与编辑同组件；上传前压缩（utils/image）；保存成功回抛 `saved`。
 */
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { dishApi, upload, type DishOut } from '@/api'
import type { StallOut } from '@/api/types'
import { compressImage } from '@/utils/image'

const props = defineProps<{
  modelValue: boolean
  /** 编辑对象；`null` = 新建 */
  dish: DishOut | null
  /** 档口选项（父级加载；带食堂名便于分组展示） */
  stalls: StallOut[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const dialog = reactive({
  id: 0,
  stallId: undefined as number | undefined,
  name: '',
  imageUrl: '',
  activate: true,
  uploading: false,
})
const saving = ref(false)

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    // 打开时整体重置表单（避免残留上次的值）
    dialog.id = props.dish?.id ?? 0
    dialog.stallId = props.dish?.stallId
    dialog.name = props.dish?.name ?? ''
    dialog.imageUrl = props.dish?.imageUrl ?? ''
    dialog.activate = props.dish?.activate ?? true
  },
)

async function onPickImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  dialog.uploading = true
  try {
    dialog.imageUrl = await upload(await compressImage(file))
    ElMessage.success('图片已上传')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    dialog.uploading = false
  }
}

async function onSave() {
  if (!dialog.stallId) {
    ElMessage.warning('请选择所属档口')
    return
  }
  if (!dialog.name.trim()) {
    ElMessage.warning('菜品名称不能为空')
    return
  }
  saving.value = true
  try {
    const body = {
      stallId: dialog.stallId,
      name: dialog.name.trim(),
      imageUrl: dialog.imageUrl || null,
      activate: dialog.activate,
    }
    if (dialog.id) await dishApi.update(dialog.id, body)
    else await dishApi.create(body)
    ElMessage.success('已保存')
    emit('update:modelValue', false)
    emit('saved')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="modelValue" class="mask" @click.self="emit('update:modelValue', false)">
    <div class="dialog">
      <h3 class="dialog__title">{{ dialog.id ? '编辑菜品' : '新建菜品' }}</h3>

      <label class="dialog__label" for="dish-stall">所属档口</label>
      <select id="dish-stall" v-model="dialog.stallId" class="dialog__input">
        <option :value="undefined">请选择</option>
        <option v-for="s in stalls" :key="s.id" :value="s.id">
          {{ s.canteenName }} · {{ s.name }}
        </option>
      </select>

      <label class="dialog__label" for="dish-name">名称</label>
      <input id="dish-name" v-model="dialog.name" class="dialog__input" type="text" />

      <label class="dialog__label" for="dish-image">菜品大图</label>
      <div class="photo">
        <img
          v-if="dialog.imageUrl"
          v-zoomimg
          :src="dialog.imageUrl"
          class="photo__thumb img--round"
          alt="菜品图"
        />
        <span v-else class="photo__empty">未上传</span>
        <div class="photo__ops">
          <label class="btn btn--sm">
            {{ dialog.uploading ? '上传中…' : '上传图片' }}
            <input id="dish-image" type="file" accept="image/*" hidden @change="onPickImage" />
          </label>
          <button v-if="dialog.imageUrl" class="btn btn--sm" @click="dialog.imageUrl = ''">
            移除
          </button>
        </div>
      </div>

      <label class="dialog__label" for="dish-activate">状态</label>
      <select id="dish-activate" v-model="dialog.activate" class="dialog__input">
        <option :value="true">在售</option>
        <option :value="false">已下架</option>
      </select>

      <footer class="dialog__foot">
        <button class="btn btn--outline" @click="emit('update:modelValue', false)">取消</button>
        <button class="btn btn--primary" :disabled="saving" @click="onSave">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.photo {
  display: flex;
  align-items: center;
  gap: 12px;
}
/* 圆形裁切由 .img--round 提供（见 theme.css） */
.photo__thumb {
  width: 80px;
  height: 80px;
  border: 1px solid var(--el-border-color-lighter);
}
.photo__empty {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
.photo__ops {
  display: flex;
  gap: 8px;
}
</style>
