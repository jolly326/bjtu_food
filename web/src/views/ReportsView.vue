<script setup lang="ts">
/**
 * B3 举报管理（页面规格见 [反馈与举报.md](../../../docs/ui/web/反馈与举报.md) §B3）。
 *
 * <p>要点：与 B2 **同一列表端点**（`GET /admin/feedbacks?category=report`）与**同一处置端点**
 * （`PUT /admin/feedbacks/{id}`）；列表私有列 = 被举报评价摘要 + 评价可见性；
 * 处置抽屉含**「同时隐藏被举报评价」复选**（默认勾选；评价已隐藏时**置灰且不可改**）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fail } from '@/utils/error'
import { handleFeedback, listFeedbacks } from '@/api/feedbacks'
import type {
  AdminPage,
  FeedbackListParams,
  FeedbackStatus,
  ReportAdminVO,
} from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import BaseDrawer from '@/components/BaseDrawer.vue'

const fStatus = ref<FeedbackStatus | ''>('')
const fReason = ref('')

function params(): FeedbackListParams {
  return {
    category: 'report',
    status: fStatus.value || undefined,
    keyword: fReason.value || undefined,
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
} = usePagedList<ReportAdminVO>(
  // B3 与 B2 同端点：`category=report` 时服务端在反馈行基础上补举报私有字段
  // （reason/reasonLabel/reviewId/reviewContent/reviewDishName/reviewHidden）⇒ 收窄为本页 VO。
  async (pageNo, pageSize) =>
    (await listFeedbacks({ page: pageNo, pageSize, ...params() })) as AdminPage<ReportAdminVO>,
)

/* ===== 处置（抽屉） ===== */
const open = ref(false)
const current = ref<ReportAdminVO | null>(null)
const outcome = ref<'handled' | 'rejected'>('handled')
const hideReview = ref(true)
const reply = ref('')
const rejectReason = ref('')
const submitting = ref(false)

/** 评价已隐藏 ⇒ 复选置灰且恒为 true（不做无意义的重复隐藏） */
const hideLocked = computed(() => current.value?.reviewHidden === true)

const canSubmit = computed(() =>
  outcome.value === 'rejected' ? rejectReason.value.trim().length > 0 : true,
)

function openHandle(row: ReportAdminVO): void {
  current.value = row
  outcome.value = 'handled'
  hideReview.value = row.reviewHidden ? true : true
  reply.value = ''
  rejectReason.value = ''
  open.value = true
}

async function submitHandle(): Promise<void> {
  const row = current.value
  if (!row) return
  if (!canSubmit.value) {
    ElMessage.warning('不采纳时必须填写原因')
    return
  }
  submitting.value = true
  try {
    await handleFeedback(row.id, {
      outcome: outcome.value,
      hideReview: outcome.value === 'handled' ? hideReview.value : false,
      reply: reply.value.trim() || undefined,
      rejectReason: outcome.value === 'rejected' ? rejectReason.value.trim() : undefined,
    })
    ElMessage.success('已处置')
    open.value = false
    await reload()
  } catch (e) {
    fail(e, '处置失败')
  } finally {
    submitting.value = false
  }
}

