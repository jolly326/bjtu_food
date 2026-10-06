<script setup lang="ts">
/**
 * A6 首页筛选视图管理（页面规格见 [首页筛选视图.md](../../../docs/ui/web/首页筛选视图.md)）。
 *
 * <p>后台只维护 tab 的**三件事**：**改名 / 启停 / 拖拽排序**。
 * 视图的筛选条件（`conditions`）与排序口径（`sort_kind`）由 seed / 代码固定，后台**不可见不可改**；
 * 新增 / 删除 tab 亦属代码改动，本页无对应入口。
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fail } from '@/utils/error'
import { useReorder } from '@/composables/useReorder'
import { listViews, sortViews, updateView } from '@/api/views'
import type { DishViewAdminVO } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseModal from '@/components/BaseModal.vue'
import ListState from '@/components/ListState.vue'
import StatusTag from '@/components/StatusTag.vue'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<DishViewAdminVO>(() => listViews())

/* ==================== 改名（轻量弹窗，单字段） ==================== */
const renameOpen = ref(false)
const renameTarget = ref<DishViewAdminVO | null>(null)
const renameLabel = ref('')
const saving = ref(false)

function openRename(row: DishViewAdminVO): void {
  renameTarget.value = row
  renameLabel.value = row.label
  renameOpen.value = true
}

async function submitRename(): Promise<void> {
  const target = renameTarget.value
  if (!target) return
  const label = renameLabel.value.trim()
  if (!label) {
    ElMessage.warning('请填写 tab 文案')
    return
  }
  if (label.length > 32) {
    ElMessage.warning('tab 文案最多 32 字')
    return
  }
  saving.value = true
  try {
    await updateView(target.id, { label, enabled: target.enabled })
    ElMessage.success('已改名（客户端即时生效）')
    renameOpen.value = false
    await load()
  } catch (e) {
    // 文案为空 / 超长 → 后端原文
    fail(e, '改名失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 启停（`enabled` 翻转，`label` 原样回传） ==================== */
async function toggle(row: DishViewAdminVO): Promise<void> {
  const next = !row.enabled
  try {
    await updateView(row.id, { label: row.label, enabled: next })
    ElMessage.success(next ? '已启用' : '已停用')
    await load()
  } catch (e) {
    // 停用「最后一个启用的视图」→ 400 原文透出，状态不变
    fail(e)
  }
}

/* ==================== 拖拽排序（提交全量行） ==================== */
const { onDragStart, onDrop } = useReorder(items, sortViews, load)

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>首页筛选视图</h2>
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
            <th class="drag-col"></th>
            <th>tab 文案</th>
            <th>键</th>
            <th>匹配</th>
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
            <td class="drag-col" title="拖拽排序">⠿</td>
            <td>{{ row.label }}</td>
            <td>
              <code class="view-key">{{ row.key }}</code>
            </td>
            <td class="num">
              {{ row.matchedCount }}
              <span v-if="row.matchedCount === 0" class="no-match">无匹配，客户端不显示</span>
            </td>
            <td><StatusTag :status="row.enabled ? 'on' : 'off'" kind="onoff" /></td>
            <td class="muted">{{ row.updatedAt }}</td>
            <td class="actions">
              <button class="link" type="button" @click="openRename(row)">改名</button>
              <button class="link" type="button" @click="toggle(row)">
                {{ row.enabled ? '停用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="foot-note">
        下发规则：启用中且匹配数不为 0 的视图才会出现在首页筛选栏；新增 / 删除 tab
        走代码（本页无新建、无删除入口）。
      </p>
    </div>

    <!-- 改名：单字段轻量弹窗 -->
    <BaseModal title="改名" :open="renameOpen" @close="renameOpen = false">
      <div class="field">
        <label for="view-label">tab 文案</label>
        <input
          id="view-label"
          class="form-input"
          v-model="renameLabel"
          maxlength="32"
          placeholder="如 面食粉类"
        />
        <div class="hint">1~32 字；保存后客户端该 tab 文案即时更新（免发版）</div>
      </div>
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
  text-align: center;
}
.view-key {
  color: var(--text-muted);
  font-family: var(--font-numeric);
}
.num {
  font-variant-numeric: tabular-nums;
}
.no-match {
  margin-left: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-xs);
}
.muted {
  color: var(--text-muted);
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
