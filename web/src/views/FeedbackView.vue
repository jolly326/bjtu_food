<script setup lang="ts">
/**
 * 反馈处理（设计稿 docs/ui/web/反馈处理.md；业务规则 docs/func/web/B1）。
 *
 * 列表卡片 + 内联筛选（类型 / 处理状态）；唯一动作 = 标记已处理（幂等）。
 * 不编辑正文、不删除、不给学生回执。
 */
import { computed, onMounted, reactive } from 'vue'

import { ElMessage } from 'element-plus'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { PAGE_SIZE, usePagedList } from '@/composables/usePagedList'
import { feedbackApi, type FeedbackOut, type FeedbackType } from '@/api'

/** 「全部」哨兵：Element Plus 的 el-option 不接受 undefined 的 value（会触发 prop 校验警告） */
const ALL = 'all' as const

const filter = reactive({
  type: ALL as FeedbackType | typeof ALL,
  /** 处理状态：ALL 全部 / true 已处理 / false 未处理 */
  handled: ALL as boolean | typeof ALL,
})

const { rows, total, page, loading, error, hasNext, load, go, reload } = usePagedList<FeedbackOut>(
  (p) =>
    feedbackApi.list({
      type: filter.type === ALL ? undefined : filter.type,
      handled: filter.handled === ALL ? undefined : filter.handled,
      page: p,
    }),
)

const totalPages = computed(() => Math.ceil(total.value / PAGE_SIZE))

function typeLabel(type: FeedbackType): string {
  return type === 'product' ? '产品反馈' : '菜品反馈'
}

async function onHandle(row: FeedbackOut): Promise<void> {
  if (row.handledAt) return
  try {
    await feedbackApi.markHandled(row.id)
    ElMessage.success('已标记处理')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
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
          <h2 class="page__title">反馈处理</h2>
        </div>
        <div class="filters">
          <el-select
            v-model="filter.type"
            class="filters__select"
            placeholder="全部类型"
            clearable
            @change="reload"
          >
            <el-option label="全部类型" :value="ALL" />
            <el-option label="产品反馈" value="product" />
            <el-option label="菜品反馈" value="dish" />
          </el-select>
          <el-select
            v-model="filter.handled"
            class="filters__select"
            placeholder="全部状态"
            @change="reload"
          >
            <el-option label="全部状态" :value="ALL" />
            <el-option label="未处理" :value="false" />
            <el-option label="已处理" :value="true" />
          </el-select>
        </div>
      </header>
    </Teleport>

    <div class="list-card-wrap">
      <div
        v-for="row in rows"
        :key="row.id"
        class="list-card"
        :class="{ 'list-card--dim': row.handledAt }"
      >
        <div class="list-card__main">
          <div class="list-card__title">
            <span class="tag" :class="row.type === 'product' ? 'tag--on' : 'tag--off'">
              {{ typeLabel(row.type) }}
            </span>
            <span class="list-card__titleText">{{ row.dishName || '—' }}</span>
          </div>

          <p class="feedback__content">{{ row.content }}</p>

          <div class="list-card__stats">
            <span>提交：{{ row.createdAt }}</span>
            <span v-if="row.handledAt">处理：{{ row.handledAt }}</span>
          </div>

          <div class="feedback__actions">
            <button
              class="btn btn--sm"
              type="button"
              :disabled="Boolean(row.handledAt)"
              @click="onHandle(row)"
            >
              {{ row.handledAt ? '已处理' : '标记处理' }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="loading" class="list-card">
        <span class="list-card__empty">加载中…</span>
      </div>
      <div v-if="!rows.length && !loading" class="list-card">
        <span class="list-card__empty">暂无反馈</span>
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
  </section>
</template>

<style scoped>
.feedback__content {
  margin: 6px 0;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.feedback__actions {
  margin-top: 8px;
}
</style>
