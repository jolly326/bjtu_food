<script setup lang="ts">
/**
 * 食堂管理。
 * 范式：顶栏标题 + 新建 → 列表卡片 → 底部分页；
 * 编辑走宽版弹窗（.dialog--wide + .edit-grid 两列栅格），删除在编辑界面内整卡切换确认（ConfirmDeleteCard）。
 * 规格见 docs/ui/web/食堂管理.md。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import ImageField from '@/components/ImageField.vue'
import ListCard from '@/components/ListCard.vue'
import ConfirmDeleteCard from '@/components/ConfirmDeleteCard.vue'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { usePagedList } from '@/composables/usePagedList'
import { canteenApi, upload, type CanteenOut } from '@/api'

const dialog = reactive({
  visible: false,
  id: 0 as number,
  name: '',
  activate: true,
  imageUrl: '',
})
const view = ref<'form' | 'delete'>('form')
const originalImageUrl = ref('')
const saving = ref(false)
const pendingFile = ref<File | null>(null)

const { rows, total, page, loading, error, hasNext, totalPages, load, go } =
  usePagedList<CanteenOut>((p) => canteenApi.list(p))

function openForm() {
  view.value = 'form'
}

function onCreate() {
  dialog.id = 0
  dialog.name = ''
  dialog.activate = true
  dialog.imageUrl = ''
  pendingFile.value = null
  originalImageUrl.value = ''
  openForm()
  dialog.visible = true
}

function onEdit(row: CanteenOut) {
  dialog.id = row.id
  dialog.name = row.name
  dialog.activate = row.activate
  dialog.imageUrl = row.imageUrl ?? ''
  originalImageUrl.value = row.imageUrl ?? ''
  pendingFile.value = null
  openForm()
  dialog.visible = true
}

async function onSave() {
  if (!dialog.name.trim()) {
    ElMessage.warning('食堂名称不能为空')
    return
  }
  saving.value = true
  try {
    let imageUrl: string | null = dialog.imageUrl || null
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
    originalImageUrl.value =
      dialog.imageUrl && !dialog.imageUrl.startsWith('blob:')
        ? dialog.imageUrl
        : originalImageUrl.value
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

function onCancel(): void {
  dialog.imageUrl = originalImageUrl.value
  dialog.visible = false
}

async function confirmRemove() {
  try {
    await canteenApi.remove(dialog.id)
    ElMessage.success('已删除')
    dialog.visible = false
    if (rows.value.length === 1 && page.value > 1) go(page.value - 1)
    else await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
    view.value = 'form'
  }
}

onMounted(load)
</script>

<template>
  <section class="page">
    <Teleport defer to="#layout-pageheader">
      <header class="page__bar">
        <div class="page__titleGroup">
          <h2 class="page__title">食堂管理</h2>
        </div>
        <div class="page__actions">
          <button class="btn btn--primary" @click="onCreate">
            <AppIcon name="add" :size="16" />新建食堂
          </button>
        </div>
      </header>
    </Teleport>

    <div class="list-card-wrap">
      <ListCard
        v-for="row in rows"
        :key="row.id"
        :title="row.name"
        :image-url="row.imageUrl"
        :meta="`${row.stallCount} 个档口`"
        cover
        interactive
        :aria-label="`编辑 ${row.name}`"
        @activate="onEdit(row)"
      >
        <template #badge>
          <span class="tag" :class="row.activate ? 'tag--on' : 'tag--off'">
            {{ row.activate ? '正常' : '已停用' }}
          </span>
        </template>
      </ListCard>

      <div v-if="loading" class="list-empty">加载中…</div>
      <div v-if="!rows.length && !loading && !error" class="list-empty">暂无食堂</div>
    </div>

    <ListError v-if="error" :message="error" @retry="load" />

    <footer class="page__foot">
      <span class="page__total">共 {{ total }} 条</span>
      <PaginationBar
        :page="page"
        :has-next="hasNext"
        :total-pages="totalPages"
        @prev="go(page - 1)"
        @next="go(page + 1)"
        @go="go"
      />
    </footer>

    <Teleport defer to="#overlay-root">
      <div v-if="dialog.visible" class="mask" @click.self="onCancel">
        <div class="dialog dialog--wide" role="dialog" aria-modal="true" aria-label="食堂编辑">
          <ConfirmDeleteCard
            v-if="view === 'delete'"
            message="删除后不可恢复。若该食堂下仍有档口，删除会被拒绝。"
            @cancel="view = 'form'"
            @confirm="confirmRemove"
          />
          <template v-else>
            <h3 class="dialog__title">{{ dialog.id ? '编辑食堂' : '新建食堂' }}</h3>

            <div class="dialog__body">
              <div class="edit-grid">
                <div class="edit-field">
                  <label class="dialog__label" for="canteen-name">名称</label>
                  <input
                    id="canteen-name"
                    v-model="dialog.name"
                    class="dialog__input"
                    type="text"
                  />
                </div>

                <div class="edit-field">
                  <label class="dialog__label" for="canteen-activate">状态</label>
                  <select id="canteen-activate" v-model="dialog.activate" class="dialog__input">
                    <option :value="true">正常</option>
                    <option :value="false">已停用</option>
                  </select>
                </div>

                <div class="edit-field edit-field--wide">
                  <label class="dialog__label">食堂图片</label>
                  <ImageField
                    v-model="dialog.imageUrl"
                    label="食堂图片"
                    @pending="pendingFile = $event"
                  />
                </div>
              </div>
            </div>

            <footer class="dialog__foot">
              <button v-if="dialog.id" class="btn btn--danger" @click="view = 'delete'">
                删除
              </button>
              <span class="dialog__spacer" />
              <button class="btn btn--outline" @click="onCancel">取消</button>
              <button class="btn btn--primary" :disabled="saving" @click="onSave">
                {{ saving ? '保存中…' : '保存' }}
              </button>
            </footer>
          </template>
        </div>
      </div>
    </Teleport>
  </section>
</template>
