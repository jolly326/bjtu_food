<script setup lang="ts">
/**
 * CorrectionView（独立「信息纠错」处理页，自反馈拆分）：
 * 原「菜品信息纠错」挂在反馈列表（「信息更新」类型：提交快照 + 采纳端点），
 * 本轮后端同批重构为独立资源 /admin/corrections，本视图为对应独立一级入口
 * （路由 /dashboard/corrections，顶部导航「信息纠错」），与「反馈」并列、互不混流。
 *
 * 页面职责：
 *  - 列表（状态筛选 pending/adopted/rejected，服务端分页）；
 *  - 处理抽屉：对比卡（原菜品 vs 用户提交七字段：名称/价格/食堂/档口/口味/食材/图片，
 *    差异行 warning 高亮 +「已修改」标）；
 *  - 「一键采纳」（两段式）：confirm 二次确认 → adopt → 若返回 needStallConfirm
 *    （提交档口文本未命中现有档口）→ 弹档口确认对话框（候选 radio + 「新建档口」选项）
 *    → 选定后重发 adopt → 成功 toast + 刷新；
 *  - 「不采纳」：回复 + 拒绝原因必填（沿用 feedback rejected 模式，PUT outcome=rejected）；
 *  - 已处理行（adopted/rejected）只读回看。
 *
 * UI 规范（project_spec §4.9 / apple-design）：按压反馈统一 v-press（scale(var(--press-scale))），
 * 颜色全部走设计 Token 禁裸 hex，图标用 Element Plus 禁 emoji，动效遵循
 * prefers-reduced-motion 降级（diff 高亮 / 缩略图 hover 均提供静态降级）。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { useDishStore } from '@/stores/dishStore'
import { useDishAttributeStore } from '@/stores/dishAttributeStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import FormDialog from '@/components/FormDialog.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import { useAsyncGuard } from '@/composables/useAsyncGuard'
import { imagesToList } from '@/api/adapter'
import { EditPen, Picture } from '@element-plus/icons-vue'
import {
  CORRECTION_STATUS_META,
  CORRECTION_PENDING,
  CORRECTION_ADOPTED,
  CORRECTION_REJECTED,
} from '@/constants'
import type { CorrectionAdminVO, StallCandidate } from '@/api/corrections'
import type { Dish } from '@/types'

const toast = useToastStore()
const confirm = useConfirmStore()
const router = useRouter()

// ===== 状态筛选（服务端过滤；默认「待处理」= 管理员处理待办） =====
const statusOptions = [
  { value: CORRECTION_PENDING, label: '待处理' },
  { value: CORRECTION_ADOPTED, label: '已采纳' },
  { value: CORRECTION_REJECTED, label: '已拒绝' },
  { value: '', label: '全部状态' },
]
const activeStatus = ref(CORRECTION_PENDING)

// 请求竞态守卫（useAsyncGuard）：过期响应整份丢弃
const { loading, error, run } = useAsyncGuard()
const rows = ref<CorrectionAdminVO[]>([])

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

async function loadList() {
  await run(async (alive) => {
    const { correctionApi } = await import('@/api')
    const res = await correctionApi.listCorrections({
      status: activeStatus.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    if (!alive()) return // 已有更新的请求发出，丢弃过期响应
    rows.value = res.list
    total.value = res.total
  })
}
onMounted(loadList)

async function onStatusChange() {
  await reloadFromFirstPage()
}

// ===== 提交人展示（匿名 → 「游客」；userId=0 亦视为匿名，兼容 WA-03 口径） =====
function submitterLabel(v: CorrectionAdminVO): string {
  return v.userId != null && v.userId !== 0 ? `用户#${v.userId}` : '游客'
}

/** 提交内容摘要（列表格）：快照关键字段单行速览，完整对比进抽屉 */
function summaryText(v: CorrectionAdminVO): string {
  const s = v.snapshot
  const parts: string[] = []
  if (s.name) parts.push(`名称：${s.name}`)
  if (s.price != null) parts.push(`价格：¥${s.price.toFixed(2)}`)
  if (s.canteenName) parts.push(`食堂：${s.canteenName}`)
  if (s.stallName) parts.push(`档口：${s.stallName}`)
  if (s.flavorTags.length) parts.push(`口味 ${s.flavorTags.length} 项`)
  if (s.ingredients.length) parts.push(`食材 ${s.ingredients.length} 项`)
  if (s.images.length) parts.push(`图片 ${s.images.length} 张`)
  return parts.join(' · ') || '（无提交内容）'
}

