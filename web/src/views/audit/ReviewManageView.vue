<script setup lang="ts">
/**
 * ReviewManageView（2026-09-15 拍板：取消人工复核；由原审核域「评价审核」视图改名而来）：
 * 「评价管理」= **事后处置**页 —— 列表 / 隐藏 / 显示 / 删除 / 批量 / 配图查看 / 详情抽屉。
 *
 * 为何不再有「安检复核」：内容机检已改为 pass/review 均放行、仅 risky 拒绝，
 * 后台不再承担人工复核职责 → 本页移除安检状态列、安检筛选、放行/驳回动作与「待复核」统计
 * （后端「内容安检状态」字段与对应复核接口同步退役，前端不再读写）。
 *
 * 数据来自 reviewApi（listAllReviews / updateById / deleteById）；评价由学生端提交，后台只处置、不新增。
 * WEB-03：用户名降级显示（getUserName 读 store.users）——本页 onMounted 显式拉取 users / dishes 字典，
 * 不依赖 store setup 顶层自动加载（WEB-02）。
 */
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
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
import { Delete, Picture } from '@element-plus/icons-vue'

const toast = useToastStore()
const confirm = useConfirmStore()
const store = useAdminStore()
const dishStore = useDishStore()
const userStore = useUserStore()

// 请求竞态守卫（UI-05 收敛为 useAsyncGuard）：显隐/关键词快速切换时仅接受最新一次请求结果，
// listAllReviews 多页循环无法中途取消，落地前校验 alive 丢弃过期响应。
const { loading, error, run } = useAsyncGuard()
const reviews = ref<any[]>([])
const selectedIds = ref<number[]>([])
const searchQuery = ref('')
/**
 * 显隐筛选（'' = 全部）：事后处置首先要知道「哪些被隐藏了」。
 * 传参经 listAllReviews 的 isHidden 透传，取值为数值 0/1（与后端 Integer 契约一致；
 * 传布尔会被序列化成 isHidden=true 触发后端类型转换失败 400）。
 */
const activeHidden = ref<'' | '0' | '1'>('')
const HIDDEN_FILTER_OPTIONS = [
  { value: '', label: '全部' },
  { value: '0', label: '显示中' },
  { value: '1', label: '已隐藏' },
]
// 批量执行中标记：禁用批量按钮，防止并发批次（WEB-105）
const batchRunning = ref(false)

async function loadList() {
  await run(async (alive) => {
    const { reviewApi } = await import('@/api')
    // 查全部评价（显示中 / 已隐藏），显隐状态由列表开关列控制；关键词 / 显隐均服务端过滤
    const isHidden = activeHidden.value === '' ? undefined : (activeHidden.value === '1' ? 1 : 0)
    const list = await reviewApi.listAllReviews(isHidden, searchQuery.value.trim() || undefined)
    if (!alive()) return // 已有更新的请求发出，丢弃过期响应
    reviews.value = list
    selectedIds.value = []
  })
}

async function onHiddenChange() {
  await loadList()
}

/**
 * 行内统计（T5）：**每个数字都取自后端 total**（pageSize=1，只取计数不拉数据），
 * 不用「当前筛选/当前页的局部计数」冒充全量：
 *   共 N 条（无筛选 total）· 已隐藏 K（isHidden=1，后端为 Integer 故传 1）。
 * 单项失败即不展示该项（宁缺勿错）；不依赖 /admin/dashboard（该接口本轮下线）。
 */
const statItems = ref<string[]>([])
async function loadStats() {
  const { listReviews } = await import('@/api/review')
  const [all, hidden] = await Promise.allSettled([
    listReviews({ page: 1, pageSize: 1 }),
    listReviews({ isHidden: 1, page: 1, pageSize: 1 }),
  ])
  const parts: string[] = []
  if (all.status === 'fulfilled') parts.push(`共 ${all.value.total} 条`)
  if (hidden.status === 'fulfilled') parts.push(`已隐藏 ${hidden.value.total}`)
  statItems.value = parts
}

