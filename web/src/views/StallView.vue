<script setup lang="ts">
/**
 * 档口管理。
 * 范式：顶栏内联筛选（所属食堂 el-select）+「新建」→ 列表卡片 → 底部分页；
 * 编辑走宽版弹窗（.dialog--wide + .edit-grid 两列栅格），删除在编辑界面内整卡切换确认（ConfirmDeleteCard）。
 * 规格见 docs/ui/web/档口管理.md。
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
import { canteenApi, stallApi, upload, type StallBody, type StallOut } from '@/api'

const FLOORS = ['负一层', '一层', '二层', '三层', '四层']

const canteens = ref<{ id: number; name: string }[]>([])
const filter = reactive({ canteenId: undefined as number | undefined })

const dialog = reactive({
  visible: false,
  id: 0,
  canteenId: undefined as number | undefined,
  name: '',
  activate: true,
  floor: '',
  windowNo: '',
  imageUrl: '',
})
const view = ref<'form' | 'delete'>('form')
const originalImageUrl = ref('')
const saving = ref(false)
const pendingFile = ref<File | null>(null)

const { rows, total, page, loading, error, hasNext, totalPages, load, go, reload } =
  usePagedList<StallOut>((p) => stallApi.list({ canteenId: filter.canteenId, page: p }))

function locText(row: StallOut): string {
  return [row.floor, row.windowNo ? `窗口 ${row.windowNo}` : ''].filter(Boolean).join(' · ')
}

async function loadCanteens() {
  const res = await canteenApi.list(1, 50)
  canteens.value = res.records
}

function openForm() {
  view.value = 'form'
}

function onCreate() {
  dialog.id = 0
  dialog.canteenId = undefined
  dialog.name = ''
  dialog.activate = true
  dialog.floor = ''
  dialog.windowNo = ''
  dialog.imageUrl = ''
  pendingFile.value = null
  originalImageUrl.value = ''
  openForm()
  dialog.visible = true
}

function onEdit(row: StallOut) {
  dialog.id = row.id
  dialog.canteenId = row.canteenId
  dialog.name = row.name
  dialog.activate = row.activate
  dialog.floor = row.floor ?? ''
  dialog.windowNo = row.windowNo ?? ''
  dialog.imageUrl = row.imageUrl ?? ''
  originalImageUrl.value = row.imageUrl ?? ''
  pendingFile.value = null
  openForm()
  dialog.visible = true
}

async function onSave() {
  if (!dialog.canteenId) {
    ElMessage.warning('请选择所属食堂')
    return
  }
  if (!dialog.name.trim()) {
    ElMessage.warning('档口名称不能为空')
    return
  }
  if (dialog.floor && !FLOORS.includes(dialog.floor)) {
    ElMessage.warning('楼层取值不合法')
    return
  }

  saving.value = true
  try {
    let imageUrl: string | null = dialog.imageUrl || null
    if (pendingFile.value) {
      imageUrl = await upload(pendingFile.value)
      pendingFile.value = null
    }
    const body: StallBody = {
      canteenId: dialog.canteenId,
      name: dialog.name.trim(),
      activate: dialog.activate,
      floor: dialog.floor || null,
      windowNo: dialog.windowNo.trim() || null,
      imageUrl,
    }
    if (dialog.id) await stallApi.update(dialog.id, body)
    else await stallApi.create(body)
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
    await stallApi.remove(dialog.id)
    ElMessage.success('已删除')
    dialog.visible = false
    if (rows.value.length === 1 && page.value > 1) go(page.value - 1)
    else await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
    view.value = 'form'
  }
}

onMounted(async () => {
  await loadCanteens()
  await load()
})
</script>

<template>
  <section class="page">
    <Teleport defer to="#layout-pageheader">
      <header class="page__bar">
        <div class="page__titleGroup">
          <h2 class="page__title">档口管理</h2>
        </div>
        <div class="filters">
          <el-select
            v-model="filter.canteenId"
            class="filters__select"
            placeholder="全部食堂"
            clearable
            @change="reload"
          >
            <el-option v-for="c in canteens" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </div>
        <div class="page__actions">
          <button class="btn btn--primary" @click="onCreate">
            <AppIcon name="add" :size="16" />新建档口
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
        cover-icon="store"
        :meta="`${row.dishCount} 个菜品 · ${locText(row) || '位置未填写'}`"
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
      <div v-if="!rows.length && !loading && !error" class="list-empty">暂无档口</div>
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

    <!-- 新建 / 编辑档口：居中弹窗 -->
    <Teleport defer to="#overlay-root">
      <div v-if="dialog.visible" class="mask" @click.self="onCancel">
        <div class="dialog dialog--wide" role="dialog" aria-modal="true" aria-label="档口编辑">
          <ConfirmDeleteCard
            v-if="view === 'delete'"
            message="删除后不可恢复。若该档口下仍有菜品，删除会被拒绝。"
            @cancel="view = 'form'"
            @confirm="confirmRemove"
          />
          <template v-else>
            <h3 class="dialog__title">{{ dialog.id ? '编辑档口' : '新建档口' }}</h3>

            <div class="dialog__body">
              <div class="edit-grid">
                <div class="edit-field">
                  <label class="dialog__label" for="stall-canteen">所属食堂</label>
                  <select id="stall-canteen" v-model="dialog.canteenId" class="dialog__input">
                    <option :value="null">请选择</option>
                    <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
                  </select>
                </div>

                <div class="edit-field">
                  <label class="dialog__label" for="stall-name">名称</label>
                  <input id="stall-name" v-model="dialog.name" class="dialog__input" type="text" />
                </div>

                <div class="edit-field">
                  <label class="dialog__label" for="stall-floor">楼层</label>
                  <select id="stall-floor" v-model="dialog.floor" class="dialog__input">
                    <option value="">不填</option>
                    <option v-for="f in FLOORS" :key="f" :value="f">{{ f }}</option>
                  </select>
                </div>

                <div class="edit-field">
                  <label class="dialog__label" for="stall-window">窗口号</label>
                  <input
                    id="stall-window"
                    v-model="dialog.windowNo"
                    class="dialog__input"
                    type="text"
                  />
                </div>

                <div class="edit-field">
                  <label class="dialog__label" for="stall-activate">状态</label>
                  <select id="stall-activate" v-model="dialog.activate" class="dialog__input">
                    <option :value="true">正常</option>
                    <option :value="false">已停用</option>
                  </select>
                </div>

                <div class="edit-field">
                  <label class="dialog__label">档口图片</label>
                  <ImageField
                    v-model="dialog.imageUrl"
                    label="档口图片"
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
