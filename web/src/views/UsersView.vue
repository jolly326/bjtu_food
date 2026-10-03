<script setup lang="ts">
/**
 * C2 用户管理（页面规格见 [列表页模板.md](../../../docs/web/ui/列表页模板.md) §一）。
 *
 * <p>要点：分页（`AdminPageResult`，**页码 + 共 N 条**）；筛选 = 关键词 + 状态；
 * **认证态不单独出字段** —— 按 `bindEmail` 是否为空派生（空串 = 未认证，管理端 VO 恒非空串）；
 * 状态列恒用 `StatusTag`（`kind="user"`）；禁用 / 启用为行内文字动作，确认文案含影响面。
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, setUserStatus } from '@/api/users'
import type { UserAdminVO, UserListParams, UserStatus } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import StateBox from '@/components/StateBox.vue'
import StatusTag from '@/components/StatusTag.vue'

const fKeyword = ref('')
const fStatus = ref<UserStatus | ''>('')

function params(): UserListParams {
  return {
    keyword: fKeyword.value || undefined,
    status: fStatus.value || undefined,
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
} = usePagedList<UserAdminVO>((pageNo, pageSize) =>
  listUsers({ page: pageNo, pageSize, ...params() }),
)

async function toggle(row: UserAdminVO): Promise<void> {
  const disabled = row.status !== 'disabled'
  try {
    await ElMessageBox.confirm(
      disabled
        ? `确认禁用用户「${row.nickname || row.id}」？禁用后其登录与写操作将被拒绝（已发表内容保留），且不再收到处置回执。`
        : `确认启用用户「${row.nickname || row.id}」？`,
      disabled ? '禁用用户' : '启用用户',
      { type: 'warning', confirmButtonText: disabled ? '禁用' : '启用', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await setUserStatus(row.id, { disabled })
    ElMessage.success(disabled ? '已禁用' : '已启用')
    await reload()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

function reset(): void {
  fKeyword.value = ''
  fStatus.value = ''
  reloadFirstPage()
}

onMounted(() => reloadFirstPage())
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>用户管理</h2></div>

    <div class="card filters">
      <input
        class="form-input"
        v-model="fKeyword"
        placeholder="昵称 / 邮箱"
        @keyup.enter="reloadFirstPage"
      />
      <select class="form-input" v-model="fStatus" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option value="active">正常</option>
        <option value="disabled">已禁用</option>
        <option value="deleted">已注销</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 分页 ⑥ 会话失效 -->
    <StateBox v-if="firstLoading" status="loading" />
    <StateBox v-else-if="sessionInvalid" status="session" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="reload" />
    <StateBox v-else-if="isEmpty" status="empty" message="暂无用户" />
    <div v-else-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>头像</th>
            <th>昵称</th>
            <th>校园邮箱</th>
            <th>状态</th>
            <th>评价数</th>
            <th>注册时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>
              <img v-if="row.avatar" :src="row.avatar" class="avatar" alt="" />
              <span v-else class="avatar avatar-placeholder">·</span>
            </td>
            <td>{{ row.nickname || '—' }}</td>
            <!-- 认证态派生：空串 = 未认证（不单独出认证字段） -->
            <td>{{ row.bindEmail || '未认证' }}</td>
            <td><StatusTag :status="row.status" kind="user" /></td>
            <td>{{ row.reviewCount }}</td>
            <td class="muted">{{ row.createdAt }}</td>
            <td class="actions">
              <button
                v-if="row.status !== 'deleted'"
                class="link"
                type="button"
                @click="toggle(row)"
              >
                {{ row.status === 'disabled' ? '启用' : '禁用' }}
              </button>
              <span v-else class="muted">—</span>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- ⑤ 分页：共 N 条 + 页码（total = 0 时不渲染） -->
      <div v-if="total > 0" class="pager">
        <span class="pager-total">共 {{ total }} 条</span>
        <div class="pager-actions">
          <button class="btn-secondary" type="button" :disabled="page <= 1" @click="prevPage">
            上一页
          </button>
          <span class="pager-page">第 {{ page }} / {{ pageCount }} 页</span>
          <button
            class="btn-secondary"
            type="button"
            :disabled="page >= pageCount"
            @click="nextPage"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
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
  width: 180px;
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-circle);
  object-fit: cover;
  display: block;
}
.avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-soft);
  color: var(--text-muted);
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
</style>
