<script setup lang="ts">
/**
 * B2 意见反馈管理（页面规格见 [反馈与举报.md](../../../docs/ui/web/反馈与举报.md) §B2）。
 *
 * <p>要点：列表走**同一端点** `GET /admin/feedbacks?category=feedback`（B3 举报为 `category=report`）；
 * 表格**只放基础列**（类型 / 提交人 / 状态 / 提交时间）—— 正文、配图、回复与**处置**都在
 * **处置抽屉**内（同一抽屉两态：`pending` = 处置表单 / 已处理 = 只读详情）。
 *
 * <p>类型只列**当前写入白名单**（`bug` / `suggestion` / `other`，真源
 * `FeedbackConst.WRITABLE_TYPES`）：筛选下拉与白名单同源。
 */
import ActiveFilters from '@/components/ActiveFilters.vue'
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import { canWrite } from '@/utils/permissions'
import { handleFeedback, listFeedbacks } from '@/api/feedbacks'
import type { FeedbackAdminVO, FeedbackListParams, FeedbackStatus } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import AppIcon from '@/components/AppIcon.vue'

const fStatus = ref<FeedbackStatus | ''>('')
const fType = ref('')
const fKeyword = ref('')

/**
 * 类型文案 = **当前提交白名单**（`FeedbackConst.WRITABLE_TYPES`：bug / suggestion / other）。
 *
 * <p>只列可写类型；万一命中未知类型，回落显示原始机器值 ——
 * 静默丢弃会让「这一行是什么」不可解释。
 */
const TYPE_LABELS: Record<string, string> = {
  bug: '程序功能 Bug',
  suggestion: '产品功能建议',
  other: '其他相关问题',
}
const typeLabel = (t: string): string => TYPE_LABELS[t] ?? t

