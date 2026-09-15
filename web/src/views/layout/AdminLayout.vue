<script setup lang="ts">
/**
 * AdminLayout：现代控制台外壳（无侧边栏）。
 * 顶部一级导航（Logo + 4 个功能入口 + 只读身份标识），内容区全宽。
 * 2026-09-15（本轮）：工作台入口已下线，一级导航收敛为 信息管理 / 内容审核 / 用户与系统。
 * 2026-09-15（取消人工复核）：原「内容审核」聚合页拆分为「评价管理 / 反馈处理」两个一级入口，
 * 一级导航定为 4 项（信息管理 / 评价管理 / 反馈处理 / 用户与系统），默认落点仍为菜品页。
 */
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Toast from '@/components/Toast.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { PriceTag, Star, ChatDotRound, User, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const activePath = computed(() => route.path)

/** 后台默认落点：菜品列表（brand 与所有空入口统一指向此处，工作台已下线） */
const DISH_LIST_PATH = '/dashboard/content?tab=dish'

// ===== 顶部一级导航（4 项） =====
/**
 * 图标语义（沿用既有语义，禁用 emoji 当图标）：信息管理=菜品价格牌、
 * 评价管理=评分星、反馈处理=反馈消息、用户与系统=用户。
 */
// 静态列表（无响应式依赖）：不用 computed，避免读处多一层 .value 的心智负担
const navItems = [
  { key: 'content', label: '信息管理', path: '/dashboard/content', icon: PriceTag },
  { key: 'reviews', label: '评价管理', path: '/dashboard/reviews', icon: Star },
  { key: 'feedback', label: '反馈处理', path: '/dashboard/feedback', icon: ChatDotRound },
  { key: 'system', label: '用户与系统', path: '/dashboard/system', icon: User },
]

/**
 * 导航激活判断：聚合页内的子路由归属对应一级入口
 * （如菜品详情 /dashboard/content/dishes/:id 归属「信息管理」）。
 * 四个入口路径互不为前缀，故按「等值或子路径」判定即可。
 */
function isNavActive(path: string) {
  return activePath.value === path || activePath.value.startsWith(`${path}/`)
}

function navTo(path: string) {
  router.push(path)
}

// ===== 身份标识（§7.10 B：管理端已无角色体系，固定文案「管理员」；
// 系统为超时自动登出，无主动退出/账号设置入口，故此处仅为只读身份展示） =====
const currentRoleLabel = ref('管理员')
</script>

<template>
  <div class="admin-shell">
    <Toast />
    <ConfirmDialog />

    <!-- ===== 顶部导航 ===== -->
    <header class="topnav">
      <div class="topnav-brand" v-press role="button" tabindex="0" aria-label="进入菜品管理" @click="navTo(DISH_LIST_PATH)" @keydown.enter.prevent="navTo(DISH_LIST_PATH)" @keydown.space.prevent="navTo(DISH_LIST_PATH)">
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
          :aria-label="n.label"
          @click="navTo(n.path)"
        >
          <el-icon class="nav-ico" aria-hidden="true"><component :is="n.icon" /></el-icon>
          <span>{{ n.label }}</span>
        </button>
      </nav>

      <div class="topnav-right">
        <div class="topbar-user" aria-label="当前身份：管理员">
          <el-icon class="tu-ico"><UserFilled /></el-icon>
          <span class="tu-name">{{ currentRoleLabel }}</span>
        </div>
      </div>
    </header>

    <!-- ===== 内容区（全宽；无装饰性入场动效，§4.3） ===== -->
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
.nav-ico { width: 16px; height: 16px; flex-shrink: 0; }

.topnav-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* ===== 身份标识（只读，无可点区域） ===== */
.topbar-user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius);
  user-select: none;
  color: var(--text-secondary);
}
.tu-ico { width: 18px; height: 18px; }
.tu-name { font-size: var(--font-base); font-weight: var(--weight-medium); color: var(--text-primary); }

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
  /* 窄屏：导航只显示图标（保留 aria-label，屏幕阅读器仍可辨识），节省横向空间 */
  .topnav-item span { display: none; }
  .topnav-item { padding: var(--space-2); }
  .brand-text { display: none; }
  .tu-name { display: none; }
}
</style>
