<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { usePageStore } from '@/stores/pageStore'
import FormDialog from '@/components/FormDialog.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import { useAdminStore } from '@/stores/adminStore'
import {
  Food, House, Shop, ChatDotRound, Document,
} from '@element-plus/icons-vue'
import type { ApplyActionVO, ApplyEntityType, ApplyStatus, ApplyType } from '@/api/apply'

type EntityTab = ApplyEntityType | 'review'

const toast = useToastStore()
const confirm = useConfirmStore()
const page = usePageStore()
const store = useAdminStore()

const entityLabel: Record<EntityTab, string> = { dish: '菜品', stall: '档口', canteen: '食堂', review: '评价' }
const entityIcon: Record<EntityTab, any> = { dish: Food, stall: Shop, canteen: House, review: ChatDotRound }
const entityTabs: EntityTab[] = ['dish', 'stall', 'canteen', 'review']
const applyTypeLabel: Record<ApplyType, string> = { NEW: '新增', CLOSE: '下架/关闭', CHANGE: '变更' }

// 当前选中的审核类型（默认菜品）
const entityType = ref<EntityTab>('dish')

// 面包屑随 tab 更新
watch(entityType, (t) => {
  page.setPage({
    breadcrumbs: [{ label: '审核中心' }, { label: `${entityLabel[t]}审核` }],
    searchPlaceholder: t === 'review' ? '搜索评价内容...' : '搜索名称/提交人...',
  })
}, { immediate: true })

type AuditStatus = 'pending' | 'approved' | 'rejected'
const statusLabel: Record<AuditStatus, string> = { pending: '待审核', approved: '已通过', rejected: '已退回' }
const statusTagType: Record<AuditStatus, 'warning' | 'success' | 'danger'> = {
  pending: 'warning',
  approved: 'success',
  rejected: 'danger',
}

const activeStatus = ref<AuditStatus>('pending')
const loading = ref(false)
const error = ref('')
const rows = ref<ApplyActionVO[]>([])
// 评价审核数据（复用 auditApi）
const reviews = ref<any[]>([])
const selectedIds = ref<number[]>([])

const isReview = computed(() => entityType.value === 'review')

const searchQuery = computed(() => page.searchQuery.trim().toLowerCase())
const filtered = computed(() => {
  const q = searchQuery.value
  if (!q) return isReview.value ? reviews.value : rows.value
  if (isReview.value) {
    return reviews.value.filter(r => (r.content || '').toLowerCase().includes(q))
  }
  return rows.value.filter(r =>
    (previewTitle(r) || '').toLowerCase().includes(q) ||
    (r.applicantName || '').toLowerCase().includes(q),
  )
})

