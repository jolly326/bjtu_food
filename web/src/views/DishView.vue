<script setup lang="ts">
/**
 * 菜品管理（设计稿 docs/ui/web/菜品管理.md；业务规则 docs/func/web/A3）。
 * likeCount 是只读派生列（点赞数 = COUNT(dish_rating.liked = 1)）。
 *
 * 交互：点击卡片 → 操作浮层（编辑 / 删除）；顶栏「批量」进入多选模式后
 * 点行 = 勾选，可批量上架 / 下架 / 改档口（逐条提交，非原子）。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'

import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import CardActionSheet from '@/components/CardActionSheet.vue'
import DishEditDialog from '@/components/DishEditDialog.vue'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { useCardMenu } from '@/composables/useCardMenu'
import { fetchAllPages } from '@/composables/useOptions'
import { PAGE_SIZE, usePagedList } from '@/composables/usePagedList'
import { canteenApi, dishApi, stallApi, type DishOut } from '@/api'
import type { StallOut } from '@/api/types'

const canteens = ref<{ id: number; name: string }[]>([])
const stalls = ref<StallOut[]>([])
const filter = reactive({
  canteenId: undefined as number | undefined,
  stallId: undefined as number | undefined,
  /** 上架状态：undefined 全部 / true 在售 / false 已下架 */
  activate: undefined as boolean | undefined,
  /** 名称模糊搜索（防抖后生效） */
  name: '',
})

const editing = ref<DishOut | null>(null)
const editVisible = ref(false)

/** 指标行点赞数文案（派生缓存列，只读） */
function likeText(row: DishOut): string {
  return `${row.likeCount} 人赞`
}

/** 筛选用的档口：随食堂联动，未选食堂时列全部 */
const filterStalls = reactive({ items: [] as StallOut[] })

async function loadCanteens() {
  canteens.value = await fetchAllPages((page, pageSize) => canteenApi.list(page, pageSize))
}

/** 选择器取全量档口：逐页拉取，避免 pageSize=50 冒充全量导致漏项 */
async function loadStalls(canteenId?: number) {
  const list = await fetchAllPages((page, pageSize) => stallApi.list({ canteenId, page, pageSize }))
  stalls.value = list as StallOut[]
  filterStalls.items = list as StallOut[]
}

/** 分页状态机：翻页 / 加载态 / 末页删空回退 / 筛选回第 1 页单点收口（见基线 §2.3「分页」） */
const { rows, total, page, loading, error, hasNext, load, go, reload } = usePagedList<DishOut>(
  (p) =>
    dishApi.list({
      stallId: filter.stallId,
      canteenId: filter.canteenId,
      activate: filter.activate,
      name: filter.name.trim() || undefined,
      page: p,
    }),
)

const totalPages = computed(() => Math.ceil(total.value / PAGE_SIZE))

function onCreate() {
  editing.value = null
  editVisible.value = true
}

function onEdit(row: DishOut) {
  editing.value = row
  editVisible.value = true
}

