<script setup lang="ts">
/**
 * B3 举报管理（页面规格见 [反馈与举报.md](../../../docs/ui/web/反馈与举报.md) §B3）。
 *
 * <p>要点：列表走 `GET /admin/reports`（筛选：状态 / 举报人）；处置走 `PUT /admin/reports/{id}`
 * （`ModerationHandleReq` 三输入 + 举报专用的 `hideReview` 联动隐藏）。
 * 举报**无正文**：结论以结构化原因（`optionName`）为准，被举报评价原文、可见性与**处置**
 * 都在**处置抽屉**内（同一抽屉两态：`pending` = 处置表单 / 已处理 = 只读详情）。
 *
 * <p>**被举报评价已删除**（`reviewDeleted = true`）：举报行保留、举报人 / 原因 / 处置状态 /
 * 处置文案完整 —— 列表显示「被举报评价已删除」，摘要与可见性置空置灰（服务端以 `null` 下发，
 * 不伪造内容）。
 *
 * <p>处置抽屉含**「同时隐藏被举报评价」复选**（默认勾选；仅 `outcome=handled` 时可用；
 * 评价已隐藏时**置灰且不可改**）。
 */
import ActiveFilters from '@/components/ActiveFilters.vue'
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import { canWrite } from '@/utils/permissions'
import { handleReport, listReports } from '@/api/reports'
import type {
  ModerationOutcome,
  ModerationStatus,
  ReportAdminVO,
  ReportListParams,
} from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ModerationHandleForm from '@/components/ModerationHandleForm.vue'

const fStatus = ref<ModerationStatus | ''>('')
const fUserId = ref<number | ''>('')

function params(): ReportListParams {
  return {
    status: fStatus.value || undefined,
    userId: fUserId.value === '' ? undefined : Number(fUserId.value),
  }
}

const {
  items,
  total,
  page,
  pageCount,
  firstLoading,
  isEmpty,
  hasData,
  error,
  sessionInvalid,
  reload,
  reloadFirstPage,
  prevPage,
  nextPage,
} = usePagedList<ReportAdminVO>((pageNo, pageSize) =>
  listReports({ page: pageNo, pageSize, ...params() }),
)

/* ===== 处置 / 详情（抽屉） ===== */
const open = ref(false)
const current = ref<ReportAdminVO | null>(null)
const outcome = ref<ModerationOutcome>('handled')
const hideReview = ref(true)
const replyTitle = ref('')
const replyContent = ref('')
const submitting = ref(false)

/** 待处理 → 处置表单；已处理 → 只读详情 */
const isPending = computed(() => current.value?.status === 'pending')
/** 评价已隐藏 ⇒ 复选置灰且恒为 true（不做无意义的重复隐藏） */
const hideLocked = computed(() => current.value?.reviewHidden === true)

const outcomeLabel = computed(() => {
  const row = current.value
  if (!row || row.status === 'pending') return '待处理'
  return row.outcome === 'rejected' ? '不予处理' : '受理'
})

function openHandle(row: ReportAdminVO): void {
  current.value = row
  outcome.value = 'handled'
  // 复选默认勾选（页面规格 反馈与举报.md §B3）；被举报评价已隐藏时置灰锁定，取值仍为 true（见 hideLocked）
  hideReview.value = true
  replyTitle.value = ''
  replyContent.value = ''
  open.value = true
}

async function submitHandle(): Promise<void> {
  const row = current.value
  if (!row) return
  if (outcome.value === 'rejected' && !replyContent.value.trim()) {
    ElMessage.warning('不予处理时必须填写不采纳原因')
    return
  }
  submitting.value = true
  try {
    await handleReport(row.id, {
      outcome: outcome.value,
      // hideReview 仅受理时消费：不予处理不下发
      hideReview: outcome.value === 'handled' ? hideReview.value : undefined,
      replyTitle: replyTitle.value.trim() || undefined,
      replyContent: replyContent.value.trim() || undefined,
    })
    ElMessage.success('已处置')
    open.value = false
    await reload()
  } catch (e) {
    // 已处置再处理（400）/ 记录不存在（4001）→ 后端原文
    fail(e, '处置失败')
  } finally {
    submitting.value = false
  }
}

function reset(): void {
  fStatus.value = ''
  fUserId.value = ''
  reloadFirstPage()
}

/** 已生效筛选条件（供 `ActiveFilters` 回显；空值不进列表） */
const activeFilters = computed(() => {
  const out: { key: string; label: string; value: string }[] = []
  if (fStatus.value) {
    out.push({
      key: 'status',
      label: '状态',
      value: fStatus.value === 'pending' ? '待处理' : '已处理',
    })
  }
  if (fUserId.value !== '' && fUserId.value !== null)
    out.push({ key: 'userId', label: '举报人', value: `#${fUserId.value}` })
  return out
})

/** 清除单个筛选条件并重查 */
function clearFilter(key: string): void {
  if (key === 'status') fStatus.value = ''
  if (key === 'userId') fUserId.value = ''
  reloadFirstPage()
}

