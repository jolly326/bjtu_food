<script setup lang="ts">
/**
 * 菜品管理（设计稿 docs/ui/web/菜品管理.md；业务规则 docs/func/web/A3）。
 *
 * 菜品本体 + **位置列表逐行展示**（本页重点）；点卡片进编辑弹窗，删除在弹窗内整卡确认。
 */
import { onMounted, reactive, ref, watch } from 'vue'

import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import DishEditDialog from '@/components/DishEditDialog.vue'
import ListCard from '@/components/ListCard.vue'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { fetchAllPages } from '@/composables/useAllPages'
import { usePagedList } from '@/composables/usePagedList'
import { canteenApi, dishApi, stallApi, type DishLocationOut, type DishOut } from '@/api'
import type { StallOut } from '@/api/types'

const canteens = ref<{ id: number; name: string }[]>([])
const stalls = ref<StallOut[]>([])
const filter = reactive({
  canteenId: undefined as number | undefined,
  stallId: undefined as number | undefined,
  /** 名称模糊搜索（防抖后生效） */
  name: '',
})

const filterStalls = reactive({ items: [] as StallOut[] })

const editing = ref<DishOut | null>(null)
const editVisible = ref(false)

function priceText(price: number | null): string {
  return price === null ? '价格待定' : `¥${(price / 100).toFixed(2)}`
}

function offeringLine(offering: DishLocationOut): string {
  const floor = offering.floor || '—'
  const stall = offering.stallName || '无档口'
  return `${offering.canteenName} · ${floor} · ${stall}`
}

async function loadCanteens(): Promise<void> {
  canteens.value = await fetchAllPages((page, pageSize) => canteenApi.list(page, pageSize))
}

async function loadStalls(canteenId?: number): Promise<void> {
  const list = await fetchAllPages((page, pageSize) => stallApi.list({ canteenId, page, pageSize }))
  stalls.value = list as StallOut[]
  filterStalls.items = list as StallOut[]
}

const { rows, total, page, loading, error, hasNext, totalPages, load, go, reload } =
  usePagedList<DishOut>((p) =>
    dishApi.list({
      stallId: filter.stallId,
      canteenId: filter.canteenId,
      name: filter.name.trim() || undefined,
      page: p,
    }),
  )

function onCreate(): void {
  editing.value = null
  editVisible.value = true
}

function onEdit(row: DishOut): void {
  editing.value = row
  editVisible.value = true
}

async function onRemove(id: number): Promise<void> {
  try {
    await dishApi.remove(id)
    ElMessage.success('已删除')
    editVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function onFilterCanteenChange(): void {
  filter.stallId = undefined
  void loadStalls(filter.canteenId)
  reload()
}

let nameTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => filter.name,
  () => {
    if (nameTimer) clearTimeout(nameTimer)
    nameTimer = setTimeout(reload, 350)
  },
)

onMounted(async () => {
  await Promise.all([loadCanteens(), loadStalls(), load()])
})
</script>

<template>
  <section class="page">
    <!-- defer：目标 #layout-pageheader 在 AdminLayout 内，是本视图的兄弟节点。
         Vue 挂载时先渲染子组件、后把父元素插入 document，故挂载瞬间目标尚不在 document 中，
         直接 Teleport 会解析成 null 并在每次 update 抛未处理异常（列表停在初始空态）。
         defer 让目标推迟到挂载后解析，此时布局已在 document 中。 -->
    <Teleport defer to="#layout-pageheader">
      <header class="page__bar">
        <div class="page__titleGroup">
          <h2 class="page__title">菜品管理</h2>
        </div>
        <div class="filters">
          <input
            v-model="filter.name"
            class="filters__search"
            type="search"
            placeholder="搜索菜品"
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
        </div>
        <div class="page__actions">
          <button class="btn btn--primary" @click="onCreate">
            <AppIcon name="add" :size="16" />新建菜品
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
        :meta="`${row.locations.length} 处售卖 · ${priceText(row.price ?? null)}`"
        cover
        cover-icon="food"
        interactive
        :aria-label="`编辑 ${row.name}`"
        @activate="onEdit(row)"
      >
        <p class="card-note">{{ row.description || '暂无描述' }}</p>
        <div class="card-lines">
          <span v-for="offering in row.locations" :key="offering.id" class="card-line">
            <span class="card-line__text">{{ offeringLine(offering) }}</span>
            <span class="tag tag--sm" :class="offering.activate ? 'tag--on' : 'tag--off'">
              {{ offering.activate ? '在售' : '已下架' }}
            </span>
          </span>
          <span v-if="!row.locations.length" class="card-line">
            <span class="card-line__text card-line__text--muted">暂无位置</span>
          </span>
        </div>
      </ListCard>

      <div v-if="loading" class="list-empty">加载中…</div>
      <div v-if="!rows.length && !loading && !error" class="list-empty">暂无菜品</div>
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

    <DishEditDialog
      v-model="editVisible"
      :dish="editing"
      :canteens="canteens"
      :stalls="stalls"
      @saved="load"
      @remove="onRemove"
    />
  </section>
</template>

<style scoped>
.card-note {
  margin: 0;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 售卖位置收紧为可换行标签：多个位置同行并列，减少卡片高度 */
.card-lines {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.card-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 3px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--web-radius-sm);
  background: var(--el-fill-color-lighter);
  font-size: 12px;
}

.card-line__text {
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-line__text--muted {
  color: var(--el-text-color-placeholder);
}
</style>