onMounted(() => {
  loadList()
  loadStats()
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

/** 用户名 / 菜品名降级显示：字典未命中时回落「用户#id / 菜品#id」，不阻塞列表 */
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
  <PageContainer>
    <PageHeader title="评价管理" subtitle="事后处置：隐藏不适宜内容、删除违规评价" />

    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="activeHidden" label="显示状态" :options="HIDDEN_FILTER_OPTIONS" :width="140" @change="onHiddenChange" />
      </template>
      <template #actions>
        <template v-if="selectedIds.length">
          <button class="btn-secondary" v-press type="button" :disabled="batchRunning" @click="batchReviews(true)">批量隐藏（{{ selectedIds.length }}）</button>
          <button class="btn-secondary" v-press type="button" :disabled="batchRunning" @click="batchReviews(false)">批量显示</button>
          <button class="btn-danger" v-press type="button" :disabled="batchRunning" @click="batchReviews(null)">批量删除</button>
        </template>
        <!-- 全量统计（均取后端 total；取不到的分项不展示） -->
        <span v-if="statItems.length" class="stat-inline">{{ statItems.join(' · ') }}</span>
      </template>
    </FilterBar>

    <!-- 评价管理表（事后处置：隐藏 / 取消隐藏 / 删除） -->
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
      :rows="reviews"
      :loading="loading"
      :error="error"
      actions-width="120px"
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

    <!-- 评价详情抽屉 -->
    <FormDialog :show="!!detailReview" title="评价详情" :width="520" :footer="false" @close="closeDetail">
      <div v-if="detailReview" class="detail">
        <div class="detail-row">
          <span class="dl">用户</span><span class="dv">{{ getUserName(detailReview.user_id) }}</span>
        </div>
        <div class="detail-row">
          <span class="dl">评分</span>
          <span class="dv"><StarRating :value="detailReview.rating" /></span>
        </div>
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
        <div class="detail-row"><span class="dl">菜品</span><span class="dv">{{ getDishName(detailReview.dish_id) }}</span></div>
        <div class="detail-row">
          <span class="dl">状态</span>
          <span class="dv">
            <StatusTag :type="detailReview.is_hidden ? 'danger' : 'success'" :text="detailReview.is_hidden ? '已隐藏' : '显示中'" />
          </span>
        </div>
        <div class="detail-row"><span class="dl">时间</span><span class="dv">{{ detailReview.created_at ? new Date(detailReview.created_at).toLocaleString('zh-CN') : '—' }}</span></div>
      </div>
      <div class="modal-actions" v-if="detailReview">
        <button class="btn-cancel" v-press @click="closeDetail">关闭</button>
        <button v-if="!detailReview.is_hidden" class="btn-danger" v-press @click="setHidden(detailReview, true)">隐藏</button>
        <button v-else class="btn-primary" v-press @click="setHidden(detailReview, false)">显示</button>
        <button class="btn-danger" v-press @click="removeReview(detailReview)">删除</button>
      </div>
    </FormDialog>
  </PageContainer>
</template>

<style scoped>
/* 行内状态开关（显示中 / 已隐藏）：与菜品评论管理同构，保持两处显隐语言一致 */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--color-error); }
/* .act-ico 已收敛至 shared.css 公共类 */

/* ===== 配图缩略图（hover 微放大提示可点，active 按压缩放） ===== */
.img-list { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.img-thumb {
  width: 88px; height: 88px; border-radius: var(--radius-md); object-fit: cover;
  border: 1px solid var(--border-light); cursor: zoom-in; background: var(--bg-soft);
  transition: transform 160ms var(--ease-out), box-shadow 160ms var(--ease-out);
}
@media (hover: hover) {
  .img-thumb:hover { transform: scale(1.04); box-shadow: var(--shadow-card); }
}
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
.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); white-space: pre-wrap; }
</style>
