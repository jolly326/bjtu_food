<script setup lang="ts">
/**
 * 运营端布局（知行食记 · 运营端）：
 * - 顶层固定导航栏：品牌名 知行食记（无衬线圆体）+ 日期；右侧操作员用户名文本（弱化，非按钮）
 * - 底部应用坞（Dock 式全局导航）：
 *   四个功能入口（主数据组：食堂 / 档口 / 菜品 ｜ 反馈组：反馈处理，分组分隔线），始终水平居中；
 *   悬停放大上浮 + 名称气泡、激活项主色 + 圆点指示；桌面 / 移动同构（无隐藏态）
 * - 退出登录：顶栏右上角图标按钮（放在顶栏，不放进坞内，避免危险操作混入导航）
 * - 内容区（唯一滚动容器）：页头吸顶于滚动区上界，内容自其下方穿过；底部留白让出应用坞，防遮挡
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
  { path: '/canteens', label: '食堂', icon: 'building', sep: false },
  { path: '/stalls', label: '档口', icon: 'store', sep: false },
  { path: '/dishes', label: '菜品', icon: 'food', sep: false },
  { path: '/feedback', label: '反馈处理', icon: 'flag', sep: true },
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
      <div class="topbar__right">
        <span class="topbar__user">{{ session.username || '运营' }}</span>
        <button class="topbar__logout" type="button" aria-label="退出登录" @click="onLogout">
          <AppIcon name="exit" :size="20" />
        </button>
      </div>
    </nav>

    <!-- 内容区 = 唯一滚动容器：页头挂载点与页面内容同处一个滚动上下文（页头吸顶），
         横向内距 / 滚动条槽位共用 ⇒ 滚动区上下左右边界与内容天然对齐 -->
    <main class="layout__main">
      <div id="layout-pageheader" class="layout__pageheader"></div>
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
            :class="{ 'dock__item--on': route.name === item.path.replace(/^\//, '') }"
            :data-label="item.label"
            :aria-label="item.label"
          >
            <AppIcon :name="item.icon" :size="22" />
            <span class="dock__bar" />
          </router-link>
        </template>
      </div>
    </nav>
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

/* ===== 顶层导航栏 ===== */
/* 顶栏：不画分割线，用一层极弱的底缘投影与内容区分离 */
.layout__topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: var(--web-topbar-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 var(--web-page-pad);
  background: var(--el-bg-color-page);
  box-shadow: 0 1px 0 var(--el-border-color-extra-light);
  z-index: 200;
}

.topbar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.brand__name {
  flex-shrink: 0;
  font-family: var(--web-font-sans);
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--el-text-color-primary);
}

