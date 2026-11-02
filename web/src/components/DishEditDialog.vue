<script setup lang="ts">
/**
 * 菜品编辑弹窗（菜品管理 / 纠错处理两页共用，规格见 docs/ui/web/菜品管理.md §2）。
 * 新建（`dish = null`）与编辑同组件；图片由 `ImageField` 收敛（本地预览、保存时上传 COS）；保存成功回抛 `saved`。
 *
 * 归属：所属食堂必选（最小定位），档口 / 楼层可选；指定档口时按所选食堂过滤档口下拉。
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ConfirmDeleteView from '@/components/ConfirmDeleteView.vue'
import ImageField from '@/components/ImageField.vue'
import { dishApi, upload, type DishOut } from '@/api'
import type { StallOut } from '@/api/types'

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
})

/** 档口按所选食堂过滤；未选食堂时列全部（避免误选跨食堂档口） */
const filteredStalls = computed(() =>
  dialog.canteenId ? props.stalls.filter((s) => s.canteenId === dialog.canteenId) : props.stalls,
)
const saving = ref(false)
/** 选图后暂存待上传文件（保存时才真正落 COS）；预览用 blob URL，关闭/取消即丢弃 */
const pendingFile = ref<File | null>(null)
/** 弹窗视图：表单 / 删除确认（整卡切换，共用同一卡片） */
const view = ref<'form' | 'delete'>('form')

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      // 关闭时丢弃待上传文件并复位到表单视图（blob 由 ImageField 卸载时释放）
      pendingFile.value = null
      view.value = 'form'
      return
    }
    // 打开时整体重置表单（避免残留上次的值）
    dialog.id = props.dish?.id ?? 0
    dialog.canteenId = props.dish?.canteenId
    dialog.stallId = props.dish?.stallId ?? undefined
    dialog.floor = props.dish?.floor ?? ''
    dialog.name = props.dish?.name ?? ''
    dialog.imageUrl = props.dish?.imageUrl ?? ''
    dialog.activate = props.dish?.activate ?? true
    pendingFile.value = null
    view.value = 'form'
  },
)

function onCanteenChange() {
  // 切换食堂后该食堂档口集合变化，原档口可能失效 ⇒ 重置
  dialog.stallId = undefined
}

/** 删除确认：复位表单并上抛 id，真正删除与关闭由视图侧收口 */
function confirmDelete() {
  view.value = 'form'
  emit('remove', dialog.id)
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
    // 仅当本次选了新图才在「保存」时落 COS
    let imageUrl = dialog.imageUrl || null
    if (pendingFile.value) {
      imageUrl = await upload(pendingFile.value)
      pendingFile.value = null
    }
    const body = {
      canteenId: dialog.canteenId,
      stallId: dialog.stallId ?? null,
      floor: dialog.floor || null,
      name: dialog.name.trim(),
      imageUrl,
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
      <ConfirmDeleteView
        v-if="view === 'delete'"
        message="删除后不可恢复，该菜品的纠错 / 点赞将一并清除。"
        @cancel="view = 'form'"
        @confirm="confirmDelete"
      />
      <template v-else>
        <h3 class="dialog__title">{{ dialog.id ? '编辑菜品' : '新建菜品' }}</h3>

        <label class="dialog__label" for="dish-name">名称</label>
        <input id="dish-name" v-model="dialog.name" class="dialog__input" type="text" />

        <div class="dialog__grid">
          <div class="dialog__cell">
            <label class="dialog__label" for="dish-canteen">所属食堂</label>
            <select
              id="dish-canteen"
              v-model="dialog.canteenId"
              class="dialog__input"
              @change="onCanteenChange"
            >
              <option :value="undefined">请选择食堂</option>
              <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="dialog__cell">
            <label class="dialog__label" for="dish-stall">所属档口</label>
            <select id="dish-stall" v-model="dialog.stallId" class="dialog__input">
              <option :value="undefined">不指定档口</option>
              <option v-for="s in filteredStalls" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="dialog__cell">
            <label class="dialog__label" for="dish-floor">楼层</label>
            <select id="dish-floor" v-model="dialog.floor" class="dialog__input">
              <option value="">不指定</option>
              <option v-for="f in FLOOR_OPTIONS" :key="f" :value="f">{{ f }}</option>
            </select>
          </div>
          <div class="dialog__cell">
            <label class="dialog__label" for="dish-activate">状态</label>
            <select id="dish-activate" v-model="dialog.activate" class="dialog__input">
              <option :value="true">在售</option>
              <option :value="false">已下架</option>
            </select>
          </div>
        </div>

        <label class="dialog__label">菜品图片</label>
        <ImageField v-model="dialog.imageUrl" label="菜品图片" @pending="pendingFile = $event" />

        <footer class="dialog__foot">
          <button v-if="dialog.id" class="btn btn--danger" @click="view = 'delete'">删除</button>
          <span class="dialog__spacer" />
          <button class="btn btn--outline" @click="emit('update:modelValue', false)">取消</button>
          <button class="btn btn--primary" :disabled="saving" @click="onSave">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </footer>
      </template>
    </div>
  </div>
</template>

<style scoped>
/* 两列紧凑栅格：把「状态」并入行列，避免独占一行 */
.dialog__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 12px;
}
@media (max-width: 640px) {
  .dialog__grid {
    grid-template-columns: 1fr;
  }
}
</style>
