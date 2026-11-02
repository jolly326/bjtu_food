<script setup lang="ts">
/**
 * 档口管理（设计稿 docs/ui/web/档口管理.md；业务规则 docs/func/web/A2）。
 * 楼层受控字典（两端常量，与后端校验清单逐字一致）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import ConfirmDeleteView from '@/components/ConfirmDeleteView.vue'
import ImageField from '@/components/ImageField.vue'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { useBatchSelection } from '@/composables/useBatchSelection'
import { PAGE_SIZE, usePagedList } from '@/composables/usePagedList'
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

const saving = ref(false)
/** 弹窗视图：表单 / 删除确认（整卡切换） */
const view = ref<'form' | 'delete'>('form')
/** 待上传图片（保存时才落 COS） */
const pendingFile = ref<File | null>(null)

/** 指标行位置文案：楼层 · 窗口号（均可空，全空则整段不显示） */
function locText(row: StallOut): string {
  return [row.floor, row.windowNo ? `窗口 ${row.windowNo}` : ''].filter(Boolean).join(' · ')
}

async function loadCanteens() {
  const res = await canteenApi.list(1, 50)
  canteens.value = res.records
}

/** 分页状态机：翻页 / 加载态 / 末页删空回退 / 筛选回第 1 页单点收口（见基线 §2.3「分页」） */
const { rows, total, page, loading, error, hasNext, load, go, reload } = usePagedList<StallOut>(
  (p) => stallApi.list({ canteenId: filter.canteenId, page: p }),
)

function onCreate() {
  dialog.id = 0
  dialog.canteenId = undefined
  dialog.name = ''
  dialog.activate = true
  dialog.floor = ''
  dialog.windowNo = ''
  dialog.imageUrl = ''
  pendingFile.value = null
  view.value = 'form'
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
  pendingFile.value = null
  view.value = 'form'
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

  let imageUrl = dialog.imageUrl || null
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

  saving.value = true
  try {
    if (dialog.id) await stallApi.update(dialog.id, body)
    else await stallApi.create(body)
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
    await stallApi.remove(dialog.id)
    ElMessage.success('已删除')
    dialog.visible = false
    view.value = 'form'
    await load()
  } catch (e) {
    view.value = 'form'
    ElMessage.error((e as Error).message)
  }
}

/* ===== 批量模式 ===== */
const {
  batchMode,
  selected,
  batchBusy,
  selectedCount,
  allSelected,
  toggleBatchMode,
  clearSelection,
  toggleSelect,
  toggleSelectAll,
  runBatch,
} = useBatchSelection(rows)

const batchCanteenDialog = reactive({ visible: false, canteenId: undefined as number | undefined })

/** 行 → 提交体（批量改单项字段时复用其余字段） */
function bodyOf(row: StallOut): StallBody {
  return {
    canteenId: row.canteenId,
    name: row.name,
    activate: row.activate,
    floor: row.floor ?? null,
    windowNo: row.windowNo ?? null,
    imageUrl: row.imageUrl ?? null,
  }
}

/** 逐条提交批量变更（分批并发在 composable 内），汇总成功 / 失败 */
async function applyBatch(patch: (row: StallOut) => StallBody): Promise<void> {
  const { ok, failed } = await runBatch((row) => stallApi.update(row.id, patch(row)))
  if (!ok && !failed.length) return
  clearSelection()
  await load()
  if (failed.length) {
    const names = failed
      .slice(0, 3)
      .map((f) => f.name)
      .join('、')
    ElMessage.warning(
      `已更新 ${ok} 项，${failed.length} 项失败（${names}${failed.length > 3 ? '…' : ''}）`,
    )
  } else {
    ElMessage.success(`已更新 ${ok} 项`)
  }
}

function onBatchOn() {
  void applyBatch((row) => ({ ...bodyOf(row), activate: true }))
}

function onBatchOff() {
  void applyBatch((row) => ({ ...bodyOf(row), activate: false }))
}

function onBatchCanteenOpen() {
  batchCanteenDialog.canteenId = undefined
  batchCanteenDialog.visible = true
}

async function onBatchCanteenConfirm() {
  if (!batchCanteenDialog.canteenId) {
    ElMessage.warning('请选择目标食堂')
    return
  }
  const canteenId = batchCanteenDialog.canteenId
  batchCanteenDialog.visible = false
  await applyBatch((row) => ({ ...bodyOf(row), canteenId }))
}

