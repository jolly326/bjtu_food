<script setup lang="ts">
/**
 * C2 用户管理（页面规格见 [列表页模板.md](../../../docs/ui/web/列表页模板.md) §一 + §C2）。
 *
 * <p>列序（§C2）：`用户`（主标识列 —— 头像 + 昵称合并为一个单元格）→ `账号`（`username`）→ `认证` →
 * `状态` → `注册时间` → `更新时间` → `操作`。
 *
 * <p>要点：分页（`AdminPageResult`，**页码 + 共 N 条**）；筛选 = 关键词 + 状态；
 * **认证态不单独出字段** —— 按 `bindEmail` 是否为空派生（空串 = 未认证，管理端 VO 恒非空串）；
 * 状态列恒用 `StatusTag`（`kind="user"`）；行内文字动作 = `禁用 / 启用` → `解绑邮箱`（仅已认证行）→
 * `删除账号`（`.link.danger`，恒最后）——
 * **禁用 / 解绑 / 删除**均走二次确认（文案写明影响面），**启用**直接执行（无二次确认）；
 * 提交中该行动作置灰。
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { fail } from '@/utils/error'
import { deleteUserAccount, listUsers, setUserStatus, unbindUserEmail } from '@/api/users'
import type { UserAdminVO, UserListParams, UserStatus } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
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

/** 行内动作并发保护：提交中该行按钮 `:disabled`（列表页模板 §1.3「并发保护」） */
const busyId = ref<number | null>(null)

/** 行动作公共骨架：busyId 置灰 → 执行 → 成功提示 → `reload()`（留在当前页） */
async function runRowAction(
  row: UserAdminVO,
  action: () => Promise<null>,
  successMessage: string,
): Promise<void> {
  busyId.value = row.id
  try {
    await action()
    ElMessage.success(successMessage)
    await reload()
  } catch (e) {
    fail(e)
  } finally {
    busyId.value = null
  }
}

async function toggle(row: UserAdminVO): Promise<void> {
  const disabled = row.status !== 'disabled'
  if (disabled) {
    try {
      await confirmDelete(
        `禁用后：该用户无法再登录，已登录的会话立即失效（写操作被拒）；已发表内容保留，可随时启用恢复。确认禁用「${row.nickname || row.id}」？`,
        { title: '禁用用户', confirmText: '禁用' },
      )
    } catch {
      return
    }
  }
  await runRowAction(
    row,
    () => setUserStatus(row.id, { status: disabled ? 'disabled' : 'active' }),
    disabled ? '已禁用' : '已启用',
  )
}

async function unbindEmail(row: UserAdminVO): Promise<void> {
  try {
    await confirmDelete(
      `解绑后：该用户立即回落为未认证状态，无法发表评价与反馈，需重新邮箱认证恢复；登录与已发表内容不受影响。确认解绑「${row.nickname || row.id}」的认证邮箱 ${row.bindEmail}？`,
      { title: '解绑邮箱', confirmText: '解绑' },
    )
  } catch {
    return
  }
  await runRowAction(row, () => unbindUserEmail(row.id), '已解绑')
}

async function deleteAccount(row: UserAdminVO): Promise<void> {
  try {
    await confirmDelete(
      `注销后：账号立即失效且不可恢复——无法再登录，微信与邮箱解绑，昵称显示为「已注销用户」；已发表内容保留且照旧公开可见。确认注销「${row.nickname || row.id}」？`,
      { title: '删除账号', confirmText: '注销' },
    )
  } catch {
    return
  }
  await runRowAction(row, () => deleteUserAccount(row.id), '已注销')
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
        placeholder="昵称 / 账号 / 邮箱"
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
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无用户"
      @retry="reload"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>用户</th>
            <th>账号</th>
            <th>认证</th>
            <th>状态</th>
            <th>注册时间</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>
              <div class="user-cell">
                <img v-if="row.avatar" :src="row.avatar" class="avatar" alt="" />
                <span v-else class="avatar avatar-placeholder">·</span>
                <span>{{ row.nickname || '—' }}</span>
              </div>
            </td>
            <td class="ellipsis muted">{{ row.username }}</td>
            <!-- 认证态派生：空串 = 未认证（不单独出认证字段） -->
            <td>
              <span v-if="row.bindEmail" class="email">{{ row.bindEmail }}</span>
              <span v-else class="muted">未认证</span>
            </td>
            <td><StatusTag :status="row.status" kind="user" /></td>
            <td class="muted">{{ row.createdAt }}</td>
            <td class="muted">{{ row.updatedAt || '—' }}</td>
            <td class="actions">
              <template v-if="row.status !== 'deleted'">
                <button
                  class="link"
                  type="button"
                  :disabled="busyId === row.id"
                  @click="toggle(row)"
                >
                  {{ row.status === 'disabled' ? '启用' : '禁用' }}
                </button>
                <!-- 解绑邮箱：仅已认证行（bindEmail 非空 = 认证态唯一判据） -->
                <button
                  v-if="row.bindEmail"
                  class="link"
                  type="button"
                  :disabled="busyId === row.id"
                  @click="unbindEmail(row)"
                >
                  解绑邮箱
                </button>
                <!-- 删除账号：不可逆终态，恒最后 -->
                <button
                  class="link danger"
                  type="button"
                  :disabled="busyId === row.id"
                  @click="deleteAccount(row)"
                >
                  删除账号
                </button>
              </template>
              <span v-else class="muted">—</span>
            </td>
          </tr>
        </tbody>
      </table>

      <Pager
        :total="total"
        :page="page"
        :page-count="pageCount"
        @prev="prevPage"
        @next="nextPage"
      />
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
/* 主标识列：头像 + 昵称合并为一个单元格（列表页模板 §C2） */
.user-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-pill);
  object-fit: cover;
  display: block;
  flex: none;
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
/* 认证列：已认证邮箱（`--font-sm`，颜色随正文默认档） */
.email {
  font-size: var(--font-sm);
}
</style>
