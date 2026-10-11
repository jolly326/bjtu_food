<script setup lang="ts">
/**
 * 工单字典管理页体（举报原因 / 反馈类型**同族共用**）。
 *
 * <p>两族字典的表结构、不变量与出参形状完全一致（`OptionAdminVO`），差异只在
 * 「被哪张工单表引用」与页面文案 —— 故一个组件两处复用，按 `kind` 切换端点与文案；
 * 文案差异收敛在下方 `CONFIG`，不向调用方再暴露参数（避免过度抽象）。
 *
 * <p>要点：字典**可维护、免发版**；数据锚在 ID（历史工单按它落库）⇒ 新建只填中文名、
 * **没有「改 ID」入口**；改名免费；**删除受引用约束**（下线一律用停用）；
 * 三条不变量由服务端强制（至少 1 条启用 / 启用 ≤8 / 引用禁删），前端只负责把错误原文透出。
 *
 * <p>表格**只放基础检索列**（名称 / 引用工单 / 状态 / 更新时间，按 ID 升序）；
 * **全部操作**（改名 / 启停 / 删除）都在**详情抽屉**内。
 */
import { computed, onMounted, ref } from 'vue'
import { confirmDelete } from '@/utils/confirm'
import { canDelete, canWrite } from '@/utils/permissions'
import { formatDateTime } from '@/utils/datetime'
import {
  createFeedbackOption,
  deleteFeedbackOption,
  listFeedbackOptions,
  renameFeedbackOption,
  updateFeedbackOptionStatus,
} from '@/api/feedbackOptions'
import {
  createReportOption,
  deleteReportOption,
  listReportOptions,
  renameReportOption,
  updateReportOptionStatus,
} from '@/api/reportOptions'
import type { OnOffStatus, OptionAdminVO } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import BaseModal from '@/components/BaseModal.vue'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import ListState from '@/components/ListState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useRowAction } from '@/composables/useRowAction'
import { ElMessage } from 'element-plus'
import { fail } from '@/utils/error'

const props = defineProps<{
  /** 字典族：`report` = 举报原因（`/admin/report-options`）｜`feedback` = 反馈类型（`/admin/feedback-options`） */
  kind: 'report' | 'feedback'
}>()

/** 启用上限 / 下限（服务端强制；页头计数徽标 = 该约束的自证） */
const ENABLE_MAX = 8

/** 两族字典的文案差异（结构同形，只有措辞不同） */
const CONFIG = {
  report: {
    title: '举报原因',
    entity: '原因',
    usageLabel: '引用举报',
    entryName: '举报弹层',
    createPlaceholder: '如 垃圾广告 / 营销刷屏',
    emptyMessage: '暂无举报原因',
    footnote: '原因 ID 是历史举报的数据锚点（由后端生成，不可修改）；下线一律用「停用」；列表按原因 ID 升序。',
  },
  feedback: {
    title: '反馈类型',
    entity: '类型',
    usageLabel: '引用反馈',
    entryName: '反馈页',
    createPlaceholder: '如 程序功能Bug',
    emptyMessage: '暂无反馈类型',
    footnote: '类型 ID 是历史反馈的数据锚点（由后端生成，不可修改）；下线一律用「停用」；列表按类型 ID 升序。',
  },
} as const

const copy = computed(() => CONFIG[props.kind])

interface DictApi {
  load: () => Promise<OptionAdminVO[]>
  create: (name: string) => Promise<OptionAdminVO>
  rename: (id: number, name: string) => Promise<null>
  updateStatus: (id: number, status: OnOffStatus) => Promise<null>
  remove: (id: number) => Promise<null>
}

const api = computed<DictApi>(() =>
  props.kind === 'report'
    ? {
        load: listReportOptions,
        create: (name) => createReportOption({ name }),
        rename: (id, name) => renameReportOption(id, { name }),
        updateStatus: updateReportOptionStatus,
        remove: deleteReportOption,
      }
    : {
        load: listFeedbackOptions,
        create: (name) => createFeedbackOption({ name }),
        rename: (id, name) => renameFeedbackOption(id, { name }),
        updateStatus: updateFeedbackOptionStatus,
        remove: deleteFeedbackOption,
      },
)

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<OptionAdminVO>(() => api.value.load())

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
    await api.value.create(newName.value.trim())
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
const current = ref<OptionAdminVO | null>(null)
const renameName = ref('')
/** 进入编辑态时的快照：用于「未保存修改」二次确认（BaseDrawer 的 dirty） */
const snapshot = ref('')

const drawerTitle = computed(() =>
  mode.value === 'view' ? `${copy.value.entity}详情` : `${copy.value.entity}改名`,
)
const dirty = computed(() => mode.value === 'edit' && renameName.value !== snapshot.value)

