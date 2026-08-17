<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import {
  ChatDotRound, Pointer, Hide, Delete, Picture, Food, Shop, User,
} from '@element-plus/icons-vue'
import type { MomentManageVO, MomentComment } from '@/api/moment'

const toast = useToastStore()
const confirm = useConfirmStore()

const searchQuery = ref('')

// 管理台分段：全部 / 已下架 / 审核中 / 已退回
// - status(0/1)：下架态（0=正常 1=已下架）
// - auditStatus(pending/approved/rejected)：审核态（区别于下架态，见 ARCH §D8）
type Segment = 'all' | 'hidden' | 'pending' | 'rejected'
const segmentLabel: Record<Segment, string> = {
  all: '全部', hidden: '已下架', pending: '审核中', rejected: '已退回',
}
// 默认定位「待审核」（待办优先），管理员进来直接看到要处理的
const activeSegment = ref<Segment>('pending')

// 当前分段对应的服务端过滤参数（undefined 表示不限制）
const segmentFilter = computed(() => {
  switch (activeSegment.value) {
    case 'hidden': return { status: 1 }
    case 'pending': return { auditStatus: 'pending' }
    case 'rejected': return { auditStatus: 'rejected' }
    default: return {}
  }
})

const loading = ref(false)
const error = ref('')
const rows = ref<MomentManageVO[]>([])
// 多选（批量操作）
const selectedIds = ref<number[]>([])

// ===== 受控分页（后端已分页，total 来自后端；pageSize ≤ 100 不触碰后端上限） =====
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const { momentApi } = await import('@/api')
    const res = await momentApi.listMoments({
      page: page.value,
      pageSize: pageSize.value,
      ...segmentFilter.value,
    })
    rows.value = res.list
    total.value = res.total
  } catch (e: any) {
    error.value = e.message || '加载动态列表失败'
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 关键词/分段变化时回到第 1 页并重新请求（受控分页：翻页即重新拉取对应页）
function reloadFromFirstPage() {
  page.value = 1
  selectedIds.value = []
  loadList()
}
function onPageChange() {
  // 翻页时清空跨页多选，避免选中不可见行
  selectedIds.value = []
  loadList()
}

// 关键词（内容 / 作者）本地检索：仅在当前页内过滤，作为分页加载的辅助预览。
// 注：受控分页下不再假设单页能拿全量；若后端支持 keyword 参数应改为服务端过滤并翻页重查。
const filtered = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter(
    r => (r.content || '').toLowerCase().includes(q) || (r.userNickname || '').toLowerCase().includes(q),
  )
})

// 关键词变化（输入或清空）→ 回到第 1 页重新拉取对应页（受控分页，不假设单页全量）
watch(searchQuery, () => { reloadFromFirstPage() })

onMounted(loadList)

async function onSegmentChange(s: Segment) {
  if (activeSegment.value === s) return
  activeSegment.value = s
  reloadFromFirstPage()
}

// ===== 详情抽屉 =====
const detail = ref<MomentManageVO | null>(null)
function openDetail(row: MomentManageVO) { detail.value = row }
function closeDetail() { detail.value = null }

const processingId = ref<number | null>(null)

async function hideMoment(row: MomentManageVO) {
  if (!await confirm.confirm(`确定强制下架动态 #${row.id}？该动态将对学生不可见（仍保留数据）。`)) return
  processingId.value = Number(row.id)
  try {
    const { momentApi } = await import('@/api')
    await momentApi.hideMoment(Number(row.id))
    toast.success('动态已下架')
    await loadList()
    if (detail.value && Number(detail.value.id) === Number(row.id)) closeDetail()
  } catch (e: any) {
    toast.error(e.message || '下架失败')
  } finally {
    processingId.value = null
  }
}

async function deleteMoment(row: MomentManageVO) {
  if (!await confirm.confirm(`确定删除动态 #${row.id}？此操作将物理删除动态及其评论，不可恢复。`)) return
  processingId.value = Number(row.id)
  try {
    const { momentApi } = await import('@/api')
    await momentApi.deleteMoment(Number(row.id))
    toast.success('动态已删除')
    await loadList()
    if (detail.value && Number(detail.value.id) === Number(row.id)) closeDetail()
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  } finally {
    processingId.value = null
  }
}

