<script setup lang="ts">
/**
 * A7 举报原因管理（页面规格见 [A7-举报原因管理](../../../docs/web/A-主数据维护/A7-举报原因管理.md)）。
 *
 * <p>要点：维护举报弹层的「原因」单选字典（**可维护、免发版**）；数据锚在 `value`（历史举报按它落库）
 * ⇒ **没有「改机器值」入口**（要改就停用旧值、新建一个）；改名免费；**删除受引用约束**（下线一律用停用）；
 * 三条不变量由服务端强制（至少 1 条启用 / 启用 ≤8 / 引用禁删），前端只负责把错误原文透出。
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createReportReason,
  deleteReportReason,
  listReportReasons,
  renameReportReason,
  sortReportReasons,
  updateReportReasonStatus,
} from '@/api/reportReasons'
import type { ReportReasonAdminVO, OnOffStatus } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseModal from '@/components/BaseModal.vue'
import StateBox from '@/components/StateBox.vue'
import StatusTag from '@/components/StatusTag.vue'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<ReportReasonAdminVO>(() => listReportReasons())

/* ==================== 新建（机器值 + 文案，2 控件 ⇒ 弹窗） ==================== */
const createOpen = ref(false)
const newValue = ref('')
const newLabel = ref('')
const saving = ref(false)

function openCreate(): void {
  newValue.value = ''
  newLabel.value = ''
  createOpen.value = true
}