function openDetail(row: OptionAdminVO): void {
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
    await api.value.rename(row.id, renameName.value.trim())
    ElMessage.success('已改名（历史工单的中文翻译同步生效）')
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

function toggle(row: OptionAdminVO): Promise<void> {
  const next: OnOffStatus = row.status === 'on' ? 'off' : 'on'
  const hint =
    next === 'on'
      ? `确认启用「${row.name}」？${copy.value.entryName}将再次出现该选项（启用数上限 8 条）。`
      : `确认停用「${row.name}」？停用后${copy.value.entryName}不再展示该选项（历史工单仍能翻译出中文），但不能停用最后一条启用。`
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(hint, {
        title: next === 'on' ? `启用${copy.value.entity}` : `停用${copy.value.entity}`,
        confirmText: next === 'on' ? '启用' : '停用',
      })
      await api.value.updateStatus(row.id, next)
    },
    successMessage: next === 'on' ? '已启用' : '已停用',
    refresh: load,
    syncAfterRefresh: syncCurrent,
  })
}

function remove(row: OptionAdminVO): Promise<void> {
  const hint =
    row.usageCount > 0
      ? `该${copy.value.entity}已被 ${row.usageCount} 条工单引用，不能删除（删掉会让历史工单翻不出中文）—— 请改用「停用」。`
      : `确认删除该${copy.value.entity}？删除后${copy.value.entryName}不再出现该选项。`
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(hint, { title: `删除${copy.value.entity}` })
      await api.value.remove(row.id)
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
      <h2>{{ copy.title }}</h2>
      <span class="tag count-tag" :class="{ low: enabledCount === 1 }">
        启用 {{ enabledCount }} / {{ ENABLE_MAX }}
      </span>
      <button class="btn-primary" type="button" v-press @click="openCreate">
        新建{{ copy.entity }}
      </button>
    </div>

    <p v-if="enabledCount === 1" class="page-note warn-note">至少保留 1 条启用</p>

    <!-- 四态：加载 / 会话失效 / 错误 / 空 / 有数据 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      :empty-message="copy.emptyMessage"
      @retry="load"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>{{ copy.entity }}</th>
            <th>{{ copy.usageLabel }}</th>
            <th>状态</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.name }}</td>
            <td class="num">{{ row.usageCount }}</td>
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
      <p class="foot-note">{{ copy.footnote }}</p>
    </div>

    <!-- 详情 / 改名抽屉（同一抽屉两态） -->
    <BaseDrawer :title="drawerTitle" :open="drawerOpen" :dirty="dirty" @close="drawerOpen = false">
      <!-- ① 查看态 -->
      <template v-if="mode === 'view' && canWrite()">
        <!-- 分组：投放信息（名称 / 状态并列）→ 引用与标识 -->
        <div class="detail-group">
          <div class="detail-group-title">投放信息</div>
          <div class="detail-meta detail-meta--grid">
            <DetailMetaRow k="名称">{{ current?.name }}</DetailMetaRow>
            <div class="meta-row">
              <span class="meta-key">状态</span>
              <span class="meta-val">
                <StatusTag :status="current?.status ?? 'off'" kind="onoff" />
              </span>
            </div>
            <div class="meta-row">
              <span class="meta-key">{{ copy.usageLabel }}</span>
              <span class="meta-val num">
                {{ current?.usageCount }}
                <span class="muted">（被引用时不可删除，请改用「停用」）</span>
              </span>
            </div>
          </div>
        </div>

        <div class="detail-group">
          <div class="detail-group-title">标识与时间</div>
          <div class="detail-meta detail-meta--grid">
            <DetailMetaRow :k="`${copy.entity} ID`" num>#{{ current?.id }}</DetailMetaRow>
            <DetailMetaRow k="更新时间">{{ formatDateTime(current?.updatedAt) }}</DetailMetaRow>
          </div>
        </div>
      </template>

      <!-- ② 改名态（只改 `name`） -->
      <template v-else>
        <div class="field">
          <label :for="`${kind}-rename-name`">{{ copy.entity }}名称</label>
          <input
            :id="`${kind}-rename-name`"
            v-model="renameName"
            class="form-input"
            maxlength="32"
            @keyup.enter="submitRename"
          />
        </div>
        <p class="hint">改名免费：历史工单的「{{ copy.entity }}」会同步显示新文案（数据锚在 ID）。</p>
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
    <BaseModal :title="`新建${copy.entity}`" :open="createOpen" @close="createOpen = false">
      <div class="field">
        <label :for="`${kind}-new-name`">{{ copy.entity }}名称</label>
        <input
          :id="`${kind}-new-name`"
          v-model="newName"
          class="form-input"
          maxlength="32"
          :placeholder="copy.createPlaceholder"
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
