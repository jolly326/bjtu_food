<script setup lang="ts">
/**
 * ReviewAuditView（UI-09：由原管理域评价审核视图改名落位审核域，路由与侧栏文案不变）：
 * 评价审核（UGC 审核中心）。
 * 实体贡献申请审核已随 apply 全链路下线（change prelaunch-loop-closure），本视图仅保留用户评价审核：
 * 列表 / 隐藏 / 显示 / 删除 / 批量 + 内容安检复核（放行/驳回）+ 配图查看。
 * 数据来自 reviewApi（listAllReviews / updateById / updateSecState / deleteById）；
 * secState 安检筛选走服务端参数（'' = 全部）。
 * （2026-09-14 Q-107：原 auditApi 随审核中心死代码删除，评价域能力统一归 reviewApi。）
 * WEB-03：用户名降级显示（getUserName 读 store.users）——本页 onMounted 显式拉取 users / dishes 字典，
 * 不再依赖 store setup 顶层自动加载（WEB-02）。
 */
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import FormDialog from '@/components/FormDialog.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import StarRating from '@/components/StarRating.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import { useAdminStore } from '@/stores/adminStore'
import { useDishStore } from '@/stores/dishStore'
import { useUserStore } from '@/stores/userStore'
import { useAsyncGuard } from '@/composables/useAsyncGuard'
import { SEC_STATE_META, SEC_FILTER_OPTIONS, SEC_REVIEW } from '@/constants'
import { toSecFilter } from '@/api/adapter'
import type { SecAction } from '@/types'
import { Delete, CircleCheck, CircleClose, Picture } from '@element-plus/icons-vue'

const toast = useToastStore()
const confirm = useConfirmStore()
const store = useAdminStore()
const dishStore = useDishStore()
const userStore = useUserStore()

// 请求竞态守卫（UI-05 收敛为 useAsyncGuard）：安检/关键词快速切换时仅接受最新一次请求结果，
// listAllReviews 多页循环无法中途取消，落地前校验 alive 丢弃过期响应。
const { loading, error, run } = useAsyncGuard()
const reviews = ref<any[]>([])
const selectedIds = ref<number[]>([])
const searchQuery = ref('')
// 安检状态筛选（'' = 全部）：review=待复核优先进入复核队列；传参前经 toSecFilter 白名单收窄
const activeSecState = ref('')
// 批量执行中标记：禁用批量按钮，防止并发批次（WEB-105）
const batchRunning = ref(false)
// 安检复核进行中的评价 id：按钮禁用防重复提交
const secActingId = ref<number | null>(null)

const filtered = computed(() => reviews.value)

async function loadList() {
  await run(async (alive) => {
    const { reviewApi } = await import('@/api')
    // 查全部评价（显示中 / 已隐藏），显隐状态由列表开关列控制；关键词 / 安检状态均服务端过滤
    const list = await reviewApi.listAllReviews(undefined, searchQuery.value.trim() || undefined, toSecFilter(activeSecState.value))
    if (!alive()) return // 已有更新的请求发出，丢弃过期响应
    reviews.value = list
    if (alive()) selectedIds.value = []
  })
}

async function onSecStateChange() {
  await loadList()
}

