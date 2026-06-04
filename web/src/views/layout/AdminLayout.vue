<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { usePageStore } from '@/stores/pageStore'
import { useConfirmStore } from '@/stores/confirmStore'
import Toast from '@/components/Toast.vue'
import SearchInput from '@/components/SearchInput.vue'

const confirm = useConfirmStore()
import canteenIcon from '@/static/icon/canteen.svg'
import chartBarIcon from '@/static/icon/chart-bar.svg'
import groupIcon from '@/static/icon/group.svg'
import tagIcon from '@/static/icon/tag.svg'
import accountIcon from '@/static/icon/account.svg'


const router = useRouter()
const route = useRoute()
const page = usePageStore()
const sidebarCollapsed = ref(false)

const activePath = computed(() => route.path)

function goBreadcrumb(item: { label: string; path?: string }) {
  if (item.path) router.push(item.path)
}
</script>

<template>
  <div class="admin-layout">
    <Toast />
    <!-- 全局确认弹窗 -->
    <Teleport to="body">
      <div v-if="confirm.visible" class="confirm-overlay" @click.self="confirm.cancel()">
        <div class="confirm-box">
          <p class="confirm-msg">{{ confirm.message }}</p>
          <div class="confirm-actions">
            <button class="btn-cancel" @click="confirm.cancel()">取消</button>
            <button class="btn-primary" @click="confirm.ok()">确定</button>
          </div>
        </div>
      </div>
    </Teleport>
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <span class="logo-text">食在交大管理系统</span>
      </div>
      <nav class="sidebar-nav">
        <div class="nav-item" :class="{ active: activePath === '/dashboard' }" @click="router.push('/dashboard')">
          <img :src="chartBarIcon" class="nav-icon-img" alt="" /><span class="nav-label">数据概览</span>
        </div>
        <div class="nav-item" :class="{ active: activePath.startsWith('/dashboard/canteens') }" @click="router.push('/dashboard/canteens')">
          <img :src="canteenIcon" class="nav-icon-img" alt="" /><span class="nav-label">食堂管理</span>
        </div>
        <div class="nav-item" :class="{ active: activePath.startsWith('/dashboard/banners') }" @click="router.push('/dashboard/banners')">
          <img :src="tagIcon" class="nav-icon-img" alt="" /><span class="nav-label">轮播管理</span>
        </div>
        <div class="nav-item" :class="{ active: activePath === '/dashboard/users' }" @click="router.push('/dashboard/users')">
          <img :src="groupIcon" class="nav-icon-img" alt="" /><span class="nav-label">用户管理</span>
        </div>
      </nav>
      <div class="sidebar-footer">
        <div class="nav-item" :class="{ active: activePath === '/dashboard/admins' }" @click="router.push('/dashboard/admins')">
          <img :src="accountIcon" class="nav-icon-img" alt="" />
          <span class="nav-label">账号设置</span>
        </div>
      </div>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <button class="toggle-btn" @click="sidebarCollapsed = !sidebarCollapsed">☰</button>
        <div class="breadcrumb-bar">
          <span v-for="(item, i) in page.breadcrumbs" :key="i" class="bc-item" :class="{ clickable: !!item.path, last: i === page.breadcrumbs.length - 1 }" @click="goBreadcrumb(item)">
            <span class="bc-sep" v-if="i > 0">&gt;</span>
            <span class="bc-label">{{ item.label }}</span>
          </span>
          <span v-if="!page.breadcrumbs.length" class="bc-placeholder">交大美食管理系统</span>
        </div>
        <div class="topbar-search" v-if="page.showSearch">
          <SearchInput :placeholder="page.searchPlaceholder" @change="page.searchQuery = $event" />
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
}
.sidebar {
  width: 220px;
  background: #6B1010;
  color: #fff;
  display: flex;
  flex-direction: column;
  transition: width .3s;
  flex-shrink: 0;
  z-index: 10;
}
.sidebar.collapsed {
  width: 0;
  overflow: hidden;
}
.sidebar-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255,255,255,.1);
}
.logo-text {
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}
.sidebar-nav {
  flex: 1;
  padding: 8px 0;
  overflow-y: auto;
}
.nav-item {
  display: flex;
  align-items: center;
  padding: 10px 20px;
  cursor: pointer;
  transition: all .2s;
  color: rgba(255,255,255,.8);
  white-space: nowrap;
  user-select: none;
}
.nav-item:hover {
  color: #fff;
  background: rgba(255,255,255,.08);
}
.nav-item.active {
  color: #fff;
  background: var(--color-primary);
}
.nav-icon-img {
  width: 18px;
  height: 18px;
  margin-right: 10px;
  flex-shrink: 0;
  filter: brightness(0) invert(1);
}
.nav-label {
  font-size: 14px;
  flex: 1;
}
.sidebar-footer {
  border-top: 1px solid rgba(255,255,255,.1);
  padding: 4px 0;
}
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.topbar {
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: var(--shadow-card);
  gap: 12px;
  flex-shrink: 0;
}
.toggle-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}
.toggle-btn:hover {
  background: var(--bg-page);
}
.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  overflow: hidden;
}
.bc-item {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.bc-item.clickable {
  cursor: pointer;
}
.bc-item.clickable:hover .bc-label {
  color: var(--color-primary);
  text-decoration: underline;
}
.bc-item.last .bc-label {
  color: var(--text-primary);
  font-weight: 500;
  font-size: 15px;
}
.bc-sep {
  color: var(--text-light);
  font-size: 13px;
  padding: 0 2px;
}
.bc-label {
  font-size: 14px;
  color: var(--text-secondary);
  transition: color .15s;
}
.bc-placeholder {
  color: var(--text-primary);
  font-weight: 500;
  font-size: 15px;
}
.topbar-search {
  display: flex;
  width: 320px;
  margin-left: auto;
}
.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

/* ===== 全局确认弹窗 ===== */
.confirm-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,.45);
  display: flex; align-items: center; justify-content: center; z-index: 2000;
}
.confirm-box {
  background: #fff; border-radius: var(--radius-lg, 12px);
  padding: 28px 32px 20px; width: 400px; max-width: 90vw;
  box-shadow: 0 8px 30px rgba(0,0,0,.15);
}
.confirm-msg { margin: 0 0 24px; font-size: 15px; color: var(--text-primary); line-height: 1.5; }
.confirm-actions { display: flex; justify-content: flex-end; gap: 12px; }
</style>
