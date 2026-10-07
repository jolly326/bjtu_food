<script setup lang="ts">
/**
 * B3 举报管理（页面规格见 [反馈与举报.md](../../../docs/ui/web/反馈与举报.md) §B3）。
 *
 * <p>要点：与 B2 **同一列表端点**（`GET /admin/feedbacks?category=report`）与**同一处置端点**
 * （`PUT /admin/feedbacks/{id}`）；表格**只放基础列**（举报原因 / 举报人 / 状态 / 提交时间）——
 * 被举报评价原文、评价可见性、补充说明与**处置**都在**处置抽屉**内
 * （同一抽屉两态：`pending` = 处置表单 / 已处理 = 只读详情）。
 *
 * <p>处置抽屉含**「同时隐藏被举报评价」复选**（默认勾选；评价已隐藏时**置灰且不可改**）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import { handleFeedback, listFeedbacks } from '@/api/feedbacks'
import type { AdminPage, FeedbackListParams, FeedbackStatus, ReportAdminVO } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ImagePreview from '@/components/ImagePreview.vue'

const fStatus = ref<FeedbackStatus | ''>('')
const fReason = ref('')

function params(): FeedbackListParams {
  return {
    category: 'report',
    status: fStatus.value || undefined,
    keyword: fReason.value || undefined,
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
} = usePagedList<ReportAdminVO>(
  // B3 与 B2 同端点：`category=report` 时服务端在反馈行基础上补举报私有字段
  // （subReasonId/reasonLabel/reviewId/reviewContent/reviewDishName/reviewHidden）⇒ 收窄为本页 VO。
  async (pageNo, pageSize) =>
    (await listFeedbacks({ page: pageNo, pageSize, ...params() })) as AdminPage<ReportAdminVO>,
)

/* ===== 处置 / 详情（抽屉） ===== */
const open = ref(false)
const current = ref<ReportAdminVO | null>(null)
const outcome = ref<'handled' | 'rejected'>('handled')
const hideReview = ref(true)
const reply = ref('')
const rejectReason = ref('')
const submitting = ref(false)

/** 待处理 → 处置表单；已处理 → 只读详情 */
const isPending = computed(() => current.value?.status === 'pending')
/** 评价已隐藏 ⇒ 复选置灰且恒为 true（不做无意义的重复隐藏） */
const hideLocked = computed(() => current.value?.reviewHidden === true)

const canSubmit = computed(() =>
  outcome.value === 'rejected' ? rejectReason.value.trim().length > 0 : true,
)

const outcomeLabel = computed(() => {
  const row = current.value
  if (!row || row.status === 'pending') return '待处理'
  return row.outcome === 'rejected' ? '不成立 / 不予处理' : '成立 / 已处理'
})

function openHandle(row: ReportAdminVO): void {
  current.value = row
  outcome.value = 'handled'
  // 复选默认勾选（页面规格 反馈与举报.md §B3）；被举报评价已隐藏时置灰锁定，取值仍为 true（见 hideLocked）
  hideReview.value = true
  reply.value = ''
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
  submitting.value = true
  try {
    await handleFeedback(row.id, {
      outcome: outcome.value,
      hideReview: outcome.value === 'handled' ? hideReview.value : false,
      reply: reply.value.trim() || undefined,
      rejectReason: outcome.value === 'rejected' ? rejectReason.value.trim() : undefined,
    })
    ElMessage.success('已处置')
    open.value = false
    await reload()
  } catch (e) {
    fail(e, '处置失败')
  } finally {
    submitting.value = false
  }
}

/* ===== 配图大图预览（被举报评价配图 / 举报补充配图共用入口） ===== */
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
  fReason.value = ''
  reloadFirstPage()
}