async function loadList() {
  loading.value = true
  error.value = ''
  selectedIds.value = []
  try {
    if (isReview.value) {
      const { auditApi } = await import('@/api')
      // 评价三态映射：待审核/已通过 → 显示中(未隐藏)；已退回 → 已隐藏
      const isHidden = activeStatus.value === 'rejected' ? true : false
      reviews.value = await auditApi.listReviews(isHidden)
    } else {
      const { listApply } = await import('@/api/apply')
      rows.value = await listApply({ entityType: entityType.value as ApplyEntityType, status: activeStatus.value })
    }
  } catch (e: any) {
    error.value = e.message || '加载审核列表失败'
    rows.value = []
    reviews.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadList)

async function onEntityChange(t: EntityTab) {
  entityType.value = t
  activeStatus.value = 'pending'
  await loadList()
}
async function onStatusChange(s: AuditStatus) {
  activeStatus.value = s
  await loadList()
}

// ===== payload 预览标题（新增/变更类无实体名时，用 payload 预览） =====
function previewTitle(r: ApplyActionVO): string {
  const p = parsePayload(r.payload)
  if (p?.name) return String(p.name)
  if (p?.title) return String(p.title)
  return `${entityLabel[r.entityType]}#${r.entityId ?? '新'}`
}
function parsePayload(payload?: any): Record<string, any> | null {
  if (!payload) return null
  if (typeof payload === 'string') {
    try { return JSON.parse(payload) } catch { return null }
  }
  return payload
}

// ===== 详情抽屉 =====
const detail = ref<ApplyActionVO | null>(null)
const detailReview = ref<any | null>(null)
const rejectReason = ref('')
const rejectError = ref('')
const processingId = ref<number | null>(null)

function openDetail(row: ApplyActionVO) {
  detail.value = row
  rejectReason.value = ''
  rejectError.value = ''
}
function openReviewDetail(r: any) {
  detailReview.value = r
}
function closeDetail() { detail.value = null; detailReview.value = null }

const isPending = computed(() => activeStatus.value === 'pending')

async function approve(row: ApplyActionVO) {
  processingId.value = Number(row.id)
  try {
    const { approveApply } = await import('@/api/apply')
    await approveApply(Number(row.id))
    toast.success(`${entityLabel[row.entityType]}申请已通过`)
    await loadList()
    closeDetail()
  } catch (e: any) {
    toast.error(e.message || '操作失败')
  } finally {
    processingId.value = null
  }
}

async function reject(row: ApplyActionVO) {
  rejectError.value = ''
  if (!rejectReason.value.trim()) {
    rejectError.value = '退回必须填写原因'
    return
  }
  if (!await confirm.confirm('确定退回该申请？退回原因将回显学生端。')) return
  processingId.value = Number(row.id)
  try {
    const { rejectApply } = await import('@/api/apply')
    await rejectApply(Number(row.id), rejectReason.value.trim())
    toast.success(`${entityLabel[row.entityType]}申请已退回`)
    await loadList()
    closeDetail()
  } catch (e: any) {
    toast.error(e.message || '操作失败')
  } finally {
    processingId.value = null
  }
}

// ===== 评价审核操作（隐藏/显示/删除） =====
async function setHidden(r: any, hidden: boolean) {
  processingId.value = Number(r.id)
  try {
    const { auditApi } = await import('@/api')
    await auditApi.setReviewHidden(Number(r.id), hidden)
    toast.success(hidden ? '评价已隐藏' : '评价已显示')
    await loadList()
    if (detailReview.value && Number(detailReview.value.id) === Number(r.id)) closeDetail()
  } catch (e: any) {
    toast.error(e.message || '操作失败')
  } finally {
    processingId.value = null
  }
}
async function removeReview(r: any) {
  if (!await confirm.confirm('确定删除该评价？此操作不可恢复。')) return
  processingId.value = Number(r.id)
  try {
    const { auditApi } = await import('@/api')
    await auditApi.deleteReview(Number(r.id))
    toast.success('评价已删除')
    await loadList()
    if (detailReview.value && Number(detailReview.value.id) === Number(r.id)) closeDetail()
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  } finally {
    processingId.value = null
  }
}
function parseImages(img?: string): string[] {
  return (img || '').split('|||').map(s => s.trim()).filter(Boolean)
}
function getUserName(userId: number | bigint): string {
  const u = store.users.find(u => Number(u.id) === Number(userId))
  return u?.nickname || u?.username || `用户${userId}`
}
function getDishName(dishId: number | bigint): string {
  const d = store.dishes.find(d => Number(d.id) === Number(dishId))
  return d?.name || `菜品${dishId}`
}

// 帮助：把 payload 关键字段列成可读行
function payloadRows(r: ApplyActionVO): { k: string; v: string }[] {
  const p = parsePayload(r.payload)
  if (!p) return []
  const skip = ['images', 'image']
  return Object.entries(p)
    .filter(([k, v]) => v !== undefined && v !== null && v !== '' && !skip.includes(k))
    .map(([k, v]) => ({ k, v: typeof v === 'object' ? JSON.stringify(v) : String(v) }))
}
function applicantName(r: ApplyActionVO): string {
  if (r.applicantName) return r.applicantName
  const u = store.users.find(u => Number(u.id) === Number(r.applicantId))
  return u?.nickname || u?.username || `用户${r.applicantId ?? '-'}`
}
</script>

<template>
  <PageContainer>
    <PageHeader title="审核中心" :count="filtered.length" />

    <!-- 审核类型切换 tab bar（菜品 / 档口 / 食堂 / 评价） -->
    <FilterBar>
      <template #tabs>
        <button v-for="t in entityTabs" :key="t"
          class="tab entity-tab" :class="{ 'tab-on': entityType === t }" v-press @click="onEntityChange(t)">
          <el-icon class="entity-ico"><component :is="entityIcon[t]" /></el-icon>{{ entityLabel[t] }}
        </button>
      </template>
    </FilterBar>

    <!-- 审核状态分段（三态切换） -->
    <div class="tabs status-tabs">
      <button v-for="s in (['pending','approved','rejected'] as AuditStatus[])" :key="s"
        class="tab status-tab" :class="{ 'tab-on': activeStatus === s }" @click="onStatusChange(s)">
        {{ statusLabel[s] }}
      </button>
      <span class="tab-count">{{ statusLabel[activeStatus] }} {{ filtered.length }} 条</span>
    </div>

    <!-- 评价审核表 -->
    <DataTable
      v-if="isReview"
      :columns="[
        { prop: 'user', label: '用户' },
        { prop: 'rating', label: '评分', width: '120px' },
        { prop: 'content', label: '内容' },
        { prop: 'dish', label: '菜品' },
        { prop: 'status', label: '状态', width: '120px', align: 'center' },
        { prop: 'actions', label: '操作', width: '200px', align: 'center' },
      ]"
      :rows="filtered"
      :loading="loading"
      :error="error"
      empty-text="暂无评价"
    >
      <template #cell-user="{ row }">{{ getUserName(row.user_id) }}</template>
      <template #cell-rating="{ row }">
        <span class="stars">{{ '★'.repeat(row.rating) }}<span class="star-off">{{ '★'.repeat(5 - row.rating) }}</span></span>
      </template>
      <template #cell-content="{ row }">
        <button class="link" v-press @click="openReviewDetail(row)">{{ row.content || '（无文字内容）' }}</button>
      </template>
      <template #cell-dish="{ row }">{{ getDishName(row.dish_id) }}</template>
      <template #cell-status="{ row }">
        <StatusTag :type="row.is_hidden ? 'danger' : 'success'" :text="row.is_hidden ? '已隐藏' : '显示中'" />
      </template>
      <template #actions="{ row }">
        <button v-if="!row.is_hidden" class="link danger" :disabled="processingId === Number(row.id)" v-press @click="setHidden(row, true)">隐藏</button>
        <button v-else class="link primary-text" :disabled="processingId === Number(row.id)" v-press @click="setHidden(row, false)">显示</button>
        <button class="link danger" :disabled="processingId === Number(row.id)" v-press @click="removeReview(row)">删除</button>
      </template>
    </DataTable>

    <!-- 实体审核表（菜品 / 档口 / 食堂） -->
    <DataTable
      v-else
      :columns="[
        { prop: 'title', label: '名称 / 预览' },
        { prop: 'applyType', label: '申请类型', width: '140px', align: 'center' },
        { prop: 'applicant', label: '提交人', width: '160px' },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },
        { prop: 'time', label: '提交时间', width: '160px' },
        { prop: 'actions', label: '操作', width: '160px', align: 'center' },
      ]"
      :rows="filtered"
      :loading="loading"
      :error="error"
      :empty-text="`暂无${statusLabel[activeStatus]}的${entityLabel[entityType]}申请`"
    >
      <template #cell-title="{ row }">
        <button class="link" v-press @click="openDetail(row)">{{ previewTitle(row) }}</button>
      </template>
      <template #cell-applyType="{ row }">
        <StatusTag
          :type="row.applyType === 'NEW' ? 'info' : row.applyType === 'CLOSE' ? 'warning' : 'gray'"
          :text="applyTypeLabel[row.applyType as ApplyType]"
        />
      </template>
      <template #cell-applicant="{ row }">{{ applicantName(row) }}</template>
      <template #cell-status="{ row }">
        <StatusTag :type="statusTagType[row.status as AuditStatus]" :text="statusLabel[row.status as AuditStatus]" />
      </template>
      <template #cell-time="{ row }">
        {{ row.createdAt ? new Date(row.createdAt).toLocaleDateString('zh-CN') : '—' }}
      </template>
      <template #actions="{ row }">
        <template v-if="isPending">
          <button class="link primary-text" :disabled="processingId === Number(row.id)" v-press @click="approve(row)">通过</button>
          <button class="link danger" :disabled="processingId === Number(row.id)" v-press @click="openDetail(row)">退回</button>
        </template>
        <button v-else class="link" v-press @click="openDetail(row)">查看</button>
      </template>
    </DataTable>

    <!-- 实体审核详情抽屉 -->
    <FormDialog :show="!!detail" title="审核详情" :width="520" :footer="false" @close="closeDetail">
      <div v-if="detail" class="detail">
        <div class="detail-row"><span class="dl">申请类型</span><span class="dv">
          <StatusTag :type="detail.applyType === 'NEW' ? 'info' : detail.applyType === 'CLOSE' ? 'warning' : 'gray'" :text="applyTypeLabel[detail.applyType]" />
        </span></div>
        <div class="detail-row"><span class="dl">名称</span><span class="dv">{{ previewTitle(detail) }}</span></div>
        <div class="detail-row"><span class="dl">关联实体</span><span class="dv">{{ entityLabel[detail.entityType] }}#{{ detail.entityId ?? '（新增，待回填）' }}</span></div>
        <div class="detail-row"><span class="dl">提交人</span><span class="dv">{{ applicantName(detail) }}</span></div>
        <div class="detail-row"><span class="dl">提交时间</span><span class="dv">{{ detail.createdAt ? new Date(detail.createdAt).toLocaleString('zh-CN') : '—' }}</span></div>

        <div class="detail-row detail-row-desc" v-if="payloadRows(detail).length">
          <span class="dl">申请内容</span>
          <span class="dv">
            <div v-for="p in payloadRows(detail)" :key="p.k" class="payload-line">
              <span class="payload-k">{{ p.k }}</span><span class="payload-v">{{ p.v }}</span>
            </div>
          </span>
        </div>

        <div class="detail-row detail-row-desc" v-if="detail.status === 'rejected'">
          <span class="dl">退回原因</span><span class="dv text-desc danger-text">{{ detail.rejectReason || '（无）' }}</span>
        </div>

        <div class="reject-area" v-if="isPending">
          <label>退回原因 <span class="required">*</span>（退回时必填，将回显学生端）</label>
          <textarea v-model="rejectReason" rows="3" placeholder="请填写退回原因..."></textarea>
          <p v-if="rejectError" class="field-error">{{ rejectError }}</p>
        </div>
      </div>
      <div class="modal-actions" v-if="isPending && detail">
        <button class="btn-cancel" v-press @click="closeDetail">取消</button>
        <button class="btn-danger" :disabled="processingId === Number(detail?.id)" v-press @click="reject(detail)">退回</button>
        <button class="btn-primary" :disabled="processingId === Number(detail?.id)" v-press @click="approve(detail)">通过</button>
      </div>
    </FormDialog>

    <!-- 评价审核详情抽屉 -->
    <FormDialog :show="!!detailReview" title="评价详情" :width="520" :footer="false" @close="closeDetail">
      <div v-if="detailReview" class="detail">
        <div class="detail-row"><span class="dl">用户</span><span class="dv">{{ getUserName(detailReview.user_id) }}</span></div>
        <div class="detail-row"><span class="dl">评分</span><span class="dv stars">{{ '★'.repeat(detailReview.rating) }}<span class="star-off">{{ '★'.repeat(5 - detailReview.rating) }}</span></span></div>
        <div class="detail-row detail-row-desc"><span class="dl">内容</span><span class="dv text-desc">{{ detailReview.content || '（无文字内容）' }}</span></div>
        <div class="detail-row"><span class="dl">菜品</span><span class="dv">{{ getDishName(detailReview.dish_id) }}</span></div>
        <div class="detail-row"><span class="dl">时间</span><span class="dv">{{ detailReview.created_at ? new Date(detailReview.created_at).toLocaleString('zh-CN') : '—' }}</span></div>
        <div class="detail-imgs" v-if="parseImages(detailReview.images).length">
          <img v-for="(img, i) in parseImages(detailReview.images)" :key="i" :src="img" class="detail-img" />
        </div>
      </div>
      <div class="modal-actions" v-if="detailReview">
        <button class="btn-cancel" v-press @click="closeDetail">关闭</button>
        <button v-if="!detailReview.is_hidden" class="btn-danger" :disabled="processingId === Number(detailReview.id)" v-press @click="setHidden(detailReview, true)">隐藏</button>
        <button v-else class="btn-primary" :disabled="processingId === Number(detailReview.id)" v-press @click="setHidden(detailReview, false)">显示</button>
        <button class="btn-danger" :disabled="processingId === Number(detailReview.id)" v-press @click="removeReview(detailReview)">删除</button>
      </div>
    </FormDialog>
  </PageContainer>