async function onRemove(row: DishOut) {
  try {
    await ElMessageBox.confirm(
      '删除后不可恢复。若该菜已有学生纠错记录，删除会被拒绝（请先改用下架）。',
      '确认删除',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  try {
    await dishApi.remove(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

/** 点击浮层（编辑 / 删除）：卡面无操作按钮 */
const cardMenu = useCardMenu<DishOut>({ onEdit, onRemove })

function onFilterCanteenChange() {
  filter.stallId = undefined
  loadStalls(filter.canteenId)
  reload()
}

/** 名称搜索：输入防抖后回第 1 页重查 */
let nameTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => filter.name,
  () => {
    if (nameTimer) clearTimeout(nameTimer)
    nameTimer = setTimeout(reload, 350)
  },
)

/* ===== 批量模式 ===== */

/** 多选模式：点行 = 勾选；批量动作逐条提交（无服务端批量端点） */
const batchMode = ref(false)
const selected = ref<Set<number>>(new Set())
const batchBusy = ref(false)
const batchStallDialog = reactive({ visible: false, stallId: undefined as number | undefined })

const selectedRows = computed(() => rows.value.filter((r) => selected.value.has(r.id)))
const selectedCount = computed(() => selectedRows.value.length)
const allSelected = computed(
  () => rows.value.length > 0 && rows.value.every((r) => selected.value.has(r.id)),
)

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  selected.value = new Set()
}

function toggleSelect(row: DishOut) {
  const next = new Set(selected.value)
  if (next.has(row.id)) next.delete(row.id)
  else next.add(row.id)
  selected.value = next
}

function toggleSelectAll() {
  selected.value = allSelected.value ? new Set() : new Set(rows.value.map((r) => r.id))
}

/** 行 → 提交体（批量改单项字段时复用其余字段） */
function bodyOf(row: DishOut) {
  return {
    stallId: row.stallId,
    name: row.name,
    imageUrl: row.imageUrl ?? null,
    activate: row.activate,
  }
}

/** 逐条提交批量变更：分批并发（每批 6 条），汇总成功 / 失败 */
async function applyBatch(patch: (row: DishOut) => ReturnType<typeof bodyOf>): Promise<void> {
  const targets = selectedRows.value
  if (!targets.length) return
  batchBusy.value = true
  let okCount = 0
  const failed: { name: string }[] = []
  try {
    for (let i = 0; i < targets.length; i += 6) {
      const chunk = targets.slice(i, i + 6)
      const results = await Promise.allSettled(
        chunk.map((row) => dishApi.update(row.id, patch(row))),
      )
      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          okCount += 1
        } else {
          failed.push({ name: chunk[index]!.name })
        }
      })
    }
  } finally {
    batchBusy.value = false
  }
  selected.value = new Set()
  await load()
  if (failed.length) {
    const names = failed
      .slice(0, 3)
      .map((f) => f.name)
      .join('、')
    ElMessage.warning(
      `已更新 ${okCount} 项，${failed.length} 项失败（${names}${failed.length > 3 ? '…' : ''}）`,
    )
  } else {
    ElMessage.success(`已更新 ${okCount} 项`)
  }
}

function onBatchOn() {
  void applyBatch((row) => ({ ...bodyOf(row), activate: true }))
}

function onBatchOff() {
  void applyBatch((row) => ({ ...bodyOf(row), activate: false }))
}

function onBatchStallOpen() {
  batchStallDialog.stallId = undefined
  batchStallDialog.visible = true
}

async function onBatchStallConfirm() {
  if (!batchStallDialog.stallId) {
    ElMessage.warning('请选择目标档口')
    return
  }
  const stallId = batchStallDialog.stallId
  batchStallDialog.visible = false
  await applyBatch((row) => ({ ...bodyOf(row), stallId }))
}