onMounted(() => reloadFirstPage())
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>举报管理</h2></div>

    <div class="card filters">
      <select class="form-input" v-model="fStatus" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="handled">已处理</option>
      </select>
      <input
        class="form-input"
        v-model="fReason"
        placeholder="举报原因"
        @keyup.enter="reloadFirstPage"
      />
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 分页 ⑥ 会话失效 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无举报"
      @retry="reload"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>举报原因</th>
            <th>举报人</th>
            <th>状态</th>
            <th>提交时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.reasonLabel }}</td>
            <td>{{ row.userNickname || '游客' }}</td>
            <td><StatusTag :status="row.status" kind="feedback" /></td>
            <td class="muted">{{ formatDateTime(row.createdAt) }}</td>
            <td class="actions">
              <button class="link" type="button" @click="openHandle(row)">
                {{ row.status === 'pending' ? '处置' : '详情' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <p class="foot-note">
        「举报原因 / 举报人 /
        状态」是快速查阅列；被举报评价原文与可见性、补充说明与处置入口都在行内「处置 / 详情」内。
      </p>

      <Pager
        :total="total"
        :page="page"
        :page-count="pageCount"
        @prev="prevPage"
        @next="nextPage"
      />
    </div>

    <!-- 处置 / 详情抽屉 -->
    <BaseDrawer :title="isPending ? '处置举报' : '举报详情'" :open="open" @close="open = false">
      <!-- 只读内容区：被举报评价原文（不截断）+ 可见性 -->
      <div class="ctx">
        <div class="ctx-label">
          被举报评价 · {{ current?.reviewDishName ?? '菜品已删除' }}
          <span class="muted"> #{{ current?.reviewId }}</span>
        </div>
        <div class="ctx-body">{{ current?.reviewContent ?? '（评价已删除）' }}</div>
        <div class="ctx-meta">
          <StatusTag :status="current?.reviewHidden ? 'hidden' : 'visible'" kind="review" />
        </div>
      </div>

      <div class="field">
        <label>举报原因</label>
        <div class="readonly">
          {{ current?.reasonLabel }}
        </div>
      </div>

      <!-- 举报人补充说明 + 配图（有则展示） -->
      <div class="field">
        <label>补充说明</label>
        <div class="readonly">{{ current?.content || '（未填写）' }}</div>
        <div v-if="current?.images?.length" class="ctx-thumbs" role="group" aria-label="举报配图">
          <button
            v-for="(img, i) in current.images"
            :key="i"
            class="thumb"
            type="button"
            :aria-label="`查看第 ${i + 1} 张举报配图`"
            @click="openPreview(current.images, i)"
          >
            <img :src="resolveImageUrl(img)" alt="" />
          </button>
        </div>
      </div>

      <!-- 已处理 → 只读结论 -->
      <div v-if="!isPending" class="detail-meta">
        <DetailMetaRow k="举报 ID" num>#{{ current?.id }}</DetailMetaRow>
        <div class="meta-row">
          <span class="meta-key">举报人</span>
          <span class="meta-val">
            {{ current?.userNickname || '游客' }}
            <span class="muted"> #{{ current?.userId }}</span>
          </span>
        </div>
        <DetailMetaRow k="处理结论">{{ outcomeLabel }}</DetailMetaRow>
        <DetailMetaRow k="回复举报人">{{ current?.reply || '—' }}</DetailMetaRow>
        <div class="meta-row" v-if="current?.rejectReason">
          <DetailMetaRow k="不成立原因">{{ current.rejectReason }}</DetailMetaRow>
        </div>
        <DetailMetaRow k="提交时间">{{ formatDateTime(current?.createdAt) }}</DetailMetaRow>
        <DetailMetaRow k="处理时间">{{ formatDateTime(current?.handledAt) }}</DetailMetaRow>
      </div>

      <!-- 待处理 → 处置表单 -->
      <template v-else>
        <div class="field">
          <label id="rep-outcome-label">处理结论</label>
          <div class="tag-options" role="radiogroup" aria-labelledby="rep-outcome-label">
            <button
              class="tag-option"
              type="button"
              role="radio"
              :aria-checked="outcome === 'handled'"
              :class="{ active: outcome === 'handled' }"
              @click="outcome = 'handled'"
            >
              成立 / 已处理
            </button>
            <button
              class="tag-option"
              type="button"
              role="radio"
              :aria-checked="outcome === 'rejected'"
              :class="{ active: outcome === 'rejected' }"
              @click="outcome = 'rejected'"
            >
              不成立 / 不予处理
            </button>
          </div>
        </div>

        <div class="field" v-if="outcome === 'handled'">
          <label class="check-line">
            <input type="checkbox" v-model="hideReview" :disabled="hideLocked" />
            <span>同时隐藏被举报评价{{ hideLocked ? '（该评价已隐藏）' : '（默认勾选）' }}</span>
          </label>
        </div>

        <div class="field" v-if="outcome === 'rejected'">
          <label for="rep-reject-reason">不成立原因（必填，≤200 字）</label>
          <input id="rep-reject-reason" class="form-input" v-model="rejectReason" maxlength="200" />
        </div>

        <div class="field">
          <label for="rep-reply">回复举报人（可选，≤600 字）</label>
          <textarea id="rep-reply" class="form-textarea" v-model="reply" rows="4" maxlength="600" />
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
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-4);
}
.filters .form-input {
  width: 200px;
}
.ctx-meta {
  margin-top: var(--space-2);
}
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
