<script setup lang="ts">
/**
 * C2 用户管理（页面规格见 [列表页模板.md](../../../docs/ui/web/列表页模板.md) §一 + §C2）。
 *
 * <p>列序（§C2）：`用户`（主标识列 —— 头像 + 昵称合并为一个单元格）→ `账号`（`username`）→ `认证` →
 * `状态` → `注册时间` → `更新时间` → `操作`。
 *
 * <p>要点：分页（`AdminPageResult`，**页码 + 共 N 条**）；筛选 = 关键词 + 状态；
 * **认证态不单独出字段** —— 按 `bindEmail` 是否为空派生（空串 = 未认证，管理端 VO 恒非空串）；
 * 状态列恒用 `StatusTag`（`kind="user"`）。
 *
 * <p>**启停保留为行级快速动作**（高频、可逆）；「解绑邮箱」「删除账号」属编辑 / 删除类 ⇒ 一律进
 * **详情抽屉**（🔴 管理端**不代改**昵称 / 头像等资料，故详情只读、无「编辑」）。
 */
import ActiveFilters from '@/components/ActiveFilters.vue'
import { computed, onMounted, ref } from 'vue'
import { confirmDelete } from '@/utils/confirm'
import { canDelete, canWrite } from '@/utils/permissions'
import { formatDateTime } from '@/utils/datetime'
import { deleteUserAccount, listUsers, setUserStatus, unbindUserEmail } from '@/api/users'
import type { UserAdminVO, UserListParams, UserStatus } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import { useRowAction } from '@/composables/useRowAction'

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

/* ===== 详情抽屉（只读 + 处置动作） ===== */
const detailOpen = ref(false)
const current = ref<UserAdminVO | null>(null)

/** 行内动作并发保护：提交中该行按钮 :disabled（列表页模板 §1.3「并发保护」） */
const { isBusy, runRowAction: runAction } = useRowAction()

function openDetail(row: UserAdminVO): void {
  current.value = row
  detailOpen.value = true
}

/** 处置成功后按 id 回填最新行（列表已 reload），避免抽屉停在旧快照上 */
function syncCurrent(id: number): void {
  if (current.value?.id !== id) return
  current.value = items.value.find((r) => r.id === id) ?? current.value
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
  await runAction({
    id: row.id,
    action: () => setUserStatus(row.id, { status: disabled ? 'disabled' : 'active' }),
    successMessage: disabled ? '已禁用' : '已启用',
    refresh: reload,
    syncAfterRefresh: syncCurrent,
  })
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
  await runAction({
    id: row.id,
    action: () => unbindUserEmail(row.id),
    successMessage: '已解绑',
    refresh: reload,
    syncAfterRefresh: syncCurrent,
  })
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
  await runAction({
    id: row.id,
    action: () => deleteUserAccount(row.id),
    successMessage: '已注销',
    refresh: reload,
    syncAfterRefresh: syncCurrent,
  })
}

/** 详情抽屉底部动作：复用行内的启停 / 解绑 / 注销（确认文案与成功提示同源、逐字一致） */
function detailToggle(): void {
  const row = current.value
  if (row) void toggle(row)
}

function detailUnbind(): void {
  const row = current.value
  if (row) void unbindEmail(row)
}

function detailDelete(): void {
  const row = current.value
  if (row) void deleteAccount(row)
}

const isDeleted = computed(() => current.value?.status === 'deleted')

function reset(): void {
  fKeyword.value = ''
  fStatus.value = ''
  reloadFirstPage()
}

/** 已生效筛选条件（供 `ActiveFilters` 回显；空值不进列表） */
const activeFilters = computed(() => {
  const out: { key: string; label: string; value: string }[] = []
  if (fKeyword.value.trim())
    out.push({ key: 'keyword', label: '关键词', value: fKeyword.value.trim() })
  if (fStatus.value)
    out.push({
      key: 'status',
      label: '状态',
      value:
        fStatus.value === 'active' ? '正常' : fStatus.value === 'disabled' ? '已禁用' : '已注销',
    })
  return out
})

