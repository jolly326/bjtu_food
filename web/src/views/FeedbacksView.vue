<script setup lang="ts">
/**
 * B2 意见反馈管理（页面规格见 [反馈与举报.md](../../../docs/ui/web/反馈与举报.md) §B2）。
 *
 * <p>要点：列表走**同一端点** `GET /admin/feedbacks?category=feedback`（B3 举报为 `category=report`）；
 * 处置载体 = **抽屉**（只读内容区 + 结论 + 回复 + 不采纳原因 ⇒ ≥5 控件）；
 * 类型只列**当前白名单**（`suggestion` / `add` / `error`；存量 `bug`/`other` 仅作展示回落）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fail } from '@/utils/error'
import { handleFeedback, listFeedbacks } from '@/api/feedbacks'
import type { FeedbackAdminVO, FeedbackListParams, FeedbackStatus } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import BaseDrawer from '@/components/BaseDrawer.vue'

const fStatus = ref<FeedbackStatus | ''>('')
const fType = ref('')
const fKeyword = ref('')

/** 类型文案（白名单 3 项 + 存量回落的 2 项） */
const TYPE_LABELS: Record<string, string> = {
  suggestion: '建议',
  add: '新增菜品',
  error: '信息有误',
  // 存量数据（不再允许新提交）
  bug: '缺陷（存量）',
  other: '其他（存量）',
}
const typeLabel = (t: string): string => TYPE_LABELS[t] ?? t

function params(): FeedbackListParams {
  return {
    category: 'feedback',
    status: fStatus.value || undefined,
    type: fType.value || undefined,
    keyword: fKeyword.value || undefined,
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
} = usePagedList<FeedbackAdminVO>((pageNo, pageSize) =>
  listFeedbacks({ page: pageNo, pageSize, ...params() }),
)

/* ===== 处置（抽屉） ===== */
const open = ref(false)
const current = ref<FeedbackAdminVO | null>(null)
const reply = ref('')
const outcome = ref<'handled' | 'rejected'>('handled')
const rejectReason = ref('')
const submitting = ref(false)

const canSubmit = computed(() => {
  if (outcome.value === 'rejected') return rejectReason.value.trim().length > 0
  return true
})

function openHandle(row: FeedbackAdminVO): void {
  current.value = row
  reply.value = ''
  outcome.value = 'handled'
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
  if (reply.value.length > 600) {
    ElMessage.warning('回复不能超过 600 字')
    return
  }
  submitting.value = true
  try {
    await handleFeedback(row.id, {
      outcome: outcome.value,
      reply: reply.value.trim() || undefined,
      rejectReason: outcome.value === 'rejected' ? rejectReason.value.trim() : undefined,
    })
    ElMessage.success('已处理')
    open.value = false
    await reload()
  } catch (e) {
    // 已处理再处理（400）/ 记录不存在（4001）→ 后端原文
    fail(e, '处理失败')
  } finally {
    submitting.value = false
  }
}

function reset(): void {
  fStatus.value = ''
  fType.value = ''
  fKeyword.value = ''
  reloadFirstPage()
}

onMounted(() => reloadFirstPage())
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>意见反馈管理</h2></div>

    <div class="card filters">
      <select class="form-input" v-model="fStatus" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="handled">已处理</option>
      </select>
      <select class="form-input" v-model="fType" @change="reloadFirstPage">
        <option value="">全部类型</option>
        <option value="suggestion">建议</option>
        <option value="add">新增菜品</option>
        <option value="error">信息有误</option>
      </select>
      <input
        class="form-input"
        v-model="fKeyword"
        placeholder="内容 / 回复"
        @keyup.enter="reloadFirstPage"
      />
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 分页 ⑥ 会话失效 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无反馈"
      @retry="reload"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>类型</th>
            <th>提交人</th>
            <th>内容</th>
            <th>配图</th>
            <th>状态</th>
            <th>回复 / 原因</th>
            <th>提交时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ typeLabel(row.type) }}</td>
            <!-- 匿名提交 userId = 0 → 服务端回落「游客」 -->
            <td>{{ row.userNickname || '游客' }}</td>
            <td class="ellipsis">{{ row.content }}</td>
            <td>
              <div class="thumbs">
                <img v-for="(img, i) in row.images" :key="i" :src="img" alt="" />
              </div>
            </td>
            <td><StatusTag :status="row.status" kind="feedback" /></td>
            <td class="ellipsis">
              <span v-if="row.reply">{{ row.reply }}</span>
              <span v-else class="muted">—</span>
              <div v-if="row.rejectReason" class="muted">原因：{{ row.rejectReason }}</div>
            </td>
            <td class="muted">{{ row.createdAt }}</td>
            <td class="actions">
              <button
                v-if="row.status === 'pending'"
                class="link"
                type="button"
                @click="openHandle(row)"
              >
                处理
              </button>
              <span v-else class="muted">已处理</span>
            </td>
          </tr>
        </tbody>
      </table>

      <Pager
        :total="total"
        :page="page"
        :page-count="pageCount"
        @prev="prevPage"
        @next="nextPage"
      />
    </div>

    <!-- 处置抽屉：只读内容区 + 结论 + 回复 + 不采纳原因 -->
    <BaseDrawer title="处理反馈" :open="open" @close="open = false">
      <div class="ctx">
        <div class="ctx-label">
          {{ typeLabel(current?.type ?? '') }} · {{ current?.userNickname || '游客' }}
        </div>
        <div class="ctx-body">{{ current?.content }}</div>
        <div v-if="current?.images?.length" class="thumbs ctx-thumbs">
          <img v-for="(img, i) in current.images" :key="i" :src="img" alt="" />
        </div>
      </div>

      <div class="field">
        <label id="fb-outcome-label">处理结论</label>
        <div class="tag-options" role="radiogroup" aria-labelledby="fb-outcome-label">
          <button
            class="tag-option"
            type="button"
            role="radio"
            :aria-checked="outcome === 'handled'"
            :class="{ active: outcome === 'handled' }"
            @click="outcome = 'handled'"
          >
            通过 / 已处理
          </button>
          <button
            class="tag-option"
            type="button"
            role="radio"
            :aria-checked="outcome === 'rejected'"
            :class="{ active: outcome === 'rejected' }"
            @click="outcome = 'rejected'"
          >
            不采纳 / 退回
          </button>
        </div>
      </div>

      <div class="field" v-if="outcome === 'rejected'">
        <label for="fb-reject-reason">不采纳原因（必填，≤200 字）</label>
        <input id="fb-reject-reason" class="form-input" v-model="rejectReason" maxlength="200" />
      </div>

      <div class="field">
        <label for="fb-reply">处理回复（可选，≤600 字）</label>
        <textarea
          id="fb-reply"
          class="form-textarea"
          v-model="reply"
          rows="4"
          maxlength="600"
          placeholder="将随站内回执下发给提交人；留空则回执用固定文案"
        />
        <div class="hint">{{ reply.length }} / 600</div>
      </div>

      <template #actions>
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
  width: 160px;
}
.thumbs {
  display: flex;
  gap: var(--space-1);
}
.thumbs img {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  object-fit: cover;
}
.ctx-thumbs {
  margin-top: var(--space-2);
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
.tag-options {
  display: flex;
  gap: var(--space-2);
}
</style>
