<script setup lang="ts">
/**
 * B4 菜品纠错管理（页面规格见 [菜品纠错.md](../../../docs/web/ui/菜品纠错.md)）。
 *
 * <p>要点：分页（页码 + 共 N 条）+ 六态；处置载体 = **抽屉**（差异对照表 + 结论 + 回复 ⇒ 含只读内容区块）；
 * **逐项勾选采纳**（`acceptedFields`）；档口两段式确认（`needStallConfirm` ⇒ 选既有档口或按提交名新建）；
 * 采纳 / 拒绝**均向提交人投递站内回执**（空附注用固定文案）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
} from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import StateBox from '@/components/StateBox.vue'
import StatusTag from '@/components/StatusTag.vue'

const fStatus = ref<CorrectionStatus | ''>('')
const fDishId = ref('')

function params(): CorrectionListParams {
  return {
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

/* ==================== 处置抽屉 ==================== */
const open = ref(false)
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

const canSubmit = computed(() => {
  if (outcome.value === 'adopted') {
    if (needStallConfirm.value && !createIfMissing.value && !chosenStallId.value) return false
    return Object.values(accepted.value).some(Boolean)
  }
  return rejectReason.value.trim().length > 0
})

async function openHandle(row: CorrectionAdminVO, mode: 'adopted' | 'rejected'): Promise<void> {
  open.value = true
  detail.value = null
  detailError.value = null
  detailLoading.value = true
  outcome.value = mode
  reply.value = ''
  rejectReason.value = ''
  accepted.value = {}
  candidates.value = []
  chosenStallId.value = 0
  createIfMissing.value = false
  needStallConfirm.value = false
  try {
    const d = await getCorrection(row.id)
    detail.value = d
    // 默认全选（管理员可逐项取消）
    for (const diff of d.differences) accepted.value[diff.field] = true
  } catch (e) {
    detailError.value = e instanceof Error ? e.message : '加载详情失败'
  } finally {
    detailLoading.value = false
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
      const acceptedFields = Object.entries(accepted.value)
        .filter(([, v]) => v)
        .map(([k]) => k)
      const res = await adoptCorrection(d.id, {
        acceptedFields,
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
      ElMessage.success('已采纳')
    } else {
      await rejectCorrection(d.id, {
        reply: reply.value.trim(),
        rejectReason: rejectReason.value.trim(),
      })
      ElMessage.success('已拒绝')
    }
    open.value = false
    await reload()
  } catch (e) {
    // 已处理再处理（400）/ 不存在（4001）/ 被引用 → 后端原文
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

function reset(): void {
  fStatus.value = ''
  fDishId.value = ''
  reloadFirstPage()
}

onMounted(() => reloadFirstPage())
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>菜品纠错管理</h2></div>

    <div class="card filters">
      <select class="form-input" v-model="fStatus" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="adopted">已采纳</option>
        <option value="rejected">已拒绝</option>
      </select>
      <input class="form-input" v-model="fDishId" placeholder="菜品 ID" @keyup.enter="reloadFirstPage" />
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 分页 ⑥ 会话失效 -->
    <StateBox v-if="firstLoading" status="loading" />
    <StateBox v-else-if="sessionInvalid" status="session" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="reload" />
    <StateBox v-else-if="isEmpty" status="empty" message="暂无纠错" />
    <div v-else-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>目标菜品</th>
            <th>提交人</th>
            <th>状态</th>
            <th>处理回复 / 原因</th>
            <th>提交时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.dishName ?? '菜品已删除' }}</td>
            <!-- 匿名提交 userId = 0 → 服务端回落「游客」 -->
            <td>{{ row.userNickname || '游客' }}</td>
            <td><StatusTag :status="row.status" kind="correction" /></td>
            <td class="ellipsis">
              <span v-if="row.reply">{{ row.reply }}</span>
              <span v-else class="muted">—</span>
              <div v-if="row.rejectReason" class="muted">原因：{{ row.rejectReason }}</div>
            </td>
            <td class="muted">{{ row.createdAt }}</td>
            <td class="actions">
              <template v-if="row.status === 'pending'">
                <button class="link" type="button" @click="openHandle(row, 'adopted')">采纳</button>
                <button class="link danger" type="button" @click="openHandle(row, 'rejected')">拒绝</button>
              </template>
              <span v-else class="muted">已处理</span>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="total > 0" class="pager">
        <span class="pager-total">共 {{ total }} 条</span>
        <div class="pager-actions">
          <button class="btn-secondary" type="button" :disabled="page <= 1" @click="prevPage">上一页</button>
          <span class="pager-page">第 {{ page }} / {{ pageCount }} 页</span>
          <button class="btn-secondary" type="button" :disabled="page >= pageCount" @click="nextPage">下一页</button>
        </div>
      </div>
    </div>

    <!-- 处置抽屉：差异对照（逐项勾选）+ 结论 + 回复 -->
    <BaseDrawer title="处理纠错" :open="open" @close="open = false">
      <StateBox v-if="detailLoading" status="loading" />
      <StateBox v-else-if="detailError" status="error" :message="detailError" />
      <template v-else-if="detail">
        <div class="ctx">
          <div class="ctx-label">
            {{ detail.dishName ?? '菜品已删除' }} · {{ detail.userNickname || '游客' }}
          </div>
        </div>

        <div class="field">
          <label>处理结论</label>
          <div class="tag-options">
            <button
              class="tag-option"
              type="button"
              role="radio"
              :aria-checked="outcome === 'adopted'"
              :class="{ active: outcome === 'adopted' }"
              @click="outcome = 'adopted'"
            >
              采纳
            </button>
            <button
              class="tag-option"
              type="button"
              role="radio"
              :aria-checked="outcome === 'rejected'"
              :class="{ active: outcome === 'rejected' }"
              @click="outcome = 'rejected'"
            >
              不采纳
            </button>
          </div>
        </div>

        <!-- 采纳：逐项勾选（楼层改动会连带同档口其它菜品，单独提示） -->
        <div v-if="outcome === 'adopted'" class="field">
          <label>采纳项（逐项勾选）</label>
          <table class="table table--compact">
            <thead>
              <tr>
                <th class="pick-col"></th>
                <th>字段</th>
                <th>原值</th>
                <th>提交值</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="diff in detail.differences" :key="diff.field">
                <td class="pick-col">
                  <input type="checkbox" v-model="accepted[diff.field]" />
                </td>
                <td>
                  {{ diff.label }}
                  <div v-if="diff.affectsOthers" class="warn">将连带同档口其它菜品</div>
                </td>
                <td class="muted">{{ diff.oldValue || '—' }}</td>
                <td>{{ diff.newValue || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 档口两段式确认 -->
        <div v-if="outcome === 'adopted' && needStallConfirm" class="field stall-confirm">
          <label>档口归属确认</label>
          <select class="form-input" v-model.number="chosenStallId" :disabled="createIfMissing">
            <option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <label class="check-line">
            <input type="checkbox" v-model="createIfMissing" />
            <span>按提交的档口名新建档口后挂靠</span>
          </label>
        </div>

        <div v-if="outcome === 'rejected'" class="field">
          <label>不采纳原因（必填，≤200 字）</label>
          <input class="form-input" v-model="rejectReason" maxlength="200" />
        </div>

        <div class="field">
          <label>处理回复{{ outcome === 'rejected' ? '（必填）' : '（可选）' }}，≤600 字</label>
          <textarea
            class="form-textarea"
            v-model="reply"
            rows="3"
            maxlength="600"
            placeholder="随站内回执下发给提交人；采纳时留空则用固定文案"
          />
          <div class="hint">{{ reply.length }} / 600</div>
        </div>
      </template>

      <template #actions>
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
    </BaseDrawer>
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
  width: 160px;
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.warn {
  color: var(--color-warning);
  font-size: var(--font-xs);
}
.hint {
  margin-top: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-xs);
  text-align: right;
}
.ctx {
  background: var(--bg-soft);
  border-radius: var(--radius);
  padding: var(--space-3);
  margin-bottom: var(--space-4);
}
.ctx-label {
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.pick-col {
  width: 32px;
}
.stall-confirm {
  background: var(--bg-soft);
  border-radius: var(--radius);
  padding: var(--space-3);
}
.check-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  font-weight: var(--weight-regular);
}
.tag-options {
  display: flex;
  gap: var(--space-2);
}
</style>
