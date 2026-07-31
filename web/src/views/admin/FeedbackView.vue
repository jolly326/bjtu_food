<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { usePageStore } from '@/stores/pageStore'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import FormDialog from '@/components/FormDialog.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import { ChatDotRound, EditPen, CircleCheck } from '@element-plus/icons-vue'
import type { FeedbackAdminVO } from '@/api/feedback'

const toast = useToastStore()
const page = usePageStore()
page.setPage({
  breadcrumbs: [{ label: '消息反馈' }, { label: '反馈处理' }],
  searchPlaceholder: '搜索内容/联系方式/提交人...',
})

const typeLabel: Record<string, string> = { suggestion: '功能建议', error: '内容纠错', report: '举报', other: '其他' }
const statusTag: Record<string, 'warning' | 'success'> = { pending: 'warning', handled: 'success' }
const statusText: Record<string, string> = { pending: '待处理', handled: '已处理' }

// 状态筛选
type FbStatus = 'all' | 'pending' | 'handled'
const statusTab: Record<FbStatus, string> = { all: '全部', pending: '待处理', handled: '已处理' }
const activeStatus = ref<FbStatus>('all')

const loading = ref(false)
const error = ref('')
const rows = ref<FeedbackAdminVO[]>([])

const searchQuery = computed(() => page.searchQuery.trim().toLowerCase())
const filtered = computed(() => {
  const q = searchQuery.value
  if (!q) return rows.value
  return rows.value.filter(
    r => (r.content || '').toLowerCase().includes(q)
      || (r.contact || '').toLowerCase().includes(q)
      || (r.userNickname || '').toLowerCase().includes(q),
  )
})

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const { feedbackApi } = await import('@/api')
    const status = activeStatus.value === 'all' ? undefined : activeStatus.value
    const res = await feedbackApi.listFeedbacks({ status, pageSize: 200 })
    rows.value = res.list
  } catch (e: any) {
    error.value = e.message || '加载反馈列表失败'
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadList)

async function onStatusChange(s: FbStatus) {
  activeStatus.value = s
  await loadList()
}

// ===== 详情 + 处理抽屉 =====
const detail = ref<FeedbackAdminVO | null>(null)
const reply = ref('')
const replyError = ref('')
const processingId = ref<number | null>(null)

function openDetail(row: FeedbackAdminVO) {
  detail.value = row
  reply.value = row.reply || ''
  replyError.value = ''
}
function closeDetail() { detail.value = null }

async function submitHandle() {
  if (!detail.value) return
  if (!reply.value.trim()) {
    replyError.value = '请填写处理说明/回复'
    return
  }
  processingId.value = Number(detail.value.id)
  try {
    const { feedbackApi } = await import('@/api')
    await feedbackApi.handleFeedback(Number(detail.value.id), reply.value.trim())
    toast.success('反馈已标记处理')
    await loadList()
    closeDetail()
  } catch (e: any) {
    toast.error(e.message || '处理失败')
  } finally {
    processingId.value = null
  }
}

function fmtTime(v: string): string {
  if (!v) return '—'
  const d = new Date(v)
  return isNaN(d.getTime()) ? v : d.toLocaleString('zh-CN')
}

async function copyMomentLink(momentId?: number) {
  if (momentId == null) return
  const link = `pages/moment/detail?id=${momentId}`
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(link)
    } else {
      const ta = document.createElement('textarea')
      ta.value = link
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    toast.success('动态链接已复制')
  } catch {
    toast.error('复制失败，请手动记录：' + link)
  }
}
</script>

