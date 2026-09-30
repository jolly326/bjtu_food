<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listCorrections, adoptCorrection, rejectCorrection } from '@/api/corrections'
import type { CorrectionAdminVO, CorrectionListParams, CorrectionStatus } from '@/types/common'
import { formatYuan } from '@/utils/money'
import { usePagedList } from '@/composables/usePagedList'
import StatusTag from '@/components/StatusTag.vue'
import BaseModal from '@/components/BaseModal.vue'
import StateBox from '@/components/StateBox.vue'

const fStatus = ref<CorrectionStatus | ''>('')
const fDishId = ref('')

function params(): CorrectionListParams {
  return {
    status: fStatus.value || undefined,
    dishId: fDishId.value ? Number(fDishId.value) : undefined,
  }
}

const { items, loading, finished, error, load } = usePagedList<CorrectionAdminVO>(
  (page, pageSize) => listCorrections({ page, pageSize, ...params() }),
  20,
)

function changes(r: CorrectionAdminVO): { label: string; value: string }[] {
  const out: { label: string; value: string }[] = []
  if (r.name != null) out.push({ label: '名称', value: r.name })
  if (r.price != null) out.push({ label: '现价', value: formatYuan(r.price) })
  if (r.canteenName != null) out.push({ label: '食堂', value: r.canteenName })
  if (r.stallName != null) out.push({ label: '档口', value: r.stallName })
  if (r.attributes) {
    const parts = Object.entries(r.attributes).map(
      ([k, v]) => `${k}: ${Array.isArray(v) ? v.join('/') : v}`,
    )
    if (parts.length) out.push({ label: '属性', value: parts.join('；') })
  }
  return out
}

// 采纳（两段式）
const adoptOpen = ref(false)
const adoptCurrent = ref<CorrectionAdminVO | null>(null)
const candidates = ref<{ id: number; name: string }[]>([])
const chosenStallId = ref(0)
const createIfMissing = ref(false)
const adopting = ref(false)

async function adopt(r: CorrectionAdminVO): Promise<void> {
  try {
    const res = await adoptCorrection(r.id)
    if (res == null) {
      ElMessage.success('已采纳')
      load(true)
      return
    }
    adoptCurrent.value = r
    candidates.value = res.candidates
    chosenStallId.value = res.candidates[0]?.id ?? 0
    createIfMissing.value = false
    adoptOpen.value = true
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '采纳失败')
  }
}
async function confirmAdopt(): Promise<void> {
  if (!adoptCurrent.value) return
  adopting.value = true
  try {
    const req = createIfMissing.value
      ? { createIfMissing: true }
      : { stallId: chosenStallId.value }
    const res = await adoptCorrection(adoptCurrent.value.id, req)
    if (res != null) {
      ElMessage.warning('仍需确认档口')
      candidates.value = res.candidates
      return
    }
    ElMessage.success('已采纳')
    adoptOpen.value = false
    load(true)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '采纳失败')
  } finally {
    adopting.value = false
  }
}

// 拒绝
const rejectOpen = ref(false)
const rejectCurrent = ref<CorrectionAdminVO | null>(null)
const reply = ref('')
const rejectReason = ref('')
const submitting = ref(false)

function openReject(r: CorrectionAdminVO): void {
  rejectCurrent.value = r
  reply.value = ''
  rejectReason.value = ''
  rejectOpen.value = true
}
async function submitReject(): Promise<void> {
  if (!rejectCurrent.value) return
  if (!reply.value.trim()) {
    ElMessage.warning('请填写回复')
    return
  }
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写不采纳原因')
    return
  }
  submitting.value = true
  try {
    await rejectCorrection(rejectCurrent.value.id, {
      reply: reply.value,
      rejectReason: rejectReason.value,
    })
    ElMessage.success('已拒绝')
    rejectOpen.value = false
    load(true)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '拒绝失败')
  } finally {
    submitting.value = false
  }
}
function search(): void {
  load(true)
}
function reset(): void {
  fStatus.value = ''
  fDishId.value = ''
  load(true)
}