</template>

<style scoped>
.review-head { display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-4); }
.review-ico { width: 20px; height: 20px; opacity: .6; }
.review-head h3 { margin: 0; font-size: var(--font-lg); color: var(--text-primary); font-weight: var(--weight-semibold); }

.entity-tabs { margin-bottom: var(--space-3); }
.entity-tab { font-size: var(--font-sm); display: inline-flex; align-items: center; gap: var(--space-1); }
.entity-ico { width: 14px; height: 14px; }

.status-tabs { margin-bottom: var(--space-4); }
.status-tab { font-size: var(--font-sm); }
.tab-count { font-size: var(--font-sm); color: var(--text-muted); margin-left: var(--space-1); }

.stars { color: var(--color-star); letter-spacing: 1px; }
.star-off { color: var(--border-strong); }
.detail-imgs { display: flex; gap: var(--space-2); flex-wrap: wrap; margin-top: var(--space-2); }
.detail-img { width: 100px; height: 100px; border-radius: var(--radius-md); object-fit: cover; border: 1px solid var(--border-color); }

.detail { display: flex; flex-direction: column; gap: var(--space-3); }
.detail-row { display: flex; gap: var(--space-3); font-size: var(--font-base); }
.detail-row-desc { align-items: flex-start; }
.dl { width: 72px; flex-shrink: 0; color: var(--text-muted); }
.dv { color: var(--text-primary); flex: 1; }
.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); }
.danger-text { color: var(--color-error) !important; }

.payload-line { display: flex; gap: var(--space-2); padding: var(--space-1) 0; border-bottom: 1px dashed var(--border-color); }
.payload-line:last-child { border-bottom: none; }
.payload-k { width: 96px; flex-shrink: 0; color: var(--text-secondary); }
.payload-v { color: var(--text-primary); word-break: break-all; }

.reject-area { margin-top: var(--space-4); border-top: 1px solid var(--border-light); padding-top: var(--space-4); }
.reject-area label { display: block; font-size: var(--font-sm); color: var(--text-secondary); margin-bottom: var(--space-2); }
.reject-area textarea {
  width: 100%; padding: var(--space-2) var(--space-3); border: 1px solid var(--border-strong);
  border-radius: var(--radius); font-size: var(--font-sm); outline: none;
  resize: vertical; box-sizing: border-box; background: var(--bg-card);
  transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out);
}
.reject-area textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent); }
.field-error { margin: var(--space-1) 0 0; font-size: var(--font-sm); color: var(--color-error); }
.required { color: var(--color-error); }
</style>
