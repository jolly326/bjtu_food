<script setup lang="ts">
/**
 * B4 菜品问题反馈管理（页面规格见 [菜品问题反馈.md](../../../docs/ui/web/菜品问题反馈.md)）。
 *
 * <p>**按 `type` 分派**（两类反馈的处置动作不同，列表先分 Tab）：
 * - `field`（信息有误）→ 抽屉内**差异对照 + 逐项勾选采纳**（`acceptedFields`）；档口**两段式确认**
 *   （`needStallConfirm` ⇒ 选既有档口或按提交名新建）；
 * - `gone`（已经下架）→ 抽屉内展示用户的**补充说明 / 图片 / 反馈人数**，处置动作**仅「下架」**或「驳回」
 *   （🔴 **不提供「删除」**：删除为物理删除且级联删除评价，仅在菜品管理中由管理员主动执行）。
 *
 * <p>表格**只放基础列**（类型 / 目标菜品 / 提交人 / 状态 / 提交时间）—— 改动项、楼层影响、处理回复与
 * **处置**都在**处置抽屉**内（同一抽屉两态：`pending` = 处置表单 / 已处理 = 只读详情）。
 *
 * <p>采纳 / 下架 / 驳回**均向提交人投递站内回执**（**文案由服务端按 `type`
 * 与「是否部分采纳」分派**，端上只传可选附注，不拼回执正文）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import {
  adoptCorrection,
  getCorrection,
  listCorrections,
  rejectCorrection,
} from '@/api/corrections'
import type {
  CorrectionAdminVO,
  CorrectionDetailVO,
  CorrectionListParams,
  CorrectionStatus,
  CorrectionType,
} from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import StateBox from '@/components/StateBox.vue'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import { useRowAction } from '@/composables/useRowAction'

const fType = ref<CorrectionType | ''>('')
const fStatus = ref<CorrectionStatus | ''>('')
const fDishId = ref('')

/** 类型 Tab（`type` 筛选；空串 = 全部）；文案与 `StatusTag kind="correctionType"` 保持一致 */
const TYPE_TABS: { value: CorrectionType | ''; label: string }[] = [
  { value: '', label: '全部' },
  { value: 'field', label: '信息有误' },
  { value: 'gone', label: '已经下架' },
]

function params(): CorrectionListParams {
  return {
    type: fType.value || undefined,
    status: fStatus.value || undefined,
    dishId: fDishId.value ? Number(fDishId.value) : undefined,
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
} = usePagedList<CorrectionAdminVO>((pageNo, pageSize) =>
  listCorrections({ page: pageNo, pageSize, ...params() }),
)

/* ==================== 处置 / 详情抽屉 ==================== */
const open = ref(false)
/** 当前列表行（决定「待处理 → 处置表单 / 已处理 → 只读详情」） */
const row = ref<CorrectionAdminVO | null>(null)
const detail = ref<CorrectionDetailVO | null>(null)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)
const outcome = ref<'adopted' | 'rejected'>('adopted')
const accepted = ref<Record<string, boolean>>({})
const reply = ref('')
const rejectReason = ref('')
const submitting = ref(false)

/* 档口两段式确认 */
const candidates = ref<{ id: number; name: string }[]>([])
const chosenStallId = ref(0)
const createIfMissing = ref(false)
const needStallConfirm = ref(false)

/** 当前详情的反馈类型（`field` = 信息有误 / `gone` = 已经下架） */
const isGone = computed(() => detail.value?.type === 'gone')
/** 待处理 → 处置表单；已处理 → 只读详情 */
const isPending = computed(() => row.value?.status === 'pending')

/** 结论文案（`status` + `rejectReason` 派生） */
const outcomeLabel = computed(() => {
  const r = row.value
  if (!r || r.status === 'pending') return '待处理'
  if (r.status === 'rejected') return r.type === 'gone' ? '已驳回（仍在售）' : '已拒绝'
  return r.type === 'gone' ? '已下架' : '已采纳'
})

/**
 * 提交按钮可用性。
 * - `gone` 型：下架**无需勾选任何项**（无可对照差异）⇒ 采纳侧恒可提交；
 * - `field` 型：至少勾选一项（空数组服务端 `400`），且档口确认段须选定归属；
 * - 驳回：原因必填。
 */
const canSubmit = computed(() => {
  if (outcome.value === 'adopted') {
    if (isGone.value) return true
    if (needStallConfirm.value && !createIfMissing.value && !chosenStallId.value) return false
    return Object.values(accepted.value).some(Boolean)
  }
  return rejectReason.value.trim().length > 0
})

