<script setup lang="ts">
/**
 * 纠错处理（设计稿 docs/ui/web/纠错处理.md；业务规则 docs/func/web/B2）。
 * 默认只查未处理；「去改菜品」是全站唯一跨菜单跳转（带 dishId 直达菜品管理）。
 * 本页只标记「看过了」：不改菜品、不回执学生、不删提交行。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { usePagedList } from '@/composables/usePagedList'
import { dishApi, issueApi, type IssueOut } from '@/api'
import { formatDateTime } from '@/utils/datetime'

const router = useRouter()

const dishes = ref<{ id: number; name: string }[]>([])
const filter = reactive({
  handled: false as boolean | undefined,
  dishId: undefined as number | undefined,
})

async function loadDishes() {
  const res = await dishApi.list({ page: 1, pageSize: 50 })
  dishes.value = res.records.map((d) => ({ id: d.id, name: d.name }))
}

/** 分页状态机：翻页 / 加载态 / 末页删空回退 / 筛选回第 1 页单点收口（见基线 §2.3「分页」） */
const { rows, total, page, loading, hasNext, load, go, reload } = usePagedList<IssueOut>((p) =>
  issueApi.list({
    handled: filter.handled,
    dishId: filter.dishId,
    page: p,
  }),
)

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
        @change="reload"
      >
        <option :value="false">未处理</option>
        <option :value="true">已处理</option>
        <option :value="undefined">全部</option>
      </select>
      <select
        v-model="filter.dishId"
        class="filters__select"
        aria-label="按菜品筛选"
        @change="reload"
      >
        <option :value="undefined">全部菜品</option>
        <option v-for="d in dishes" :key="d.id" :value="d.id">{{ d.name }}</option>
      </select>
    </div>

    <div class="list-card-wrap">
      <div
        class="list-card"
        :class="{ 'list-card--dim': row.handledAt != null }"
        v-for="row in rows"
        :key="row.id"
      >
        <div class="list-card__main">
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.dishName }}</span>
            <span class="tag">{{ row.optionName }}</span>
          </div>
          <div class="list-card__path">{{ row.canteenName }} · {{ row.stallName }}</div>
          <div class="list-card__stats">
            <span class="list-card__stat">提交于 {{ formatDateTime(row.createdAt) }}</span>
            <span v-if="row.handledAt" class="list-card__stat">
              处理于 {{ formatDateTime(row.handledAt) }}
            </span>
          </div>
        </div>
        <div class="list-card__actions">
          <template v-if="row.handledAt == null">
            <button class="link" @click="onFix(row)">去改菜品</button>
            <button class="link" @click="onHandle(row)">标记已处理</button>
          </template>
          <button v-else class="link" @click="onUndo(row)">撤销</button>
        </div>
      </div>
      <div class="list-card" v-if="!rows.length && !loading">
        <span class="list-card__empty">暂无待处理的纠错</span>
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
/* 基础形态在 theme.css（#app .tag）；本页仅覆写中性底色（错误类型 tag） */
.tag {
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
</style>
