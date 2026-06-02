<script setup lang="ts">
import { computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { usePageStore } from '@/stores/pageStore'

const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '用户管理' }] })

const roleLevel: Record<string, number> = { user: 0, admin: 1, super_admin: 2 }
const roleLabels: Record<string, string> = { user: '普通用户', admin: '管理员', super_admin: '超级管理员' }
const statusMap: Record<string, { label: string; cls: string }> = {
  active: { label: '正常', cls: 'tag-green' },
  disabled: { label: '已禁用', cls: 'tag-red' },
}

const currentUser = computed(() => {
  const name = localStorage.getItem('username')
  return store.users.find(u => u.username === name) ?? store.users.find(u => u.role === 'super_admin') ?? null
})
function getLevel(role: string) { return roleLevel[role] ?? -1 }
const currentLevel = computed(() => getLevel(currentUser.value?.role ?? ''))

// 按角色分组
const adminUsers = computed(() =>
  store.users.filter(u => u.role === 'admin' && currentLevel.value > getLevel(u.role))
)
const normalUsers = computed(() =>
  store.users.filter(u => u.role === 'user' && currentLevel.value > getLevel(u.role))
)
const canSeeAdmins = computed(() => currentLevel.value > (roleLevel['admin'] ?? -1))
const hasAdminSection = computed(() => canSeeAdmins.value && adminUsers.value.length)

function canManage(target: { role: string }) { return getLevel(target.role) < currentLevel.value }

async function handleToggle(id: number) {
  const u = store.users.find(u => Number(u.id) === id)
  if (!u) return
  if (!canManage(u)) { toast.error('无权操作该用户'); return }
  const action = u.status === 'active' ? '禁用' : '启用'
  if (!await confirm.confirm(`确定${action}该用户？`)) return
  store.toggleUserStatus(id); toast.success(`用户已${action}`)
}
</script>

<template>
  <div class="page">
    <!-- 当前管理员信息 -->
    <div class="admin-badge" v-if="currentUser">
      <div class="badge-left">
        <span class="badge-avatar">{{ (currentUser.nickname || currentUser.username)[0] }}</span>
        <div class="badge-info">
          <span class="badge-name">{{ currentUser.nickname || currentUser.username }}</span>
          <span class="badge-role">@{{ currentUser.username }} · <span class="tag" :class="'tag-' + currentUser.role">{{ roleLabels[currentUser.role] }}</span></span>
        </div>
      </div>
      <div class="badge-right">
        <span class="badge-scope">可管理：<strong>{{ roleLabels[Object.keys(roleLevel).find(k => roleLevel[k] === currentLevel - 1) || ''] || '暂无' }}</strong></span>
      </div>
    </div>

    <!-- 管理员列表（仅超级管理员可见） -->
    <div class="section-card" v-if="hasAdminSection">
      <div class="section-card-header">
        <h3>管理人员</h3>
        <span class="section-count">{{ adminUsers.length }} 人</span>
      </div>
      <table class="table">
        <thead><tr><th style="width:60px">ID</th><th style="width:50px">头像</th><th>用户名</th><th>昵称</th><th style="width:100px">角色</th><th style="width:80px">状态</th><th style="width:120px">注册时间</th><th style="width:80px">操作</th></tr></thead>
        <tbody>
          <tr v-for="u in adminUsers" :key="Number(u.id)">
            <td class="cell-id">{{ u.id }}</td>
            <td><div class="avatar-cell"><span class="avatar-placeholder avatar-admin">{{ (u.nickname || u.username)[0] }}</span></div></td>
            <td class="cell-username">{{ u.username }}</td>
            <td>{{ u.nickname || '-' }}</td>
            <td><span class="tag tag-admin">管理员</span></td>
            <td><span class="tag" :class="statusMap[u.status]?.cls || 'tag-gray'">{{ statusMap[u.status]?.label || u.status }}</span></td>
            <td class="cell-date">{{ u.created_at.toLocaleDateString('zh-CN') }}</td>
            <td class="actions">
              <button class="action-btn" :class="u.status === 'active' ? 'action-disable' : 'action-enable'" @click="handleToggle(Number(u.id))">{{ u.status === 'active' ? '禁用' : '启用' }}</button>
            </td>
          </tr>
          <tr v-if="!adminUsers.length"><td colspan="8" class="empty">暂无管理员</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 普通用户列表 -->
    <div class="section-card">
      <div class="section-card-header">
        <h3>普通用户</h3>
        <span class="section-count">{{ normalUsers.length }} 人</span>
      </div>
      <table class="table">
        <thead><tr><th style="width:60px">ID</th><th style="width:50px">头像</th><th>用户名</th><th>昵称</th><th style="width:100px">角色</th><th style="width:80px">状态</th><th style="width:120px">注册时间</th><th style="width:80px">操作</th></tr></thead>
        <tbody>
          <tr v-for="u in normalUsers" :key="Number(u.id)">
            <td class="cell-id">{{ u.id }}</td>
            <td><div class="avatar-cell"><img v-if="u.avatar" :src="u.avatar" class="avatar-img" /><span v-else class="avatar-placeholder">{{ (u.nickname || u.username)[0] }}</span></div></td>
            <td class="cell-username">{{ u.username }}</td>
            <td>{{ u.nickname || '-' }}</td>
            <td><span class="tag tag-user">普通用户</span></td>
            <td><span class="tag" :class="statusMap[u.status]?.cls || 'tag-gray'">{{ statusMap[u.status]?.label || u.status }}</span></td>
            <td class="cell-date">{{ u.created_at.toLocaleDateString('zh-CN') }}</td>
            <td class="actions">
              <button class="action-btn" :class="u.status === 'active' ? 'action-disable' : 'action-enable'" @click="handleToggle(Number(u.id))">{{ u.status === 'active' ? '禁用' : '启用' }}</button>
            </td>
          </tr>
          <tr v-if="!normalUsers.length"><td colspan="8" class="empty">暂无用户</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="!hasAdminSection && !normalUsers.length" class="empty-state">暂无可见用户</div>
  </div>