// ===== 详情 + 处理抽屉 =====
const detail = ref<CorrectionAdminVO | null>(null)
const processingId = ref<number | null>(null)
/** 一键采纳提交中（与 processingId 分离：两个动作各自的 loading 态） */
const adoptingId = ref<number | null>(null)

/** 不采纳子流程：进入后展示回复 + 原因输入，退出回到采纳视图 */
const rejecting = ref(false)
const reply = ref('')
const replyError = ref('')
const rejectReason = ref('')
const rejectReasonError = ref('')

const isHandled = computed(() => {
  const s = detail.value?.status
  return s === CORRECTION_ADOPTED || s === CORRECTION_REJECTED
})

function openDetail(row: CorrectionAdminVO) {
  detail.value = row
  rejecting.value = false
  reply.value = ''
  replyError.value = ''
  rejectReason.value = ''
  rejectReasonError.value = ''
  originalDish.value = null
  if (row.status === CORRECTION_PENDING) void ensureCompareData()
}
function closeDetail() {
  detail.value = null
}

// ===== 原菜品数据（对比卡左侧；懒加载 + 静默降级，不阻塞抽屉打开） =====
/**
 * 原菜品定位：按纠错行 `dishId` **精确匹配**（不是旧版「按名 + 档口」模糊回退——
 * dishId 是采纳写回的目标主键，用它对比才与实际写回结果一致）。
 * 数据源为 /admin/dishes 聚合（dishStore，含已下架菜品）；
 * 找不到（已物理删除）→ 对比卡降级为仅展示提交内容（采纳时后端 4001 拦截）。
 * 注：后端 /admin/dishes 无单查端点（仅分页列表），store 全量缓存后按 id 精确取，避免逐行扫表。
 */
const dishStore = useDishStore()
const dishAttr = useDishAttributeStore()
const originalDish = ref<Dish | null>(null)

async function ensureCompareData() {
  // 口味/食材机器值 → 中文：dishAttributeStore（后端字典唯一真源，未命中原样透出）
  if (!dishAttr.list.length) {
    dishAttr.ensureLoaded().catch(() => { /* 字典失败：机器值原样透出，不阻塞 */ })
  }
  if (!dishStore.list.length) {
    try { await dishStore.loadAll() } catch { /* 全量失败：原列降级为「—」 */ }
  }
  const dishId = detail.value?.dishId
  originalDish.value =
    dishId != null ? dishStore.list.find(d => Number(d.id) === Number(dishId)) ?? null : null
}

// ===== 对比卡（原菜品 vs 用户提交七字段；有差异 warning 高亮 +「已修改」标） =====
/** 字段级对比行：before=当前菜品 / after=用户提交；changed=有差异 */
interface DiffRow { label: string; before: string; after: string; changed: boolean }

function sameSet(a?: string[], b?: string[]): boolean {
  const x = [...new Set(a ?? [])].sort()
  const y = [...new Set(b ?? [])].sort()
  return x.length === y.length && x.every((v, i) => v === y[i])
}

const detailDiff = computed<DiffRow[]>(() => {
  const d = detail.value
  const s = d?.snapshot
  if (!d || !s) return []
  const o = originalDish.value
  const rows: DiffRow[] = []
  const push = (label: string, before: string, after: string, changed: boolean) =>
    rows.push({ label, before, after, changed })
  push('名称', o?.name ?? '—', s.name || '—', !!o && o.name !== (s.name || ''))
  // 价格两侧均已是元（快照由 API 层分→元；Dish 出参同理），按分取整比较避免浮点误判
  const cents = (v?: number) => (v == null ? null : Math.round(v * 100))
  push(
    '价格',
    o ? `¥${o.price.toFixed(2)}` : '—',
    s.price != null ? `¥${s.price.toFixed(2)}` : '—',
    !!o && cents(o.price) !== cents(s.price),
  )
  push('食堂', o?.canteenName || '—', s.canteenName || '—', !!o && (o.canteenName || '') !== (s.canteenName || ''))
  push('档口', o?.stallName || '—', s.stallName || '—', !!o && (o.stallName || '') !== (s.stallName || ''))
  push('口味', o ? dishAttr.labelsText('flavorTags', o.flavorTags) : '—',
    dishAttr.labelsText('flavorTags', s.flavorTags), !!o && !sameSet(o.flavorTags, s.flavorTags))
  push('食材', o ? dishAttr.labelsText('ingredients', o.ingredients) : '—',
    dishAttr.labelsText('ingredients', s.ingredients), !!o && !sameSet(o.ingredients, s.ingredients))
  return rows
})