.brand__date {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 操作员用户名文本（非按钮，Q 版动森：纯文本弱化即可）；
   窄屏时品牌名优先，用户名超长收缩省略，顶栏不产生横向溢出 */
.topbar__user {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 顶栏右侧：用户名 + 退出登录图标按钮 */
.topbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.topbar__logout {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: var(--el-border-radius-base);
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition:
    color 0.15s ease,
    background 0.15s ease;
}

.topbar__logout:hover {
  color: var(--el-color-error);
  background: var(--el-color-error-light-9);
}

/* ===== 内容区（唯一滚动容器；底部让出应用坞，防遮挡，见基线 §2.3） ===== */
.layout__main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  /* 顶栏 fixed 脱离布局流：内容区用等高外边距下移，滚动区上界 = 顶栏下沿 */
  margin-top: var(--web-topbar-h);
  padding: 0 var(--web-page-pad) 104px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  /* 滚动条槽位常驻：各页内容长短切换时内容宽度不跳变，页头与卡片边界始终对齐 */
  scrollbar-gutter: stable;
}

/* ===== 页头（内容区内吸顶，滚动时内容自其下方穿过） =====
   页面通过 Teleport 把 .page__bar 投射到这里；与内容共用同一滚动上下文与横向内距，
   左右边界天然对齐；底内距给出内容消失线，正文不会贴着筛选行被裁切。 */
.layout__pageheader {
  position: sticky;
  top: 0;
  z-index: 10;
  /* 上：与顶栏的间距；下：内容消失线（同时是页头与卡片的间距） */
  padding: var(--web-gap) 0 var(--web-gap);
  background: var(--el-bg-color-page);
}

/* ===== 底部应用坞（Dock 式全局导航：常驻水平居中，见基线 §2.3）===== */
/* 🔴 left:0 + right:0 + flex 居中（不依赖 translateX(-50%)）：
   视口宽度变化（滚动条出现 / 消失）时坞不产生横向位移，始终水平居中 */
.layout__dock {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(12px + env(safe-area-inset-bottom));
  display: flex;
  justify-content: center;
  /* 容器横跨整屏但自身不吃事件，避免遮挡内容点击 */
  pointer-events: none;
  z-index: 150;
}

.dock__wrap {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px;
  border-radius: 26px;
  background: var(--el-bg-color-overlay);
  box-shadow: var(--el-box-shadow-elevated);
  /* 坞本体恢复点击 */
  pointer-events: auto;
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
    transform 0.18s var(--web-ease-bounce),
    background 0.15s ease,
    color 0.15s ease;
}

/* 悬停：Q 弹上浮放大（过冲缓动，动森式的「弹一下」） */
.dock__item:hover {
  transform: translateY(-2px) scale(1.1);
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
  border-radius: var(--web-radius-sm);
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

/* 激活指示：底部小横条（宽 > 高）—— 区别于「运行中」圆点语义 */
.dock__bar {
  position: absolute;
  bottom: 3px;
  left: 50%;
  transform: translateX(-50%);
  width: 16px;
  height: 3px;
  border-radius: var(--web-radius-pill);
  background: var(--el-color-primary);
  opacity: 0;
  transition: opacity 0.15s ease;
}

.dock__item--on .dock__bar {
  opacity: 1;
}

/* 分组分隔：不用实线，用一段浅色短竖条弱化分组 */
.dock__sep {
  flex-shrink: 0;
  width: 1px;
  height: 20px;
  margin: 0 5px;
  background: var(--el-border-color-extra-light);
}

/* 窄屏（≤640px）：图标格收窄保证整坞一屏内**仍水平居中**（极窄屏 safe 兜底可横滚）。
   横向内距与顶栏高由 `--web-page-pad` / `--web-topbar-h` 统一收档（theme.css），此处只收坞与底部净空 */
@media (max-width: 640px) {
  .layout__main {
    padding-bottom: 96px;
  }
  .layout__dock {
    /* 居中由基座的 left:0 + right:0 + flex 保证，窄屏仅收窄底距 */
    bottom: calc(8px + env(safe-area-inset-bottom));
  }
  .dock__wrap {
    gap: 0;
    padding: 4px 6px;
  }
  /* 触控格 40×44（8 项 + 2 分隔 ≈ 338px，360px 屏内不溢出且保住 ≥44px 点按高度） */
  .dock__item {
    width: 40px;
    height: 44px;
    flex-shrink: 0;
  }
  .dock__sep {
    height: 18px;
    margin: 0 1px;
  }
}

/* 触屏（无 hover 指针）：点按后 hover 态会「粘滞」，一律还原；名称气泡在激活项常显 */
@media (hover: none) {
  .dock__item:hover {
    transform: none;
  }
  .dock__item:not(.dock__item--on):hover {
    background: transparent;
    color: var(--el-text-color-regular);
  }
  /* 气泡独立于 hover（防粘滞）：激活项常显名称，弥补触屏无悬停的图标识别 */
  .dock__item:hover::after {
    opacity: 0;
    transform: translateX(-50%) translateY(4px);
  }
  .dock__item--on::after,
  .dock__item--on:hover::after {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}
</style>