function reset(): void {
  fStatus.value = ''
  fReason.value = ''
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
      <input class="form-input" v-model="fReason" placeholder="举报原因" @keyup.enter="reloadFirstPage" />
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
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
            <th>评价状态</th>
            <th>举报人</th>
            <th>补充说明</th>
            <th>状态</th>
            <th>提交时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.reasonLabel }}</td>
            <td class="ellipsis">
              <span v-if="row.reviewContent">{{ row.reviewContent }}</span>
              <span v-else class="muted">评价已删除</span>
              <div v-if="row.reviewDishName" class="muted">{{ row.reviewDishName }}</div>
            </td>
            <td>
              <StatusTag :status="row.reviewHidden ? 'hidden' : 'visible'" kind="review" />
            </td>
            <td>{{ row.userNickname || '游客' }}</td>
            <td class="ellipsis">{{ row.content || '—' }}</td>
            <td><StatusTag :status="row.status" kind="feedback" /></td>
            <td class="muted">{{ row.createdAt }}</td>
            <td class="actions">
              <button v-if="row.status === 'pending'" class="link" type="button" @click="openHandle(row)">
                处置
              </button>
              <span v-else class="muted">已处置</span>
            </td>
          </tr>
        </tbody>
      </table>

      <Pager :total="total" :page="page" :page-count="pageCount" @prev="prevPage" @next="nextPage" />
    </div>

    <!-- 处置抽屉：被举报评价只读摘要 + 同时隐藏复选 + 结论 + 回复 -->
    <BaseDrawer title="处置举报" :open="open" @close="open = false">
      <div class="ctx">
        <div class="ctx-label">
          被举报评价 · {{ current?.reviewDishName ?? '菜品已删除' }}
        </div>
        <div class="ctx-body">{{ current?.reviewContent ?? '（评价已删除）' }}</div>
        <div class="ctx-meta">
          <StatusTag :status="current?.reviewHidden ? 'hidden' : 'visible'" kind="review" />
        </div>
      </div>

      <div class="field">
        <label>举报原因</label>
        <div class="readonly">{{ current?.reasonLabel }}<span v-if="current?.content"> · {{ current.content }}</span></div>
      </div>

      <div class="field">
        <label id="rep-outcome-label">处理结论</label>
        <div class="tag-options" role="radiogroup" aria-labelledby="rep-outcome-label">
          <button
            class="tag-option"
            type="button"
            role="radio"
            :aria-checked="outcome === 'handled'"
            :class="{ active: outcome === 'handled' }"
            @click="outcome = 'handled'"
          >
            成立 / 已处理
          </button>
          <button
            class="tag-option"
            type="button"
            role="radio"
            :aria-checked="outcome === 'rejected'"
            :class="{ active: outcome === 'rejected' }"
            @click="outcome = 'rejected'"
          >
            不成立 / 不予处理
          </button>
        </div>
      </div>

      <div class="field" v-if="outcome === 'handled'">
        <label class="check-line">
          <input type="checkbox" v-model="hideReview" :disabled="hideLocked" />
          <span>同时隐藏被举报评价{{ hideLocked ? '（该评价已隐藏）' : '（默认勾选）' }}</span>
        </label>
      </div>

      <div class="field" v-if="outcome === 'rejected'">
        <label for="rep-reject-reason">不成立原因（必填，≤200 字）</label>
        <input id="rep-reject-reason" class="form-input" v-model="rejectReason" maxlength="200" />
      </div>

      <div class="field">
        <label for="rep-reply">回复举报人（可选，≤600 字）</label>
        <textarea id="rep-reply" class="form-textarea" v-model="reply" rows="4" maxlength="600" />
        <div class="hint">{{ reply.length }} / 600</div>
      </div>

      <template #actions>
        <button class="btn-secondary" type="button" @click="open = false">取消</button>
        <button class="btn-primary" type="button" :disabled="submitting" v-press @click="submitHandle">
          {{ submitting ? '提交中…' : '提交处置' }}
        </button>
      </template>
    </BaseDrawer>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-4);
}
.filters .form-input {
  width: 200px;
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.hint {
  margin-top: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-xs);
  text-align: right;
}
.ctx {
  background: var(--bg-soft);
  border-radius: var(--radius);
  padding: var(--space-3);
  margin-bottom: var(--space-4);
}
.ctx-label {
  font-size: var(--font-xs);
  color: var(--text-muted);
  margin-bottom: var(--space-1);
}
.ctx-body {
  white-space: pre-wrap;
  color: var(--text-primary);
}
.ctx-meta {
  margin-top: var(--space-2);
}
.readonly {
  color: var(--text-secondary);
}
.check-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: var(--weight-regular);
}
.tag-options {
  display: flex;
  gap: var(--space-2);
}
</style>