/** 行内动作并发保护：提交中该行按钮 `:disabled`（列表页模板 §1.3「并发保护」） */
// 抽屉详情加载也占同一把锁（无「动作 / 成功提示 / 刷新」语义，故不进 runRowAction，只借锁与置灰判据）
const { busyId, isBusy } = useRowAction()

async function openDetail(target: CorrectionAdminVO): Promise<void> {
  open.value = true
  row.value = target
  detail.value = null
  detailError.value = null
  detailLoading.value = true
  outcome.value = 'adopted'
  reply.value = ''
  rejectReason.value = ''
  accepted.value = {}
  candidates.value = []
  chosenStallId.value = 0
  createIfMissing.value = false
  needStallConfirm.value = false
  busyId.value = target.id
  try {
    const d = await getCorrection(target.id)
    detail.value = d
    // `field` 型默认全选（管理员可逐项取消）；`gone` 型无差异项，无可勾选
    for (const diff of d.differences) accepted.value[diff.field] = true
  } catch (e) {
    detailError.value = e instanceof Error ? e.message : '加载详情失败'
  } finally {
    detailLoading.value = false
    busyId.value = null
  }
}

async function submit(): Promise<void> {
  const d = detail.value
  if (!d) return
  if (reply.value.length > 600) {
    ElMessage.warning('回复不能超过 600 字')
    return
  }
  submitting.value = true
  try {
    if (outcome.value === 'adopted') {
      // `gone` 型：请求体只带可选附注，服务端忽略其余字段并直接下架菜品（可逆）
      const res = isGone.value
        ? await adoptCorrection(d.id, reply.value.trim() ? { reply: reply.value.trim() } : {})
        : await adoptCorrection(d.id, {
            acceptedFields: Object.entries(accepted.value)
              .filter(([, v]) => v)
              .map(([k]) => k),
            ...(needStallConfirm.value
              ? createIfMissing.value
                ? { createIfMissing: true }
                : { stallId: chosenStallId.value }
              : {}),
          })
      if (res != null) {
        // 档口未匹配：进入第二段确认
        needStallConfirm.value = true
        candidates.value = res.candidates
        chosenStallId.value = res.candidates[0]?.id ?? 0
        return
      }
      ElMessage.success(isGone.value ? '已下架' : '已采纳')
    } else {
      await rejectCorrection(d.id, {
        reply: reply.value.trim(),
        rejectReason: rejectReason.value.trim(),
      })
      ElMessage.success(isGone.value ? '已驳回' : '已拒绝')
    }
    open.value = false
    row.value = null
    await reload()
  } catch (e) {
    // 已处理再处理（400）/ 不存在（4001）/ 被引用 → 后端原文
    fail(e, '提交失败')
  } finally {
    submitting.value = false
  }
}

/* ==================== 配图大图预览（`gone` 型用户佐证） ==================== */
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
  fType.value = ''
  fStatus.value = ''
  fDishId.value = ''
  reloadFirstPage()
}

/** 切换问题类型 Tab：改筛选条件并回到第一页（单一 handler —— 模板内联多语句会导致构建失败） */
function pickType(value: CorrectionType | '') {
  fType.value = value
  reloadFirstPage()
}

