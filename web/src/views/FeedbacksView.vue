<script setup lang="ts">
/**
 * B2 意见反馈管理（页面规格见 [反馈与举报.md](../../../docs/ui/web/反馈与举报.md) §B2）。
 *
 * <p>要点：列表走 `GET /admin/feedbacks`（筛选：状态 / 反馈类型 / 提交人 / 关键词）；
 * 表格**只放基础列**（类型 / 提交人 / 状态 / 提交时间）—— 正文、配图与**处置**都在
 * **处置抽屉**内（同一抽屉两态：`pending` = 处置表单 / 已处理 = 只读详情）。
 *
 * <p>类型下拉数据源 = 反馈类型字典（`GET /admin/feedback-options`，**含已停用**——
 * 停用类型的历史反馈仍需能筛能译）。
 */
import ActiveFilters from '@/components/ActiveFilters.vue'
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import { canWrite } from '@/utils/permissions'
import { handleFeedback, listFeedbacks } from '@/api/feedbacks'
import { listFeedbackOptions } from '@/api/feedbackOptions'
import type {
  FeedbackAdminVO,
  FeedbackListParams,
  ModerationOutcome,
  ModerationStatus,
  OptionAdminVO,
} from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import { useRowAction } from '@/composables/useRowAction'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import AppIcon from '@/components/AppIcon.vue'
import ModerationHandleForm from '@/components/ModerationHandleForm.vue'

const fStatus = ref<ModerationStatus | ''>('')
const fOptionId = ref(0)
const fUserId = ref<number | ''>('')
const fKeyword = ref('')

/** 类型下拉选项（含已停用：历史反馈仍按停用类型落库，必须能筛能译） */
const options = ref<OptionAdminVO[]>([])

function params(): FeedbackListParams {
  return {
    status: fStatus.value || undefined,
    optionId: fOptionId.value || undefined,
    userId: fUserId.value === '' ? undefined : Number(fUserId.value),
    keyword: fKeyword.value || undefined,
  }
}

const {
  items,
  total,
  page,
  pageCount,
  firstLoading,
  isEmpty,
  hasData,
  error,
  sessionInvalid,
  reload,
  reloadFirstPage,
  prevPage,
  nextPage,
} = usePagedList<FeedbackAdminVO>((pageNo, pageSize) =>
  listFeedbacks({ page: pageNo, pageSize, ...params() }),
)

onMounted(async () => {
  reloadFirstPage()
  try {
    options.value = await listFeedbackOptions()
  } catch (e) {
    // 字典拉取失败不阻断列表（类型筛选退化为不可用），错误原文透出
    fail(e, '反馈类型加载失败')
  }
})

/* ===== 处置 / 详情（抽屉） ===== */
const open = ref(false)
const current = ref<FeedbackAdminVO | null>(null)
const outcome = ref<ModerationOutcome>('handled')
const replyTitle = ref('')
const replyContent = ref('')
/** 行内 / 抽屉动作并发保护：提交中该行（或抽屉当前行）动作置灰（列表页模板 §1.3「并发保护」） */
const { isBusy, runRowAction } = useRowAction()

/** 待处理 → 处置表单；已处理 → 只读详情（再处置服务端会以 400 拒绝） */
const isPending = computed(() => current.value?.status === 'pending')

const outcomeLabel = computed(() => {
  const row = current.value
  if (!row || row.status === 'pending') return '待处理'
  return row.outcome === 'rejected' ? '不采纳' : '通过'
})

function openHandle(row: FeedbackAdminVO): void {
  current.value = row
  outcome.value = 'handled'
  replyTitle.value = ''
  replyContent.value = ''
  open.value = true
}

function submitHandle(): Promise<void> {
  const row = current.value
  if (!row) return Promise.resolve()
  if (outcome.value === 'rejected' && !replyContent.value.trim()) {
    ElMessage.warning('不采纳时必须填写不采纳原因')
    return Promise.resolve()
  }
  return runRowAction({
    id: row.id,
    action: () =>
      handleFeedback(row.id, {
        outcome: outcome.value,
        replyTitle: replyTitle.value.trim() || undefined,
        replyContent: replyContent.value.trim() || undefined,
      }),
    successMessage: '已处理',
    failMessage: '处理失败',
    refresh: reload,
    closeDrawer: () => {
      open.value = false
    },
  })
}