</template>

<style scoped>
/* ===== 当前管理员身份卡片 ===== */
.admin-badge {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
  padding: 16px 24px;
  margin-bottom: 16px;
}
.badge-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.badge-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  flex-shrink: 0;
}
.badge-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.badge-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.badge-role {
  font-size: 12px;
  color: var(--text-muted);
}
.badge-right {
  font-size: 13px;
  color: var(--text-secondary);
}
.badge-right strong {
  color: var(--color-primary);
  font-weight: 600;
}

/* ===== 分组卡片 ===== */
.section-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
  overflow: hidden;
  margin-bottom: 16px;
}
.section-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px;
  border-bottom: 1px solid #f0f0f0;
}
.section-card-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.section-count {
  font-size: 12px;
  color: var(--text-muted);
  background: #f5f5f5;
  padding: 2px 10px;
  border-radius: 10px;
}

/* ===== 表格 ===== */
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.table th {
  text-align: center;
  padding: 10px 12px;
  background: #fafafa;
  color: var(--text-secondary);
  font-weight: 500;
  font-size: 13px;
  white-space: nowrap;
}
.table td {
  text-align: center;
  padding: 10px 12px;
  border-bottom: 1px solid #f5f5f5;
  color: var(--text-primary);
}
.table tbody tr:hover { background: #fafbff; }

.avatar-cell {
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-img { width: 30px; height: 30px; border-radius: 50%; object-fit: cover; }
.avatar-placeholder {
  width: 30px; height: 30px; border-radius: 50%; background: var(--color-primary); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600;
}
.avatar-admin { background: var(--color-primary-light); }

.cell-id { color: var(--text-muted); font-size: 13px; }
.cell-username { font-weight: 500; }
.cell-date { color: var(--text-muted); font-size: 13px; white-space: nowrap; }

.tag {
  display: inline-block; padding: 2px 10px; border-radius: 10px;
  font-size: 12px; font-weight: 500;
}
.tag-green { background: var(--color-success-bg); color: var(--color-success); }
.tag-red { background: var(--color-error-bg); color: var(--color-error); }
.tag-gray { background: #f5f5f5; color: var(--text-muted); }
.tag-user { background: #f0f5ff; color: #2f54eb; }
.tag-admin { background: var(--color-primary-bg); color: var(--color-primary); }
.tag-super_admin { background: linear-gradient(135deg, #8B3A2B, #6B1010); color: #fff; }

.action-btn {
  padding: 3px 12px; border-radius: 6px; font-size: 12px; font-weight: 500;
  cursor: pointer; transition: all .2s; border: 1px solid;
}
.action-disable { border-color: var(--color-warning); color: var(--color-warning); background: #fff; }
.action-disable:hover { background: var(--color-warning-bg); }
.action-enable { border-color: var(--color-success); color: var(--color-success); background: #fff; }
.action-enable:hover { background: var(--color-success-bg); }

.empty { text-align: center; color: var(--text-light); padding: 40px 16px !important; }
.empty-state { text-align: center; color: var(--text-light); padding: 60px 0; font-size: 14px; }
</style>
