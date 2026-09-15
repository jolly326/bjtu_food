<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import FormDialog from '@/components/FormDialog.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import { useAsyncGuard } from '@/composables/useAsyncGuard'
import { ChatDotRound, EditPen, CircleCheck, CircleClose, Picture } from '@element-plus/icons-vue'
import { SEC_STATE_META, SEC_FILTER_OPTIONS, SEC_PASS, FEEDBACK_STATUS_META, FEEDBACK_PENDING, FEEDBACK_HANDLED, FEEDBACK_TYPE_META } from '@/constants'
import { toSecFilter } from '@/api/adapter'
import type { FeedbackAdminVO } from '@/api/feedback'

const toast = useToastStore()
const confirm = useConfirmStore()
const router = useRouter()
const route = useRoute()

const searchQuery = ref('')

// 类型文案统一收敛至 constants（唯一真源，避免各处各写一套导致口径漂移）
const typeLabel = FEEDBACK_TYPE_META

/**
 * 二级类型展示（DEV-01）：仅「功能建议」（type=suggestion）有值，idea=想法 / problem=问题。
 * 与一级类型同格拼为「功能建议 · 想法」，避免出现「建议」与二级类型两处口径不一致。
 * 后端字段可选（历史数据 / 其他类型均无值）→ 缺省不展示，不产生占位符。
 * 列表 type 列宽相应由 120px 调至 170px（容纳拼合后的最长文案，12px 字 + 两侧单元格内边距）。
 */
