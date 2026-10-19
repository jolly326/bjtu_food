<script setup lang="ts">
/**
 * 评分数据（设计稿 docs/ui/web/评分数据.md；业务规则 docs/func/web/B1）。
 * 只读页：无新建、无卡面操作、不展示提交人。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dishApi, recordApi, type RecordOut } from '@/api'
import { usePagedList } from '@/composables/usePagedList'
import { formatDateTime } from '@/utils/datetime'

const REACTIONS = [
  { value: '', label: '全部' },
  { value: '5', label: '喜欢' },
  { value: '3', label: '一般' },
  { value: '1', label: '不喜欢' },
  { value: 'none', label: '未评分' },
]

const REACTION_LABEL: Record<string, string> = {
  '5': '喜欢',
  '3': '一般',
  '1': '不喜欢',
}

const dishes = ref<{ id: number; name: string }[]>([])
const filter = reactive({ dishId: undefined as number | undefined, reaction: '' })

/** 评分概览（列表上方统计卡）：4 路并发只取 total，随菜品筛选联动、不随反应筛选 */
const OVERVIEW_STATS = [
  { key: '5', label: '喜欢' },
  { key: '3', label: '一般' },
  { key: '1', label: '不喜欢' },
  { key: 'none', label: '未评分' },
] as const

const overview = reactive({
  loading: false,
  counts: { '5': 0, '3': 0, '1': 0, none: 0 } as Record<string, number>,
})
const overviewTotal = computed(() => Object.values(overview.counts).reduce((a, b) => a + b, 0))

function pct(key: string): number {
  if (!overviewTotal.value) return 0
  return Math.round(((overview.counts[key] ?? 0) / overviewTotal.value) * 100)
}

async function loadOverview() {
  overview.loading = true
  try {
    const res = await Promise.all(
      OVERVIEW_STATS.map((s) =>
        recordApi.list({ dishId: filter.dishId, reaction: s.key, page: 1, pageSize: 1 }),
      ),
    )
    OVERVIEW_STATS.forEach((s, i) => {
      overview.counts[s.key] = res[i]?.total ?? 0
    })
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    overview.loading = false
  }
}

/** 反应标签 class：null 时给中性 tag，避免生成无效的 `tag--rnull` */
function reactionClass(v: number | null): string {
  return v == null ? 'tag--none' : `tag--r${v}`
}

async function loadDishes() {
  const res = await dishApi.list({ page: 1, pageSize: 50 })
  dishes.value = res.records.map((d) => ({ id: d.id, name: d.name }))
}

/** 分页状态机：翻页 / 加载态 / 末页删空回退 / 筛选回第 1 页单点收口（见基线 §2.3「分页」） */
const { rows, total, page, loading, hasNext, load, go, reload } = usePagedList<RecordOut>((p) =>
  recordApi.list({
    dishId: filter.dishId,
    reaction: filter.reaction || undefined,
    page: p,
  }),
)

/** 仅菜品筛选变化时需要联动刷新概览（反应筛选不影响分布口径） */
function onDishFilterChange() {
  reload()
  loadOverview()
}

onMounted(async () => {
  await loadDishes()
  await Promise.all([load(), loadOverview()])
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">评分数据</h2>
    </header>

    <div class="filters">
      <select
        v-model="filter.dishId"
        class="filters__select"
        aria-label="按菜品筛选"
        @change="onDishFilterChange"
      >
        <option :value="undefined">全部菜品</option>
        <option v-for="d in dishes" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
      <select
        v-model="filter.reaction"
        class="filters__select"
        aria-label="按反应筛选"
        @change="reload"
      >
        <option v-for="r in REACTIONS" :key="r.value" :value="r.value">{{ r.label }}</option>
      </select>
    </div>

    <div class="overview" aria-label="评分分布概览">
      <div v-for="s in OVERVIEW_STATS" :key="s.key" class="overview__item">
        <span class="overview__label">
          {{ s.label }}
          <span class="overview__pct">{{ pct(s.key) }}%</span>
        </span>
        <strong class="overview__count">{{
          overview.loading ? '—' : overview.counts[s.key]
        }}</strong>
        <span class="overview__bar">
          <span
            class="overview__barFill"
            :class="'overview__barFill--' + s.key"
            :style="{ width: pct(s.key) + '%' }"
          ></span>
        </span>
      </div>
    </div>

    <div class="list-card-wrap">
      <div class="list-card" v-for="row in rows" :key="row.id">
        <div class="list-card__main">
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.dishName }}</span>
            <span class="tag" :class="reactionClass(row.reaction)">
              {{ row.reaction == null ? '未评分' : REACTION_LABEL[String(row.reaction)] }}
            </span>
          </div>
          <div class="list-card__path">{{ row.canteenName }} · {{ row.stallName }}</div>
          <div class="list-card__stats">
            <span class="list-card__stat">想吃于 {{ formatDateTime(row.wantedAt) }}</span>
            <span v-if="row.reactedAt" class="list-card__stat">
              评分于 {{ formatDateTime(row.reactedAt) }}
            </span>
          </div>
        </div>
      </div>
      <div class="list-card" v-if="!rows.length && !loading">
        <span class="list-card__empty">暂无数据</span>
      </div>
    </div>

    <footer class="page__foot">
      <span class="page__total">共 {{ total }} 条</span>
      <div class="pager">
        <button class="btn" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
        <span class="pager__cur">{{ page }}</span>
        <button class="btn" :disabled="!hasNext" @click="go(page + 1)">下一页</button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.tag--r5 {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.tag--r3 {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}
.tag--r1 {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
.tag--none {
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}
</style>