onMounted(() => reloadFirstPage())
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>举报管理</h2></div>

    <div class="card filters">
      <select class="form-input" v-model="fStatus" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="handled">已处理</option>
      </select>
      <input
        class="form-input"
        v-model.number="fUserId"
        type="number"
        min="1"
        placeholder="举报人 ID"
        @keyup.enter="reloadFirstPage"
      />
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
      <!-- 已生效筛选条件回显：让管理员一眼看出当前结果被什么条件筛出（可点单个清除） -->
      <ActiveFilters :items="activeFilters" @remove="clearFilter" @clear="reset" />
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 分页 ⑥ 会话失效 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无举报"
      @retry="reload"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>举报原因</th>
            <th>被举报评价</th>
            <th>举报人</th>
            <th>状态</th>
            <th>提交时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <!-- 原因中文名由字典实时翻译（停用原因仍可译出），端上零硬编码 -->
            <td>{{ row.optionName }}</td>
            <!-- 被举报评价：摘要 + 菜品名 + 可见性；已删除 ⇒ 整格置灰展示占位说明 -->
            <td>
              <template v-if="row.reviewDeleted">
                <span class="muted">被举报评价已删除</span>
              </template>
              <template v-else>
                <div class="review-summary">{{ row.reviewContent || '（无内容）' }}</div>
                <div class="review-meta muted">
                  {{ row.reviewDishName ?? '—' }}
                  <span v-if="row.reviewId != null" class="muted"> #{{ row.reviewId }}</span>
                  <StatusTag :status="row.reviewHidden ? 'hidden' : 'visible'" kind="review" />
                </div>
              </template>
            </td>
            <!-- 举报人昵称：服务端联表带出，恒可读到（注销账号为「已注销用户」） -->
            <td>{{ row.userName }}</td>
            <td><StatusTag :status="row.status" kind="feedback" /></td>
            <td class="muted">{{ formatDateTime(row.createdAt) }}</td>
            <td class="actions">
              <button v-if="canWrite()" class="link" type="button" @click="openHandle(row)">
                {{ row.status === 'pending' ? '处置' : '详情' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <p class="foot-note">
        「举报原因 / 被举报评价 / 举报人 / 状态」是快速查阅列；评价原文与处置入口都在行内 「处置 /
        详情」内。
      </p>

      <Pager
        :total="total"
        :page="page"
        :page-count="pageCount"
        @prev="prevPage"
        @next="nextPage"
      />
    </div>

    <!-- 处置 / 详情抽屉 -->
    <BaseDrawer :title="isPending ? '处置举报' : '举报详情'" :open="open" @close="open = false">
      <!-- 只读内容区：被举报评价（正文全文 + 可见性；已删除 ⇒ 占位说明，不显示可见性） -->
      <div class="ctx">
        <div class="ctx-label">
          被举报评价 · {{ current?.reviewDishName ?? '—' }}
          <span v-if="current?.reviewId != null" class="muted"> #{{ current.reviewId }}</span>
        </div>
        <div class="ctx-body">{{ current?.reviewContent ?? '被举报评价已删除' }}</div>
        <div v-if="!current?.reviewDeleted" class="ctx-meta">
          <StatusTag :status="current?.reviewHidden ? 'hidden' : 'visible'" kind="review" />
        </div>
      </div>

      <div class="field">
        <label>举报原因</label>
        <div class="readonly">{{ current?.optionName }}</div>
      </div>

      <!-- 已处理 → 只读结论与回执（分两组：处理结果 / 时间与标识） -->
      <div v-if="!isPending" class="detail-group">
        <div class="detail-group-title">处理结果</div>
        <div class="detail-meta detail-meta--grid">
          <DetailMetaRow k="处理结论" span>{{ outcomeLabel }}</DetailMetaRow>
          <div class="meta-row meta-row--span">
            <span class="meta-key">举报人</span>
            <span class="meta-val">
              {{ current?.userName }}
              <span class="muted"> #{{ current?.userId }}</span>
            </span>
          </div>
          <DetailMetaRow k="处置人">{{ current?.handledByName ?? '—' }}</DetailMetaRow>
          <DetailMetaRow k="回执标题">{{ current?.replyTitle ?? '—' }}</DetailMetaRow>
          <DetailMetaRow k="处置文案" span>{{ current?.replyContent || '—' }}</DetailMetaRow>
        </div>
      </div>

      <div v-if="!isPending" class="detail-group">
        <div class="detail-group-title">时间与标识</div>
        <div class="detail-meta detail-meta--grid">
          <DetailMetaRow k="举报 ID" num>#{{ current?.id }}</DetailMetaRow>
          <DetailMetaRow k="提交时间">{{ formatDateTime(current?.createdAt) }}</DetailMetaRow>
          <DetailMetaRow k="处置时间">{{ formatDateTime(current?.handledAt) }}</DetailMetaRow>
        </div>
      </div>

      <!-- 待处理 → 处置表单（三输入与反馈处置共用同一表单体；hideReview 复选仅本页有） -->
      <ModerationHandleForm
        v-if="isPending"
        v-model:outcome="outcome"
        v-model:reply-title="replyTitle"
        v-model:reply-content="replyContent"
        v-model:hide-review="hideReview"
        handled-label="受理"
        rejected-label="不予处理"
        recipient="举报人"
        show-hide-review
        :hide-locked="hideLocked"
      />

      <template #actions>
        <template v-if="isPending">
          <button class="btn-secondary" type="button" @click="open = false">取消</button>
          <button
            class="btn-primary"
            type="button"
            :disabled="submitting"
            v-press
            @click="submitHandle"
          >
            {{ submitting ? '提交中…' : '提交处置' }}
          </button>
        </template>
        <template v-else>
          <button class="btn-secondary" type="button" @click="open = false">关闭</button>
        </template>
      </template>
    </BaseDrawer>
  </div>
</template>

<style scoped>
/* 筛选区内控件定宽（`.filters` 容器样式已收敛到全局 shared.css） */
.filters .form-input {
  width: 200px;
}
/* 被举报评价摘要：单行截断（完整内容在抽屉内看） */
.review-summary {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-1);
  font-size: var(--font-xs);
}
.ctx-meta {
  margin-top: var(--space-2);
}
</style>