onMounted(() => {
  loadList()
  // WEB-03：用户名 / 菜品名降级显示依赖的字典显式加载（域间独立，单域失败回落「用户#id」不影响列表）
  userStore.loadAll().catch(() => {})
  dishStore.loadAll().catch(() => {})
})

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
    const { reviewApi } = await import('@/api')
    await reviewApi.updateById(Number(r.id), { is_hidden: hidden })
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
  const { reviewApi } = await import('@/api')
  const action = hidden === null ? '删除' : hidden ? '隐藏' : '显示'
  let okCount = 0
  const failedIds: number[] = []
  for (const id of selectedIds.value) {
    try {
      if (hidden === null) await reviewApi.deleteById(id)
      else await reviewApi.updateById(id, { is_hidden: hidden })
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
    const { reviewApi } = await import('@/api')
    await reviewApi.deleteById(Number(r.id))
    toast.success('评价已删除')
    await loadList()
    if (detailReview.value && Number(detailReview.value.id) === Number(r.id)) closeDetail()
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}

/** 配图点击：新窗口查看原图（COS 公网地址，noopener 防标签页劫持） */
function openImage(url: string) {
  if (!url) return
  window.open(url, '_blank', 'noopener')
}

/**
 * 内容安检复核：放行（pass）/ 驳回（rejected），仅对待复核行开放。
 * 两种动作均二次确认（驳回影响用户内容可见性，放行影响平台安全，均不可静默提交）。
 */
async function reviewSecState(r: any, state: SecAction) {
  if (secActingId.value !== null) return
  if (!await confirm.confirm(state === 'pass'
    ? '确定放行该评价？放行后评价恢复正常展示。'
    : '确定驳回该评价？驳回后评价内容将被拦截，不再对用户展示。')) return
  secActingId.value = Number(r.id)
  try {
    const { reviewApi } = await import('@/api')
    await reviewApi.updateSecState(Number(r.id), state)
    toast.success(state === 'pass' ? '评价已放行' : '评价已驳回')
    await loadList()
    if (detailReview.value && Number(detailReview.value.id) === Number(r.id)) closeDetail()
  } catch (e: any) {
    toast.error(e.message || '安检复核操作失败')
  } finally {
    secActingId.value = null
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
    <template #default>
      <FilterSelect v-model="activeSecState" label="安检" :options="SEC_FILTER_OPTIONS" :width="150" @change="onSecStateChange" />
    </template>
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
      { prop: 'secState', label: '安检', width: '90px', align: 'center' },
      { prop: 'status', label: '状态', width: '110px', align: 'center' },

    ]"
    :rows="filtered"
    :loading="loading"
    :error="error"
    actions-width="200px"
    empty-text="暂无评价"
  >
    <template #cell-user="{ row }">{{ getUserName(row.user_id) }}</template>
    <template #cell-rating="{ row }">
      <StarRating :value="row.rating" />
    </template>
    <template #cell-content="{ row }">
      <button class="link" v-press @click="openReviewDetail(row)">{{ row.content || '（无文字内容）' }}</button>
      <span v-if="(row.images || []).length" class="img-flag" title="该评价附有配图">
        <el-icon><Picture /></el-icon>{{ row.images.length }}
      </span>
    </template>
    <template #cell-dish="{ row }">{{ getDishName(row.dish_id) }}</template>
    <template #cell-time="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString('zh-CN') : '—' }}</template>
    <template #cell-secState="{ row }">
      <StatusTag :type="SEC_STATE_META[row.secState]?.type || 'success'" :text="SEC_STATE_META[row.secState]?.text || '正常'" />
    </template>
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
      <template v-if="row.secState === SEC_REVIEW">
        <button class="link primary-text" v-press :disabled="secActingId !== null" @click="reviewSecState(row, 'pass')">
          <el-icon class="act-ico"><CircleCheck /></el-icon>放行
        </button>
        <button class="link warn" v-press :disabled="secActingId !== null" @click="reviewSecState(row, 'rejected')">
          <el-icon class="act-ico"><CircleClose /></el-icon>驳回
        </button>
      </template>
      <button class="link danger" v-press @click="removeReview(row)">
        <el-icon class="act-ico"><Delete /></el-icon>删除
      </button>
    </template>
  </DataTable>

  <!-- 评价审核详情抽屉 -->
  <FormDialog :show="!!detailReview" title="评价详情" :width="520" :footer="false" @close="closeDetail">
    <div v-if="detailReview" class="detail">
      <div class="detail-row"><span class="dl">用户</span><span class="dv">{{ getUserName(detailReview.user_id) }}</span></div>
      <div class="detail-row"><span class="dl">评分</span><span class="dv"><StarRating :value="detailReview.rating" /></span></div>
      <div class="detail-row detail-row-desc"><span class="dl">内容</span><span class="dv text-desc">{{ detailReview.content || '（无文字内容）' }}</span></div>
      <div class="detail-row detail-row-desc" v-if="(detailReview.images || []).length">
        <span class="dl">配图</span>
        <div class="dv">
          <div class="img-list">
            <img
              v-for="(img, i) in detailReview.images"
              :key="i"
              :src="img"
              class="img-thumb"
              alt="评价配图"
              loading="lazy"
              @click="openImage(img)"
            />
          </div>
          <p class="img-hint">点击图片在新窗口查看原图</p>
        </div>
      </div>
      <div class="detail-row"><span class="dl">安检</span><span class="dv">
        <StatusTag :type="SEC_STATE_META[detailReview.secState]?.type || 'success'" :text="SEC_STATE_META[detailReview.secState]?.text || '正常'" />
      </span></div>
      <div class="detail-row"><span class="dl">菜品</span><span class="dv">{{ getDishName(detailReview.dish_id) }}</span></div>
      <div class="detail-row"><span class="dl">时间</span><span class="dv">{{ detailReview.created_at ? new Date(detailReview.created_at).toLocaleString('zh-CN') : '—' }}</span></div>
    </div>
    <div class="modal-actions" v-if="detailReview">
      <button class="btn-cancel" v-press @click="closeDetail">关闭</button>
      <template v-if="detailReview.secState === SEC_REVIEW">
        <button class="btn-success" v-press :disabled="secActingId !== null" @click="reviewSecState(detailReview, 'pass')">放行</button>
        <button class="btn-warn" v-press :disabled="secActingId !== null" @click="reviewSecState(detailReview, 'rejected')">驳回</button>
      </template>
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

/* 星级样式已收敛至 StarRating 组件（P3-17） */
/* 行内状态开关 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--color-error); }
/* .act-ico 已收敛至 shared.css 公共类 */
/* ===== 安检复核配图（缩略图：hover 微放大提示可点，active 按压缩放） ===== */
.img-list { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.img-thumb {
  width: 88px; height: 88px; border-radius: var(--radius-md); object-fit: cover;
  border: 1px solid var(--border-light); cursor: zoom-in; background: var(--bg-soft);
  transition: transform 160ms var(--ease-out), box-shadow 160ms var(--ease-out);
}
.img-thumb:hover { transform: scale(1.04); box-shadow: var(--shadow-card); }
.img-thumb:active { transform: scale(var(--press-scale)); }
.img-hint { margin: var(--space-2) 0 0; font-size: var(--font-xs); color: var(--text-light); }
@media (prefers-reduced-motion: reduce) {
  .img-thumb { transition: none; }
  .img-thumb:hover { transform: none; }
}

.detail { display: flex; flex-direction: column; gap: var(--space-3); }
.detail-row { display: flex; gap: var(--space-3); font-size: var(--font-base); }
.detail-row-desc { align-items: flex-start; }
.dl { width: 72px; flex-shrink: 0; color: var(--text-muted); }
.dv { color: var(--text-primary); flex: 1; }
.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); }
.danger-text { color: var(--color-error) !important; }
</style>