const FEEDBACK_SUGGESTION = 'suggestion'
const SUB_LABEL: Record<'idea' | 'problem', string> = { idea: '想法', problem: '问题' }
function typeText(v: { type: string; sub?: 'idea' | 'problem' }): string {
  const base = typeLabel[v.type] || v.type
  const sub = v.type === FEEDBACK_SUGGESTION ? v.sub : undefined
  return sub ? `${base} · ${SUB_LABEL[sub]}` : base
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

// 请求竞态守卫（UI-05 收敛为 useAsyncGuard）：过期响应整份丢弃（防连续输入数据错乱）
const { loading, error, run } = useAsyncGuard()
const rows = ref<FeedbackAdminVO[]>([])

// ===== 受控分页（后端已分页，total 来自后端；pageSize ≤ 100 不触碰后端上限） =====
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

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
  await run(async (alive) => {
    const { feedbackApi } = await import('@/api')
    const res = await feedbackApi.listFeedbacks({
      status: activeStatus.value || undefined,
      keyword: searchQuery.value.trim() || undefined,
      type: activeType.value || undefined,
      secState: toSecFilter(activeSecState.value) || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    if (!alive()) return // 已有更新的请求发出，丢弃过期响应
    rows.value = res.list
    total.value = res.total
  })
}

/**
 * 单条反馈深链直达：`/dashboard/audit?tab=feedback&fid=<id>`（P1-03）。
 * （原入口为工作台「待办明细」，工作台已下线，深链参数保持兼容。）
 * 复用既有详情抽屉（本页无独立 /feedbacks/:id 路由，也无 GET /admin/feedbacks/{id} 单查接口），
 * 故在列表落地后按 id 定位该行并自动打开抽屉；若不在当前页则回退为「关键词=该条摘要」服务端检索，
 * 保证「看得到是哪一条 → 点进就能处理」闭环，且不改后端契约、不新增页面。
 */
async function openFeedbackById(id: number) {
  const hit = rows.value.find(r => Number(r.id) === id)
  if (hit) {
    openDetail(hit)
    return
  }
  // 不在当前页：用待办标题（内容摘要）走服务端关键词检索，命中后自动打开
  const title = route.query.title
  if (typeof title === 'string' && title) {
    searchQuery.value = title
    await reloadFromFirstPage()
    const found = rows.value.find(r => Number(r.id) === id)
    if (found) openDetail(found)
    else toast.error('未在当前筛选条件下找到该反馈，已按摘要检索')
    return
  }
  toast.error('该反馈不在当前列表中，可用关键词检索')
}

/** 消费 route.query.fid（含页内二次跳转：同实例不重建，故需 watch 而非仅 onMounted 读取） */
async function consumeFid() {
  const raw = route.query.fid
  const id = typeof raw === 'string' ? Number(raw) : NaN
  if (!Number.isFinite(id)) return
  // 先按默认筛选（待处理）拉一遍列表，再按 id 定位自动打开详情抽屉
  await loadList()
  await openFeedbackById(id)
}

onMounted(consumeFid)
watch(() => route.query.fid, () => { consumeFid() })

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
/**
 * 处理结论（§7.23 第 5 条，2026-09-15 拍板）：
 *  - handled = 已处理（缺省）；rejected = 不采纳/退回；
 *  - 选「不采纳」时原因必填（1~200 字，与后端 FeedbackHandleReq 校验一致），
 *    原因随回执一并向提交人展示。
 */
const outcome = ref<'handled' | 'rejected'>('handled')
const rejectReason = ref('')
const rejectReasonError = ref('')
const processingId = ref<number | null>(null)

/**
 * 提交人展示（WA-02）：后端 userId 为空（匿名）时不再显示「用户#0」，改显「游客」。
 * 兼容历史数据：userId=0 亦视为匿名（WA-03）。
 */
function submitterLabel(v: FeedbackAdminVO): string {
  if (v.userNickname) return v.userNickname
  return v.userId != null && v.userId !== 0 ? `用户#${v.userId}` : '游客'
}
/** 匿名反馈判定（WA-03）：无站内回执，确认文案不承诺「学生将收到回复」 */
const isAnon = computed(() => {
  const d = detail.value
  return d != null && (d.userId == null || d.userId === 0)
})

function openDetail(row: FeedbackAdminVO) {
  detail.value = row
  reply.value = row.reply || ''
  replyError.value = ''
  outcome.value = row.outcome === 'rejected' ? 'rejected' : 'handled'
  rejectReason.value = row.rejectReason || ''
  rejectReasonError.value = ''
}
function closeDetail() { detail.value = null }

async function submitHandle() {
  if (!detail.value) return
  // 回复必填（2026-09-14 拍板 project_spec §7.16；后端 FeedbackHandleReq.reply 为 @NotBlank）：
  // 纯空白视为未填写 → 前端拦截，不发请求（避免必然 400）。
  // 匿名反馈（WA-03）不承诺「学生将收到」，避免无差别虚假承诺。
  const trimmed = reply.value.trim()
  if (!trimmed) {
    replyError.value = isAnon.value ? '请填写处理回复' : '请填写处理回复（学生将收到该内容）'
    return
  }
  if (trimmed.length > 1000) {
    replyError.value = '回复内容不能超过 1000 字'
    return
  }
  replyError.value = ''
  // 不采纳原因必填（§7.23 第 5 条）：outcome=rejected 时 1~200 字，后端同样校验（纯空白 → 400）
  let reason: string | undefined
  if (outcome.value === 'rejected') {
    const trimmedReason = rejectReason.value.trim()
    if (!trimmedReason) {
      rejectReasonError.value = isAnon.value ? '不采纳 / 退回必须填写原因' : '不采纳 / 退回必须填写原因（学生将收到该原因）'
      return
    }
    if (trimmedReason.length > 200) {
      rejectReasonError.value = '不采纳原因不能超过 200 字'
      return
    }
    reason = trimmedReason
  }
  rejectReasonError.value = ''
  // 二次确认（Q-112 ③，最简实现）：标记处理为不可逆，提交前先确认。
  // 匿名反馈（WA-03）无站内回执，不承诺「学生将收到回复」，改显匿名提示。
  const confirmText = isAnon.value
    ? (outcome.value === 'rejected'
        ? '确定将该反馈标记为「不采纳」？该反馈为匿名提交，无站内回执。'
        : '确定将该反馈标记为「已处理」？该反馈为匿名提交，无站内回执。')
    : (outcome.value === 'rejected'
        ? '确定将该反馈标记为「不采纳」？提交后学生将收到回复与不采纳原因。'
        : '确定将该反馈标记为「已处理」？提交后学生将收到你的回复。')
  if (!await confirm.confirm(confirmText)) return
  processingId.value = Number(detail.value.id)
  try {
    const { feedbackApi } = await import('@/api')
    await feedbackApi.handleFeedback(Number(detail.value.id), trimmed, {
      outcome: outcome.value,
      rejectReason: reason,
    })
    toast.success(outcome.value === 'rejected' ? '反馈已标记不采纳' : '反馈已标记处理')
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

/**
 * 关联菜品展示（DEV-04 收口）：菜品名由后端 FeedbackAdminVO.relatedDishName 回填
 * （服务端批量查询、含已下架菜品），前端不再直连公开端点 GET /dishes/{id}。
 * 缺省（历史数据 / 已下架且未回填）时退回既有「菜品#id」占位，不发起二次请求。
 */
function dishLabel(id?: number, name?: string): string {
  if (name) return name
  return id != null ? `菜品#${id}` : '—'
}

/**
 * 关联菜品一键直达：菜品详情独立页已随「食堂/档口随菜品一起维护」收敛而删除
 * （project_spec §7.15），
 * 故改为跳到菜品列表（`/dashboard/content?tab=dish`）由管理员在列表内检索编辑。
 * 检索词优先用服务端回填的菜品名（含已下架菜品），无名字时退回菜品 id。
 */
function goDishEdit(dishId?: number, dishName?: string) {
  if (dishId == null) return
  router.push({
    path: '/dashboard/content',
    query: { tab: 'dish', q: dishName || String(dishId) },
  })
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
        { prop: 'type', label: '类型', width: '170px', align: 'center' },
        { prop: 'related', label: '关联对象', width: '140px', align: 'center' },
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
        <span class="type-pill"><el-icon class="type-ico"><ChatDotRound /></el-icon>{{ typeText(row) }}</span>
      </template>
      <template #cell-related="{ row }">
        <span v-if="row.relatedType === 'review'" class="related">评价#{{ row.relatedId }}</span>
        <button
          v-else-if="row.relatedType === 'dish'"
          class="link dish-name-cell"
          v-press
          :title="`菜品#${row.relatedId}`"
          @click="goDishEdit(row.relatedId, row.relatedDishName)"
        >{{ dishLabel(row.relatedId, row.relatedDishName) }}</button>
        <span v-else class="muted">—</span>
      </template>
      <template #cell-content="{ row }">
        <button class="link" v-press @click="openDetail(row)">{{ row.content || '（无内容）' }}</button>
        <span v-if="(row.images || []).length" class="img-flag" title="该反馈附有配图">
          <el-icon><Picture /></el-icon>{{ row.images.length }}
        </span>
      </template>
      <template #cell-contact="{ row }"><span class="muted">{{ row.contact || '—' }}</span></template>
      <template #cell-submitter="{ row }">{{ submitterLabel(row) }}</template>
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
      :confirm-text="outcome === 'rejected' ? '标记不采纳' : '标记处理'"
      :confirm-disabled="false"
      :confirm-loading="processingId !== null"
      @close="closeDetail"
      @confirm="submitHandle"
    >
      <div v-if="detail" class="detail">
        <div class="detail-row"><span class="dl">类型</span>
          <span class="dv"><span class="type-pill">{{ typeText(detail) }}</span></span>
        </div>
        <div class="detail-row"><span class="dl">提交人</span><span class="dv">{{ submitterLabel(detail) }}</span></div>
        <div class="detail-row"><span class="dl">联系方式</span><span class="dv muted">{{ detail.contact || '—' }}</span></div>
        <div class="detail-row"><span class="dl">提交时间</span><span class="dv">{{ fmtTime(detail.createdAt) }}</span></div>
        <div class="detail-row" v-if="detail.relatedType === 'review'">
          <span class="dl">关联对象</span>
          <span class="dv">
            <span class="related">评价 #{{ detail.relatedId }}</span>
            <button class="link" v-press @click="copyReviewLink(detail.relatedId)">复制标识</button>
          </span>
        </div>
        <div class="detail-row" v-else-if="detail.relatedType === 'dish'">
          <span class="dl">关联菜品</span>
          <span class="dv">
            <button
              class="link"
              v-press
              :title="`菜品#${detail.relatedId}`"
              @click="goDishEdit(detail.relatedId, detail.relatedDishName)"
            >{{ dishLabel(detail.relatedId, detail.relatedDishName) }}</button>
          </span>
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
        <!-- 不采纳原因回显（§7.23 第 5 条：随回执向提交人展示；历史数据无 outcome 时按 rejectReason 兜底） -->
        <div class="detail-row detail-row-desc" v-if="detail.status === FEEDBACK_HANDLED && (detail.outcome === 'rejected' || detail.rejectReason)">
          <span class="dl">不采纳原因</span>
          <span class="dv text-desc reject-reason">{{ detail.rejectReason || '—' }}</span>
        </div>

        <div class="reply-area" v-if="detail.status !== FEEDBACK_HANDLED">
          <!-- 处理结论（§7.23 第 5 条）：已处理 = 通过并回复；不采纳/退回 = 必填原因 -->
          <label>处理结论</label>
          <div class="outcome-group" role="radiogroup" aria-label="处理结论">
            <button
              type="button"
              class="outcome-opt"
              :class="{ on: outcome === 'handled' }"
              role="radio"
              :aria-checked="outcome === 'handled'"
              v-press
              @click="outcome = 'handled'"
            ><el-icon class="outcome-ico"><CircleCheck /></el-icon>已处理</button>
            <button
              type="button"
              class="outcome-opt"
              :class="{ on: outcome === 'rejected' }"
              role="radio"
              :aria-checked="outcome === 'rejected'"
              v-press
              @click="outcome = 'rejected'"
            ><el-icon class="outcome-ico"><CircleClose /></el-icon>不采纳 / 退回</button>
          </div>

          <!-- 回复必填（2026-09-14 §7.16）：与后端 @NotBlank 校验规则严格一致，前端措辞不得出现「选填/留空」 -->
          <label class="mt-label">处理说明 / 回复 <span class="required">*</span>（必填）</label>
          <textarea v-model="reply" rows="4" :placeholder="isAnon ? '请填写处理回复' : '请填写处理回复，学生将收到该内容'"></textarea>
          <p v-if="replyError" class="field-error">{{ replyError }}</p>

          <!-- 不采纳原因（§7.23 第 5 条）：选「不采纳/退回」时必填，1~200 字，后端同样校验 -->
          <template v-if="outcome === 'rejected'">
            <label class="mt-label">不采纳原因 <span class="required">*</span>（必填，1~200 字）</label>
            <textarea v-model="rejectReason" rows="3" :placeholder="isAnon ? '请填写不采纳 / 退回原因' : '请填写不采纳 / 退回原因，学生将收到该原因'"></textarea>
            <p v-if="rejectReasonError" class="field-error">{{ rejectReasonError }}</p>
          </template>
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

/* nowrap：DEV-01 后文案可能带二级类型（功能建议 · 想法），避免窄格内折行破坏行高 */
.type-pill { display: inline-flex; align-items: center; gap: var(--space-1); padding: 2px var(--space-2); border-radius: var(--radius-pill); background: var(--color-primary-bg); color: var(--color-primary); font-size: var(--font-xs); font-weight: var(--weight-medium); white-space: nowrap; }
.related { display: inline-flex; align-items: center; padding: 2px var(--space-2); border-radius: var(--radius-pill); background: var(--color-error-bg); color: var(--color-error); font-size: var(--font-xs); font-weight: var(--weight-medium); }
/**
 * 关联菜品名（DEV-04）：列宽 140px（减两侧 --space-4 内边距 ≈ 108px 可用），
 * 名称可能较长（含已下架菜品），单元格内单行截断，避免撑高行高/挤压相邻列；
 * 完整名称经 title 提示，点击仍可跳转菜品列表检索。
 */
.dish-name-cell { display: inline-block; max-width: 108px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom; }
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
/* 结论选择之后的标签追加与输入框一致的垂直间距 */
.reply-area .mt-label { margin-top: var(--space-3); }
.reply-area textarea {
  width: 100%; padding: var(--space-2) var(--space-3); border: 1px solid var(--border-strong);
  border-radius: var(--radius); font-size: var(--font-sm); outline: none; resize: vertical; box-sizing: border-box;
  background: var(--bg-card); transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out);
}
.reply-area textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent); }
.required { color: var(--color-error); }
.handled-tip { display: flex; align-items: center; gap: var(--space-2); margin-top: var(--space-3); color: var(--color-success); font-size: var(--font-sm); }

/* ===== 处理结论选择（§7.23 第 5 条）：胶囊单选，选中态走语义色 ===== */
.outcome-group { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.outcome-opt {
  display: inline-flex; align-items: center; gap: var(--space-1);
  padding: var(--space-2) var(--space-4);
  border: 1px solid var(--border-strong); border-radius: var(--radius-pill);
  background: var(--bg-card); color: var(--text-secondary);
  font-size: var(--font-sm); font-weight: var(--weight-medium);
  cursor: pointer; user-select: none;
  transition: background 0.2s var(--ease-out), border-color 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
.outcome-opt:hover { border-color: var(--color-primary); color: var(--color-primary); }
.outcome-opt:active { transform: scale(var(--press-scale)); }
.outcome-opt:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.outcome-opt.on { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary); }
.outcome-ico { width: 13px; height: 13px; }

/* 不采纳原因回显：与错误色语义一致的弱化背景，便于一眼识别 */
.reject-reason { color: var(--text-primary); background: var(--color-error-bg); border-radius: var(--radius-sm); padding: var(--space-2) var(--space-3); display: inline-block; }
</style>