const detailImages = computed<{ before: string[]; after: string[]; changed: boolean } | null>(() => {
  const d = detail.value
  const s = d?.snapshot
  if (!d || !s) return null
  const before = originalDish.value ? imagesToList(originalDish.value.image) : []
  return { before, after: s.images, changed: !!originalDish.value && !sameSet(before, s.images) }
})

// ===== 一键采纳（两段式） =====
/**
 * 档口确认对话框（两段式第二段）：提交档口文本未命中现有档口时，后端返回
 * { needStallConfirm, candidates }（未执行采纳）→ 弹出候选选择：
 *  - 选某个候选档口 → 重发 adopt 带 stallId；
 *  - 「新建档口：{提交文本}」→ 重发 adopt 带 createIfMissing=true（无提交文本时不提供该选项）。
 */
const stallDialogShow = ref(false)
const stallCandidates = ref<StallCandidate[]>([])
/** 档口选择：候选为 'stall:<id>'，新建为 'new' */
const stallChoice = ref('')

async function submitAdopt() {
  if (!detail.value) return
  if (!await confirm.confirm('确定采纳该信息纠错？提交内容将直接写回目标菜品。')) return
  await doAdopt({})
}

async function doAdopt(options: { stallId?: number; createIfMissing?: boolean }) {
  if (!detail.value) return
  adoptingId.value = Number(detail.value.id)
  try {
    const { correctionApi } = await import('@/api')
    const res = await correctionApi.adoptCorrection(Number(detail.value.id), options)
    if (res?.needStallConfirm) {
      // 未执行采纳：弹档口确认对话框（候选 radio + 新建选项）
      const candidates = res.candidates ?? []
      stallCandidates.value = candidates
      const first = candidates[0]
      stallChoice.value = first ? `stall:${first.id}` : 'new'
      stallDialogShow.value = true
      return
    }
    toast.success('已采纳，菜品信息已更新')
    await loadList()
    closeDetail()
  } catch (e: any) {
    toast.error(e.message || '采纳失败')
  } finally {
    adoptingId.value = null
  }
}

async function confirmStall() {
  if (stallChoice.value === 'new') {
    await doAdopt({ createIfMissing: true })
  } else {
    const stallId = Number(stallChoice.value.replace('stall:', ''))
    if (!Number.isFinite(stallId)) return
    await doAdopt({ stallId })
  }
  stallDialogShow.value = false
}

// ===== 不采纳（reply + rejectReason 必填，沿用 feedback rejected 模式） =====
function startReject() {
  rejecting.value = true
  reply.value = ''
  replyError.value = ''
  rejectReason.value = ''
  rejectReasonError.value = ''
}

