<script setup lang="ts">
/**
 * 食堂管理（设计稿 docs/ui/web/食堂管理.md；业务规则 docs/func/web/A1）。
 * 范式：顶栏 + 列表卡片 + 分页；新建 / 编辑走 520px 弹窗；删除二次确认。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import ConfirmDeleteView from '@/components/ConfirmDeleteView.vue'
import ImageField from '@/components/ImageField.vue'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { PAGE_SIZE, usePagedList } from '@/composables/usePagedList'
import { canteenApi, upload, type CanteenOut } from '@/api'

const dialog = reactive({
  visible: false,
  id: 0 as number,
  name: '',
  activate: true,
  imageUrl: '',
})
const saving = ref(false)
/** 弹窗视图：表单 / 删除确认（整卡切换） */
const view = ref<'form' | 'delete'>('form')
/** 待上传图片（保存时才落 COS） */
const pendingFile = ref<File | null>(null)

/** 分页状态机：翻页 / 加载态 / 末页删空回退单点收口（见基线 §2.3「分页」） */
const { rows, total, page, loading, error, hasNext, load, go } = usePagedList<CanteenOut>((p) =>
  canteenApi.list(p),
)

function onCreate() {
  dialog.id = 0
  dialog.name = ''
  dialog.activate = true
  dialog.imageUrl = ''
  pendingFile.value = null
  view.value = 'form'
  dialog.visible = true
}

function onEdit(row: CanteenOut) {
  dialog.id = row.id
  dialog.name = row.name
  dialog.activate = row.activate
  dialog.imageUrl = row.imageUrl ?? ''
  pendingFile.value = null
  view.value = 'form'
  dialog.visible = true
}

async function onSave() {
  if (!dialog.name.trim()) {
    ElMessage.warning('食堂名称不能为空')
    return
  }
  saving.value = true
  try {
    let imageUrl = dialog.imageUrl || null
    if (pendingFile.value) {
      imageUrl = await upload(pendingFile.value)
      pendingFile.value = null
    }
    const body = { name: dialog.name.trim(), activate: dialog.activate, imageUrl }
    if (dialog.id) {
      await canteenApi.update(dialog.id, body)
    } else {
      await canteenApi.create(body)
    }
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

/** 删除（由弹窗内整卡「确认删除」触发；不再用 ElMessageBox） */
async function onRemove() {
  if (!dialog.id) return
  try {
    await canteenApi.remove(dialog.id)
    ElMessage.success('已删除')
    dialog.visible = false
    view.value = 'form'
    await load() /* 末页删空自动回退由 usePagedList 单点处理 */
  } catch (e) {
    view.value = 'form'
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">食堂管理</h2>
      <button class="btn btn--primary" @click="onCreate">
        <AppIcon name="add" :size="16" />新建
      </button>
    </header>

    <div class="list-card-wrap">
      <div
        v-for="row in rows"
        :key="row.id"
        class="list-card"
        tabindex="0"
        role="button"
        :aria-label="`编辑 ${row.name}`"
        @click="onEdit(row)"
        @keydown.enter.prevent="onEdit(row)"
        @keydown.space.prevent="onEdit(row)"
      >
        <img
          v-if="row.imageUrl"
          v-zoomimg
          :src="row.imageUrl"
          class="list-card__cover"
          :alt="row.name"
          loading="lazy"
        />
        <span v-else class="list-card__cover list-card__cover--ph" aria-hidden="true">
          <AppIcon name="image" :size="28" />
        </span>
        <div class="list-card__main">
          <h3 class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
            <span class="tag" :class="row.activate ? 'tag--on' : 'tag--off'">
              {{ row.activate ? '正常' : '已停用' }}
            </span>
          </h3>
          <div class="list-card__stats">
            <span class="list-card__stat">{{ row.stallCount }} 个档口</span>
          </div>
        </div>
      </div>
      <div class="list-card" v-if="!rows.length && !loading">
        <span class="list-card__empty">暂无数据</span>
      </div>
    </div>

    <ListError v-if="error" :message="error" @retry="load" />

    <footer class="page__foot">
      <span class="page__total">共 {{ total }} 条</span>
      <PaginationBar
        :page="page"
        :has-next="hasNext"
        :total-pages="Math.ceil(total / PAGE_SIZE)"
        @prev="go(page - 1)"
        @next="go(page + 1)"
      />
    </footer>

    <div v-if="dialog.visible" class="mask" @click.self="dialog.visible = false">
      <div class="dialog">
        <ConfirmDeleteView
          v-if="view === 'delete'"
          message="删除后不可恢复。若该食堂下仍有档口，删除会被拒绝。"
          @cancel="view = 'form'"
          @confirm="onRemove"
        />
        <template v-else>
          <h3 class="dialog__title">{{ dialog.id ? '编辑食堂' : '新建食堂' }}</h3>
          <label class="dialog__label" for="canteen-name">名称</label>
          <input id="canteen-name" v-model="dialog.name" class="dialog__input" type="text" />
          <label class="dialog__label" for="canteen-activate">状态</label>
          <select id="canteen-activate" v-model="dialog.activate" class="dialog__input">
            <option :value="true">正常</option>
            <option :value="false">已停用</option>
          </select>
          <label class="dialog__label">食堂图片</label>
          <ImageField v-model="dialog.imageUrl" label="食堂图片" @pending="pendingFile = $event" />
          <footer class="dialog__foot">
            <button v-if="dialog.id" class="btn btn--danger" @click="view = 'delete'">删除</button>
            <span class="dialog__spacer" />
            <button class="btn btn--outline" @click="dialog.visible = false">取消</button>
            <button class="btn btn--primary" :disabled="saving" @click="onSave">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </footer>
        </template>
      </div>
    </div>
  </section>
</template>
