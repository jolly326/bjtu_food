<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { usePageStore } from '@/stores/pageStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { useAdminUserStore } from '@/stores/adminUserStore'
import Toast from '@/components/Toast.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {
  House, PriceTag, ChatDotRound, Picture, User, UserFilled, Menu,
  Document, ChatLineSquare, Fold, ArrowLeft,
} from '@element-plus/icons-vue'

const confirm = useConfirmStore()

const router = useRouter()
const route = useRoute()
const page = usePageStore()
const adminUser = useAdminUserStore()

const activePath = computed(() => route.path)
// 管理员管理入口仅超级管理员可见
const canManageAdmins = computed(() => adminUser.myRole === 'super_admin' || adminUser.myRole === 'superadmin')

// 浮层抽屉：<960 默认折叠为 overlay + scrim；>=960 常驻
const isMobile = ref(false)
const overlayOpen = ref(false)
function checkViewport() {
  isMobile.value = window.innerWidth < 960
  if (!isMobile.value) overlayOpen.value = false
}
onMounted(() => {
  checkViewport()
  window.addEventListener('resize', checkViewport)
})
onUnmounted(() => window.removeEventListener('resize', checkViewport))

function navTo(path: string) {
  router.push(path)
  if (isMobile.value) overlayOpen.value = false
}
function toggleOverlay() {
  overlayOpen.value = !overlayOpen.value
}
function goBreadcrumb(item: { label: string; path?: string }) {
  if (item.path) router.push(item.path)
}
</script>

<template>
  <div class="admin-layout">
    <Toast />
    <ConfirmDialog />

    <!-- 浮层 scrim（仅移动态显示） -->
    <div v-if="isMobile && overlayOpen" class="scrim" @click="overlayOpen = false" />

    <aside
      class="sidebar"
      :class="{ collapsed: isMobile && !overlayOpen, overlay: isMobile && overlayOpen }"
    >
      <div class="sidebar-header">
        <span class="logo-text">食在交大管理系统</span>
        <button v-if="isMobile" class="side-close" v-press type="button" aria-label="关闭菜单" @click="overlayOpen = false">
          <el-icon><ArrowLeft /></el-icon>
        </button>
      </div>
      <nav class="sidebar-nav">
        <!-- ① 概览 -->
        <div class="nav-group">
          <div class="nav-group-title">概览</div>
          <div class="nav-item" :class="{ active: activePath === '/dashboard' }" v-press @click="navTo('/dashboard')">
            <el-icon class="nav-icon-img"><House /></el-icon><span class="nav-label">仪表盘</span>
          </div>
        </div>

        <!-- ② 内容 -->
        <div class="nav-group">
          <div class="nav-group-title">内容</div>
          <div class="nav-item" :class="{ active: activePath.startsWith('/dashboard/canteens') }" v-press @click="navTo('/dashboard/canteens')">
            <el-icon class="nav-icon-img"><House /></el-icon><span class="nav-label">食堂</span>
          </div>
          <div class="nav-item" :class="{ active: activePath.startsWith('/dashboard/dishes') }" v-press @click="navTo('/dashboard/dishes')">
            <el-icon class="nav-icon-img"><PriceTag /></el-icon><span class="nav-label">菜品</span>
          </div>
          <div class="nav-item" :class="{ active: activePath.startsWith('/dashboard/banners') }" v-press @click="navTo('/dashboard/banners')">
            <el-icon class="nav-icon-img"><Picture /></el-icon><span class="nav-label">轮播</span>
          </div>
        </div>

        <!-- ③ 运营 -->
        <div class="nav-group">
          <div class="nav-group-title">运营</div>
          <div class="nav-item" :class="{ active: activePath.startsWith('/dashboard/reviews') }" v-press @click="navTo('/dashboard/reviews')">
            <el-icon class="nav-icon-img"><Document /></el-icon><span class="nav-label">审核中心</span>
          </div>
          <div class="nav-item" :class="{ active: activePath.startsWith('/dashboard/moments') }" v-press @click="navTo('/dashboard/moments')">
            <el-icon class="nav-icon-img"><ChatDotRound /></el-icon><span class="nav-label">动态管理</span>
          </div>
          <div class="nav-item" :class="{ active: activePath.startsWith('/dashboard/feedbacks') }" v-press @click="navTo('/dashboard/feedbacks')">
            <el-icon class="nav-icon-img"><ChatLineSquare /></el-icon><span class="nav-label">反馈举报</span>
          </div>
          <div class="nav-item" :class="{ active: activePath === '/dashboard/users' }" v-press @click="navTo('/dashboard/users')">
            <el-icon class="nav-icon-img"><User /></el-icon><span class="nav-label">用户</span>
          </div>
          <div v-if="canManageAdmins" class="nav-item" :class="{ active: activePath === '/dashboard/admins' }" v-press @click="navTo('/dashboard/admins')">
            <el-icon class="nav-icon-img"><UserFilled /></el-icon>
            <span class="nav-label">管理员</span>
          </div>
          <div class="nav-item" :class="{ active: activePath === '/dashboard/operation-logs' }" v-press @click="navTo('/dashboard/operation-logs')">
            <el-icon class="nav-icon-img"><Document /></el-icon><span class="nav-label">操作日志</span>
          </div>
          <div class="nav-item" :class="{ active: activePath === '/dashboard/account' }" v-press @click="navTo('/dashboard/account')">
            <el-icon class="nav-icon-img"><UserFilled /></el-icon>
            <span class="nav-label">账号设置</span>
          </div>
        </div>
      </nav>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <button class="toggle-btn" v-press type="button" :aria-label="isMobile ? '打开菜单' : '折叠菜单'" @click="toggleOverlay">
          <el-icon><Menu v-if="!isMobile || !overlayOpen" /><Fold v-else /></el-icon>
        </button>
        <div class="breadcrumb-bar">
          <span v-for="(item, i) in page.breadcrumbs" :key="i" class="bc-item" :class="{ clickable: !!item.path, last: i === page.breadcrumbs.length - 1 }" @click="goBreadcrumb(item)">
            <span class="bc-sep" v-if="i > 0">&gt;</span>
            <span class="bc-label">{{ item.label }}</span>
          </span>
          <span v-if="!page.breadcrumbs.length" class="bc-placeholder">交大美食管理系统</span>
        </div>
      </header>
      <main class="content"><router-view /></main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  background: var(--bg-page);
  overflow-x: hidden;
}