// ===== 批量操作 =====
async function batchHide() {
  const targets = rows.value.filter(r => selectedIds.value.includes(Number(r.id)) && r.status !== 1)
  if (!targets.length) {
    toast.success('所选动态均无需下架')
    return
  }
  if (!await confirm.confirm(`确定批量下架 ${targets.length} 条动态？将对学生不可见（数据保留）。`)) return
  processingId.value = -1
  try {
    const { momentApi } = await import('@/api')
    for (const t of targets) await momentApi.hideMoment(Number(t.id))
    toast.success(`已批量下架 ${targets.length} 条动态`)
    selectedIds.value = []
    await loadList()
  } catch (e: any) {
    toast.error(e.message || '批量下架失败')
  } finally {
    processingId.value = null
  }
}

async function batchDelete() {
  if (!selectedIds.value.length) return
  if (!await confirm.confirm(`确定批量删除 ${selectedIds.value.length} 条动态？此操作将物理删除动态及其评论，不可恢复。`)) return
  processingId.value = -1
  try {
    const { momentApi } = await import('@/api')
    for (const id of selectedIds.value) await momentApi.deleteMoment(id)
    toast.success(`已删除 ${selectedIds.value.length} 条动态`)
    selectedIds.value = []
    await loadList()
  } catch (e: any) {
    toast.error(e.message || '批量删除失败')
  } finally {
    processingId.value = null
  }
}

// 状态标签映射
const auditStatusTag: Record<string, 'warning' | 'success' | 'danger'> = {
  pending: 'warning', approved: 'success', rejected: 'danger',
}
const auditStatusText: Record<string, string> = {
  pending: '待审核', approved: '已通过', rejected: '已退回',
}
const relatedTypeText: Record<string, string> = { dish: '菜品', stall: '档口', none: '自由动态' }

function fmtTime(v: string): string {
  if (!v) return '—'
  const d = new Date(v)
  return isNaN(d.getTime()) ? v : d.toLocaleString('zh-CN')
}

// ===== 评论治理（查看单条评论 / 删除） =====
const commentModal = ref(false)
const comments = ref<MomentComment[]>([])
const commentLoading = ref(false)

