<script setup lang="ts">
/**
 * ApplyReviewView：评价审核（UGC 审核中心）。
 * 实体贡献申请审核已随 apply 全链路下线（change prelaunch-loop-closure），本视图仅保留用户评价审核：
 * 列表 / 隐藏 / 显示 / 删除 / 批量，数据来自 auditApi（listAllReviews / setReviewHidden / deleteReview）。
 */
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FormDialog from '@/components/FormDialog.vue'
import DataTable from '@/components/DataTable.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import { useAdminStore } from '@/stores/adminStore'
import { Delete } from '@element-plus/icons-vue'

const toast = useToastStore()
const confirm = useConfirmStore()
const store = useAdminStore()

const loading = ref(false)
const error = ref('')
const reviews = ref<any[]>([])
const selectedIds = ref<number[]>([])
const searchQuery = ref('')
// 批量执行中标记：禁用批量按钮，防止并发批次（WEB-105）
const batchRunning = ref(false)

const filtered = computed(() => reviews.value)

async function loadList() {
  loading.value = true
  error.value = ''
  selectedIds.value = []
  try {
    const { auditApi } = await import('@/api')
    // 查全部评价（显示中 / 已隐藏），显隐状态由列表开关列控制；关键词服务端过滤
    reviews.value = await auditApi.listAllReviews(undefined, searchQuery.value.trim() || undefined)
  } catch (e: any) {
    error.value = e.message || '加载评价列表失败'
    reviews.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadList)

// 关键词变化（输入或清空）→ 防抖 300ms 后按服务端 keyword 过滤重拉（WEB-103，对照 FeedbackView 既有模式）
let searchDebounce: ReturnType<typeof setTimeout> | undefined
watch(searchQuery, () => {
  clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => { loadList() }, 300)
})
// 卸载时清理防抖定时器，避免组件销毁后回调仍触发（M4：定时器泄漏修复）
onBeforeUnmount(() => clearTimeout(searchDebounce))

const detailReview = ref<any | null>(null)
function openReviewDetail(r: any) { detailReview.value = r }
function closeDetail() { detailReview.value = null }

async function setHidden(r: any, hidden: boolean) {
  if (!await confirm.confirm(hidden ? '确定隐藏该评价？' : '确定显示该评价？')) return
  try {
    const { auditApi } = await import('@/api')
    await auditApi.setReviewHidden(Number(r.id), hidden)
    toast.success(hidden ? '评价已隐藏' : '评价已显示')
    await loadList()
    if (detailReview.value && Number(detailReview.value.id) === Number(r.id)) closeDetail()
  } catch (e: any) {
    toast.error(e.message || '操作失败')
  }
}
async function toggleHidden(r: any, hidden: boolean) { await setHidden(r, hidden) }