async function submitCreate(): Promise<void> {
  if (!newValue.value.trim() || !newLabel.value.trim()) {
    ElMessage.warning('请填写机器值与中文标签')
    return
  }
  saving.value = true
  try {
    await createReportReason({ value: newValue.value.trim(), label: newLabel.value.trim() })
    ElMessage.success('已新建')
    createOpen.value = false
    await load()
  } catch (e) {
    // 机器值格式非法 / 重名 / 启用数已达上限 8 → 后端原文（400）
    ElMessage.error(e instanceof Error ? e.message : '新建失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 改名（只改 label，1 控件 ⇒ 弹窗） ==================== */
const renameOpen = ref(false)
const renameTarget = ref<ReportReasonAdminVO | null>(null)
const renameLabel = ref('')

function openRename(row: ReportReasonAdminVO): void {
  renameTarget.value = row
  renameLabel.value = row.label
  renameOpen.value = true
}

async function submitRename(): Promise<void> {
  const row = renameTarget.value
  if (!row) return
  if (!renameLabel.value.trim()) {
    ElMessage.warning('请输入中文标签')
    return
  }
  saving.value = true
  try {
    await renameReportReason(row.id, { label: renameLabel.value.trim() })
    ElMessage.success('已改名（历史举报的中文翻译同步生效）')
    renameOpen.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '改名失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 启停 / 删除 ==================== */
async function toggle(row: ReportReasonAdminVO): Promise<void> {
  const next: OnOffStatus = row.status === 'on' ? 'off' : 'on'
  const hint =
    next === 'on'
      ? `确认启用「${row.label}」？举报弹层将再次出现该选项（启用数上限 8 条）。`
      : `确认停用「${row.label}」？停用后举报弹层不再展示该选项（历史举报仍能翻译出中文），但**不能停用最后一条启用**。`
  try {
    await ElMessageBox.confirm(hint, next === 'on' ? '启用原因' : '停用原因', {
      type: 'warning',
      confirmButtonText: next === 'on' ? '启用' : '停用',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await updateReportReasonStatus(row.id, next)
    ElMessage.success(next === 'on' ? '已启用' : '已停用')
    await load()
  } catch (e) {
    // 停用最后一条启用 / 启用数超上限 → 后端原文
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function remove(row: ReportReasonAdminVO): Promise<void> {
  const hint =
    row.feedbackCount > 0
      ? `该原因已被 ${row.feedbackCount} 条举报引用，**不能删除**（删掉会让历史举报翻不出中文）—— 请改用「停用」。`
      : '确认删除该举报原因？删除后举报弹层不再出现该选项。'
  try {
    await ElMessageBox.confirm(hint, '删除原因', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteReportReason(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

/* ==================== 拖拽排序（提交全量行） ==================== */
const dragIndex = ref<number | null>(null)

function onDragStart(index: number): void {
  dragIndex.value = index
}

async function onDrop(index: number): Promise<void> {
  const from = dragIndex.value
  dragIndex.value = null
  if (from === null || from === index) return
  const next = [...items.value]
  const [moved] = next.splice(from, 1)
  if (!moved) return
  next.splice(index, 0, moved)
  items.value = next
  try {
    await sortReportReasons({ items: next.map((r, i) => ({ id: r.id, order: i + 1 })) })
    ElMessage.success('顺序已保存')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '排序保存失败')
    await load()
  }
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>举报原因</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建原因</button>
    </div>

    <!-- 四态：加载 / 会话失效 / 错误 / 空 / 有数据 -->
    <StateBox v-if="firstLoading" status="loading" />
    <StateBox v-else-if="sessionInvalid" status="session" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="load" />
    <StateBox v-else-if="isEmpty" status="empty" message="暂无举报原因" />
    <div v-else-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th class="drag-col"></th>
            <th>原因</th>
            <th>机器值</th>
            <th>引用举报</th>
            <th>状态</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(row, index) in items"
            :key="row.id"
            draggable="true"
            @dragstart="onDragStart(index)"
            @dragover.prevent
            @drop="onDrop(index)"
          >
            <td class="drag-col" title="拖拽排序">⋮⋮</td>
            <td>{{ row.label }}</td>
            <td><code>{{ row.value }}</code></td>
            <td class="num">{{ row.feedbackCount }}</td>
            <td><StatusTag :status="row.status" kind="onoff" /></td>
            <td class="muted">{{ row.updatedAt }}</td>
            <td class="actions">
              <button class="link" type="button" @click="openRename(row)">改名</button>
              <button class="link" type="button" @click="toggle(row)">
                {{ row.status === 'on' ? '停用' : '启用' }}
              </button>
              <button class="link danger" type="button" @click="remove(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="foot-note">
        机器值是历史举报的数据锚点，**在用后不可修改**（要改就停用旧值、新建一个）；下线一律用「停用」。
      </p>
    </div>

    <!-- 新建：机器值 + 中文标签（2 控件） -->
    <BaseModal title="新建举报原因" :open="createOpen" @close="createOpen = false">
      <div class="field">
        <label>机器值</label>
        <input class="form-input" v-model="newValue" placeholder="小写字母 / 数字 / -（如 spam）" />
        <div class="hint">全站唯一，**在用后不可修改**</div>
      </div>
      <div class="field">
        <label>中文标签</label>
        <input class="form-input" v-model="newLabel" placeholder="如 垃圾广告 / 营销刷屏" />
      </div>
      <template #actions>
        <button class="btn-secondary" type="button" @click="createOpen = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="submitCreate">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseModal>

    <!-- 改名：只改 label（1 控件） -->
    <BaseModal title="原因改名" :open="renameOpen" @close="renameOpen = false">
      <div class="field">
        <label>中文标签</label>
        <input class="form-input" v-model="renameLabel" @keyup.enter="submitRename" />
      </div>
      <p class="hint">改名免费：历史举报的「原因」会同步显示新文案（数据锚在机器值）。</p>
      <template #actions>
        <button class="btn-secondary" type="button" @click="renameOpen = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="submitRename">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.drag-col {
  width: 28px;
  color: var(--text-muted);
  cursor: grab;
  user-select: none;
  text-align: center;
}
.num {
  font-variant-numeric: tabular-nums;
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.hint {
  margin-top: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-xs);
}
.foot-note {
  margin: 0;
  padding: var(--space-3) var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-xs);
  border-top: 1px solid var(--table-border);
}
</style>