async function submitReject() {
  if (!detail.value) return
  // 回复必填（与后端 @NotBlank 校验一致，纯空白拦截，不发必然 400 的请求）
  const trimmed = reply.value.trim()
  if (!trimmed) {
    replyError.value = '请填写处理回复（提交人将收到该内容）'
    return
  }
  if (trimmed.length > 1000) {
    replyError.value = '回复内容不能超过 1000 字'
    return
  }
  replyError.value = ''
  // 拒绝原因必填：1~200 字，后端同样校验
  const trimmedReason = rejectReason.value.trim()
  if (!trimmedReason) {
    rejectReasonError.value = '拒绝必须填写原因（提交人将收到该原因）'
    return
  }
  if (trimmedReason.length > 200) {
    rejectReasonError.value = '拒绝原因不能超过 200 字'
    return
  }
  rejectReasonError.value = ''
  // 二次确认：拒绝为不可逆动作，提交前先确认
  if (!await confirm.confirm('确定拒绝该信息纠错？提交人将收到你的回复与拒绝原因。')) return
  processingId.value = Number(detail.value.id)
  try {
    const { correctionApi } = await import('@/api')
    await correctionApi.rejectCorrection(Number(detail.value.id), trimmed, trimmedReason)
    toast.success('纠错已拒绝')
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
 * 目标菜品一键直达：跳到菜品列表（`/dashboard/content?tab=dish`）由管理员检索编辑，
 * 检索词用后端回填的菜品名（含已下架/已删除菜品）。
 */
function goDishEdit(dishName?: string) {
  if (!dishName) return
  router.push({ path: '/dashboard/content', query: { tab: 'dish', q: dishName } })
}
</script>

<template>
  <PageContainer>
    <PageHeader title="信息纠错" subtitle="处理学生提交的菜品信息纠错：采纳写回 / 拒绝（必填原因）" />

    <!-- 筛选行：仅状态（服务端过滤），无关键词搜索（后端未提供 keyword 参数，不放死搜索框） -->
    <div class="filter-row">
      <FilterSelect v-model="activeStatus" label="状态" :options="statusOptions" :width="140" @change="onStatusChange" />
    </div>

    <DataTable
      server-mode
      :server-total="total"
      v-model:server-page="page"
      v-model:server-page-size="pageSize"
      @page-change="onPageChange"
      :columns="[
        { prop: 'dish', label: '目标菜品', width: '180px' },
        { prop: 'summary', label: '提交内容', ellipsis: true },
        { prop: 'submitter', label: '提交人', width: '130px' },
        { prop: 'time', label: '提交时间', width: '170px', sortable: true, sortValue: (row) => row.createdAt },
        { prop: 'status', label: '状态', width: '100px', align: 'center' },
      ]"
      :rows="rows"
      :loading="loading"
      :error="error"
      empty-text="暂无纠错"
    >
      <template #cell-dish="{ row }">
        <!-- 目标菜品名（后端回填），点击直达菜品管理检索 -->
        <button
          class="link dish-name-cell"
          v-press
          :title="`目标菜品：${row.dishName || '—'} —— 点击去菜品列表检索`"
          @click="goDishEdit(row.dishName)"
        >{{ row.dishName || `菜品#${row.dishId}` }}</button>
      </template>
      <template #cell-summary="{ row }">
        <button class="link" v-press @click="openDetail(row)">{{ summaryText(row) }}</button>
        <span v-if="row.snapshot.images.length" class="img-flag" title="该纠错附有图片">
          <el-icon><Picture /></el-icon>{{ row.snapshot.images.length }}
        </span>
      </template>
      <template #cell-submitter="{ row }">
        <span class="sub-name">{{ submitterLabel(row) }}</span>
      </template>
      <template #cell-time="{ row }">{{ fmtTime(row.createdAt) }}</template>
      <template #cell-status="{ row }">
        <StatusTag :type="CORRECTION_STATUS_META[row.status]?.type || 'warning'" :text="CORRECTION_STATUS_META[row.status]?.text || row.status" />
      </template>
      <template #actions="{ row }">
        <button class="link" v-press @click="openDetail(row)">
          <el-icon class="act-ico"><EditPen /></el-icon>{{ row.status === CORRECTION_PENDING ? '处理' : '查看' }}
        </button>
      </template>
    </DataTable>

    <!-- 详情 + 处理抽屉 -->
    <FormDialog
      :show="!!detail"
      :title="isHandled ? '纠错详情' : '处理信息纠错'"
      :width="520"
      :footer="false"
      @close="closeDetail"
    >
      <div v-if="detail" class="detail">
        <!-- 元信息内联压缩：目标菜品 / 提交人 / 提交时间（与 FeedbackView 同款单块内联排布） -->
        <div class="meta">
          <span class="meta-item">
            目标菜品
            <button
              class="link"
              v-press
              :title="`菜品 #${detail.dishId}`"
              @click="goDishEdit(detail.dishName)"
            >{{ detail.dishName || `菜品#${detail.dishId}` }}</button>
          </span>
          <span class="meta-item">提交人<span class="mv">{{ submitterLabel(detail) }}</span></span>
          <span class="meta-item">提交时间<span class="mv">{{ fmtTime(detail.createdAt) }}</span></span>
        </div>

        <!-- 对比卡：原菜品 vs 用户提交七字段（有差异的字段 warning 高亮 +「已修改」标） -->
        <div class="diff-card">
          <div class="diff-title">
            菜品信息对比
            <span class="diff-target">{{ detail.dishName || detail.snapshot.name || '—' }}</span>
            <span v-if="detail.status === CORRECTION_PENDING && !originalDish" class="diff-missing">未找到当前菜品，仅展示提交内容</span>
          </div>
          <div v-for="r in detailDiff" :key="r.label" class="diff-row">
            <span class="diff-label">{{ r.label }}</span>
            <span class="diff-before" :class="{ 'diff-changed': r.changed }">{{ r.before }}</span>
            <span class="diff-arrow" aria-hidden="true">→</span>
            <span class="diff-after" :class="{ 'diff-changed': r.changed }">
              {{ r.after }}
              <span v-if="r.changed" class="diff-badge">已修改</span>
            </span>
          </div>
          <div v-if="detailImages && (detailImages.before.length || detailImages.after.length)" class="diff-row">
            <span class="diff-label">图片</span>
            <div class="diff-img-groups" :class="{ 'diff-changed': detailImages.changed }">
              <div class="diff-img-group">
                <span class="diff-img-label">当前菜品</span>
                <div class="img-list">
                  <template v-if="detailImages.before.length">
                    <el-image
                      v-for="(img, i) in detailImages.before"
                      :key="`b${i}`"
                      :src="img"
                      :preview-src-list="detailImages.before"
                      :initial-index="i"
                      fit="cover"
                      class="img-thumb"
                      preview-teleported
                      hide-on-click-modal
                      alt="菜品当前图片"
                      loading="lazy"
                    />
                  </template>
                  <span v-else class="img-empty">无图</span>
                </div>
              </div>
              <div class="diff-img-group">
                <span class="diff-img-label">用户提交</span>
                <div class="img-list">
                  <template v-if="detailImages.after.length">
                    <el-image
                      v-for="(img, i) in detailImages.after"
                      :key="`a${i}`"
                      :src="img"
                      :preview-src-list="detailImages.after"
                      :initial-index="i"
                      fit="cover"
                      class="img-thumb"
                      preview-teleported
                      hide-on-click-modal
                      alt="用户提交图片"
                      loading="lazy"
                    />
                  </template>
                  <span v-else class="img-empty">无图</span>
                </div>
              </div>
            </div>
            <span v-if="detailImages.changed" class="diff-badge">已修改</span>
          </div>
          <p v-if="detail.status === CORRECTION_PENDING && !rejecting" class="diff-hint">
            「一键采纳」将把「用户提交」列内容直接写回目标菜品；如拒绝，请说明原因。
          </p>
        </div>

        <!-- 已处理：只读回看 -->
        <template v-if="isHandled">
          <div class="detail-row"><span class="dl">处理时间</span><span class="dv">{{ fmtTime(detail.handledAt) }}</span></div>
          <div class="detail-row detail-row-desc" v-if="detail.reply"><span class="dl">处理回复</span><span class="dv text-desc">{{ detail.reply }}</span></div>
          <div class="detail-row detail-row-desc" v-if="detail.status === CORRECTION_REJECTED">
            <span class="dl">拒绝原因</span>
            <span class="dv text-desc reject-reason">{{ detail.rejectReason || '—' }}</span>
          </div>
        </template>

        <!-- 待处理 · 不采纳子流程：回复 + 拒绝原因必填 -->
        <div class="reply-area" v-if="detail.status === CORRECTION_PENDING && rejecting">
          <label>处理回复 <span class="required">*</span>（必填，提交人将收到该内容）</label>
          <textarea v-model="reply" rows="4" placeholder="请填写处理回复"></textarea>
          <p v-if="replyError" class="field-error">{{ replyError }}</p>
          <label class="mt-label">拒绝原因 <span class="required">*</span>（必填，1~200 字）</label>
          <textarea v-model="rejectReason" rows="3" placeholder="请填写拒绝原因"></textarea>
          <p v-if="rejectReasonError" class="field-error">{{ rejectReasonError }}</p>
        </div>
      </div>

      <!-- 底部操作：待处理 → 采纳 / 不采纳；已处理 → 只读关闭；不采纳子流程 → 返回 / 确认拒绝 -->
      <template v-if="detail" #actions>
        <template v-if="detail.status === CORRECTION_PENDING">
          <template v-if="!rejecting">
            <button class="btn-cancel" v-press @click="closeDetail">关闭</button>
            <button class="btn-cancel" v-press :disabled="adoptingId !== null" @click="startReject">不采纳</button>
            <button class="btn-primary" v-press :disabled="adoptingId !== null" @click="submitAdopt">
              {{ adoptingId !== null ? '采纳中…' : '一键采纳' }}
            </button>
          </template>
          <template v-else>
            <button class="btn-cancel" v-press @click="rejecting = false">返回</button>
            <button class="btn-primary" v-press :disabled="processingId !== null" @click="submitReject">
              {{ processingId !== null ? '处理中…' : '确认拒绝' }}
            </button>
          </template>
        </template>
        <button v-else class="btn-cancel" v-press @click="closeDetail">关闭</button>
      </template>
    </FormDialog>

    <!-- 档口确认对话框（两段式采纳第二段）：候选 radio + 「新建档口」选项 -->
    <FormDialog
      :show="stallDialogShow"
      title="选择档口"
      :width="420"
      :footer="false"
      @close="stallDialogShow = false"
    >
      <div v-if="detail" class="stall-confirm">
        <p class="stall-lead">
          提交的档口「{{ detail.snapshot.stallName || '未填写' }}」未匹配到现有档口，请选择归属：
        </p>
        <div class="stall-group" role="radiogroup" aria-label="档口选择">
          <button
            v-for="c in stallCandidates"
            :key="c.id"
            type="button"
            class="stall-opt"
            :class="{ on: stallChoice === `stall:${c.id}` }"
            role="radio"
            :aria-checked="stallChoice === `stall:${c.id}`"
            v-press
            @click="stallChoice = `stall:${c.id}`"
          >{{ c.name }}</button>
          <button
            v-if="detail.snapshot.stallName"
            type="button"
            class="stall-opt"
            :class="{ on: stallChoice === 'new' }"
            role="radio"
            :aria-checked="stallChoice === 'new'"
            v-press
            @click="stallChoice = 'new'"
          >或新建档口：{{ detail.snapshot.stallName }}</button>
        </div>
      </div>
      <template #actions>
        <button class="btn-cancel" v-press @click="stallDialogShow = false">取消</button>
        <button
          class="btn-primary"
          v-press
          :disabled="adoptingId !== null || (stallChoice === 'new' && !(detail?.snapshot.stallName))"
          @click="confirmStall"
        >
          {{ adoptingId !== null ? '采纳中…' : '确认采纳' }}
        </button>
      </template>
    </FormDialog>
  </PageContainer>
</template>

<style scoped>
/* 筛选行（仅状态下拉）：与 FilterBar 同款卡片质感（背景/边框/圆角/阴影走 Token） */
.filter-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-5);
  padding: var(--space-3) var(--space-4);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