/** 清除单个筛选条件并重查 */
function clearFilter(key: string): void {
  if (key === 'keyword') fKeyword.value = ''
  if (key === 'status') fStatus.value = ''
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
      <!-- 已生效筛选条件回显 -->
      <ActiveFilters :items="activeFilters" @remove="clearFilter" @clear="reset" />
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
                <img v-if="row.avatar" :src="resolveImageUrl(row.avatar)" class="avatar" alt="" />
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
            <td class="muted">{{ formatDateTime(row.createdAt) }}</td>
            <td class="muted">{{ formatDateTime(row.updatedAt) }}</td>
            <td class="actions">
              <template v-if="row.status !== 'deleted'">
                <!-- 启停：行级快速动作（高频、可逆，见 §C2 差异节） -->
                <button
                  v-if="canWrite()"
                  class="link"
                  type="button"
                  :disabled="isBusy(row.id)"
                  @click="toggle(row)"
                >
                  {{ row.status === 'disabled' ? '启用' : '禁用' }}
                </button>
                <button
                  class="link"
                  type="button"
                  :disabled="isBusy(row.id)"
                  @click="openDetail(row)"
                >
                  详情
                </button>
              </template>
              <button v-else class="link" type="button" @click="openDetail(row)">详情</button>
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

    <!-- 详情抽屉：只读资料 + 解绑邮箱 / 注销账号（编辑 / 删除类动作一律在此） -->
    <BaseDrawer title="用户详情" :open="detailOpen" @close="detailOpen = false">
      <div class="field">
        <label id="user-brief-label">身份</label>
        <div class="user-cell" role="group" aria-labelledby="user-brief-label">
          <img
            v-if="current?.avatar"
            :src="resolveImageUrl(current.avatar)"
            class="avatar"
            alt=""
          />
          <span v-else class="avatar avatar-placeholder" aria-hidden="true">·</span>
          <span>{{ current?.nickname || '—' }}</span>
        </div>
      </div>

      <!-- 分组：账号标识 → 绑定与认证 → 时间 -->
      <div class="detail-group">
        <div class="detail-group-title">账号标识</div>
        <div class="detail-meta detail-meta--grid">
          <DetailMetaRow k="账号">{{ current?.username }}</DetailMetaRow>
          <DetailMetaRow k="用户 ID" num>#{{ current?.id }}</DetailMetaRow>
          <div class="meta-row">
            <span class="meta-key">状态</span>
            <span class="meta-val">
              <StatusTag :status="current?.status ?? 'active'" kind="user" />
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-key">微信绑定</span>
            <span class="meta-val">
              {{ current?.wechatBound ? '已绑定' : '未绑定' }}
              <span class="muted">（仅布尔标识，不回显 openid）</span>
            </span>
          </div>
        </div>
      </div>

      <div class="detail-group">
        <div class="detail-group-title">认证与时间</div>
        <div class="detail-meta detail-meta--grid">
          <div class="meta-row">
            <span class="meta-key">认证邮箱</span>
            <span class="meta-val">
              <template v-if="current?.bindEmail">{{ current.bindEmail }}</template>
              <span v-else class="muted">未认证</span>
            </span>
          </div>
          <DetailMetaRow k="注册时间">{{ formatDateTime(current?.createdAt) }}</DetailMetaRow>
          <DetailMetaRow k="更新时间">{{ formatDateTime(current?.updatedAt) }}</DetailMetaRow>
        </div>
      </div>

      <p class="foot-note">
        管理端不代改资料（昵称 / 头像由用户本人维护）；可在此禁用 / 启用、解绑认证邮箱或注销账号。
      </p>

      <template #actions>
        <template v-if="!isDeleted">
          <button
            v-if="canWrite()"
            class="link"
            type="button"
            :disabled="isBusy(current?.id)"
            @click="detailToggle"
          >
            {{ current?.status === 'disabled' ? '启用' : '禁用' }}
          </button>
          <button
            v-if="current?.bindEmail"
            class="link"
            type="button"
            :disabled="isBusy(current?.id)"
            @click="detailUnbind"
          >
            解绑邮箱
          </button>
          <button
            v-if="canDelete()"
            class="link danger"
            type="button"
            :disabled="isBusy(current?.id)"
            @click="detailDelete"
          >
            删除账号
          </button>
        </template>
        <button v-else class="btn-secondary" type="button" @click="detailOpen = false">关闭</button>
      </template>
    </BaseDrawer>
  </div>
</template>

<style scoped>
/* 筛选区内控件定宽（.filters 容器样式已收敛到全局 shared.css） */
.filters .form-input {
  width: 180px;
}
/* 认证列：已认证邮箱（`--font-sm`，颜色随正文默认档） */
.email {
  font-size: var(--font-sm);
}
</style>