/* ===== 配图大图预览（表格角标 / 抽屉配图共用入口） ===== */
const previewOpen = ref(false)
const previewImages = ref<string[]>([])
const previewIndex = ref(0)

function openPreview(images: string[], index: number): void {
  if (!images || images.length === 0) return
  previewImages.value = images
  previewIndex.value = index
  previewOpen.value = true
}

function reset(): void {
  fStatus.value = ''
  fOptionId.value = 0
  fUserId.value = ''
  fKeyword.value = ''
  reloadFirstPage()
}

/** 已生效筛选条件（供 `ActiveFilters` 回显） */
const activeFilters = computed(() => {
  const out: { key: string; label: string; value: string }[] = []
  if (fStatus.value)
    out.push({
      key: 'status',
      label: '状态',
      value: fStatus.value === 'pending' ? '待处理' : '已处理',
    })
  if (fOptionId.value) {
    const opt = options.value.find((o) => o.id === fOptionId.value)
    out.push({ key: 'optionId', label: '类型', value: opt?.name ?? `#${fOptionId.value}` })
  }
  if (fUserId.value !== '' && fUserId.value !== null)
    out.push({ key: 'userId', label: '提交人', value: `#${fUserId.value}` })
  if (fKeyword.value.trim())
    out.push({ key: 'keyword', label: '关键词', value: fKeyword.value.trim() })
  return out
})