/* 目标菜品名：列宽内单行截断，完整名称经 title 提示（同 FeedbackView .dish-name-cell 口径） */
.dish-name-cell { display: inline-block; max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: bottom; }

/* 列表「提交人」格 */
.sub-name { color: var(--text-primary); }

/* ===== 对比卡 ===== */
.diff-card {
  display: flex; flex-direction: column; gap: var(--space-2);
  border: 1px solid var(--border-light); border-radius: var(--radius);
  background: var(--bg-soft); padding: var(--space-3);
}
.diff-title { display: flex; align-items: baseline; gap: var(--space-2); flex-wrap: wrap; font-size: var(--font-sm); font-weight: var(--weight-medium); color: var(--text-primary); }
.diff-target { color: var(--color-primary-text); font-weight: var(--weight-regular); }
.diff-missing { font-size: var(--font-xs); font-weight: var(--weight-regular); color: var(--color-warning); }
.diff-row { display: flex; align-items: flex-start; gap: var(--space-2); font-size: var(--font-sm); }
.diff-label { width: 48px; flex-shrink: 0; color: var(--text-muted); line-height: var(--leading-loose); }
.diff-before, .diff-after { flex: 1; min-width: 0; word-break: break-all; line-height: var(--leading-loose); }
.diff-before { color: var(--text-secondary); }
.diff-after { color: var(--text-primary); display: flex; align-items: center; gap: var(--space-1); flex-wrap: wrap; }
.diff-arrow { flex-shrink: 0; color: var(--text-light); line-height: var(--leading-loose); }
/* 有差异的字段：warning 弱化底色（两侧同染，视线沿行即可对比）；reduced-motion 下静态降级 */
.diff-changed { background: var(--color-warning-soft); border-radius: var(--radius-sm); padding: 0 var(--space-1); }
.diff-badge {
  flex-shrink: 0; font-size: var(--font-xs); line-height: var(--leading-base);
  padding: 0 var(--space-2); border-radius: var(--radius-pill);
  background: var(--color-warning-bg); color: var(--color-warning);
}
.diff-img-groups { flex: 1; min-width: 0; display: flex; gap: var(--space-3); }
.diff-img-group { flex: 1; min-width: 0; }
.diff-img-label { display: block; font-size: var(--font-xs); color: var(--text-muted); margin-bottom: var(--space-1); }
.img-empty { font-size: var(--font-xs); color: var(--text-light); }
.diff-hint { margin: var(--space-1) 0 0; font-size: var(--font-xs); color: var(--text-light); }
@media (prefers-reduced-motion: reduce) {
  .diff-changed { transition: none; }
}

