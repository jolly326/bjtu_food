<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toastStore'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import FormDialog from '@/components/FormDialog.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import { ChatDotRound, EditPen, CircleCheck, Picture } from '@element-plus/icons-vue'
import { SEC_STATE_META, SEC_FILTER_OPTIONS, SEC_PASS, FEEDBACK_STATUS_META, FEEDBACK_PENDING, FEEDBACK_HANDLED } from '@/constants'
import { toSecFilter } from '@/api/adapter'
import type { FeedbackAdminVO } from '@/api/feedback'

const toast = useToastStore()
const router = useRouter()

const searchQuery = ref('')

const typeLabel: Record<string, string> = {
  suggestion: '功能建议',
  error: '内容纠错',
  add: '新增菜品',
  bug: '系统问题',
  report: '举报',
  other: '其他',
}
// 状态展示元数据（tag 类型 + 文案）统一收敛至 constants/index.ts（RF13）

// 状态筛选（prelaunch-final-audit AUD-PM-13：处理后需可回看，避免「已处理」分支不可达）
// 默认「待处理」= 管理员处理待办；切换「已处理」可回看历史回复与处理时间。
const statusOptions = [
  { value: FEEDBACK_PENDING, label: '待处理' },
  { value: FEEDBACK_HANDLED, label: '已处理' },
  { value: '', label: '全部状态' },
]
const activeStatus = ref(FEEDBACK_PENDING)

// 类型筛选（§5 举报处理：可筛 type=report 等；2026-08-17 新增 add/bug）
const typeOptions = [
  { value: '', label: '全部类型' },
  { value: 'suggestion', label: '功能建议' },
  { value: 'add', label: '新增菜品' },
  { value: 'error', label: '内容纠错' },
  { value: 'bug', label: '系统问题' },
  { value: 'report', label: '举报' },
  { value: 'other', label: '其他' },
]
const activeType = ref('')

// 安检状态筛选（'' = 全部，服务端过滤）：筛「待复核」时优先处理被安检拦截的反馈
const activeSecState = ref('')

const loading = ref(false)
const error = ref('')
const rows = ref<FeedbackAdminVO[]>([])

// ===== 受控分页（后端已分页，total 来自后端；pageSize ≤ 100 不触碰后端上限） =====
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 请求竞态守卫：仅接受最新一次请求结果，丢弃过期响应（防连续输入数据错乱）
let reqToken = 0

async function reloadFromFirstPage() {
  page.value = 1
  await loadList()
}
function onPageChange() {
  loadList()
}

// 关键词检索已改为服务端 keyword 过滤（后端按 content/contact/userNickname 模糊），
// 翻页/改类型会重新请求后端对应页，不再本地截断当前页子集。
const filtered = computed(() => rows.value)

async function loadList() {
  loading.value = true
  error.value = ''
  const token = ++reqToken
  try {
    const { feedbackApi } = await import('@/api')
    const res = await feedbackApi.listFeedbacks({
      status: activeStatus.value || undefined,
      keyword: searchQuery.value.trim() || undefined,
      type: activeType.value || undefined,
      secState: toSecFilter(activeSecState.value) || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    if (token !== reqToken) return // 已有更新的请求发出，丢弃过期响应
    rows.value = res.list
    total.value = res.total
  } catch (e: any) {
    if (token !== reqToken) return
    error.value = e.message || '加载反馈列表失败'
    rows.value = []
    total.value = 0
  } finally {
    if (token === reqToken) loading.value = false
  }
}

onMounted(loadList)
async function onTypeChange() {
  await reloadFromFirstPage()
}
async function onStatusChange() {
  await reloadFromFirstPage()
}
async function onSecStateChange() {
  await reloadFromFirstPage()
}

// 关键词变化（输入或清空）→ 回到第 1 页重新拉取对应页（受控分页，不假设单页全量）
// 加 300ms 防抖，避免连续输入每个 keystroke 都发请求（去重），并 await 确保完成。
let searchDebounce: ReturnType<typeof setTimeout> | undefined
watch(searchQuery, () => {
  clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => { reloadFromFirstPage() }, 300)
})
// 卸载时清理防抖定时器，避免组件销毁后回调仍触发（M4：定时器泄漏修复）
onBeforeUnmount(() => clearTimeout(searchDebounce))

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
  // 处理说明/回复允许为空（后端对无回复走通用回执文案，见 change prelaunch-loop-closure 4.2 / 10.4）
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

/** 关联菜品一键直达编辑详情：反馈关联 dish 经公开详情接口拿到 stallId/canteenId，拼出档口链路路由 */
async function goDishEdit(dishId?: number) {
  if (dishId == null) return
  try {
    const { dishApi } = await import('@/api')
    const dish = await dishApi.getById(dishId)
    const stallId = Number(dish.stallId)
    const canteenId = Number(dish.canteenId)
    if (!stallId || !canteenId) {
      toast.error('无法定位该菜品所属档口 / 食堂')
      return
    }
    router.push(`/dashboard/canteens/${canteenId}/stalls/${stallId}/dishes/${dishId}`)
  } catch (e: any) {
    toast.error(e.message || '跳转菜品编辑失败')
  }
}

async function copyReviewLink(reviewId?: number) {
  if (reviewId == null) return
  // AUD-PM-17：小程序无评价详情页，改为纯评价标识（避免被当作可打开的小程序路由）
  const link = `review#${reviewId}`
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
    toast.success('评价标识已复制')
  } catch {
    toast.error('复制失败，请手动记录：' + link)
  }
}
</script>