onMounted(async () => {
  await Promise.all([loadCanteens(), loadStalls(), load()])
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">菜品管理</h2>
      <div class="page__actions">
        <button class="btn btn--outline" :aria-pressed="batchMode" @click="toggleBatchMode">
          {{ batchMode ? '退出批量' : '批量' }}
        </button>
        <button class="btn btn--primary" @click="onCreate">
          <AppIcon name="plus" :size="16" />新建
        </button>
      </div>
    </header>

    <div class="filters">
      <input
        v-model="filter.name"
        class="filters__search"
        type="search"
        placeholder="搜索菜品名称"
        aria-label="按名称搜索"
      />
      <el-select
        v-model="filter.canteenId"
        class="filters__select"
        placeholder="全部食堂"
        clearable
        @change="onFilterCanteenChange"
      >
        <el-option v-for="c in canteens" :key="c.id" :value="c.id" :label="c.name" />
      </el-select>
      <el-select
        v-model="filter.stallId"
        class="filters__select"
        placeholder="全部档口"
        clearable
        @change="reload"
      >
        <el-option v-for="s in filterStalls.items" :key="s.id" :value="s.id" :label="s.name" />
      </el-select>
      <el-select
        v-model="filter.activate"
        class="filters__select"
        placeholder="全部状态"
        @change="reload"
      >
        <el-option label="全部状态" :value="undefined" />
        <el-option label="在售" :value="true" />
        <el-option label="已下架" :value="false" />
      </el-select>
    </div>

    <!-- 批量操作条：仅多选模式出现 -->
    <div v-if="batchMode" class="batch-bar">
      <label class="batch-bar__all">
        <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />
        <span>全选本页</span>
      </label>
      <span class="batch-bar__count">已选 {{ selectedCount }} 项</span>
      <div class="batch-bar__ops">
        <button class="btn btn--sm" :disabled="batchBusy || !selectedCount" @click="onBatchOn">
          批量上架
        </button>
        <button class="btn btn--sm" :disabled="batchBusy || !selectedCount" @click="onBatchOff">
          批量下架
        </button>
        <button
          class="btn btn--sm"
          :disabled="batchBusy || !selectedCount"
          @click="onBatchStallOpen"
        >
          批量改档口
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
        :aria-haspopup="batchMode ? undefined : 'menu'"
        v-bind="batchMode ? {} : cardMenu.openAttrs(row, { title: row.name })"
        @click="batchMode ? toggleSelect(row) : undefined"
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
          v-else-if="row.imageUrl"
          v-zoomimg
          :src="row.imageUrl"
          class="list-card__figure"
          :alt="row.name"
          loading="lazy"
        />
        <span
          v-else-if="!batchMode"
          class="list-card__figure list-card__figure--ph"
          aria-hidden="true"
        >
          <AppIcon name="image-broken" :size="20" />
        </span>
        <div class="list-card__main">
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
            <span class="tag" :class="row.activate ? 'tag--on' : 'tag--off'">
              {{ row.activate ? '在售' : '已下架' }}
            </span>
          </div>
          <div class="list-card__path">{{ row.canteenName }} · {{ row.stallName }}</div>
          <div class="list-card__stats">
            <span class="list-card__like">
              <AppIcon name="heart" :size="12" />
              {{ likeText(row) }}
            </span>
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
        :total-pages="totalPages"
        @prev="go(page - 1)"
        @next="go(page + 1)"
        @go="go"
      />
    </footer>

    <!-- 批量改档口：选目标档口 -->
    <div
      v-if="batchStallDialog.visible"
      class="mask"
      @click.self="batchStallDialog.visible = false"
    >
      <div class="dialog">
        <h3 class="dialog__title">批量改档口</h3>
        <p class="dialog__hint">将所选 {{ selectedCount }} 道菜移到目标档口（名称与图片不变）。</p>
        <label class="dialog__label" for="batch-stall">目标档口</label>
        <select id="batch-stall" v-model="batchStallDialog.stallId" class="dialog__input">
          <option :value="undefined">请选择</option>
          <option v-for="s in stalls" :key="s.id" :value="s.id">
            {{ s.canteenName }} · {{ s.name }}
          </option>
        </select>
        <footer class="dialog__foot">
          <button class="btn btn--outline" @click="batchStallDialog.visible = false">取消</button>
          <button class="btn btn--primary" @click="onBatchStallConfirm">确定</button>
        </footer>
      </div>
    </div>

    <DishEditDialog v-model="editVisible" :dish="editing" :stalls="stalls" @saved="load" />

    <CardActionSheet
      v-model="cardMenu.open"
      :title="cardMenu.title"
      :remove-disabled="cardMenu.removeDisabled"
      :remove-hint="cardMenu.removeHint"
      @edit="cardMenu.pickEdit"
      @remove="cardMenu.pickRemove"
    />
  </section>
</template>
