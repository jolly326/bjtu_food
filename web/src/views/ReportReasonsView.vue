<script setup lang="ts">
/**
 * A7 举报原因管理（页面规格见 [A7-举报原因管理](../../../docs/func/web/A-主数据维护/A7-举报原因管理.md)）。
 *
 * <p>要点：维护举报弹层的「原因」单选字典（**可维护、免发版**）；数据锚在原因 ID（历史举报按它落库）
 * ⇒ 新建只填中文名、**没有「改 ID」入口**；改名免费；**删除受引用约束**（下线一律用停用）；
 * 三条不变量由服务端强制（至少 1 条启用 / 启用 ≤8 / 引用禁删），前端只负责把错误原文透出。
 *
 * <p>表格**只放基础检索列**（原因 / 引用举报 / 状态 / 更新时间，按原因 ID 升序）；
 * **全部操作**（改名 / 启停 / 删除）都在**详情抽屉**内。
 */
import { computed, onMounted, ref } from 'vue'
import { confirmDelete } from '@/utils/confirm'
import { canDelete, canWrite } from '@/utils/permissions'
import { formatDateTime } from '@/utils/datetime'
import {
  createReportReason,
  deleteReportReason,
  listReportReasons,
  renameReportReason,
  updateReportReasonStatus,
} from '@/api/reportReasons'
import type { OnOffStatus, ReportReasonAdminVO } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import BaseModal from '@/components/BaseModal.vue'
import ListState from '@/components/ListState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useRowAction } from '@/composables/useRowAction'
import { ElMessage } from 'element-plus'
import { fail } from '@/utils/error'

/** 启用上限 / 下限（服务端强制；页头计数徽标 = 该约束的自证） */
const ENABLE_MAX = 8

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<ReportReasonAdminVO>(() => listReportReasons())

const enabledCount = computed(() => items.value.filter((r) => r.status === 'on').length)

/* ==================== 新建（文案，1 控件 ⇒ 弹窗） ==================== */
const createOpen = ref(false)
const newName = ref('')
const saving = ref(false)

function openCreate(): void {
  newName.value = ''
  createOpen.value = true
}

