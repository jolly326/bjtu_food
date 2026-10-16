<script setup lang="ts">
/**
 * 评分数据（设计稿 docs/ui/web/评分数据.md；业务规则 docs/func/web/B1）。
 * 只读页：无新建、无行内操作、不展示提交人。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dishApi, recordApi, type RecordOut } from '@/api'

const REACTIONS = [
  { value: '', label: '全部' },
  { value: '5', label: '好吃' },
  { value: '3', label: '一般' },
  { value: '1', label: '踩' },
  { value: 'none', label: '未评分' },
]

const REACTION_LABEL: Record<string, string> = { '5': '好吃', '3': '一般', '1': '踩' }

const rows = ref<RecordOut[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const dishes = ref<{ id: number; name: string }[]>([])
const filter = reactive({ dishId: undefined as number | undefined, reaction: '' })

async function loadDishes() {
  const res = await dishApi.list({ page: 1, size: 50 })
  dishes.value = res.records.map((d) => ({ id: d.id, name: d.name }))
}

async function load() {
  loading.value = true
  try {
    const res = await recordApi.list({
      dishId: filter.dishId,
      reaction: filter.reaction || undefined,
      page: page.value,
    })
    rows.value = res.records
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function prevPage() {
  page.value -= 1
  await load()
}

async function nextPage() {
  page.value += 1
  await load()
}

function applyFilter() {
  page.value = 1
  load()
}

function resetFilter() {
  filter.dishId = undefined
  filter.reaction = ''
  page.value = 1
  load()
}

onMounted(async () => {
  await loadDishes()
  await load()
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">评分数据</h2>
    </header>

    <div class="filters">
      <select v-model="filter.dishId" class="filters__select" @change="applyFilter">
        <option :value="undefined">全部菜品</option>
        <option v-for="d in dishes" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
      <select v-model="filter.reaction" class="filters__select" @change="applyFilter">
        <option v-for="r in REACTIONS" :key="r.value" :value="r.value">{{ r.label }}</option>
      </select>
      <button class="btn" @click="resetFilter">重置</button>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>菜品</th>
          <th>食堂</th>
          <th>档口</th>
          <th>反应</th>
          <th class="table__muted">去品尝时间</th>
          <th class="table__muted">评分时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.dishName }}</td>
          <td>{{ row.canteenName }}</td>
          <td>{{ row.stallName }}</td>
          <td>
            <span v-if="row.reaction == null" class="tag tag--none">未评分</span>
            <span v-else class="tag" :class="'tag--r' + row.reaction">{{
              REACTION_LABEL[String(row.reaction)]
            }}</span>
          </td>
          <td class="table__muted">{{ row.wantedAt }}</td>
          <td class="table__muted">{{ row.reactedAt ?? '—' }}</td>
        </tr>
        <tr v-if="!rows.length && !loading">
          <td colspan="6" class="table__empty">暂无数据</td>
        </tr>
      </tbody>
    </table>

    <footer class="page__foot">
      <span class="page__total">共 {{ total }} 条</span>
      <div class="pager">
        <button class="btn" :disabled="page <= 1" @click="prevPage">上一页</button>
        <span class="pager__cur">{{ page }}</span>
        <button class="btn" :disabled="page * 20 >= total" @click="nextPage">下一页</button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page__title {
  margin: 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
}
.page__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page__total {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.pager {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pager__cur {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.filters {
  display: flex;
  gap: 8px;
}
.filters__select {
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
}

.btn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-size: 13px;
  cursor: pointer;
}
.btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.table th,
.table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  text-align: left;
}
.table th {
  color: var(--el-text-color-secondary);
  font-weight: 600;
}
.table__muted {
  color: var(--el-text-color-secondary);
}
.table__empty {
  padding: 40px 0;
  text-align: center;
  color: var(--el-text-color-placeholder);
}

.tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
}
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