async function openComments(row: any) {
  const { listComments } = await import('@/api/moment')
  commentModal.value = true
  commentLoading.value = true
  try {
    comments.value = await listComments({ momentId: Number(row.id) })
  } catch {
    comments.value = []
  } finally {
    commentLoading.value = false
  }
}
async function handleDeleteComment(c: MomentComment) {
  if (!await confirm.confirm('确定删除这条评论？其回复与「有用」标记将一并删除。')) return
  try {
    const { deleteComment } = await import('@/api/moment')
    await deleteComment(c.id)
    toast.success('评论已删除')
    comments.value = comments.value.filter(x => x.id !== c.id)
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}
</script>

<template>
    <FilterBar v-model="searchQuery">
      <template #tabs>
        <button v-for="s in (['all','hidden','pending','rejected'] as Segment[])" :key="s"
          class="tab status-tab" :class="{ 'tab-on': activeSegment === s }" v-press @click="onSegmentChange(s)">
          {{ segmentLabel[s] }}
        </button>
      </template>
      <template #actions>
        <template v-if="selectedIds.length">
          <button class="btn-secondary" :disabled="processingId === -1" v-press type="button" @click="batchHide">批量下架（{{ selectedIds.length }}）</button>
          <button class="btn-danger" :disabled="processingId === -1" v-press type="button" @click="batchDelete">批量删除</button>
        </template>
      </template>
    </FilterBar>

    <DataTable
      selectable
      v-model:selectedIds="selectedIds"
      server-mode
      :server-total="total"
      v-model:server-page="page"
      v-model:server-page-size="pageSize"
      @page-change="onPageChange"
      :columns="[
        { prop: 'author', label: '作者', width: '160px' },
        { prop: 'content', label: '内容摘要', ellipsis: true },
        { prop: 'related', label: '关联对象', width: '140px' },
        { prop: 'auditStatus', label: '审核状态', width: '110px', align: 'center' },
        { prop: 'downStatus', label: '下架状态', width: '110px', align: 'center' },
        { prop: 'time', label: '发布时间', width: '160px', sortable: true, sortValue: (row) => row.createdAt },

      ]"
      :rows="filtered"
      :loading="loading"
      :error="error"
      :empty-text="activeSegment === 'hidden' ? '暂无已下架动态' : (activeSegment === 'pending' ? '暂无待审核动态' : (activeSegment === 'rejected' ? '暂无已退回动态' : '暂无动态'))"
    >
      <template #cell-author="{ row }">
        <button class="link" v-press @click="openDetail(row)">{{ row.userNickname || ('用户#' + row.userId) }}</button>
      </template>
      <template #cell-content="{ row }">
        <span class="ellipsis">{{ row.content || '（无文字）' }}</span>
        <span v-if="row.images?.length" class="img-badge"><el-icon><Picture /></el-icon>{{ row.images.length }}</span>
      </template>
      <template #cell-related="{ row }">
        <span v-if="row.relatedType && row.relatedType !== 'none'">
          <el-icon class="rel-ico" v-if="row.relatedType === 'dish'"><Food /></el-icon>
          <el-icon class="rel-ico" v-else><Shop /></el-icon>
          {{ relatedTypeText[row.relatedType] }}#{{ row.relatedId }}
          <div v-if="row.relatedName" class="rel-name">{{ row.relatedName }}</div>
        </span>
        <span v-else class="muted">—</span>
      </template>
      <template #cell-auditStatus="{ row }">
        <StatusTag :type="auditStatusTag[row.auditStatus] || 'info'" :text="auditStatusText[row.auditStatus] || row.auditStatus" />
      </template>
      <template #cell-downStatus="{ row }">
        <StatusTag :type="row.status === 1 ? 'danger' : 'success'" :text="row.status === 1 ? '已下架' : '正常'" />
      </template>
      <template #cell-time="{ row }">{{ fmtTime(row.createdAt) }}</template>
      <template #actions="{ row }">
        <button class="link" v-press @click="openComments(row)">
          <el-icon class="act-ico"><ChatDotRound /></el-icon>评论
        </button>
        <button v-if="row.status !== 1" class="link danger" :disabled="processingId === Number(row.id)" v-press @click="hideMoment(row)">
          <el-icon class="act-ico"><Hide /></el-icon>下架
        </button>
        <button v-else class="link" :disabled="processingId === Number(row.id)" v-press @click="openDetail(row)">查看</button>
        <button class="link danger" :disabled="processingId === Number(row.id)" v-press @click="deleteMoment(row)">
          <el-icon class="act-ico"><Delete /></el-icon>删除
        </button>
      </template>
    </DataTable>

    <!-- 评论治理弹窗 -->
    <FormDialog :show="commentModal" title="动态评论" :width="560" :footer="false" @close="commentModal = false">
      <div v-if="commentLoading" class="state-box">加载中…</div>
      <div v-else-if="!comments.length" class="state-box">该动态暂无评论</div>
      <div v-else class="cmt-list">
        <div v-for="c in comments" :key="c.id" class="cmt-item">
          <div class="cmt-main">
            <span class="cmt-user">用户#{{ c.userId }}</span>
            <span v-if="c.parentId" class="cmt-reply">回复</span>
            <span class="cmt-content">{{ c.content }}</span>
          </div>
          <div class="cmt-meta">
            <span class="cmt-time">{{ fmtTime(c.createdAt || '') }}</span>
            <button class="link danger" v-press @click="handleDeleteComment(c)">删除</button>
          </div>
        </div>
      </div>
    </FormDialog>

    <!-- 详情抽屉：图 + 文 + 关联对象预览卡 -->
    <FormDialog :show="!!detail" title="动态详情" :width="560" :footer="false" @close="closeDetail">
      <div v-if="detail" class="detail">
        <div class="detail-author">
          <el-icon v-if="detail.userAvatar" class="avatar"><User /></el-icon>
          <div>
            <div class="author-name">{{ detail.userNickname || ('用户#' + detail.userId) }}</div>
            <div class="author-sub">{{ fmtTime(detail.createdAt) }}</div>
          </div>
        </div>

        <div class="detail-imgs" v-if="detail.images?.length">
          <img v-for="(img, i) in detail.images" :key="i" :src="img" class="detail-img" />
        </div>

        <div class="detail-content">{{ detail.content || '（无文字内容）' }}</div>

        <div class="detail-row"><span class="dl">关联对象</span>
          <span class="dv" v-if="detail.relatedType && detail.relatedType !== 'none'">
            <el-icon class="rel-ico"><component :is="detail.relatedType === 'dish' ? Food : Shop" /></el-icon>
            {{ relatedTypeText[detail.relatedType] }}#{{ detail.relatedId }}
            <template v-if="detail.relatedName">（{{ detail.relatedName }}）</template>
          </span>
          <span class="dv muted" v-else>自由动态</span>
        </div>
        <div class="detail-row"><span class="dl">审核状态</span>
          <span class="dv"><StatusTag :type="auditStatusTag[detail.auditStatus] || 'info'" :text="auditStatusText[detail.auditStatus] || detail.auditStatus" /></span>
        </div>
        <div class="detail-row"><span class="dl">下架状态</span>
          <span class="dv"><StatusTag :type="detail.status === 1 ? 'danger' : 'success'" :text="detail.status === 1 ? '已下架' : '正常'" /></span>
        </div>
        <div class="detail-row"><span class="dl">互动</span>
          <span class="dv"><el-icon class="rel-ico"><Pointer /></el-icon>{{ detail.usefulCount }} 有用 ·
            <el-icon class="rel-ico"><ChatDotRound /></el-icon>{{ detail.commentCount }} 评论</span>
        </div>

        <div class="detail-row detail-row-desc" v-if="detail.auditStatus === 'rejected'">
          <span class="dl">退回原因</span><span class="dv text-desc danger-text">{{ detail.rejectReason || '（无）' }}</span>
        </div>

        <div class="modal-actions" v-if="detail.status !== 1">
          <button class="btn-cancel" v-press @click="closeDetail">关闭</button>
          <button class="btn-danger" :disabled="processingId === Number(detail.id)" v-press @click="hideMoment(detail)">
            <el-icon class="act-ico"><Hide /></el-icon>强制下架
          </button>
        </div>
      </div>
    </FormDialog>
