<script setup lang="ts">
/**
 * 菜品管理（设计稿 docs/ui/web/菜品管理.md；业务规则 docs/func/web/A3）。
 *
 * 菜品本体 + **售卖点列表逐行展示**（本页重点）；点卡片进编辑弹窗，删除在弹窗内整卡确认。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'

import { ElMessage } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import DishEditDialog from '@/components/DishEditDialog.vue'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { fetchAllPages } from '@/composables/useAllPages'
import { PAGE_SIZE, usePagedList } from '@/composables/usePagedList'
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

const { rows, total, page, loading, error, hasNext, load, go, reload } = usePagedList<DishOut>(
  (p) =>
    dishApi.list({
      stallId: filter.stallId,
      canteenId: filter.canteenId,
      name: filter.name.trim() || undefined,
      page: p,
    }),
)

const totalPages = computed(() => Math.ceil(total.value / PAGE_SIZE))

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
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
            <span class="tag tag--count">{{ row.locations.length }} 处售卖</span>
            <span class="tag">{{ priceText(row.price ?? null) }}</span>
          </div>

          <div class="list-card__path">{{ row.description || '暂无描述' }}</div>

          <div class="offering-list">
            <div v-for="offering in row.locations" :key="offering.id" class="offering-row">
              <span class="offering-row__text">{{ offeringLine(offering) }}</span>
              <span class="tag" :class="offering.activate ? 'tag--on' : 'tag--off'">
                {{ offering.activate ? '在售' : '已下架' }}
              </span>
            </div>
            <div v-if="!row.locations.length" class="offering-row">
              <span class="offering-row__text offering-row__text--muted">暂无售卖点</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="loading" class="list-card">
        <span class="list-card__empty">加载中…</span>
      </div>
      <div v-if="!rows.length && !loading" class="list-card">
        <span class="list-card__empty">暂无菜品</span>
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
.offering-list {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.offering-list__head {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.offering-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
}

.offering-row__text {
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.offering-row__text--muted {
  color: var(--el-text-color-placeholder);
}

.tag--count {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
}
</style>
