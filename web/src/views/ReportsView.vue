<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listReports, handleReport } from '@/api/reports'
import type { ReportAdminVO, ReportListParams, FeedbackStatus } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import StatusTag from '@/components/StatusTag.vue'
import BaseModal from '@/components/BaseModal.vue'
import StateBox from '@/components/StateBox.vue'

const fStatus = ref<FeedbackStatus | ''>('')
const fReason = ref('')

function params(): ReportListParams {
  return {
    status: fStatus.value || undefined,
    reason: fReason.value || undefined,
  }
}

const { items, loading, finished, error, load } = usePagedList<ReportAdminVO>(
  (page, pageSize) => listReports({ page, pageSize, ...params() }),
  20,
)

const actionLabel = (a: string | null): string =>
  a === 'hideReview' ? '已隐藏被举报评价' : a === 'dismiss' ? '不予处理' : '—'

const open = ref(false)
const current = ref<ReportAdminVO | null>(null)
const action = ref<'hideReview' | 'dismiss'>('dismiss')
const reply = ref('')
const submitting = ref(false)

function openHandle(r: ReportAdminVO): void {
  current.value = r
  action.value = 'dismiss'
  reply.value = ''
  open.value = true
}
async function submitHandle(): Promise<void> {
  if (!current.value) return
  if (!reply.value.trim()) {
    ElMessage.warning('请填写回复')
    return
  }
  submitting.value = true
  try {
    await handleReport(current.value.id, { action: action.value, reply: reply.value })
    ElMessage.success('已处理')
    open.value = false
    load(true)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '处理失败')
  } finally {
    submitting.value = false
  }
}
function search(): void {
  load(true)
}
function reset(): void {
  fStatus.value = ''
  fReason.value = ''
  load(true)
}

onMounted(() => load(true))
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>举报管理</h2></div>

    <div class="card filters">
      <select class="form-input" v-model="fStatus" @change="search">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="handled">已处理</option>
      </select>
      <input class="form-input" v-model="fReason" placeholder="举报原因（机器值）" @keyup.enter="search" />
      <button class="btn-primary" type="button" v-press @click="search">查询</button>
      <button class="btn-ghost" type="button" @click="reset">重置</button>
    </div>

    <StateBox v-if="loading && !items.length" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="search" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>原因</th>
            <th>被举报评价</th>
            <th>评价状态</th>
            <th>举报人</th>
            <th>补充说明</th>
            <th>状态</th>
            <th>处置</th>
            <th>时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in items" :key="r.id">
            <td>{{ r.reasonLabel }}</td>
            <td class="ellipsis">
              <span v-if="r.reviewContent">{{ r.reviewContent }}</span>
              <span v-else class="muted">评价已删除</span>
              <div v-if="r.reviewDishName" class="muted">{{ r.reviewDishName }}</div>
            </td>
            <td>
              <span :class="r.reviewHidden ? 'tag tag-red' : 'tag tag-green'">
                {{ r.reviewHidden ? '已隐藏' : '正常' }}
              </span>
            </td>
            <td>{{ r.userNickname ?? '游客' }}</td>
            <td class="ellipsis">{{ r.content || '—' }}</td>
            <td><StatusTag :status="r.status" kind="feedback" /></td>
            <td class="muted">{{ actionLabel(r.action) }}</td>
            <td class="muted">{{ r.createdAt }}</td>
            <td class="actions">
              <button
                v-if="r.status === 'pending'"
                class="link"
                type="button"
                @click="openHandle(r)"
              >
                处理
              </button>
              <span v-else class="muted">已处理</span>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="9"><StateBox status="empty" /></td>
          </tr>
        </tbody>
      </table>
      <div class="pager">
        <span v-if="loading" class="muted">加载中…</span>
        <button v-else-if="!finished" class="btn-ghost" type="button" @click="load()">加载更多</button>
        <span v-else-if="items.length" class="muted">已全部加载</span>
      </div>
    </div>

    <BaseModal title="处理举报" :open="open" @close="open = false">
      <div class="ctx">
        <div class="ctx-label">被举报评价</div>
        <div class="ctx-body">
          {{ current?.reviewContent ?? '（评价已删除）' }}
        </div>
      </div>
      <div class="field">
        <label>处置动作</label>
        <div class="radios">
          <label><input type="radio" value="dismiss" v-model="action" /> 不予处理</label>
          <label><input type="radio" value="hideReview" v-model="action" /> 隐藏被举报评价</label>
        </div>
      </div>
      <div class="field">
        <label>回复举报人（1~1000 字，必填）</label>
        <textarea class="form-textarea" v-model="reply" rows="3" />
      </div>
      <template #actions>
        <button class="btn-ghost" type="button" @click="open = false">取消</button>
        <button class="btn-primary" type="button" :disabled="submitting" v-press @click="submitHandle">
          {{ submitting ? '提交中…' : '提交' }}
        </button>
      </template>
    </BaseModal>
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
.pager {
  padding: var(--space-4);
  text-align: center;
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
}
.radios {
  display: flex;
  gap: var(--space-5);
}
.radios label {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}
</style>
