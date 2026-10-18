<script setup lang="ts">
/**
 * 运营端布局（知行食记 · 运营端）：
 * - 顶层固定导航栏：品牌名 知行食记（衬线）+ 日期；右侧 Admin（橙色主按钮）
 * - 底部应用坞（Dock 式全局导航，规格见 docs/ui/web/公共组件与形态基线.md §2.2）：
 *   七个功能入口（主数据组 ｜ 反馈组，分组分隔线）+ 分隔线 + 退出登录；
 *   悬停放大上浮 + 名称气泡、激活项主色 + 圆点指示；桌面 / 移动同构（无隐藏态）
 * - 内容区（滚动）：底部留白让出应用坞，防遮挡
 */
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import { useSessionStore } from '@/stores/session'
import { computed } from 'vue'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

/** 应用坞入口项 */
type DockItem = {
  path: string
  label: string
  icon: string
  /** 该项前渲染分组分隔线（主数据组 ｜ 反馈组） */
  sep: boolean
}

const MENUS: DockItem[] = [
  { path: '/canteens', label: '食堂', icon: 'home', sep: false },
  { path: '/stalls', label: '档口', icon: 'compass', sep: false },
  { path: '/dishes', label: '菜品', icon: 'dish', sep: false },
  { path: '/posters', label: '海报', icon: 'image', sep: false },
  { path: '/records', label: '评分数据', icon: 'star', sep: true },
  { path: '/issues', label: '纠错处理', icon: 'report', sep: false },
  { path: '/options', label: '纠错选项', icon: 'settings', sep: false },
]

const now = computed(() => {
  const d = new Date()
  return d.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })
})

function onLogout() {
  session.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <!-- 顶层固定导航栏（滚动时保留） -->
    <nav class="layout__topbar">
      <div class="topbar__brand">
        <span class="brand__name">知行食记</span>
        <span class="brand__date">{{ now }}</span>
      </div>
      <div class="topbar__actions">
        <button class="topbar__btn topbar__btn--primary">Admin</button>
      </div>
    </nav>

    <main class="layout__main">
      <RouterView />
    </main>

    <!-- 底部应用坞（Dock 式全局导航：悬浮胶囊居中，悬停放大 + 名称气泡 + 激活圆点） -->
    <nav class="layout__dock" aria-label="全局导航">
      <div class="dock__wrap">
        <template v-for="item in MENUS" :key="item.path">
          <span v-if="item.sep" class="dock__sep" aria-hidden="true" />
          <router-link
            :to="item.path"
            class="dock__item"
            :class="{ 'dock__item--on': route.path.startsWith(item.path) }"
            :data-label="item.label"
          >
            <AppIcon :name="item.icon" :size="22" />
            <span class="dock__dot" />
          </router-link>
        </template>

        <span class="dock__sep" aria-hidden="true" />

        <button class="dock__item dock__item--danger" data-label="退出登录" @click="onLogout">
          <AppIcon name="log-out" :size="22" />
        </button>
      </div>
    </nav>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--el-bg-color-page);
}

/* ===== 顶层导航栏 ===== */
.layout__topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--el-bg-color-page);
  border-bottom: 1px solid var(--el-border-color-extra-light);
  z-index: 200;
}

.topbar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand__name {
  font-family: var(--web-font-serif);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--el-text-color-primary);
}

.brand__date {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.topbar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.topbar__btn {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 14px;
  border-radius: var(--el-border-radius-sm);
  font-size: 13px;
  cursor: pointer;
  border: 1px solid transparent;
  background: transparent;
  color: var(--el-text-color-regular);
}

.topbar__btn--outline {
  border-color: var(--el-border-color);
}
.topbar__btn--outline:hover {
  background: var(--el-fill-color-light);
}

.topbar__btn--primary {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary);
  color: #fff;
}
.topbar__btn--primary:hover {
  background: var(--el-color-primary-dark-2);
}

/* ===== 内容区（底部让出应用坞，防遮挡，见基线 §2.2） ===== */
.layout__main {
  flex: 1;
  min-width: 0;
  margin-top: 56px;
  padding: 20px 20px 104px;
  overflow-y: auto;
}

/* ===== 底部应用坞（Dock 式全局导航：悬浮胶囊居中，见基线 §2.2）===== */
.layout__dock {
  position: fixed;
  left: 50%;
  bottom: calc(12px + env(safe-area-inset-bottom));
  transform: translateX(-50%);
  z-index: 150;
}

.dock__wrap {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-large);
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-elevated);
}

.dock__item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: var(--el-border-radius-base);
  background: transparent;
  color: var(--el-text-color-regular);
  text-decoration: none;
  cursor: pointer;
  transform-origin: center bottom;
  transition:
    transform 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;
}

/* 悬停放大上浮（macOS Dock 风） */
.dock__item:hover {
  transform: translateY(-2px) scale(1.12);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

/* 悬停名称气泡（tooltip） */
.dock__item::after {
  content: attr(data-label);
  position: absolute;
  bottom: calc(100% + 10px);
  left: 50%;
  transform: translateX(-50%) translateY(4px);
  padding: 4px 10px;
  border-radius: var(--el-border-radius-sm);
  background: var(--el-text-color-primary);
  color: var(--el-bg-color);
  font-size: 12px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition:
    opacity 0.15s ease,
    transform 0.15s ease;
}

.dock__item:hover::after {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

/* 激活项：主色 + 底部圆点指示（macOS「运行中」语义） */
.dock__item--on {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.dock__dot {
  position: absolute;
  bottom: 3px;
  left: 50%;
  transform: translateX(-50%);
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--el-color-primary);
  opacity: 0;
}

.dock__item--on .dock__dot {
  opacity: 1;
}

/* 分组分隔线（主数据组 ｜ 反馈组 ｜ 退出登录） */
.dock__sep {
  flex-shrink: 0;
  width: 1px;
  height: 24px;
  margin: 0 4px;
  background: var(--el-border-color);
}

.dock__item--danger {
  color: var(--el-text-color-secondary);
}

.dock__item--danger:hover {
  color: var(--el-color-error);
  background: var(--el-color-error-light-9);
}

/* 窄屏（≤640px）：Dock 图标缩小并允许横向滚动，避免七项溢出；内容区收窄边距 */
@media (max-width: 640px) {
  .layout__main {
    margin-top: 52px;
    padding: 12px 12px 96px;
  }
  .layout__topbar {
    height: 52px;
    padding: 0 12px;
  }
  .layout__dock {
    left: 0;
    right: 0;
    width: 100%;
    transform: none;
    bottom: calc(8px + env(safe-area-inset-bottom));
  }
  .dock__wrap {
    overflow-x: auto;
    gap: 4px;
    padding: 4px 8px;
    justify-content: flex-start;
    scrollbar-width: none;
  }
  .dock__wrap::-webkit-scrollbar {
    display: none;
  }
  .dock__item {
    width: 40px;
    height: 40px;
    flex-shrink: 0;
  }
  .dock__sep {
    height: 20px;
    margin: 0 2px;
  }
}
</style>