<template>
    <FilterBar v-model="searchQuery">
      <template #default>
        <FilterSelect v-model="activeStatus" label="状态" :options="statusOptions" :width="140" @change="onStatusChange" />
        <FilterSelect v-model="activeSecState" label="安检" :options="SEC_FILTER_OPTIONS" :width="150" @change="onSecStateChange" />
        <FilterSelect v-model="activeType" label="类型" :options="typeOptions" :width="150" @change="onTypeChange" />
      </template>
    </FilterBar>

    <DataTable
      server-mode
      :server-total="total"
      v-model:server-page="page"
      v-model:server-page-size="pageSize"
      @page-change="onPageChange"
      :columns="[
        { prop: 'type', label: '类型', width: '120px', align: 'center' },
        { prop: 'related', label: '关联评价', width: '140px', align: 'center' },
        { prop: 'content', label: '内容', ellipsis: true },
        { prop: 'contact', label: '联系方式', width: '160px' },
        { prop: 'submitter', label: '提交人', width: '140px' },
        { prop: 'time', label: '提交时间', width: '170px', sortable: true, sortValue: (row) => row.createdAt },
        { prop: 'secState', label: '安检', width: '90px', align: 'center' },
        { prop: 'status', label: '状态', width: '100px', align: 'center' },

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
        <span v-if="row.relatedType === 'review'" class="related">评价#{{ row.relatedId }}</span>
        <button v-else-if="row.relatedType === 'dish'" class="link" v-press @click="goDishEdit(row.relatedId)">菜品#{{ row.relatedId }}</button>
        <span v-else class="muted">—</span>
      </template>
      <template #cell-content="{ row }">
        <button class="link" v-press @click="openDetail(row)">{{ row.content || '（无内容）' }}</button>
        <span v-if="(row.images || []).length" class="img-flag" title="该反馈附有配图">
          <el-icon><Picture /></el-icon>{{ row.images.length }}
        </span>
      </template>
      <template #cell-contact="{ row }"><span class="muted">{{ row.contact || '—' }}</span></template>
      <template #cell-submitter="{ row }">{{ row.userNickname || ('用户#' + row.userId) }}</template>
      <template #cell-time="{ row }">{{ fmtTime(row.createdAt) }}</template>
      <template #cell-secState="{ row }">
        <StatusTag :type="SEC_STATE_META[row.secState]?.type || 'success'" :text="SEC_STATE_META[row.secState]?.text || '正常'" />
      </template>
      <template #cell-status="{ row }">
        <StatusTag :type="FEEDBACK_STATUS_META[row.status]?.type || 'warning'" :text="FEEDBACK_STATUS_META[row.status]?.text || row.status" />
      </template>
      <template #actions="{ row }">
        <button class="link" v-press @click="openDetail(row)">
          <el-icon class="act-ico"><EditPen /></el-icon>{{ row.status === FEEDBACK_HANDLED ? '查看' : '处理' }}
        </button>
      </template>
    </DataTable>

    <!-- 详情 + 处理抽屉 -->
    <FormDialog
      :show="!!detail"
      :title="detail?.status === FEEDBACK_HANDLED ? '反馈详情' : '处理反馈'"
      :width="520"
      :footer="detail?.status !== FEEDBACK_HANDLED"
      :confirm-text="'标记处理'"
      :confirm-disabled="false"
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
        <div class="detail-row" v-if="detail.relatedType === 'review'">
          <span class="dl">关联评价</span>
          <span class="dv">
            <span class="related">评价 #{{ detail.relatedId }}</span>
            <button class="link" v-press @click="copyReviewLink(detail.relatedId)">复制标识</button>
          </span>
        </div>
        <div class="detail-row" v-else-if="detail.relatedType === 'dish'">
          <span class="dl">关联菜品</span>
          <span class="dv"><button class="link" v-press @click="goDishEdit(detail.relatedId)">菜品 #{{ detail.relatedId }}</button></span>
        </div>
        <div class="detail-row detail-row-desc"><span class="dl">内容</span><span class="dv text-desc">{{ detail.content || '（无）' }}</span></div>
        <div class="detail-row detail-row-desc" v-if="(detail.images || []).length">
          <span class="dl">配图</span>
          <div class="dv">
            <div class="img-list">
              <el-image
                v-for="(img, i) in detail.images"
                :key="i"
                :src="img"
                :preview-src-list="detail.images"
                :initial-index="i"
                fit="cover"
                class="img-thumb"
                preview-teleported
                hide-on-click-modal
                alt="反馈配图"
                loading="lazy"
              />
            </div>
            <p class="img-hint">点击图片放大预览</p>
          </div>
        </div>
        <div class="detail-row" v-if="detail.secState && detail.secState !== SEC_PASS">
          <span class="dl">安检</span>
          <span class="dv">
            <StatusTag :type="SEC_STATE_META[detail.secState]?.type || 'success'" :text="SEC_STATE_META[detail.secState]?.text || '正常'" />
          </span>
        </div>
        <div class="detail-row" v-if="detail.status === FEEDBACK_HANDLED"><span class="dl">处理时间</span><span class="dv">{{ fmtTime(detail.handledAt) }}</span></div>
        <div class="detail-row detail-row-desc" v-if="detail.reply"><span class="dl">历史回复</span><span class="dv text-desc">{{ detail.reply }}</span></div>

        <div class="reply-area" v-if="detail.status !== FEEDBACK_HANDLED">
          <!-- AUD-PM-14：后端允许空回复（走通用回执文案），此处不得标注必填 -->
          <label>处理说明 / 回复（选填）</label>
          <textarea v-model="reply" rows="4" placeholder="可填写处理说明或回复内容；留空则用户收到通用回执"></textarea>
          <p v-if="replyError" class="field-error">{{ replyError }}</p>
        </div>
        <div v-else class="handled-tip"><el-icon><CircleCheck /></el-icon>该反馈已处理</div>
      </div>
      <template v-if="detail?.status === FEEDBACK_HANDLED" #actions>
        <button class="btn-cancel" v-press @click="closeDetail">关闭</button>
      </template>
    </FormDialog>