onMounted(async () => {
  await loadCanteens()
  await load()
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">档口管理</h2>
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
        <button class="btn btn--outline" :aria-pressed="batchMode" @click="toggleBatchMode">
          {{ batchMode ? '退出批量' : '批量' }}
        </button>
        <button class="btn btn--primary" @click="onCreate">
          <AppIcon name="add" :size="16" />新建
        </button>
      </div>
    </header>

    <!-- 批量操作条：仅多选模式出现 -->
    <div v-if="batchMode" class="batch-bar">
      <label class="batch-bar__all">
        <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />
        <span>全选本页</span>
      </label>
      <span class="batch-bar__count">已选 {{ selectedCount }} 项</span>
      <div class="batch-bar__ops">
        <button class="btn btn--sm" :disabled="batchBusy || !selectedCount" @click="onBatchOn">
          批量启用
        </button>
        <button class="btn btn--sm" :disabled="batchBusy || !selectedCount" @click="onBatchOff">
          批量停用
        </button>
        <button
          class="btn btn--sm"
          :disabled="batchBusy || !selectedCount"
          @click="onBatchCanteenOpen"
        >
          批量改食堂
        </button>
      </div>
    </div>

    <div class="list-card-wrap">
      <div
        v-for="row in rows"
        :key="row.id"
        class="list-card"
        :class="{
          'list-card--selectable': batchMode,
          'list-card--checked': batchMode && selected.has(row.id),
        }"
        :tabindex="batchMode ? -1 : 0"
        :role="batchMode ? undefined : 'button'"
        :aria-label="batchMode ? undefined : `编辑 ${row.name}`"
        @click="batchMode ? toggleSelect(row) : onEdit(row)"
        @keydown.enter.prevent="batchMode ? undefined : onEdit(row)"
        @keydown.space.prevent="batchMode ? undefined : onEdit(row)"
      >
        <input
          v-if="batchMode"
          class="list-card__check"
          type="checkbox"
          :checked="selected.has(row.id)"
          :aria-label="`选择 ${row.name}`"
          @click.stop
          @change="toggleSelect(row)"
        />
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
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
            <span class="tag" :class="row.activate ? 'tag--on' : 'tag--off'">
              {{ row.activate ? '正常' : '已停用' }}
            </span>
          </div>
          <div class="list-card__path">{{ row.canteenName }}</div>
          <div class="list-card__stats">
            <span v-if="locText(row)" class="list-card__stat">{{ locText(row) }}</span>
            <span class="list-card__stat">{{ row.dishCount }} 道菜</span>
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
        @go="go"
      />
    </footer>

    <!-- 批量改食堂 -->
    <div
      v-if="batchCanteenDialog.visible"
      class="mask"
      @click.self="batchCanteenDialog.visible = false"
    >
      <div class="dialog">
        <h3 class="dialog__title">批量改食堂</h3>
        <p class="dialog__hint">
          将所选 {{ selectedCount }} 个档口移到目标食堂（名称 / 楼层 / 图片不变）。
        </p>
        <label class="dialog__label" for="batch-canteen">目标食堂</label>
        <select id="batch-canteen" v-model="batchCanteenDialog.canteenId" class="dialog__input">
          <option :value="undefined">请选择</option>
          <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <footer class="dialog__foot">
          <button class="btn btn--outline" @click="batchCanteenDialog.visible = false">取消</button>
          <button class="btn btn--primary" @click="onBatchCanteenConfirm">确定</button>
        </footer>
      </div>
    </div>

    <div v-if="dialog.visible" class="mask" @click.self="dialog.visible = false">
      <div class="dialog">
        <ConfirmDeleteView
          v-if="view === 'delete'"
          message="删除后不可恢复。若该档口下仍有菜品，删除会被拒绝。"
          @cancel="view = 'form'"
          @confirm="onRemove"
        />
        <template v-else>
          <h3 class="dialog__title">{{ dialog.id ? '编辑档口' : '新建档口' }}</h3>

          <label class="dialog__label" for="stall-canteen">所属食堂</label>
          <select id="stall-canteen" v-model="dialog.canteenId" class="dialog__input">
            <option :value="undefined">请选择</option>
            <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>

          <label class="dialog__label" for="stall-name">名称</label>
          <input id="stall-name" v-model="dialog.name" class="dialog__input" type="text" />

          <label class="dialog__label" for="stall-floor">楼层</label>
          <select id="stall-floor" v-model="dialog.floor" class="dialog__input">
            <option value="">不填</option>
            <option v-for="f in FLOORS" :key="f" :value="f">{{ f }}</option>
          </select>

          <label class="dialog__label" for="stall-window">窗口号</label>
          <input id="stall-window" v-model="dialog.windowNo" class="dialog__input" type="text" />

          <label class="dialog__label" for="stall-activate">状态</label>
          <select id="stall-activate" v-model="dialog.activate" class="dialog__input">
            <option :value="true">正常</option>
            <option :value="false">已停用</option>
          </select>

          <label class="dialog__label">档口图片</label>
          <ImageField v-model="dialog.imageUrl" label="档口图片" @pending="pendingFile = $event" />

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
