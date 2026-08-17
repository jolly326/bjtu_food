<script setup lang="ts">
/**
 * AdminLayout：现代控制台外壳（无侧边栏）。
 * 顶部一级导航（Logo + 4 个功能入口 + 用户菜单），内容区全宽。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useConfirmStore } from '@/stores/confirmStore'
import { useAdminUserStore } from '@/stores/adminUserStore'
import { useUserStore } from '@/stores/userStore'
import { useToastStore } from '@/stores/toastStore'
import Toast from '@/components/Toast.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { House, PriceTag, Document, User, UserFilled, ArrowDown } from '@element-plus/icons-vue'

const confirm = useConfirmStore()

const router = useRouter()
const route = useRoute()
const adminUser = useAdminUserStore()
const userStore = useUserStore()
const toast = useToastStore()

const activePath = computed(() => route.path)

// ===== 顶部一级导航 =====
const navItems = computed(() => [
  { key: 'dashboard', label: '工作台', path: '/dashboard', icon: House },
  { key: 'content', label: '信息管理', path: '/dashboard/content', icon: PriceTag },
  { key: 'audit', label: '内容审核', path: '/dashboard/audit', icon: Document },
  { key: 'system', label: '用户与系统', path: '/dashboard/system', icon: User },
])

/**
 * 导航激活判断：聚合页内的详情/子路由归属对应一级入口。
 * - 食堂详情(/dashboard/canteens/…) 归属「信息管理」
 * - 账号设置(/dashboard/account) 归属「用户与系统」
 */
function isNavActive(path: string) {
  if (path === '/dashboard') return activePath.value === '/dashboard'
  if (path === '/dashboard/content') {
    return activePath.value === '/dashboard/content' || activePath.value.startsWith('/dashboard/canteens')
  }
  if (path === '/dashboard/audit') return activePath.value.startsWith('/dashboard/audit')
  if (path === '/dashboard/system') {
    return activePath.value === '/dashboard/system' || activePath.value === '/dashboard/account'
  }
  return false
}

function navTo(path: string) {
  router.push(path)
}

// ===== 用户菜单（账号设置 / 退出登录） =====
const userMenuOpen = ref(false)
function goAccount() { userMenuOpen.value = false; router.push('/dashboard/account') }
function logout() {
  // 统一清理：token/username/adminId 及 store 状态，避免残留上一账号信息（M09）
  userStore.clearAuth()
  toast.clear() // 清理残留 toast（L02）
  router.replace('/login')
}
</script>

<template>
  <div class="admin-shell">
    <Toast />
    <ConfirmDialog />

    <!-- ===== 顶部导航 ===== -->
    <header class="topnav">
      <div class="topnav-brand" v-press role="button" tabindex="0" aria-label="回到工作台" @click="navTo('/dashboard')" @keydown.enter.prevent="navTo('/dashboard')" @keydown.space.prevent="navTo('/dashboard')">
        <span class="brand-text">知行食记</span>
      </div>

      <nav class="topnav-nav">
        <button
          v-for="n in navItems"
          :key="n.key"
          class="topnav-item"
          :class="{ on: isNavActive(n.path) }"
          v-press
          type="button"
          @click="navTo(n.path)"
        >
          <span>{{ n.label }}</span>
        </button>
      </nav>

      <div class="topnav-right">
        <div class="topbar-user" role="button" tabindex="0" :aria-expanded="userMenuOpen" aria-haspopup="menu" @click="userMenuOpen = !userMenuOpen" @keydown.enter.prevent="userMenuOpen = !userMenuOpen" @keydown.space.prevent="userMenuOpen = !userMenuOpen">
          <el-icon class="tu-ico"><UserFilled /></el-icon>
          <span class="tu-name">{{ adminUser.myRole === 'super_admin' ? '超级管理员' : '管理员' }}</span>
          <el-icon class="tu-caret" :class="{ open: userMenuOpen }"><ArrowDown /></el-icon>
          <div v-if="userMenuOpen" class="user-menu" @click.stop>
            <button class="um-item" v-press @click="goAccount">账号设置</button>
            <button class="um-item danger" v-press @click="logout">退出登录</button>
          </div>
          <div v-if="userMenuOpen" class="user-menu-mask" @click="userMenuOpen = false"></div>
        </div>
      </div>
    </header>

    <!-- ===== 内容区（全宽） ===== -->
    <main class="shell-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
  overflow: hidden;
}

