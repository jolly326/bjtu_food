<script setup lang="ts">
/**
 * A6 首页筛选视图管理（页面规格见 [首页筛选视图.md](../../../docs/ui/web/首页筛选视图.md)）。
 *
 * <p>视图是**纯数据**：文案 / 筛选种类 / 启停全部落库，本页支持**新建 / 编辑 / 启用停用 / 删除**
 * （全部免发版）。筛选面只有「种类」一维 —— `categoryId = null` 即「为你推荐」（不按种类筛），
 * 无可配条件语言；取值合法性由服务端裁决（见 [api/web/views.md](../../../docs/api/web/views.md)）。
 *
 * <p>表格**只放基础列**（tab 文案 / 筛选种类 / 匹配 / 状态 / 更新时间）；
 * **全部操作**（编辑 / 启停 / 删除）都在**详情抽屉**内；列表按视图 ID 升序。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import { confirmDelete } from '@/utils/confirm'
import { canDelete, canWrite } from '@/utils/permissions'
import { createView, deleteView, listViews, updateView } from '@/api/views'
import { listCategories } from '@/api/categories'
import type { DishCategoryAdminVO, DishViewAdminVO } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import ListState from '@/components/ListState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useRowAction } from '@/composables/useRowAction'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<DishViewAdminVO>(() => listViews())

/* ==================== 筛选种类（唯一筛选配置） ==================== */

/** 「为你推荐（不筛种类）」在下拉里的取值（= 契约的 `categoryId = null`） */
const NO_CATEGORY = ''

/** 种类候选（菜品种类字典，按种类 `id` 升序） */
const categories = ref<DishCategoryAdminVO[]>([])

/** 下拉选项：固定首项「为你推荐（不筛种类）」+ 种类字典 */
const categoryOptions = computed<{ value: string; label: string }[]>(() => [
  { value: NO_CATEGORY, label: '为你推荐（不筛种类）' },
  ...categories.value.map((c) => ({ value: String(c.id), label: c.name })),
])

/** 种类 ID → 中文名（`null` = 为你推荐）；列表行与详情**同一套渲染** */
function categoryText(categoryId: number | null): string {
  if (categoryId == null) return '为你推荐'
  return categories.value.find((c) => c.id === categoryId)?.name ?? `#${categoryId}`
}

/* ==================== 抽屉两态：查看 / 新建或编辑 ==================== */
type DrawerMode = 'view' | 'edit'

const drawerOpen = ref(false)
const mode = ref<DrawerMode>('view')
const current = ref<DishViewAdminVO | null>(null)
const saving = ref(false)
/** 进入编辑态时的快照：用于「未保存修改」二次确认（BaseDrawer 的 dirty） */
const snapshot = ref('')

/** 表单态：`category` 用字符串承接下拉取值（`NO_CATEGORY` = 不筛种类） */
const form = ref<{ name: string; category: string }>({ name: '', category: NO_CATEGORY })

const drawerTitle = computed(() => {
  if (mode.value === 'view') return '视图详情'
  return current.value ? '编辑视图' : '新建视图'
})
const dirty = computed(() => mode.value === 'edit' && formSnapshot() !== snapshot.value)

function formSnapshot(): string {
  return JSON.stringify(form.value)
}

/** 行内 / 抽屉动作并发保护：提交中该行（或抽屉当前行）动作置灰（列表页模板 §1.3「并发保护」） */
const { isBusy, runRowAction } = useRowAction()

function openDetail(row: DishViewAdminVO): void {
  current.value = row
  mode.value = 'view'
  drawerOpen.value = true
}

function openCreate(): void {
  current.value = null
  form.value = { name: '', category: NO_CATEGORY }
  snapshot.value = formSnapshot()
  mode.value = 'edit'
  drawerOpen.value = true
}

function startEdit(): void {
  const row = current.value
  if (!row) return
  form.value = { name: row.name, category: row.categoryId == null ? '' : String(row.categoryId) }
  snapshot.value = formSnapshot()
  mode.value = 'edit'
}

function cancelEdit(): void {
  if (current.value) {
    mode.value = 'view'
  } else {
    drawerOpen.value = false
  }
}

/* ==================== 保存 / 启停 / 删除 ==================== */

/** 表单 → 契约入参的 `categoryId`（不筛种类下发 `null`） */
function selectedCategoryId(): number | null {
  return form.value.category === NO_CATEGORY ? null : Number(form.value.category)
}