onMounted(() => reloadFirstPage())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>菜品问题反馈管理</h2>
      <!-- 类型 Tab（`type` 筛选）：两类反馈处置动作不同，先分流再看列表 -->
      <div class="tabs" role="tablist" aria-label="问题类型">
        <button
          v-for="t in TYPE_TABS"
          :key="t.value"
          class="tab"
          type="button"
          role="tab"
          :aria-selected="fType === t.value"
          :class="{ active: fType === t.value }"
          v-press
          @click="pickType(t.value)"
        >
          {{ t.label }}
        </button>
      </div>
    </div>

    <div class="card filters">
      <select class="form-input" v-model="fStatus" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="adopted">已采纳</option>
        <option value="rejected">已拒绝</option>
      </select>
      <input
        class="form-input"
        v-model="fDishId"
        placeholder="菜品 ID"
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
      empty-message="暂无反馈"
      @retry="reload"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>类型</th>
            <th>目标菜品</th>
            <th>提交人</th>
            <th>状态</th>
            <th>提交时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td><StatusTag :status="item.type" kind="correctionType" /></td>
            <td>{{ item.dishName ?? '菜品已删除' }}</td>
            <!-- 匿名提交 userId = 0 且昵称为空 → 回落「游客」 -->
            <td>{{ item.userNickname || '游客' }}</td>
            <td><StatusTag :status="item.status" kind="correction" /></td>
            <td class="muted">{{ formatDateTime(item.createdAt) }}</td>
            <td class="actions">
              <button
                class="link"
                type="button"
                :disabled="isBusy(item.id)"
                @click="openDetail(item)"
              >
                {{ item.status === 'pending' ? '处理' : '详情' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <p class="foot-note">
        「类型 / 目标菜品 / 提交人 /
        状态」是快速查阅列；改动项对照、楼层影响、用户佐证与处置入口都在行内「处理 / 详情」内。
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
    <BaseDrawer
      :title="isPending ? (isGone ? '处理下架反馈' : '处理反馈') : '反馈详情'"
      :open="open"
      @close="open = false"
    >
      <StateBox v-if="detailLoading" status="loading" />
      <StateBox v-else-if="detailError" status="error" :message="detailError" />
      <template v-else-if="detail">
        <div class="ctx">
          <div class="ctx-label">
            {{ detail.dishName ?? '菜品已删除' }} · {{ detail.userNickname || '游客' }}
            <StatusTag :status="detail.type" kind="correctionType" />
          </div>
        </div>

        <!-- `gone` 型：用户佐证（补充说明 + 图片）+ 同菜品反馈人数（下架与否人工判断） -->
        <template v-if="isGone">
          <div class="field">
            <label id="corr-gone-label">用户反馈</label>
            <dl class="gone-info" aria-labelledby="corr-gone-label">
              <dt>补充说明</dt>
              <dd>
                <span v-if="detail.note">{{ detail.note }}</span>
                <span v-else class="muted">未填写</span>
              </dd>
              <dt>反馈人数</dt>
              <dd>
                {{ detail.goneUserCount ?? 1 }} 人反馈已下架
                <span class="muted">（仅参考，是否下架由人工判断）</span>
              </dd>
            </dl>
            <div v-if="detail.images?.length" class="ctx-thumbs" role="group" aria-label="反馈图片">
              <button
                v-for="(img, i) in detail.images"
                :key="i"
                class="thumb"
                type="button"
                :aria-label="`查看第 ${i + 1} 张反馈图片`"
                @click="openPreview(detail.images, i)"
              >
                <img :src="resolveImageUrl(img)" alt="" />
              </button>
            </div>
          </div>
        </template>

        <!-- `field` 型差异对照：待处理时可逐项勾选；已处理时只读回放 -->
        <div v-if="!isGone" class="field">
          <label id="corr-adopt-label">
            {{ isPending ? '采纳项（逐项勾选）' : '提交的差异项' }}
          </label>
          <!-- 表格外包 `.table-wrap` ⇒ 窄屏获得横向滚动兜底 -->
          <div class="table-wrap">
            <table class="table table--compact" aria-labelledby="corr-adopt-label">
              <thead>
                <tr>
                  <th v-if="isPending" class="pick-col"></th>
                  <th>字段</th>
                  <th>原值</th>
                  <th>提交值</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="diff in detail.differences" :key="diff.field">
                  <td v-if="isPending" class="pick-col">
                    <input type="checkbox" v-model="accepted[diff.field]" />
                  </td>
                  <td>
                    {{ diff.label }}
                    <div v-if="diff.affectsOthers" class="warn">将连带同档口其它菜品</div>
                  </td>
                  <td class="muted">{{ diff.oldValue || '—' }}</td>
                  <td>{{ diff.newValue || '—' }}</td>
                </tr>
                <tr v-if="!detail.differences.length">
                  <td :colspan="isPending ? 4 : 3" class="muted">已无待采纳差异项</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 已处理 → 只读结论与回复 -->
        <div v-if="!isPending" class="detail-meta">
          <DetailMetaRow k="反馈 ID" num>#{{ detail.id }}</DetailMetaRow>
          <div class="meta-row">
            <span class="meta-key">提交人</span>
            <span class="meta-val">
              {{ detail.userNickname || '游客' }}
              <span class="muted"> #{{ detail.userId }}</span>
            </span>
          </div>
          <DetailMetaRow k="处理结论">{{ outcomeLabel }}</DetailMetaRow>
          <DetailMetaRow k="处理回复">{{ detail.reply || '—' }}</DetailMetaRow>
          <div class="meta-row" v-if="detail.rejectReason">
            <DetailMetaRow k="不采纳原因">{{ detail.rejectReason }}</DetailMetaRow>
          </div>
          <DetailMetaRow k="提交时间">{{ formatDateTime(detail.createdAt) }}</DetailMetaRow>
          <DetailMetaRow k="处理时间">{{ formatDateTime(detail.handledAt) }}</DetailMetaRow>
        </div>

        <!-- 待处理 → 处置表单 -->
        <template v-else>
          <div class="field">
            <label id="corr-outcome-label">处理结论</label>
            <div class="tag-options" role="radiogroup" aria-labelledby="corr-outcome-label">
              <button
                class="tag-option"
                type="button"
                role="radio"
                :aria-checked="outcome === 'adopted'"
                :class="{ active: outcome === 'adopted' }"
                @click="outcome = 'adopted'"
              >
                {{ isGone ? '下架该菜品' : '采纳' }}
              </button>
              <button
                class="tag-option"
                type="button"
                role="radio"
                :aria-checked="outcome === 'rejected'"
                :class="{ active: outcome === 'rejected' }"
                @click="outcome = 'rejected'"
              >
                {{ isGone ? '驳回（仍在售）' : '不采纳' }}
              </button>
            </div>
          </div>

          <!-- `gone` 型采纳 = 下架：说明写回影响（可逆，评价保留；本流程不提供删除） -->
          <p v-if="isGone && outcome === 'adopted'" class="warn note-line">
            下架仅把该菜品置为「已下架」，可重新上架、评价完整保留；删除请到「菜品管理」由管理员主动执行。
          </p>

          <!-- 档口两段式确认（仅 `field` 型采纳了档口名时才触发） -->
          <div
            v-if="!isGone && outcome === 'adopted' && needStallConfirm"
            class="field stall-confirm"
          >
            <label for="corr-stall">档口归属确认</label>
            <select
              id="corr-stall"
              class="form-input"
              v-model.number="chosenStallId"
              :disabled="createIfMissing"
            >
              <option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <label class="check-line">
              <input type="checkbox" v-model="createIfMissing" />
              <span>按提交的档口名新建档口后挂靠</span>
            </label>
          </div>

          <div v-if="outcome === 'rejected'" class="field">
            <label for="corr-reject-reason">{{
              isGone ? '仍在售的原因（必填，≤200 字）' : '不采纳原因（必填，≤200 字）'
            }}</label>
            <input
              id="corr-reject-reason"
              class="form-input"
              v-model="rejectReason"
              maxlength="200"
            />
          </div>

          <div class="field">
            <label for="corr-reply">处理回复（可选，≤600 字）</label>
            <textarea
              id="corr-reply"
              class="form-textarea"
              v-model="reply"
              rows="3"
              maxlength="600"
              placeholder="随站内回执下发给提交人；留空则用固定文案"
            />
            <div class="hint">{{ reply.length }} / 600</div>
          </div>
        </template>
      </template>

      <template #actions>
        <template v-if="isPending">
          <button class="btn-secondary" type="button" @click="open = false">取消</button>
          <button
            class="btn-primary"
            type="button"
            :disabled="submitting || !canSubmit"
            v-press
            @click="submit"
          >
            {{ submitting ? '提交中…' : '确认' }}
          </button>
        </template>
        <template v-else>
          <button class="btn-secondary" type="button" @click="open = false">关闭</button>
        </template>
      </template>
    </BaseDrawer>

    <!-- 反馈图片大图预览：挂载即打开 -->
    <ImagePreview
      v-if="previewOpen"
      :images="previewImages"
      :index="previewIndex"
      @close="previewOpen = false"
    />
  </div>
</template>

<style scoped>
/* 类型 Tab（`type` 筛选）：与页头同一行，置于标题右侧 */
.tabs {
  display: flex;
  gap: var(--space-1);
  margin-left: auto;
}
.tab {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: var(--font-sm);
  cursor: pointer;
}
.tab.active {
  background: var(--color-primary-bg);
  border-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: var(--weight-medium);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-4);
}
.filters .form-input {
  width: 160px;
}
.warn {
  color: var(--color-warning);
  font-size: var(--font-xs);
}
.note-line {
  margin: 0 0 var(--space-4);
  line-height: 1.5;
}

/* `gone` 型佐证区：说明 + 反馈人数（定义列表，左标签右内容） */
.gone-info {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: var(--space-2) var(--space-3);
  margin: 0;
  font-size: var(--font-sm);
}
.gone-info dt {
  color: var(--text-secondary);
}
.gone-info dd {
  margin: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

/* 用户配图（只读对比，不提供上传 / 删除） */
.ctx-thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}
.ctx-thumbs img {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-sm);
  object-fit: cover;
}
.pick-col {
  width: 32px;
}
.stall-confirm {
  background: var(--bg-soft);
  border-radius: var(--radius);
  padding: var(--space-3);
}
</style>