async function batchReviews(hidden: boolean | null) {
  if (!selectedIds.value.length || batchRunning.value) return
  if (hidden === null) {
    if (!await confirm.confirm(`确定批量删除 ${selectedIds.value.length} 条评价？此操作不可恢复。`)) return
  } else {
    if (!await confirm.confirm(`确定批量${hidden ? '隐藏' : '显示'} ${selectedIds.value.length} 条评价？`)) return
  }
  // WEB-105：循环纯接口调用并统计成败（单条失败不中断批次），结束后统一 loadList 一次
  batchRunning.value = true
  const { auditApi } = await import('@/api')
  const action = hidden === null ? '删除' : hidden ? '隐藏' : '显示'
  let okCount = 0
  const failedIds: number[] = []
  for (const id of selectedIds.value) {
    try {
      if (hidden === null) await auditApi.deleteReview(id)
      else await auditApi.setReviewHidden(id, hidden)
      okCount++
    } catch {
      failedIds.push(id)
    }
  }
  await loadList()
  batchRunning.value = false
  const failCount = failedIds.length
  if (failCount === 0) {
    toast.success(hidden === null ? `已删除 ${okCount} 条评价` : `已批量${hidden ? '隐藏' : '显示'} ${okCount} 条评价`)
  } else {
    // 部分失败：汇总成败计数，仅保留失败项便于重试（loadList 已清空选择，此处回填）
    toast.error(`批量${action}完成：成功 ${okCount} 条，失败 ${failCount} 条`)
    selectedIds.value = failedIds
  }
}
async function removeReview(r: any) {
  if (!await confirm.confirm('确定删除该评价？此操作不可恢复。')) return
  try {
    const { auditApi } = await import('@/api')
    await auditApi.deleteReview(Number(r.id))
    toast.success('评价已删除')
    await loadList()
    if (detailReview.value && Number(detailReview.value.id) === Number(r.id)) closeDetail()
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}
function getUserName(userId: number | bigint): string {
  const u = store.users.find(u => Number(u.id) === Number(userId))
  return u?.nickname || u?.username || `用户${userId}`
}
function getDishName(dishId: number | bigint): string {
  const d = store.dishes.find(d => Number(d.id) === Number(dishId))
  return d?.name || `菜品${dishId}`
}
</script>

<template>
  <FilterBar v-model="searchQuery">
    <template #actions>
      <template v-if="selectedIds.length">
        <button class="btn-secondary" v-press type="button" :disabled="batchRunning" @click="batchReviews(true)">批量隐藏（{{ selectedIds.length }}）</button>
        <button class="btn-secondary" v-press type="button" :disabled="batchRunning" @click="batchReviews(false)">批量显示</button>
        <button class="btn-danger" v-press type="button" :disabled="batchRunning" @click="batchReviews(null)">批量删除</button>
      </template>
    </template>
  </FilterBar>

  <!-- 评价审核表 -->
  <DataTable
    selectable
    v-model:selectedIds="selectedIds"
    :columns="[
      { prop: 'user', label: '用户' },
      { prop: 'rating', label: '评分', width: '120px', sortable: true, sortValue: (row) => row.rating },
      { prop: 'content', label: '内容', ellipsis: true },
      { prop: 'dish', label: '菜品' },
      { prop: 'time', label: '时间', width: '150px', sortable: true, sortValue: (row) => row.created_at },
      { prop: 'status', label: '状态', width: '110px', align: 'center' },

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
    <template #cell-time="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString('zh-CN') : '—' }}</template>
    <template #cell-status="{ row }">
      <div class="status-cell">
        <el-switch
          :model-value="!row.is_hidden"
          :loading="false"
          :disabled="false"
          @change="(v: any) => toggleHidden(row, !v)"
        />
        <span class="status-text" :class="!row.is_hidden ? 'on' : 'off'">{{ row.is_hidden ? '已隐藏' : '显示中' }}</span>
      </div>
    </template>
    <template #actions="{ row }">
      <button class="link danger" v-press @click="removeReview(row)">
        <el-icon class="act-ico"><Delete /></el-icon>删除
      </button>
    </template>
  </DataTable>

  <!-- 评价审核详情抽屉 -->
  <FormDialog :show="!!detailReview" title="评价详情" :width="520" :footer="false" @close="closeDetail">
    <div v-if="detailReview" class="detail">
      <div class="detail-row"><span class="dl">用户</span><span class="dv">{{ getUserName(detailReview.user_id) }}</span></div>
      <div class="detail-row"><span class="dl">评分</span><span class="dv stars">{{ '★'.repeat(detailReview.rating) }}<span class="star-off">{{ '★'.repeat(5 - detailReview.rating) }}</span></span></div>
      <div class="detail-row detail-row-desc"><span class="dl">内容</span><span class="dv text-desc">{{ detailReview.content || '（无文字内容）' }}</span></div>
      <div class="detail-row"><span class="dl">菜品</span><span class="dv">{{ getDishName(detailReview.dish_id) }}</span></div>
      <div class="detail-row"><span class="dl">时间</span><span class="dv">{{ detailReview.created_at ? new Date(detailReview.created_at).toLocaleString('zh-CN') : '—' }}</span></div>
    </div>
    <div class="modal-actions" v-if="detailReview">
      <button class="btn-cancel" v-press @click="closeDetail">关闭</button>
      <button v-if="!detailReview.is_hidden" class="btn-danger" v-press @click="setHidden(detailReview, true)">隐藏</button>
      <button v-else class="btn-primary" v-press @click="setHidden(detailReview, false)">显示</button>
      <button class="btn-danger" v-press @click="removeReview(detailReview)">删除</button>
    </div>
  </FormDialog>
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
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--color-error); }
/* .act-ico 已收敛至 shared.css 公共类 */
/* 评价图片展示已下线（prelaunch-loop-closure 10.5），相关样式一并移除 */
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