</template>

<style scoped>
.status-tabs { margin-bottom: var(--space-4); }
.status-tab { font-size: var(--font-sm); }
.tab-count { font-size: var(--font-sm); color: var(--text-muted); margin-left: var(--space-1); }

.type-pill { display: inline-flex; align-items: center; gap: var(--space-1); padding: 2px var(--space-2); border-radius: var(--radius-pill); background: var(--color-primary-bg); color: var(--color-primary); font-size: var(--font-xs); font-weight: var(--weight-medium); }
.related { display: inline-flex; align-items: center; padding: 2px var(--space-2); border-radius: var(--radius-pill); background: var(--color-error-bg); color: var(--color-error); font-size: var(--font-xs); font-weight: var(--weight-medium); }
.type-ico { width: 13px; height: 13px; }
/* .act-ico 已收敛至 shared.css 公共类 */
.muted { color: var(--text-light); }

.detail { display: flex; flex-direction: column; gap: var(--space-3); }
.detail-row { display: flex; gap: var(--space-3); font-size: var(--font-base); }
.detail-row-desc { align-items: flex-start; }
.dl { width: 64px; flex-shrink: 0; color: var(--text-muted); }
.dv { color: var(--text-primary); flex: 1; }
.dv.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); white-space: pre-wrap; }
/* 反馈附图缩略图：横向排列、点击页内放大预览（el-image viewer） */
.img-list { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.img-thumb {
  width: 72px; height: 72px; border-radius: var(--radius); cursor: zoom-in;
  border: 1px solid var(--border-light); overflow: hidden; background: var(--bg-soft); display: block;
  transition: transform 160ms var(--ease-out), box-shadow 160ms var(--ease-out);
}
.img-thumb:hover { transform: scale(1.04); box-shadow: var(--shadow-card); }
.img-thumb:active { transform: scale(var(--press-scale)); }
.img-hint { margin: var(--space-2) 0 0; font-size: var(--font-xs); color: var(--text-light); }
/* .img-flag（列表「附有配图」角标）已收敛至 shared.css 公共类 */
@media (prefers-reduced-motion: reduce) {
  .img-thumb { transition: none; }
  .img-thumb:hover { transform: none; }
}
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
