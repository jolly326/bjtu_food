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

const rows = ref<RecordOut[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const dishes = ref<{ id: number; name: string }[]>([])
const filter = reactive({ dishId: undefined as number | undefined, reaction: '' })

async function loadDishes() {
  const res = await dishApi.list({ page: 1, pageSize: 50 })
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
      <select
        v-model="filter.dishId"
        class="filters__select"
        aria-label="按菜品筛选"
        @change="applyFilter"
      >
        <option :value="undefined">全部菜品</option>
        <option v-for="d in dishes" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
      <select
        v-model="filter.reaction"
        class="filters__select"
        aria-label="按反应筛选"
        @change="applyFilter"
      >
        <option v-for="r in REACTIONS" :key="r.value" :value="r.value">{{ r.label }}</option>
      </select>
      <button class="btn btn--outline" @click="resetFilter">重置</button>
    </div>

    <div class="list-card-wrap">
      <div
        class="list-card"
        v-for="row in rows"
        :key="row.id"
      >
        <div class="list-card__main">
          <h3 class="list-card__title">
            <span class="list-card__titleText">{{ row.dishName }}</span>
          </h3>
          <div class="list-card__meta">
            <span
              class="list-card__metaText"
              :class="'tag tag--r' + row.reaction"
            >
              {{
                row.reaction == null
                  ? '未评分'
                  : REACTION_LABEL[String(row.reaction)]
              }}
            </span>
          </div>
        </div>
      </div>
      <span v-if="!rows.length && !loading" class="list-card__empty">暂无数据</span>
    </div>

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


`n