async function save(): Promise<void> {
  const name = form.value.name.trim()
  if (!name) {
    ElMessage.warning('请填写 tab 文案')
    return
  }
  if (name.length > 32) {
    ElMessage.warning('tab 文案最多 32 字')
    return
  }
  saving.value = true
  const categoryId = selectedCategoryId()
  try {
    if (current.value) {
      // 三字段整体替换：`enabled` 原样回传（本端点不表达「启停」，启停走详情内的独立动作）
      await updateView(current.value.id, { name, categoryId, enabled: current.value.enabled })
      ElMessage.success('已保存（客户端即时生效）')
      await load()
      const refreshed = items.value.find((r) => r.id === current.value?.id)
      if (refreshed) {
        current.value = refreshed
        mode.value = 'view'
      }
    } else {
      await createView({ name, categoryId })
      ElMessage.success('已新建（默认启用）')
      await load()
      drawerOpen.value = false
    }
  } catch (e) {
    // 文案为空 / 超长、种类 ID 不存在、停用最后一个启用视图 → 后端原文
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/** 刷新后按 id 回填抽屉数据（抽屉展示的不是该行时才不动，避免串数据） */
function syncCurrent(id: number): void {
  if (current.value?.id !== id) return
  current.value = items.value.find((r) => r.id === id) ?? current.value
}

function toggle(row: DishViewAdminVO): Promise<void> {
  const next = !row.enabled
  return runRowAction({
    id: row.id,
    action: () => updateView(row.id, { name: row.name, categoryId: row.categoryId, enabled: next }),
    successMessage: next ? '已启用' : '已停用',
    refresh: load,
    syncAfterRefresh: syncCurrent,
  })
}

function remove(row: DishViewAdminVO): Promise<void> {
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(`确认删除视图「${row.name}」？该 tab 将从首页筛选栏移除。`, {
        title: '删除视图',
      })
      await deleteView(row.id)
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

onMounted(async () => {
  // 筛选种类的候选：失败不阻塞列表（详情退回 `#id` 展示）
  try {
    categories.value = await listCategories()
  } catch {
    /* 列表仍可展示 */
  }
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>首页筛选视图</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建视图</button>
    </div>

    <!-- 四态：加载 / 会话失效 / 错误 / 空 / 有数据 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无筛选视图"
      @retry="load"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>tab 文案</th>
            <th>筛选种类</th>
            <th>匹配</th>
            <th>状态</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.name }}</td>
            <td>{{ categoryText(row.categoryId) }}</td>
            <td class="num">
              {{ row.matchedCount }}
              <span v-if="row.matchedCount === 0" class="no-match">无匹配，客户端不显示</span>
            </td>
            <td><StatusTag :status="row.enabled ? 'on' : 'off'" kind="onoff" /></td>
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
        下发规则：启用中且匹配数不为 0 的视图才会出现在首页筛选栏；列表与首页筛选栏的顺序均为视图 ID
        升序。
      </p>
    </div>

    <!-- 详情 / 编辑抽屉（同一抽屉两态） -->
    <BaseDrawer :title="drawerTitle" :open="drawerOpen" :dirty="dirty" @close="drawerOpen = false">
      <!-- ① 查看态 -->
      <template v-if="mode === 'view' && canWrite() && current">
        <div class="detail-group">
          <div class="detail-group-title">视图信息</div>
          <div class="detail-meta detail-meta--grid">
            <DetailMetaRow k="视图 ID" num>#{{ current.id }}</DetailMetaRow>
            <DetailMetaRow k="tab 文案">{{ current.name }}</DetailMetaRow>
            <DetailMetaRow k="筛选种类">
              {{ categoryText(current.categoryId) }}
            </DetailMetaRow>
            <div class="meta-row">
              <span class="meta-key">状态</span>
              <span class="meta-val">
                <StatusTag :status="current.enabled ? 'on' : 'off'" kind="onoff" />
              </span>
            </div>
            <div class="meta-row">
              <span class="meta-key">匹配菜品</span>
              <span class="meta-val num">
                {{ current.matchedCount }}
                <span class="muted">（为 0 时客户端不显示该 tab）</span>
              </span>
            </div>
            <DetailMetaRow k="更新时间">{{ formatDateTime(current.updatedAt) }}</DetailMetaRow>
          </div>
        </div>
      </template>

      <!-- ② 新建 / 编辑态 -->
      <template v-else>
        <div class="field">
          <label for="view-name">tab 文案</label>
          <input
            id="view-name"
            class="form-input"
            v-model="form.name"
            maxlength="32"
            placeholder="如 面食粉类"
          />
          <div class="hint">1~32 字；保存后客户端该 tab 文案即时更新（免发版）</div>
        </div>

        <div class="field">
          <label for="view-category">筛选种类</label>
          <select id="view-category" class="form-input" v-model="form.category">
            <option v-for="o in categoryOptions" :key="o.value" :value="o.value">
              {{ o.label }}
            </option>
          </select>
        </div>
      </template>

      <template #actions>
        <template v-if="mode === 'view' && canWrite() && current">
          <button
            v-if="canWrite()"
            class="link"
            type="button"
            :disabled="isBusy(current.id)"
            @click="startEdit"
          >
            编辑
          </button>
          <button
            class="link"
            type="button"
            :disabled="isBusy(current.id)"
            @click="toggle(current)"
          >
            {{ current.enabled ? '停用' : '启用' }}
          </button>
          <button
            v-if="canDelete()"
            class="link danger"
            type="button"
            :disabled="isBusy(current.id)"
            @click="remove(current)"
          >
            删除
          </button>
        </template>
        <template v-else>
          <button class="btn-secondary" type="button" @click="cancelEdit">取消</button>
          <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </template>
      </template>
    </BaseDrawer>
  </div>
</template>

<style scoped>
.no-match {
  margin-left: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-xs);
}
</style>