.detail { display: flex; flex-direction: column; gap: var(--space-3); }
/* 元信息块：单块内联排布，标签弱化、取值走主文本色，窄屏自动折行 */
.meta {
  display: flex; flex-wrap: wrap; align-items: center;
  gap: var(--space-2) var(--space-4);
  font-size: var(--font-sm); color: var(--text-muted);
}
.meta-item { display: inline-flex; align-items: center; gap: var(--space-1); }
.mv { color: var(--text-primary); }
.detail-row { display: flex; gap: var(--space-3); font-size: var(--font-base); }
.detail-row-desc { align-items: flex-start; }
.dl { width: 64px; flex-shrink: 0; color: var(--text-muted); }
.dv { color: var(--text-primary); flex: 1; }
.dv.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); white-space: pre-wrap; }
/* 纠错附图缩略图：横向排列、点击页内放大预览（el-image viewer） */
.img-list { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.img-thumb {
  width: 72px; height: 72px; border-radius: var(--radius); cursor: zoom-in;
  border: 1px solid var(--border-light); overflow: hidden; background: var(--bg-soft); display: block;
  transition: transform 160ms var(--ease-out), box-shadow 160ms var(--ease-out);
}
.img-thumb:hover { transform: scale(1.04); box-shadow: var(--shadow-card); }
.img-thumb:active { transform: scale(var(--press-scale)); }
@media (prefers-reduced-motion: reduce) {
  .img-thumb { transition: none; }
  .img-thumb:hover { transform: none; }
}
.reply-area { margin-top: var(--space-4); border-top: 1px solid var(--border-light); padding-top: var(--space-4); }
.reply-area label { display: block; font-size: var(--font-sm); color: var(--text-secondary); margin-bottom: var(--space-2); }
.reply-area .mt-label { margin-top: var(--space-3); }
.reply-area textarea {
  width: 100%; padding: var(--space-2) var(--space-3); border: 1px solid var(--border-strong);
  border-radius: var(--radius); font-size: var(--font-sm); outline: none; resize: vertical; box-sizing: border-box;
  background: var(--bg-card); transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out);
}
.reply-area textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent); }
@media (prefers-reduced-motion: reduce) {
  .reply-area textarea { transition: none; }
}
.required { color: var(--color-error); }
.field-error { margin: var(--space-1) 0 0; font-size: var(--font-xs); color: var(--color-error); }