/* ===== 顶部导航：白底毛玻璃（沉稳，单一砖红点缀） ===== */
.topnav {
  height: var(--nav-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-6);
  padding: 0 var(--space-6);
  background: var(--nav-bg);
  backdrop-filter: var(--nav-blur);
  -webkit-backdrop-filter: var(--nav-blur);
  border-bottom: 1px solid var(--border-light);
  box-shadow: 0 1px 0 color-mix(in srgb, var(--text-primary) 4%, transparent);
  position: sticky;
  top: 0;
  z-index: 30;
}
@media (prefers-reduced-transparency: reduce) {
  .topnav { background: var(--bg-card); backdrop-filter: none; -webkit-backdrop-filter: none; }
}
.topnav-brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
  padding: var(--space-1);
  border-radius: var(--radius);
  transition: background 0.2s var(--ease-out);
}
.topnav-brand:hover { background: var(--bg-soft); }
.topnav-brand:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.brand-text {
  font-size: var(--font-lg);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  letter-spacing: 0.02em;
  white-space: nowrap;
}
.topnav-nav {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex: 1;
  min-width: 0;
  overflow-x: auto;
}
.topnav-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border: none;
  background: none;
  border-radius: var(--radius);
  font-size: var(--font-base);
  color: var(--text-secondary);
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
.topnav-item:hover { background: var(--bg-soft); color: var(--text-primary); }
.topnav-item:active { transform: scale(var(--press-scale)); }
.topnav-item:focus-visible { outline: 2px solid var(--color-primary); outline-offset: -2px; }
.topnav-item.on { background: var(--nav-item-active-bg); color: var(--nav-item-active-color); font-weight: var(--weight-semibold); }

.topnav-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* ===== 用户菜单 ===== */
.topbar-user {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius);
  cursor: pointer;
  user-select: none;
  color: var(--text-secondary);
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out);
}
.topbar-user:hover { background: var(--bg-hover); color: var(--text-primary); }
.topbar-user:active { transform: scale(var(--press-scale)); }
.topbar-user:focus-visible { outline: none; box-shadow: var(--focus-ring); }
.tu-ico { width: 18px; height: 18px; }
.tu-name { font-size: var(--font-base); font-weight: var(--weight-medium); color: var(--text-primary); }
.tu-caret { width: 14px; height: 14px; color: var(--text-light); transition: transform 0.2s var(--ease-out); }
.tu-caret.open { transform: rotate(180deg); }

.user-menu {
  position: absolute;
  top: calc(100% + var(--space-2));
  right: 0;
  min-width: 140px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-pop);
  padding: var(--space-1);
  z-index: 60;
  animation: user-menu-in 0.16s var(--ease-out) both;
  transform-origin: top right;
}
@keyframes user-menu-in {
  from { opacity: 0; transform: translateY(-6px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
@media (prefers-reduced-motion: reduce) {
  .user-menu { animation: none; }
}
.um-item {
  display: block;
  width: 100%;
  padding: var(--space-2) var(--space-3);
  border: none;
  background: none;
  border-radius: var(--radius);
  text-align: left;
  font-size: var(--font-base);
  color: var(--text-primary);
  cursor: pointer;
  transition: background 0.15s var(--ease-out), color 0.15s var(--ease-out);
}
.um-item:hover { background: var(--bg-soft); }
.um-item.danger { color: var(--color-error); }
.um-item.danger:hover { background: var(--color-error-bg); }
.user-menu-mask { position: fixed; inset: 0; z-index: 55; }

/* ===== 内容区 ===== */
.shell-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  /* 页面留白统一由 PageContainer 控制，避免双 padding */
}
@media (max-width: 767px) {
  .topnav { padding: 0 var(--space-3); gap: var(--space-2); }
  /* 窄屏：导航只显示图标，节省横向空间 */
  .topnav-item span { display: none; }
  .topnav-item { padding: var(--space-2); }
  .brand-text { display: none; }
  .tu-name { display: none; }
}
</style>
