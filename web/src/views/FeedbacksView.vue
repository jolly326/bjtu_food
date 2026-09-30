<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listFeedbacks, handleFeedback } from '@/api/feedbacks'
import type { FeedbackAdminVO, FeedbackListParams, FeedbackStatus } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import StatusTag from '@/components/StatusTag.vue'
import BaseModal from '@/components/BaseModal.vue'
import StateBox from '@/components/StateBox.vue'

const fStatus = ref<FeedbackStatus | ''>('')
const fType = ref<'bug' | 'suggestion' | 'other' | ''>('')
const fKeyword = ref('')

function params(): FeedbackListParams {
  return {
    status: fStatus.value || undefined,
    type: fType.value || undefined,
    keyword: fKeyword.value || undefined,
  }
}

const { items, loading, finished, error, load } = usePagedList<FeedbackAdminVO>(
  (page, pageSize) => listFeedbacks({ page, pageSize, ...params() }),
  20,
)

const typeLabel = (t: string): string =>
  ({ bug: '缺陷', suggestion: '建议', other: '其他' })[t] ?? t

const open = ref(false)
const current = ref<FeedbackAdminVO | null>(null)
const reply = ref('')
const outcome = ref<'handled' | 'rejected'>('handled')
const rejectReason = ref('')
const submitting = ref(false)

function openHandle(r: FeedbackAdminVO): void {
  current.value = r
  reply.value = ''
  outcome.value = 'handled'
  rejectReason.value = ''
  open.value = true
}
async function submitHandle(): Promise<void> {
  if (!current.value) return
  if (!reply.value.trim()) {
    ElMessage.warning('请填写处理回复')
    return
  }
  if (outcome.value === 'rejected' && !rejectReason.value.trim()) {
    ElMessage.warning('请填写不采纳原因')
    return
  }
  submitting.value = true
  try {
    await handleFeedback(current.value.id, {
      reply: reply.value,
      outcome: outcome.value,
      rejectReason: outcome.value === 'rejected' ? rejectReason.value : undefined,
    })
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
  fType.value = ''
  fKeyword.value = ''
  load(true)
}

onMounted(() => load(true))
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>意见反馈管理</h2></div>

    <div class="card filters">
      <select class="form-input" v-model="fStatus" @change="search">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="handled">已处理</option>
      </select>
      <select class="form-input" v-model="fType" @change="search">
        <option value="">全部类型</option>
        <option value="bug">缺陷</option>
        <option value="suggestion">建议</option>
        <option value="other">其他</option>
      </select>
      <input class="form-input" v-model="fKeyword" placeholder="内容 / 回复" @keyup.enter="search" />
      <button class="btn-primary" type="button" v-press @click="search">查询</button>
      <button class="btn-ghost" type="button" @click="reset">重置</button>
    </div>

    <StateBox v-if="loading && !items.length" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="search" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>类型</th>
            <th>提交人</th>
            <th>内容</th>
            <th>配图</th>
            <th>状态</th>
            <th>回复 / 原因</th>
            <th>时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in items" :key="r.id">
            <td>{{ typeLabel(r.type) }}</td>
            <td>{{ r.userNickname ?? '游客' }}</td>
            <td class="ellipsis">{{ r.content }}</td>
            <td>
              <div class="thumbs">
                <img v-for="(img, i) in r.images" :key="i" :src="img" alt="" />
              </div>
            </td>
            <td><StatusTag :status="r.status" kind="feedback" /></td>
            <td class="ellipsis">
              <span v-if="r.reply">{{ r.reply }}</span>
              <span v-else class="muted">—</span>
              <div v-if="r.rejectReason" class="muted">原因：{{ r.rejectReason }}</div>
            </td>
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
            <td colspan="8"><StateBox status="empty" /></td>
          </tr>
        </tbody>
      </table>
      <div class="pager">
        <span v-if="loading" class="muted">加载中…</span>
        <button v-else-if="!finished" class="btn-ghost" type="button" @click="load()">加载更多</button>
        <span v-else-if="items.length" class="muted">已全部加载</span>
      </div>
    </div>

    <BaseModal title="处理反馈" :open="open" @close="open = false">
      <div class="ctx">
        <div class="ctx-label">反馈内容</div>
        <div class="ctx-body">{{ current?.content }}</div>
      </div>
      <div class="field">
        <label>处理结论</label>
        <div class="radios">
          <label><input type="radio" value="handled" v-model="outcome" /> 通过 / 已处理</label>
          <label><input type="radio" value="rejected" v-model="outcome" /> 不采纳 / 退回</label>
        </div>
      </div>
      <div class="field" v-if="outcome === 'rejected'">
        <label>不采纳原因（1~200 字，必填）</label>
        <input class="form-input" v-model="rejectReason" />
      </div>
      <div class="field">
        <label>回复（1~1000 字，必填）</label>
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
