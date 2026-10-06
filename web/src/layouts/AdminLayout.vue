<script setup lang="ts">
import { computed } from 'vue'
import { getUsername } from '@/api/session'

/**
 * 管理后台外壳（左侧栏 + 内容区）。
 *
 * <p>口径真源：[C1 管理员登录与访问控制](../../../docs/func/web/C-账号与访问/C1-管理员登录与访问控制.md)
 * —— **账密登录，无个人页**：🔴 前端不持有口令，底部身份区回显**登录响应下发的 username**
 * （不是静态文案）；导航 **13 项**见 [UI 基线 §四](../../../docs/ui/web/公共组件与形态基线.md)。
 */
interface NavItem {
  to: string
  label: string
}
interface NavGroup {
  title: string
  items: NavItem[]
}

/** 身份区显示名：取登录时回签的 username；取不到则兜底「管理员」 */
const displayName = computed(() => getUsername() || '管理员')

const groups: NavGroup[] = [
  {
    title: '概览',
    items: [{ to: '/dashboard', label: '运营看板' }],
  },
  {
    title: '主数据',
    items: [
      { to: '/canteens', label: '食堂管理' },
      { to: '/stalls', label: '档口管理' },
      { to: '/dishes', label: '菜品管理' },
      { to: '/dimensions', label: '属性维度' },
      { to: '/views', label: '首页筛选视图' },
      { to: '/banners', label: '首页 Banner' },
    ],
  },
  {
    title: '治理',
    items: [
      { to: '/reviews', label: '评价管理' },
      { to: '/feedbacks', label: '意见反馈' },
      { to: '/reports', label: '举报管理' },
      { to: '/corrections', label: '菜品问题反馈' },
      { to: '/users', label: '用户管理' },
    ],
  },
  {
    title: '配置',
    items: [{ to: '/report-reasons', label: '举报原因' }],
  },
]
</script>

<template>
  <div class="admin-shell">
    <a class="skip-link" href="#main-content">跳到主内容</a>
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-logo">食</div>
        <div class="brand-text">
          <div class="brand-name">食在交大</div>
          <div class="brand-sub">管理后台</div>
        </div>
      </div>

      <nav class="nav">
        <div v-for="g in groups" :key="g.title" class="nav-group">
          <div class="nav-group-title">{{ g.title }}</div>
          <RouterLink
            v-for="item in g.items"
            :key="item.to"
            :to="item.to"
            class="nav-item"
            v-press
          >
            {{ item.label }}
          </RouterLink>
        </div>
      </nav>

      <div class="sidebar-foot">
        <div class="admin-name">{{ displayName }}</div>
        <div class="admin-role">已登录</div>
      </div>
    </aside>

    <main class="content" id="main-content">
      <RouterView v-slot="{ Component }">
        <transition name="view-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </RouterView>
    </main>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  min-height: 100vh;
  background: var(--bg-page);
}

.sidebar {
  width: 232px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--nav-bg);
  backdrop-filter: var(--nav-blur);
  -webkit-backdrop-filter: var(--nav-blur);
  border-right: 1px solid var(--border-light);
  padding: var(--space-5) var(--space-3);
  box-sizing: border-box;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-2) var(--space-5);
}
.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: var(--radius);
  background: var(--color-primary);
  color: var(--color-on-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
}
.brand-name {
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.brand-sub {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.nav {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}
.nav-group-title {
  font-size: var(--font-xs);
  color: var(--text-muted);
  letter-spacing: 0.04em;
  padding: 0 var(--space-3);
  margin-bottom: var(--space-2);
}
.nav-item {
  display: block;
  padding: var(--space-2) var(--space-3);
  margin-bottom: var(--space-1);
  border-radius: var(--radius);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: var(--font-base);
  font-weight: var(--weight-medium);
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.nav-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.nav-item.router-link-active {
  background: var(--color-primary-bg);
  color: var(--color-primary-text);
}

.sidebar-foot {
  padding: var(--space-3) var(--space-2) 0;
  border-top: 1px solid var(--border-light);
}
.admin-name {
  font-size: var(--font-base);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.admin-role {
  font-size: var(--font-xs);
  color: var(--text-muted);
}

.content {
  flex: 1;
  min-width: 0;
  padding: var(--space-6) var(--space-8);
  box-sizing: border-box;
}

.view-fade-enter-active,
.view-fade-leave-active {
  transition: opacity 0.18s var(--ease-out);
}
.view-fade-enter-from,
.view-fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .view-fade-enter-active,
  .view-fade-leave-active {
    transition: opacity 0.18s ease;
  }
}

@media (max-width: 768px) {
  .sidebar {
    width: 180px;
  }
  .content {
    padding: var(--space-4) var(--space-4);
  }
}
</style>