function params(): FeedbackListParams {
  return {
    category: 'feedback',
    status: fStatus.value || undefined,
    type: fType.value || undefined,
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

/* ===== 处置 / 详情（抽屉） ===== */
const open = ref(false)
const current = ref<FeedbackAdminVO | null>(null)
const reply = ref('')
const outcome = ref<'handled' | 'rejected'>('handled')
const rejectReason = ref('')
const submitting = ref(false)

/** 待处理 → 处置表单；已处理 → 只读详情（再处置服务端会以 400 拒绝） */
const isPending = computed(() => current.value?.status === 'pending')

const canSubmit = computed(() => {
  if (outcome.value === 'rejected') return rejectReason.value.trim().length > 0
  return true
})

/** 结论文案（表无物理列，按 `status` + `rejectReason` 派生） */
const outcomeLabel = computed(() => {
  const row = current.value
  if (!row || row.status === 'pending') return '待处理'
  return row.outcome === 'rejected' ? '不采纳 / 退回' : '通过 / 已处理'
})

function openHandle(row: FeedbackAdminVO): void {
  current.value = row
  reply.value = ''
  outcome.value = 'handled'
  rejectReason.value = ''
  open.value = true
}

async function submitHandle(): Promise<void> {
  const row = current.value
  if (!row) return
  if (!canSubmit.value) {
    ElMessage.warning('不采纳时必须填写原因')
    return
  }
  if (reply.value.length > 600) {
    ElMessage.warning('回复不能超过 600 字')
    return
  }
  submitting.value = true
  try {
    await handleFeedback(row.id, {
      outcome: outcome.value,
      reply: reply.value.trim() || undefined,
      rejectReason: outcome.value === 'rejected' ? rejectReason.value.trim() : undefined,
    })
    ElMessage.success('已处理')
    open.value = false
    await reload()
  } catch (e) {
    // 已处理再处理（400）/ 记录不存在（4001）→ 后端原文
    fail(e, '处理失败')
  } finally {
    submitting.value = false
  }
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
  fType.value = ''
  fKeyword.value = ''
  reloadFirstPage()
}

/** 已生效筛选条件（供 `ActiveFilters` 回显；类型文案复用页面既有 `typeLabel`） */
const activeFilters = computed(() => {
  const out: { key: string; label: string; value: string }[] = []
  if (fStatus.value)
    out.push({
      key: 'status',
      label: '状态',
      value: fStatus.value === 'pending' ? '待处理' : '已处理',
    })
  if (fType.value) out.push({ key: 'type', label: '类型', value: typeLabel(fType.value) })
  if (fKeyword.value.trim())
    out.push({ key: 'keyword', label: '内容', value: fKeyword.value.trim() })
  return out
})

/** 清除单个筛选条件并重查 */
function clearFilter(key: string): void {
  if (key === 'status') fStatus.value = ''
  if (key === 'type') fType.value = ''
  if (key === 'keyword') fKeyword.value = ''
  reloadFirstPage()
}

onMounted(() => reloadFirstPage())
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>意见反馈管理</h2></div>

    <div class="card filters">
      <select class="form-input" v-model="fStatus" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="handled">已处理</option>
      </select>
      <select class="form-input" v-model="fType" @change="reloadFirstPage">
        <option value="">全部类型</option>
        <option value="bug">程序功能 Bug</option>
        <option value="suggestion">产品功能建议</option>
        <option value="other">其他相关问题</option>
      </select>
      <input
        class="form-input"
        v-model="fKeyword"
        placeholder="内容 / 回复"
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
              {{ typeLabel(row.type) }}
              <!-- 「该行有配图」为审核高频判据 ⇒ 以角标保留在基础列内 -->
              <span v-if="row.images?.length" class="img-flag">
                <AppIcon name="image" :size="12" />{{ row.images.length }}
              </span>
            </td>
            <!-- 匿名提交 userId = 0 → 服务端回落「游客」 -->
            <td>{{ row.userNickname || '游客' }}</td>
            <td><StatusTag :status="row.status" kind="feedback" /></td>
            <td class="muted">{{ formatDateTime(row.createdAt) }}</td>
            <td class="actions">
              <button v-if="canWrite()" class="link" type="button" @click="openHandle(row)">
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
          {{ typeLabel(current?.type ?? '') }} · {{ current?.userNickname || '游客' }}
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

      <!-- 已处理 → 只读结论与回复（分两组：处理结果 / 提交与处理时间） -->
      <div v-if="!isPending" class="detail-group">
        <div class="detail-group-title">处理结果</div>
        <div class="detail-meta detail-meta--grid">
          <DetailMetaRow k="处理结论" span>{{ outcomeLabel }}</DetailMetaRow>
          <div class="meta-row meta-row--span">
            <span class="meta-key">提交人</span>
            <span class="meta-val">
              {{ current?.userNickname || '游客' }}
              <span class="muted"> #{{ current?.userId }}</span>
            </span>
          </div>
          <DetailMetaRow k="处理回复" span>{{ current?.reply || '—' }}</DetailMetaRow>
          <div v-if="current?.rejectReason" class="meta-row meta-row--span">
            <span class="meta-key">不采纳原因</span>
            <span class="meta-val">{{ current.rejectReason }}</span>
          </div>
        </div>
      </div>

      <div v-if="!isPending" class="detail-group">
        <div class="detail-group-title">时间与标识</div>
        <div class="detail-meta detail-meta--grid">
          <DetailMetaRow k="反馈 ID" num>#{{ current?.id }}</DetailMetaRow>
          <DetailMetaRow k="提交时间">{{ formatDateTime(current?.createdAt) }}</DetailMetaRow>
          <DetailMetaRow k="处理时间">{{ formatDateTime(current?.handledAt) }}</DetailMetaRow>
        </div>
      </div>

      <!-- 待处理 → 处置表单 -->
      <template v-else>
        <div class="field">
          <label id="fb-outcome-label">处理结论</label>
          <div class="tag-options" role="radiogroup" aria-labelledby="fb-outcome-label">
            <button
              class="tag-option"
              type="button"
              role="radio"
              :aria-checked="outcome === 'handled'"
              :class="{ active: outcome === 'handled' }"
              @click="outcome = 'handled'"
            >
              通过 / 已处理
            </button>
            <button
              class="tag-option"
              type="button"
              role="radio"
              :aria-checked="outcome === 'rejected'"
              :class="{ active: outcome === 'rejected' }"
              @click="outcome = 'rejected'"
            >
              不采纳 / 退回
            </button>
          </div>
        </div>

        <div class="field" v-if="outcome === 'rejected'">
          <label for="fb-reject-reason">不采纳原因（必填，≤200 字）</label>
          <input id="fb-reject-reason" class="form-input" v-model="rejectReason" maxlength="200" />
        </div>

        <div class="field">
          <label for="fb-reply">处理回复（可选，≤600 字）</label>
          <textarea
            id="fb-reply"
            class="form-textarea"
            v-model="reply"
            rows="4"
            maxlength="600"
            placeholder="将随站内回执下发给提交人；留空则回执用固定文案"
          />
          <div class="hint">{{ reply.length }} / 600</div>
        </div>
      </template>

      <template #actions>
        <template v-if="isPending">
          <button class="btn-secondary" type="button" @click="open = false">取消</button>
          <button
            class="btn-primary"
            type="button"
            :disabled="submitting"
            v-press
            @click="submitHandle"
          >
            {{ submitting ? '提交中…' : '提交处置' }}
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