onMounted(() => load(true))
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>菜品纠错管理</h2></div>

    <div class="card filters">
      <select class="form-input" v-model="fStatus" @change="search">
        <option value="">全部状态</option>
        <option value="pending">待处理</option>
        <option value="adopted">已采纳</option>
        <option value="rejected">已拒绝</option>
      </select>
      <input class="form-input" v-model="fDishId" placeholder="菜品 ID" @keyup.enter="search" />
      <button class="btn-primary" type="button" v-press @click="search">查询</button>
      <button class="btn-ghost" type="button" @click="reset">重置</button>
    </div>

    <StateBox v-if="loading && !items.length" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="search" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>目标菜品</th>
            <th>提交人</th>
            <th>改动项</th>
            <th>配图</th>
            <th>状态</th>
            <th>回复 / 原因</th>
            <th>时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in items" :key="r.id">
            <td>{{ r.dishName ?? '菜品已删除' }}</td>
            <td>{{ r.userNickname ?? '游客' }}</td>
            <td>
              <div v-for="c in changes(r)" :key="c.label" class="change">
                <span class="change-label">{{ c.label }}</span>{{ c.value }}
              </div>
              <span v-if="!changes(r).length" class="muted">—</span>
            </td>
            <td>
              <div class="thumbs">
                <img v-for="(img, i) in r.images" :key="i" :src="img" alt="" />
              </div>
            </td>
            <td><StatusTag :status="r.status" kind="correction" /></td>
            <td class="ellipsis">
              <span v-if="r.reply">{{ r.reply }}</span>
              <span v-else class="muted">—</span>
              <div v-if="r.rejectReason" class="muted">原因：{{ r.rejectReason }}</div>
            </td>
            <td class="muted">{{ r.createdAt }}</td>
            <td class="actions">
              <template v-if="r.status === 'pending'">
                <button class="link" type="button" @click="adopt(r)">采纳</button>
                <button class="link danger" type="button" @click="openReject(r)">拒绝</button>
              </template>
              <span v-else class="muted">已处理</span>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="8"><StateBox status="empty" /></td>
          </tr>
        </tbody>
      </table>
      <div class="pager">
        <span v-if="loading" class="muted">加载中…</span>
        <button v-else-if="!finished" class="btn-ghost" type="button" @click="load()">加载更多</button>
        <span v-else-if="items.length" class="muted">已全部加载</span>
      </div>
    </div>

    <BaseModal title="确认归属档口" :open="adoptOpen" @close="adoptOpen = false">
      <p class="hint">
        提交的档口名「{{ adoptCurrent?.stallName || '（未提供）' }}」未匹配到现有档口，请选择归属：
      </p>
      <div class="field">
        <label>选择既有档口</label>
        <select class="form-input" v-model.number="chosenStallId" :disabled="createIfMissing">
          <option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </div>
      <div class="field">
        <label class="check">
          <input type="checkbox" v-model="createIfMissing" />
          按提交的档口名新建档口后挂靠
        </label>
      </div>
      <template #actions>
        <button class="btn-ghost" type="button" @click="adoptOpen = false">取消</button>
        <button class="btn-primary" type="button" :disabled="adopting" v-press @click="confirmAdopt">
          {{ adopting ? '提交中…' : '确认采纳' }}
        </button>
      </template>
    </BaseModal>

    <BaseModal title="拒绝纠错" :open="rejectOpen" @close="rejectOpen = false">
      <div class="field">
        <label>回复（1~1000 字，必填）</label>
        <textarea class="form-textarea" v-model="reply" rows="2" />
      </div>
      <div class="field">
        <label>不采纳原因（1~200 字，必填）</label>
        <input class="form-input" v-model="rejectReason" />
      </div>
      <template #actions>
        <button class="btn-ghost" type="button" @click="rejectOpen = false">取消</button>
        <button class="btn-primary" type="button" :disabled="submitting" v-press @click="submitReject">
          {{ submitting ? '提交中…' : '提交' }}
        </button>
      </template>
    </BaseModal>
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
.change {
  font-size: var(--font-sm);
}
.change-label {
  color: var(--text-muted);
  margin-right: var(--space-1);
}
.thumbs {
  display: flex;
  gap: var(--space-1);
}
.thumbs img {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  object-fit: cover;
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.pager {
  padding: var(--space-4);
  text-align: center;
}
.hint {
  color: var(--text-muted);
  margin: 0 0 var(--space-3);
}
.check {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
</style>