</template>

<style scoped>
.status-tab { font-size: var(--font-sm); }

.ellipsis { display: inline-block; max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; }
.img-badge { margin-left: var(--space-2); color: var(--text-light); font-size: var(--font-xs); display: inline-flex; align-items: center; gap: 2px; }
.img-badge .el-icon { width: 13px; height: 13px; }
.rel-ico { width: 14px; height: 14px; vertical-align: -2px; opacity: .6; }
.rel-name { font-size: var(--font-xs); color: var(--text-muted); }
/* .act-ico 已收敛至 shared.css 公共类 */

/* 评论治理弹窗 */
.cmt-list { display: flex; flex-direction: column; max-height: 60vh; overflow-y: auto; }
.cmt-item { padding: var(--space-3) var(--space-1); border-bottom: 1px solid var(--border-soft); }
.cmt-item:last-child { border-bottom: none; }
.cmt-main { display: flex; align-items: baseline; gap: var(--space-2); }
.cmt-user { font-size: var(--font-xs); font-weight: var(--weight-medium); color: var(--text-muted); flex-shrink: 0; }
.cmt-reply { font-size: var(--font-xs); color: var(--text-light); flex-shrink: 0; }
.cmt-content { font-size: var(--font-base); color: var(--text-primary); line-height: 1.5; }
.cmt-meta { display: flex; align-items: center; justify-content: space-between; margin-top: var(--space-1); }
.cmt-time { font-size: var(--font-xs); color: var(--text-light); }
.muted { color: var(--text-light); }

.detail { display: flex; flex-direction: column; gap: var(--space-3); }
.detail-author { display: flex; align-items: center; gap: var(--space-3); }
.detail-author .avatar { width: 32px; height: 32px; opacity: .5; }
.author-name { font-weight: var(--weight-semibold); color: var(--text-primary); }
.author-sub { font-size: var(--font-xs); color: var(--text-muted); }
.detail-imgs { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.detail-img { width: 96px; height: 96px; border-radius: var(--radius-md); object-fit: cover; border: 1px solid var(--border-color); }
.detail-content { font-size: var(--font-base); color: var(--text-primary); line-height: var(--leading-loose); background: var(--bg-soft); padding: var(--space-3); border-radius: var(--radius); white-space: pre-wrap; }
.detail-row { display: flex; gap: var(--space-3); font-size: var(--font-base); }
.detail-row-desc { align-items: flex-start; }
.dl { width: 64px; flex-shrink: 0; color: var(--text-muted); }
.dv { color: var(--text-primary); flex: 1; display: inline-flex; align-items: center; gap: var(--space-1); flex-wrap: wrap; }
.dv.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); }
.danger-text { color: var(--color-error) !important; }
.modal-actions { display: flex; justify-content: flex-end; gap: var(--space-3); margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--border-light); }
.btn-cancel { padding: var(--space-2) var(--space-5); background: var(--bg-card); color: var(--text-secondary); border: 1px solid var(--border-color); border-radius: var(--radius); font-size: var(--font-base); cursor: pointer; font-weight: var(--weight-medium); }
.btn-cancel:hover { color: var(--color-primary); border-color: var(--color-primary); }
.btn-danger { padding: var(--space-2) var(--space-5); border: 1px solid var(--color-error); border-radius: var(--radius); background: var(--bg-card); color: var(--color-error); font-size: var(--font-base); cursor: pointer; font-weight: var(--weight-medium); display: inline-flex; align-items: center; gap: var(--space-1); }
.btn-danger:hover { background: var(--color-error); color: var(--text-white); }

/* 批量操作按钮 */
</style>
