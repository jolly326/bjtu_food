<script setup lang="ts">
/**
 * 菜品编辑弹窗（菜品管理 / 纠错处理两页共用，规格见 docs/ui/web/菜品管理.md §2）。
 * 新建（`dish = null`）与编辑同组件；上传前压缩（utils/image）；保存成功回抛 `saved`。
 *
 * 归属：食堂必填（最小定位），档口 / 楼层可选；指定档口时按所选食堂过滤档口下拉。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { dishApi, upload, type DishOut } from '@/api'
import type { StallOut } from '@/api/types'
import { compressImage } from '@/utils/image'

/** 楼层受控字典（与后端 dish.floor 同源：docs/schema/dish.md） */
const FLOOR_OPTIONS = ['负一层', '一层', '二层', '三层', '四层']

const props = defineProps<{
  modelValue: boolean
  /** 编辑对象；`null` = 新建 */
  dish: DishOut | null
  /** 食堂选项（父级加载） */
  canteens: { id: number; name: string }[]
  /** 档口选项（父级加载；按所选食堂过滤） */
  stalls: StallOut[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
  /** 删除入口在编辑界面内：上抛 id，二次确认与删除由视图侧收口 */
  remove: [id: number]
}>()

const dialog = reactive({
  id: 0,
  canteenId: undefined as number | undefined,
  stallId: undefined as number | undefined,
  floor: '' as string,
  name: '',
  imageUrl: '',
  activate: true,
  uploading: false,
})

/** 档口按所选食堂过滤；未选食堂时列全部（避免误选跨食堂档口） */
const filteredStalls = computed(() =>
  dialog.canteenId ? props.stalls.filter((s) => s.canteenId === dialog.canteenId) : props.stalls,
)
const saving = ref(false)

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    // 打开时整体重置表单（避免残留上次的值）
    dialog.id = props.dish?.id ?? 0
    dialog.canteenId = props.dish?.canteenId
    dialog.stallId = props.dish?.stallId ?? undefined
    dialog.floor = props.dish?.floor ?? ''
    dialog.name = props.dish?.name ?? ''
    dialog.imageUrl = props.dish?.imageUrl ?? ''
    dialog.activate = props.dish?.activate ?? true
  },
)

function onCanteenChange() {
  // 切换食堂后该食堂档口集合变化，原档口可能失效 ⇒ 重置
  dialog.stallId = undefined
}

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
  if (!dialog.canteenId) {
    ElMessage.warning('请选择所属食堂')
    return
  }
  if (!dialog.name.trim()) {
    ElMessage.warning('菜品名称不能为空')
    return
  }
  saving.value = true
  try {
    const body = {
      canteenId: dialog.canteenId,
      stallId: dialog.stallId ?? null,
      floor: dialog.floor || null,
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

      <label class="dialog__label" for="dish-canteen">所属食堂（必填）</label>
      <select id="dish-canteen" v-model="dialog.canteenId" class="dialog__input" @change="onCanteenChange">
        <option :value="undefined">请选择食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>

      <label class="dialog__label" for="dish-stall">所属档口（可选）</label>
      <select id="dish-stall" v-model="dialog.stallId" class="dialog__input">
        <option :value="undefined">暂不指定（只挂食堂）</option>
        <option v-for="s in filteredStalls" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>

      <label class="dialog__label" for="dish-floor">楼层（可选）</label>
      <select id="dish-floor" v-model="dialog.floor" class="dialog__input">
        <option value="">暂不指定（回落档口楼层）</option>
        <option v-for="f in FLOOR_OPTIONS" :key="f" :value="f">{{ f }}</option>
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
        <button v-if="dialog.id" class="btn btn--danger" @click="emit('remove', dialog.id)">
          删除
        </button>
        <span class="dialog__spacer" />
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