/* 拒绝原因回显：与错误色语义一致的弱化背景，便于一眼识别 */
.reject-reason { color: var(--text-primary); background: var(--color-error-bg); border-radius: var(--radius-sm); padding: var(--space-2) var(--space-3); display: inline-block; }

/* ===== 档口确认对话框（两段式第二段）：胶囊单选，选中态走语义色 ===== */
.stall-confirm { display: flex; flex-direction: column; gap: var(--space-3); }
.stall-lead { margin: 0; font-size: var(--font-sm); color: var(--text-secondary); line-height: var(--leading-loose); }
.stall-group { display: flex; flex-direction: column; gap: var(--space-2); }
.stall-opt {
  display: flex; align-items: center; gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-strong); border-radius: var(--radius);
  background: var(--bg-card); color: var(--text-secondary);
  font-size: var(--font-sm); font-weight: var(--weight-medium);
  cursor: pointer; user-select: none; text-align: left;
  transition: background 0.2s var(--ease-out), border-color 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
/* hover / 选中态：文字走文字档（C1：填充档作底上文字不达 4.5:1） */
.stall-opt:hover { border-color: var(--color-primary); color: var(--color-primary-text); }
.stall-opt:active { transform: scale(var(--press-scale)); }
.stall-opt:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.stall-opt.on { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary-text); }
@media (prefers-reduced-motion: reduce) {
  .stall-opt { transition: none; }
}
</style>