/* ===== 侧边栏（220px 深红底，材质比顶栏更"厚"） ===== */
.sidebar {
  width: 220px;
  flex-shrink: 0;
  background: var(--color-primary-dark);
  color: var(--text-white);
  display: flex;
  flex-direction: column;
  transition: transform 0.3s var(--ease-drawer), width 0.3s var(--ease-out);
  z-index: 30;
}
.sidebar.collapsed {
  /* 移动态：移出视口（非 width:0 硬切） */
  transform: translateX(-100%);
  width: 220px;
}
.sidebar.overlay {
  position: fixed;
  inset: 0 auto 0 0;
  box-shadow: var(--shadow-pop);
}
.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-5);
  border-bottom: 1px solid color-mix(in srgb, var(--text-white) 10%, transparent);
}
.logo-text {
  font-size: var(--font-xl);
  font-weight: var(--weight-semibold);
  white-space: nowrap;
}
.side-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px; height: 32px;
  border: none; background: none; color: var(--text-white);
  cursor: pointer; border-radius: var(--radius-sm);
}
.side-close:active { transform: scale(var(--press-scale)); }
.sidebar-nav { flex: 1; padding: var(--space-2) 0; overflow-y: auto; }
.nav-group { padding: var(--space-2) 0; }
.nav-group-title {
  padding: var(--space-2) var(--space-5);
  font-size: var(--font-xs);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--text-white) 45%, transparent);
  user-select: none;
}
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  padding: var(--space-3) var(--space-5);
  cursor: pointer;
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out);
  color: color-mix(in srgb, var(--text-white) 80%, transparent);
  white-space: nowrap;
  user-select: none;
}
@media (hover: hover) {
  .nav-item:hover {
    color: var(--text-white);
    background: color-mix(in srgb, var(--text-white) 8%, transparent);
  }
}
/* 激活项：轻亮背景 + 左侧 3px 主色竖条 */
.nav-item.active {
  color: var(--text-white);
  background: color-mix(in srgb, var(--text-white) 14%, transparent);
}
.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  background: var(--text-white);
  border-radius: 0 2px 2px 0;
}
.nav-icon-img {
  width: 18px;
  height: 18px;
  margin-right: var(--space-3);
  flex-shrink: 0;
  color: inherit;
}
.nav-label { font-size: var(--font-base); flex: 1; }

/* ===== 主区 ===== */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.topbar {
  height: 60px;
  background: color-mix(in srgb, var(--bg-card) 72%, transparent);
  backdrop-filter: var(--blur-material);
  -webkit-backdrop-filter: var(--blur-material);
  display: flex;
  align-items: center;
  padding: 0 var(--space-5);
  box-shadow: var(--shadow-card);
  gap: var(--space-3);
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-light);
}
.toggle-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  transition: background 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
@media (hover: hover) {
  .toggle-btn:hover { background: var(--bg-hover); }
}
.toggle-btn:active { transform: scale(var(--press-scale)); }
.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.bc-item { display: flex; align-items: center; gap: var(--space-1); white-space: nowrap; }
.bc-item.clickable { cursor: pointer; }
@media (hover: hover) {
  .bc-item.clickable:hover .bc-label { color: var(--color-primary); text-decoration: underline; }
}
.bc-item.last .bc-label { color: var(--text-primary); font-weight: var(--weight-medium); font-size: var(--font-md); }
.bc-sep { color: var(--text-light); font-size: var(--font-sm); padding: 0 var(--space-1); }
.bc-label { font-size: var(--font-base); color: var(--text-secondary); transition: color .15s var(--ease-out); }
.bc-placeholder { color: var(--text-primary); font-weight: var(--weight-medium); font-size: var(--font-md); }

.content { flex: 1; padding: 0; overflow-y: auto; overflow-x: hidden; }

/* ===== 浮层 scrim（移动态） ===== */
.scrim {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 25;
  backdrop-filter: var(--blur-material);
  -webkit-backdrop-filter: var(--blur-material);
}

@media (prefers-reduced-transparency: reduce) {
  .topbar, .scrim { backdrop-filter: none; -webkit-backdrop-filter: none; background: var(--bg-card); }
  .scrim { background: rgba(0, 0, 0, 0.55); }
}
@media (prefers-reduced-motion: reduce) {
  .sidebar { transition: none; }
}
</style>
