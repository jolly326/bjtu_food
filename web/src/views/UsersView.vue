<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, setUserStatus } from '@/api/users'
import type { UserAdminVO, UserListParams } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import StatusTag from '@/components/StatusTag.vue'
import StateBox from '@/components/StateBox.vue'

const fKeyword = ref('')
const fStatus = ref<'normal' | 'disabled' | ''>('')

function params(): UserListParams {
  return {
    keyword: fKeyword.value || undefined,
    status: fStatus.value || undefined,
  }
}

const { items, loading, finished, error, load } = usePagedList<UserAdminVO>(
  (page, pageSize) => listUsers({ page, pageSize, ...params() }),
  20,
)

async function toggle(r: UserAdminVO): Promise<void> {
  const disabled = r.status !== 'disabled'
  try {
    await ElMessageBox.confirm(
      disabled
        ? `确认禁用用户「${r.nickname || r.id}」？其登录与写操作将被拒绝（已发表内容保留）。`
        : `确认启用用户「${r.nickname || r.id}」？`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await setUserStatus(r.id, { disabled })
    ElMessage.success(disabled ? '已禁用' : '已启用')
    load(true)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}
function search(): void {
  load(true)
}
function reset(): void {
  fKeyword.value = ''
  fStatus.value = ''
  load(true)
}

onMounted(() => load(true))
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>用户管理</h2></div>

    <div class="card filters">
      <input class="form-input" v-model="fKeyword" placeholder="昵称 / 邮箱" @keyup.enter="search" />
      <select class="form-input" v-model="fStatus" @change="search">
        <option value="">全部状态</option>
        <option value="normal">正常</option>
        <option value="disabled">已禁用</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="search">查询</button>
      <button class="btn-ghost" type="button" @click="reset">重置</button>
    </div>

    <StateBox v-if="loading && !items.length" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="search" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>头像</th>
            <th>昵称</th>
            <th>绑定邮箱</th>
            <th>认证</th>
            <th>微信</th>
            <th>状态</th>
            <th>评价数</th>
            <th>注册时间</th>
            <th>最近登录</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in items" :key="r.id">
            <td>
              <img v-if="r.avatar" :src="r.avatar" class="avatar" alt="" />
              <span v-else class="avatar avatar-placeholder">·</span>
            </td>
            <td>{{ r.nickname || '—' }}</td>
            <td>{{ r.bindEmail ?? '未认证' }}</td>
            <td>{{ r.verified ? '已认证' : '—' }}</td>
            <td>{{ r.wechatBound ? '已绑定' : '未绑定' }}</td>
            <td><StatusTag :status="r.status" kind="user" /></td>
            <td>{{ r.reviewCount }}</td>
            <td class="muted">{{ r.createdAt }}</td>
            <td class="muted">{{ r.lastLoginAt ?? '从未登录' }}</td>
            <td class="actions">
              <button
                v-if="r.status !== 'deleted'"
                class="link"
                type="button"
                @click="toggle(r)"
              >
                {{ r.status === 'disabled' ? '启用' : '禁用' }}
              </button>
              <span v-else class="muted">—</span>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="10"><StateBox status="empty" /></td>
          </tr>
        </tbody>
      </table>
      <div class="pager">
        <span v-if="loading" class="muted">加载中…</span>
        <button v-else-if="!finished" class="btn-ghost" type="button" @click="load()">加载更多</button>
        <span v-else-if="items.length" class="muted">已全部加载</span>
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
  width: 36px;
  height: 36px;
  border-radius: 50%;
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
.pager {
  padding: var(--space-4);
  text-align: center;
}
</style>
