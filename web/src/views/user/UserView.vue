<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { usePageStore } from '@/stores/pageStore'

const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '用户管理' }], showSearch: true, searchPlaceholder: '搜索学生用户名或昵称...' })

const students = computed(() => store.users.filter(u => u.role === 'user'))

const stats = computed(() => ({
  total: students.value.length,
  active: students.value.filter(u => u.status === 'active').length,
  disabled: students.value.filter(u => u.status === 'disabled').length,
}))

const searchQuery = computed(() => page.searchQuery.trim().toLowerCase())
const filteredStudents = computed(() => {
  const q = searchQuery.value
  if (!q) return students.value
  return students.value.filter(u =>
    u.username.toLowerCase().includes(q) ||
    (u.nickname || '').toLowerCase().includes(q)
  )
})

async function handleToggle(id: number) {
  const u = store.users.find(u => Number(u.id) === id)
  if (!u) return
  const action = u.status === 'active' ? '禁用' : '启用'
  if (!await confirm.confirm(`确定${action}学生「${u.nickname || u.username}」？`)) return
  store.toggleUserStatus(id)
  toast.success(`学生已${action}`)
}
</script>

<template>
  <div class="page">
    <!-- 数据概览 -->
    <div class="stats-row">
      <div class="stat-card">
        <span class="stat-num">{{ stats.total }}</span>
        <span class="stat-label">学生总数</span>
      </div>
      <div class="stat-card stat-active">
        <span class="stat-num">{{ stats.active }}</span>
        <span class="stat-label">正常</span>
      </div>
      <div class="stat-card stat-disabled">
        <span class="stat-num">{{ stats.disabled }}</span>
        <span class="stat-label">已禁用</span>
      </div>
    </div>

    <!-- 学生列表 -->
    <div class="card">
      <div class="card-hd">
        <h3>学生列表</h3>
        <span class="section-count">{{ filteredStudents.length }} 人</span>
      </div>
      <table class="table" v-if="filteredStudents.length">
        <thead>
          <tr>
            <th style="width:44px">头像</th>
            <th>用户信息</th>
            <th style="width:120px">状态</th>
            <th style="width:120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in filteredStudents" :key="Number(u.id)">
            <td>
              <span class="avatar-circle">{{ (u.nickname || u.username)[0] }}</span>
            </td>
            <td class="cell-userinfo">
              <div class="user-name">{{ u.nickname || u.username }}</div>
              <div class="user-meta">
                <span class="user-username">@{{ u.username }}</span>
                <span class="user-sep">·</span>
                <span class="user-date">{{ u.created_at.toLocaleDateString('zh-CN') }} 注册</span>
              </div>
            </td>
            <td>
              <span class="tag" :class="u.status === 'active' ? 'tag-green' : 'tag-red'">
                {{ u.status === 'active' ? '正常' : '已禁用' }}
              </span>
            </td>
            <td>
              <button
                class="action-btn"
                :class="u.status === 'active' ? 'action-disable' : 'action-enable'"
                @click="handleToggle(Number(u.id))"
              >
                {{ u.status === 'active' ? '禁用' : '启用' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty-state">
        {{ searchQuery ? '没有匹配的学生' : '暂无学生用户' }}
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 数据概览 ===== */
.stats-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 10px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
}
.stat-num {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  color: var(--text-primary);
}
.stat-label {
  font-size: 13px;
  color: var(--text-muted);
}
.stat-active .stat-num { color: var(--color-success); }
.stat-disabled .stat-num { color: var(--color-error); }

/* ===== 学生列表卡片 ===== */
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
  padding: 10px 24px;
  background: #fafafa;
  color: var(--text-secondary);
  font-weight: 500;
  font-size: 13px;
  white-space: nowrap;
}
.table th:first-child,
.table th:nth-child(2) { text-align: left; }
.table th:nth-child(2) { padding-left: 16px; }
.table td {
  text-align: center;
  padding: 12px 24px;
  border-bottom: 1px solid #f5f5f5;
  color: var(--text-primary);
  vertical-align: middle;
}
.table tbody tr:hover { background: #fafbff; }

.avatar-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

/* ===== 用户信息列 ===== */
.cell-userinfo {
  text-align: left !important;
  padding: 12px 12px !important;
}
.user-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
}
.user-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-muted);
}
.user-username { color: var(--text-muted); }
.user-sep { color: #ddd; }
.user-date { color: var(--text-light); }

.tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}
.tag-green { background: var(--color-success-bg); color: var(--color-success); }
.tag-red { background: var(--color-error-bg); color: var(--color-error); }

.action-btn {
  padding: 4px 14px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all .2s;
  border: 1px solid;
}
.action-disable { border-color: var(--color-warning); color: var(--color-warning); background: #fff; }
.action-disable:hover { background: var(--color-warning-bg); }
.action-enable { border-color: var(--color-success); color: var(--color-success); background: #fff; }
.action-enable:hover { background: var(--color-success-bg); }

.empty-state {
  text-align: center;
  color: var(--text-light);
  padding: 60px 0;
  font-size: 14px;
}
</style>
