<script setup lang="ts">
/**
 * 运营端布局：
 * - 顶部固定导航栏集中承载品牌、四个功能入口、操作员与退出登录
 * - 内容区 = 唯一滚动容器；页头吸顶于滚动区上界
 * - 全局框架与配色口径见 docs/func/web/约定与红线.md「界面框架与配色」
 */
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useSessionStore } from '@/stores/session'

type NavItem = { path: string; label: string; icon: string }

const NAV_ITEMS: NavItem[] = [
  { path: '/canteens', label: '食堂', icon: 'building' },
  { path: '/stalls', label: '档口', icon: 'store' },
  { path: '/dishes', label: '菜品', icon: 'food' },
  { path: '/feedback', label: '反馈处理', icon: 'flag' },
]

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

function onLogout() {
  session.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <nav class="layout__topbar" aria-label="全局导航">
      <span class="topbar__brand">知行食记</span>

      <div class="topbar__nav">
        <router-link
          v-for="item in NAV_ITEMS"
          :key="item.path"
          :to="item.path"
          class="topnav__item"
          :class="{ 'topnav__item--on': route.name === item.path.replace(/^\//, '') }"
          :aria-label="item.label"
        >
          <AppIcon :name="item.icon" :size="18" />
          <span class="topnav__label">{{ item.label }}</span>
        </router-link>
      </div>

      <div class="topbar__right">
        <span class="topbar__user">{{ session.username || '运营' }}</span>
        <button class="topbar__logout" type="button" aria-label="退出登录" @click="onLogout">
          <AppIcon name="exit" :size="18" />
        </button>
      </div>
    </nav>

    <main class="layout__main">
      <div id="layout-pageheader" class="layout__pageheader"></div>
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background: var(--el-bg-color-page);
}

/* ===== 顶部固定导航栏：全部功能入口集中于此 ===== */
.layout__topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: var(--web-topbar-h);
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 var(--web-page-pad);
  background: var(--web-bg-topbar);
  border-bottom: 1px solid var(--el-border-color-lighter);
  z-index: 200;
}

.topbar__brand {
  flex-shrink: 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--el-text-color-primary);
}

.topbar__nav {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.topnav__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  border-radius: var(--web-radius-sm);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.topnav__item:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.topnav__item--on,
.topnav__item--on:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.topnav__item:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.topbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  margin-left: auto;
}

.topbar__user {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar__logout {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: var(--web-radius-sm);
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition:
    color 0.15s ease,
    background 0.15s ease;
}

.topbar__logout:hover {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

/* ===== 内容区 = 唯一滚动容器 ===== */
.layout__main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  margin-top: var(--web-topbar-h);
  padding: 0 var(--web-page-pad) 32px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.layout__pageheader {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: var(--web-gap) 0;
  background: var(--el-bg-color-page);
}

/* 窄屏：导航收成图标，保住四个入口一屏可达 */
@media (max-width: 767px) {
  .layout__topbar {
    gap: 8px;
  }

  .topbar__brand {
    font-size: 14px;
  }

  .topbar__user {
    display: none;
  }

  .topnav__item {
    width: 34px;
    padding: 0;
    justify-content: center;
  }

  .topnav__label {
    display: none;
  }
}

@media (max-width: 400px) {
  .topbar__brand {
    display: none;
  }
}

@media (hover: none) {
  .topnav__item:hover {
    background: transparent;
    color: var(--el-text-color-secondary);
  }

  .topnav__item--on:hover {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }
}
</style>