async function submitCreate(): Promise<void> {
  if (!newName.value.trim()) {
    ElMessage.warning('请填写中文名')
    return
  }
  saving.value = true
  try {
    await createReportReason({ name: newName.value.trim() })
    ElMessage.success('已新建')
    createOpen.value = false
    await load()
  } catch (e) {
    // 名称重名 / 启用数已达上限 8 → 后端原文（400）
    fail(e, '新建失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 详情 / 改名抽屉 ==================== */
/** 抽屉两态：`view` = 详情（只读 + 操作）/ `edit` = 改名表单 */
type DrawerMode = 'view' | 'edit'

const drawerOpen = ref(false)
const mode = ref<DrawerMode>('view')
const current = ref<ReportReasonAdminVO | null>(null)
const renameName = ref('')
/** 进入编辑态时的快照：用于「未保存修改」二次确认（BaseDrawer 的 dirty） */
const snapshot = ref('')

const drawerTitle = computed(() => (mode.value === 'view' ? '举报原因详情' : '原因改名'))
const dirty = computed(() => mode.value === 'edit' && renameName.value !== snapshot.value)

function openDetail(row: ReportReasonAdminVO): void {
  current.value = row
  renameName.value = row.name
  snapshot.value = row.name
  mode.value = 'view'
  drawerOpen.value = true
}

function startRename(): void {
  renameName.value = current.value?.name ?? ''
  snapshot.value = renameName.value
  mode.value = 'edit'
}

function cancelEdit(): void {
  if (current.value) {
    renameName.value = current.value.name
    mode.value = 'view'
  }
}

async function submitRename(): Promise<void> {
  const row = current.value
  if (!row) return
  if (!renameName.value.trim()) {
    ElMessage.warning('请输入中文名')
    return
  }
  saving.value = true
  try {
    await renameReportReason(row.id, { name: renameName.value.trim() })
    ElMessage.success('已改名（历史举报的中文翻译同步生效）')
    await load()
    const refreshed = items.value.find((r) => r.id === row.id)
    if (refreshed) {
      current.value = refreshed
      renameName.value = refreshed.name
      snapshot.value = refreshed.name
      mode.value = 'view'
    } else {
      drawerOpen.value = false
    }
  } catch (e) {
    fail(e, '改名失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 启停 / 删除 ==================== */
/** 行内 / 抽屉动作并发保护：提交中该行（或抽屉当前行）动作置灰（列表页模板 §1.3「并发保护」） */
const { isBusy, runRowAction } = useRowAction()

/** 刷新后按 id 回填抽屉数据（抽屉展示的不是该行时才不动，避免串数据） */
function syncCurrent(id: number): void {
  if (current.value?.id !== id) return
  current.value = items.value.find((r) => r.id === id) ?? current.value
}

function toggle(row: ReportReasonAdminVO): Promise<void> {
  const next: OnOffStatus = row.status === 'on' ? 'off' : 'on'
  const hint =
    next === 'on'
      ? `确认启用「${row.name}」？举报弹层将再次出现该选项（启用数上限 8 条）。`
      : `确认停用「${row.name}」？停用后举报弹层不再展示该选项（历史举报仍能翻译出中文），但不能停用最后一条启用。`
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(hint, {
        title: next === 'on' ? '启用原因' : '停用原因',
        confirmText: next === 'on' ? '启用' : '停用',
      })
      await updateReportReasonStatus(row.id, next)
    },
    successMessage: next === 'on' ? '已启用' : '已停用',
    refresh: load,
    syncAfterRefresh: syncCurrent,
  })
}

function remove(row: ReportReasonAdminVO): Promise<void> {
  const hint =
    row.feedbackCount > 0
      ? `该原因已被 ${row.feedbackCount} 条举报引用，不能删除（删掉会让历史举报翻不出中文）—— 请改用「停用」。`
      : '确认删除该举报原因？删除后举报弹层不再出现该选项。'
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(hint, { title: '删除原因' })
      await deleteReportReason(row.id)
    },
    successMessage: '已删除',
    failMessage: '删除失败',
    refresh: load,
    closeDrawer: () => {
      if (current.value?.id !== row.id) return
      drawerOpen.value = false
      current.value = null
    },
  })
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>举报原因</h2>
      <span class="tag count-tag" :class="{ low: enabledCount === 1 }">
        启用 {{ enabledCount }} / {{ ENABLE_MAX }}
      </span>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建原因</button>
    </div>

    <p v-if="enabledCount === 1" class="page-note warn-note">至少保留 1 条启用</p>

    <!-- 四态：加载 / 会话失效 / 错误 / 空 / 有数据 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无举报原因"
      @retry="load"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>原因</th>
            <th>引用举报</th>
            <th>状态</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.name }}</td>
            <td class="num">{{ row.feedbackCount }}</td>
            <td><StatusTag :status="row.status" kind="onoff" /></td>
            <td class="muted">{{ formatDateTime(row.updatedAt) }}</td>
            <td class="actions">
              <button
                class="link"
                type="button"
                :disabled="isBusy(row.id)"
                @click="openDetail(row)"
              >
                详情
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="foot-note">
        原因 ID 是历史举报的数据锚点（由后端生成，不可修改）；下线一律用「停用」；列表按原因 ID
        升序。
      </p>
    </div>

    <!-- 详情 / 改名抽屉（同一抽屉两态） -->
    <BaseDrawer :title="drawerTitle" :open="drawerOpen" :dirty="dirty" @close="drawerOpen = false">
      <!-- ① 查看态 -->
      <template v-if="mode === 'view' && canWrite()">
        <!-- 分组：投放信息（名称 / 状态并列）→ 引用与标识 -->
        <div class="detail-group">
          <div class="detail-group-title">投放信息</div>
          <div class="detail-meta detail-meta--grid">
            <DetailMetaRow k="原因名称">{{ current?.name }}</DetailMetaRow>
            <div class="meta-row">
              <span class="meta-key">状态</span>
              <span class="meta-val">
                <StatusTag :status="current?.status ?? 'off'" kind="onoff" />
              </span>
            </div>
            <div class="meta-row">
              <span class="meta-key">引用举报</span>
              <span class="meta-val num">
                {{ current?.feedbackCount }}
                <span class="muted">（被引用时不可删除，请改用「停用」）</span>
              </span>
            </div>
          </div>
        </div>

        <div class="detail-group">
          <div class="detail-group-title">标识与时间</div>
          <div class="detail-meta detail-meta--grid">
            <DetailMetaRow k="原因 ID" num>#{{ current?.id }}</DetailMetaRow>
            <DetailMetaRow k="更新时间">{{ formatDateTime(current?.updatedAt) }}</DetailMetaRow>
          </div>
        </div>
      </template>

      <!-- ② 改名态（只改 `name`） -->
      <template v-else>
        <div class="field">
          <label for="rr-rename-name">原因名称</label>
          <input
            id="rr-rename-name"
            class="form-input"
            v-model="renameName"
            maxlength="32"
            @keyup.enter="submitRename"
          />
        </div>
        <p class="hint">改名免费：历史举报的「原因」会同步显示新文案（数据锚在原因 ID）。</p>
      </template>

      <template #actions>
        <template v-if="mode === 'view' && canWrite()">
          <button class="link" type="button" :disabled="isBusy(current?.id)" @click="startRename">
            改名
          </button>
          <button
            class="link"
            type="button"
            :disabled="isBusy(current?.id)"
            @click="current && toggle(current)"
          >
            {{ current?.status === 'on' ? '停用' : '启用' }}
          </button>
          <button
            v-if="canDelete()"
            class="link danger"
            type="button"
            :disabled="isBusy(current?.id)"
            @click="current && remove(current)"
          >
            删除
          </button>
        </template>
        <template v-else>
          <button class="btn-secondary" type="button" @click="cancelEdit">取消</button>
          <button
            class="btn-primary"
            type="button"
            :disabled="saving"
            v-press
            @click="submitRename"
          >
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </template>
      </template>
    </BaseDrawer>

    <!-- 新建：中文名（1 控件） -->
    <BaseModal title="新建举报原因" :open="createOpen" @close="createOpen = false">
      <div class="field">
        <label for="rr-new-name">原因名称</label>
        <input
          id="rr-new-name"
          class="form-input"
          v-model="newName"
          maxlength="32"
          placeholder="如 垃圾广告 / 营销刷屏"
        />
      </div>
      <template #actions>
        <button class="btn-secondary" type="button" @click="createOpen = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="submitCreate">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.page-note {
  margin: 0 0 var(--space-4);
  font-size: var(--font-sm);
}
.warn-note {
  color: var(--color-warning);
}
/* 仅剩 1 条启用：计数徽标转警示色（基线 §1.8「不新增第四种处置色」，此处复用警示档） */
.count-tag.low {
  color: var(--color-warning);
}
</style>