/** 清除单个筛选条件并重查 */
function clearFilter(key: string): void {
  if (key === 'status') fStatus.value = ''
  if (key === 'optionId') fOptionId.value = 0
  if (key === 'userId') fUserId.value = ''
  if (key === 'keyword') fKeyword.value = ''
  reloadFirstPage()
}
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>意见反馈管理</h2></div>

    <!-- 筛选区是紧凑单行（不引入可见 `<label>`）⇒ 每个控件以 `aria-label` 自报字段名 -->
    <div class="card filters">
      <select
        class="form-input"
        v-model="fStatus"
        aria-label="按状态筛选"
        @change="reloadFirstPage"
      >
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="handled">已处理</option>
      </select>
      <select
        class="form-input"
        v-model.number="fOptionId"
        aria-label="按反馈类型筛选"
        @change="reloadFirstPage"
      >
        <option value="0">全部类型</option>
        <option v-for="opt in options" :key="opt.id" :value="opt.id">{{ opt.name }}</option>
      </select>
      <input
        class="form-input"
        v-model.number="fUserId"
        type="number"
        min="1"
        aria-label="按提交人 ID 筛选"
        placeholder="提交人 ID"
        @keyup.enter="reloadFirstPage"
      />
      <input
        class="form-input"
        v-model="fKeyword"
        aria-label="正文 / 处置文案关键词"
        placeholder="正文 / 处置文案"
        @keyup.enter="reloadFirstPage"
      />
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
      <!-- 已生效筛选条件回显：让管理员一眼看出当前结果被什么条件筛出（可点单个清除） -->
      <ActiveFilters :items="activeFilters" @remove="clearFilter" @clear="reset" />
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 分页 ⑥ 会话失效 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无反馈"
      @retry="reload"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>类型</th>
            <th>提交人</th>
            <th>状态</th>
            <th>提交时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>
              <!-- 类型中文名由字典实时翻译（停用类型仍可译出），端上零硬编码 -->
              {{ row.optionName }}
              <!-- 「该行有配图」为审核高频判据 ⇒ 以角标保留在基础列内 -->
              <span v-if="row.images?.length" class="img-flag">
                <AppIcon name="image" :size="12" />{{ row.images.length }}
              </span>
            </td>
            <!-- 提交人昵称：服务端联表带出，恒可读到（注销账号为「已注销用户」） -->
            <td>{{ row.userName }}</td>
            <td><StatusTag :status="row.status" kind="feedback" /></td>
            <td class="muted">{{ formatDateTime(row.createdAt) }}</td>
            <td class="actions">
              <button
                v-if="canWrite()"
                class="link"
                type="button"
                :disabled="isBusy(row.id)"
                @click="openHandle(row)"
              >
                {{ row.status === 'pending' ? '处理' : '详情' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <Pager
        :total="total"
        :page="page"
        :page-count="pageCount"
        @prev="prevPage"
        @next="nextPage"
      />
    </div>

    <!-- 处置 / 详情抽屉 -->
    <BaseDrawer :title="isPending ? '处理反馈' : '反馈详情'" :open="open" @close="open = false">
      <!-- 只读内容区：正文全文（不截断）+ 配图（点击看大图） -->
      <div class="ctx">
        <div class="ctx-label">
          {{ current?.optionName }} · {{ current?.userName }}
          <StatusTag :status="current?.status ?? 'pending'" kind="feedback" />
        </div>
        <div class="ctx-body">{{ current?.content || '（无内容）' }}</div>
        <div v-if="current?.images?.length" class="ctx-thumbs" role="group" aria-label="反馈配图">
          <button
            v-for="(img, i) in current.images"
            :key="i"
            class="thumb"
            type="button"
            :aria-label="`查看第 ${i + 1} 张反馈配图`"
            @click="openPreview(current.images, i)"
          >
            <img :src="resolveImageUrl(img)" alt="" />
          </button>
        </div>
      </div>

      <!-- 已处理 → 只读结论与回执（分两组：处理结果 / 时间与标识） -->
      <div v-if="!isPending" class="detail-group">
        <div class="detail-group-title">处理结果</div>
        <div class="detail-meta detail-meta--grid">
          <DetailMetaRow k="处理结论" span>{{ outcomeLabel }}</DetailMetaRow>
          <div class="meta-row meta-row--span">
            <span class="meta-key">提交人</span>
            <span class="meta-val">
              {{ current?.userName }}
              <span class="muted"> #{{ current?.userId }}</span>
            </span>
          </div>
          <DetailMetaRow k="处置人">{{ current?.handledByName ?? '—' }}</DetailMetaRow>
          <DetailMetaRow k="回执标题">{{ current?.replyTitle ?? '—' }}</DetailMetaRow>
          <DetailMetaRow k="处置文案" span>{{ current?.replyContent || '—' }}</DetailMetaRow>
        </div>
      </div>

      <div v-if="!isPending" class="detail-group">
        <div class="detail-group-title">时间与标识</div>
        <div class="detail-meta detail-meta--grid">
          <DetailMetaRow k="反馈 ID" num>#{{ current?.id }}</DetailMetaRow>
          <DetailMetaRow k="提交时间">{{ formatDateTime(current?.createdAt) }}</DetailMetaRow>
          <DetailMetaRow k="处置时间">{{ formatDateTime(current?.handledAt) }}</DetailMetaRow>
        </div>
      </div>

      <!-- 待处理 → 处置表单（三输入与举报处置共用同一表单体） -->
      <ModerationHandleForm
        v-if="isPending"
        v-model:outcome="outcome"
        v-model:reply-title="replyTitle"
        v-model:reply-content="replyContent"
        handled-label="通过 / 已处理"
        rejected-label="不采纳 / 退回"
        recipient="提交人"
      />

      <template #actions>
        <template v-if="isPending">
          <button class="btn-secondary" type="button" @click="open = false">取消</button>
          <button
            class="btn-primary"
            type="button"
            :disabled="isBusy(current?.id)"
            v-press
            @click="submitHandle"
          >
            {{ isBusy(current?.id) ? '提交中…' : '提交处置' }}
          </button>
        </template>
        <template v-else>
          <button class="btn-secondary" type="button" @click="open = false">关闭</button>
        </template>
      </template>
    </BaseDrawer>

    <!-- 配图大图预览：挂载即打开 -->
    <ImagePreview
      v-if="previewOpen"
      :images="previewImages"
      :index="previewIndex"
      @close="previewOpen = false"
    />
  </div>
</template>

<style scoped>
/* 筛选区内控件定宽（.filters 容器样式已收敛到全局 shared.css） */
.filters .form-input {
  width: 160px;
}
/* 抽屉内配图：与表格缩略图同档尺寸，点击即看大图 */
.ctx-thumbs {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
.ctx-thumbs img {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-sm);
  object-fit: cover;
}
</style>
