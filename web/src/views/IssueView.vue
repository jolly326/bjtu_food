<script setup lang="ts">
/**
 * 纠错处理（设计稿 docs/ui/web/纠错处理.md；业务规则 docs/func/web/B2）。
 * 默认只查未处理；「去改菜品」是全站唯一跨菜单跳转（带 dishId 直达菜品管理）。
 * 本页只标记「看过了」：不改菜品、不回执学生、不删提交行。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { dishApi, issueApi, type IssueOut } from '@/api'

const router = useRouter()

const rows = ref<IssueOut[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const dishes = ref<{ id: number; name: string }[]>([])
const filter = reactive({
  handled: false as boolean | undefined,
  dishId: undefined as number | undefined,
})

async function loadDishes() {
  const res = await dishApi.list({ page: 1, pageSize: 50 })
  dishes.value = res.records.map((d) => ({ id: d.id, name: d.name }))
}

async function load() {
  loading.value = true
  try {
    const res = await issueApi.list({
      handled: filter.handled,
      dishId: filter.dishId,
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
  filter.handled = false
  filter.dishId = undefined
  page.value = 1
  load()
}

/** 一键直达：跳菜品管理并自动打开该菜编辑弹窗 */
function onFix(row: IssueOut) {
  router.push({ name: 'dishes', query: { dishId: String(row.dishId) } })
}

async function onHandle(row: IssueOut) {
  try {
    await issueApi.setHandled(row.id, true)
    ElMessage.success('已标记')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function onUndo(row: IssueOut) {
  try {
    await issueApi.setHandled(row.id, false)
    ElMessage.success('已撤销')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  await loadDishes()
  await load()
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">纠错处理</h2>
    </header>

    <div class="filters">
      <select
        v-model="filter.handled"
        class="filters__select"
        aria-label="按处理状态筛选"
        @change="applyFilter"
      >
        <option :value="false">未处理</option>
        <option :value="true">已处理</option>
        <option :value="undefined">全部</option>
      </select>
      <select
        v-model="filter.dishId"
        class="filters__select"
        aria-label="按菜品筛选"
        @change="applyFilter"
      >
        <option :value="undefined">全部菜品</option>
        <option v-for="d in dishes" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
      <button class="btn btn--outline" @click="resetFilter">重置</button>
    </div>

    <div class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>菜品</th>
            <th>错误类型</th>
            <th class="table__ops">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>{{ row.dishName }}</td>
            <td>
              <span class="tag">{{ row.optionName }}</span>
            </td>
            <td class="table__ops">
              <template v-if="row.handledAt == null">
                <button class="link" @click="onFix(row)">去改菜品</button>
                <button class="link" @click="onHandle(row)">标记已处理</button>
              </template>
              <button v-else class="link" @click="onUndo(row)">撤销</button>
            </td>
          </tr>
          <tr v-if="!rows.length && !loading">
            <td colspan="3" class="table__empty">暂无待处理的纠错</td>
          </tr>
        </tbody>
      </table>
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

.table__muted {
  color: var(--el-text-color-secondary);
}
.table__ops {
  text-align: right;
  white-space: nowrap;
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
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.link {
  border: none;
  background: none;
  color: var(--el-color-primary);
  font-size: 13px;
  cursor: pointer;
  padding: 0 6px;
}

/* 表格横向滚动容器：窄屏（手机）下保持表格可读，不撑破布局 */
.table-wrap {
  overflow-x: auto;
  margin-bottom: 4px;
}
</style>