<template>
  <PageContainer>
    <PageHeader title="反馈与举报处理" :count="rows.length" />

    <FilterBar>
      <template #tabs>
        <button v-for="s in (['all','pending','handled'] as FbStatus[])" :key="s"
          class="tab status-tab" :class="{ 'tab-on': activeStatus === s }" v-press @click="onStatusChange(s)">
          {{ statusTab[s] }}
        </button>
      </template>
    </FilterBar>

    <DataTable
      :columns="[
        { prop: 'type', label: '类型', width: '120px', align: 'center' },
        { prop: 'related', label: '关联动态', width: '140px', align: 'center' },
        { prop: 'content', label: '内容' },
        { prop: 'contact', label: '联系方式', width: '160px' },
        { prop: 'submitter', label: '提交人', width: '140px' },
        { prop: 'time', label: '提交时间', width: '170px' },
        { prop: 'status', label: '状态', width: '100px', align: 'center' },
        { prop: 'actions', label: '操作', width: '120px', align: 'center' },
      ]"
      :rows="filtered"
      :loading="loading"
      :error="error"
      empty-text="暂无反馈"
    >
      <template #cell-type="{ row }">
        <span class="type-pill"><el-icon class="type-ico"><ChatDotRound /></el-icon>{{ typeLabel[row.type] || row.type }}</span>
      </template>
      <template #cell-related="{ row }">
        <span v-if="row.relatedType === 'moment'" class="related">动态#{{ row.relatedId }}</span>
        <span v-else class="muted">—</span>
      </template>
      <template #cell-content="{ row }">
        <button class="link" v-press @click="openDetail(row)">{{ row.content || '（无内容）' }}</button>
      </template>
      <template #cell-contact="{ row }"><span class="muted">{{ row.contact || '—' }}</span></template>
      <template #cell-submitter="{ row }">{{ row.userNickname || ('用户#' + row.userId) }}</template>
      <template #cell-time="{ row }">{{ fmtTime(row.createdAt) }}</template>
      <template #cell-status="{ row }">
        <StatusTag :type="statusTag[row.status] || 'warning'" :text="statusText[row.status] || row.status" />
      </template>
      <template #actions="{ row }">
        <button class="link" v-press @click="openDetail(row)">
          <el-icon class="act-ico"><EditPen /></el-icon>{{ row.status === 'handled' ? '查看' : '处理' }}
        </button>
      </template>
    </DataTable>

    <!-- 详情 + 处理抽屉 -->
    <FormDialog
      :show="!!detail"
      :title="detail?.status === 'handled' ? '反馈详情' : '处理反馈'"
      :width="520"
      :footer="detail?.status !== 'handled'"
      :confirm-text="'标记处理'"
      :confirm-disabled="!reply.trim()"
      :confirm-loading="processingId !== null"
      @close="closeDetail"
      @confirm="submitHandle"
    >
      <div v-if="detail" class="detail">
        <div class="detail-row"><span class="dl">类型</span>
          <span class="dv"><span class="type-pill">{{ typeLabel[detail.type] || detail.type }}</span></span>
        </div>
        <div class="detail-row"><span class="dl">提交人</span><span class="dv">{{ detail.userNickname || ('用户#' + detail.userId) }}</span></div>
        <div class="detail-row"><span class="dl">联系方式</span><span class="dv muted">{{ detail.contact || '—' }}</span></div>
        <div class="detail-row"><span class="dl">提交时间</span><span class="dv">{{ fmtTime(detail.createdAt) }}</span></div>
        <div class="detail-row" v-if="detail.relatedType === 'moment'">
          <span class="dl">关联动态</span>
          <span class="dv">
            <span class="related">动态 #{{ detail.relatedId }}</span>
            <button class="link" v-press @click="copyMomentLink(detail.relatedId)">复制链接</button>
          </span>
        </div>
        <div class="detail-row detail-row-desc"><span class="dl">内容</span><span class="dv text-desc">{{ detail.content || '（无）' }}</span></div>
        <div class="detail-row" v-if="detail.status === 'handled'"><span class="dl">处理时间</span><span class="dv">{{ fmtTime(detail.handledAt) }}</span></div>
        <div class="detail-row detail-row-desc" v-if="detail.reply"><span class="dl">历史回复</span><span class="dv text-desc">{{ detail.reply }}</span></div>

        <div class="reply-area" v-if="detail.status !== 'handled'">
          <label>处理说明 / 回复 <span class="required">*</span></label>
          <textarea v-model="reply" rows="4" placeholder="请填写处理说明或回复内容..."></textarea>
          <p v-if="replyError" class="field-error">{{ replyError }}</p>
        </div>
        <div v-else class="handled-tip"><el-icon><CircleCheck /></el-icon>该反馈已处理</div>
      </div>
      <template v-if="detail?.status === 'handled'" #actions>
        <button class="btn-cancel" v-press @click="closeDetail">关闭</button>
      </template>
    </FormDialog>
  </PageContainer>
</template>

<style scoped>
.status-tabs { margin-bottom: var(--space-4); }
.status-tab { font-size: var(--font-sm); }
.tab-count { font-size: var(--font-sm); color: var(--text-muted); margin-left: var(--space-1); }

.type-pill { display: inline-flex; align-items: center; gap: var(--space-1); padding: 2px var(--space-2); border-radius: var(--radius-pill); background: var(--color-primary-bg); color: var(--color-primary); font-size: var(--font-xs); font-weight: var(--weight-medium); }
.related { display: inline-flex; align-items: center; padding: 2px var(--space-2); border-radius: var(--radius-pill); background: var(--color-error-bg); color: var(--color-error); font-size: var(--font-xs); font-weight: var(--weight-medium); }
.type-ico { width: 13px; height: 13px; }
.act-ico { width: 13px; height: 13px; vertical-align: -2px; }
.muted { color: var(--text-light); }

.detail { display: flex; flex-direction: column; gap: var(--space-3); }
.detail-row { display: flex; gap: var(--space-3); font-size: var(--font-base); }
.detail-row-desc { align-items: flex-start; }
.dl { width: 64px; flex-shrink: 0; color: var(--text-muted); }
.dv { color: var(--text-primary); flex: 1; }
.dv.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); white-space: pre-wrap; }
.reply-area { margin-top: var(--space-4); border-top: 1px solid var(--border-light); padding-top: var(--space-4); }
.reply-area label { display: block; font-size: var(--font-sm); color: var(--text-secondary); margin-bottom: var(--space-2); }
.reply-area textarea {
  width: 100%; padding: var(--space-2) var(--space-3); border: 1px solid var(--border-strong);
  border-radius: var(--radius); font-size: var(--font-sm); outline: none; resize: vertical; box-sizing: border-box;
  background: var(--bg-card); transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out);
}
.reply-area textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent); }
.required { color: var(--color-error); }
.handled-tip { display: flex; align-items: center; gap: var(--space-2); margin-top: var(--space-3); color: var(--color-success); font-size: var(--font-sm); }
</style>
