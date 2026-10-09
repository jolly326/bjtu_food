<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getUsername } from '@/api/session'
import { fetchMe } from '@/api/auth'
import { roleLabel } from '@/utils/permissions'
import AccountSecurityDialog from '@/components/AccountSecurityDialog.vue'

/**
 * 管理后台外壳（左侧栏 + 内容区）。
 *
 * <p>口径真源：[C1 管理员登录与访问控制](../../../docs/func/web/C-账号与访问/C1-管理员登录与访问控制.md)
 * —— **账密登录，无个人页**：🔴 前端不持有口令，底部身份区回显**登录响应下发的 username**
 * （不是静态文案）。
 *
 * <p>导航 **5 组 / 14 项，按业务域分组**（运营 / 场所 / 内容 / 互动 / 账号）——
 * 分组口径见 [UI 基线 §1.1](../../../docs/ui/web/公共组件与形态基线.md)：
 * 同一个「域」的菜单放一起（一所食堂的档口归「场所」、菜品与它引用的字典归「内容」、
 * 一切 UGC 与治理字典归「互动」），避免按「主数据 / 治理 / 配置」这类**实现视角**切分。
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

/** 角色展示名（口径见 utils/permissions：未知 / 缺失一律按只读展示） */
const roleName = ref(roleLabel())

/** 口令是否已超期（>180 天）⇒ 顶部提示改密，**不强制踢出** */
const passwordAging = ref(false)

/** 账号安全弹窗（改密 / 动态口令） */
const securityOpen = ref(false)

/**
 * 补齐身份区与安全提示所需的信息。
 *
 * <p>角色与安全状态只有 `GET /admin/auth/me` 会下发，而路由守卫那次调用只用于「验活」、
 * 不回传数据 —— 故此处按需再取一次（管理端单人低频，一次额外请求可接受）。
 */
onMounted(async () => {
  try {
    const me = await fetchMe()
    roleName.value = roleLabel()
    passwordAging.value = !!me.passwordAging
  } catch {
    // 401 由请求层统一清 token 并跳登录页；此处不重复处置
  }
})

/** 导航分组（5 组 / 14 项，按业务域） */
const groups: NavGroup[] = [
  {
    title: '运营',
    items: [{ to: '/dashboard', label: '运营看板' }],
  },
  {
    title: '场所',
    items: [
      { to: '/canteens', label: '食堂管理' },
      { to: '/stalls', label: '档口管理' },
    ],
  },
  {
    title: '内容',
    items: [
      { to: '/dishes', label: '菜品管理' },
      { to: '/dimensions', label: '属性维度' },
      { to: '/views', label: '首页筛选视图' },
      { to: '/banners', label: '首页 Banner' },
    ],
  },
  {
    title: '互动',
    items: [
      { to: '/reviews', label: '评价管理' },
      { to: '/feedbacks', label: '意见反馈' },
      { to: '/reports', label: '举报管理' },
      { to: '/corrections', label: '菜品问题反馈' },
      { to: '/report-reasons', label: '举报原因' },
    ],
  },
  {
    title: '账号',
    items: [
      { to: '/users', label: '用户管理' },
      { to: '/alerts', label: '安全告警' },
    ],
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
          <div class="brand-name">知行食记</div>
          <div class="brand-sub">管理后台</div>
        </div>
      </div>

      <nav class="nav">
        <div v-for="g in groups" :key="g.title" class="nav-group">
          <div class="nav-group-title">{{ g.title }}</div>
          <RouterLink v-for="item in g.items" :key="item.to" :to="item.to" class="nav-item" v-press>
            {{ item.label }}
          </RouterLink>
        </div>
      </nav>

      <div class="sidebar-foot">
        <div class="admin-name">{{ displayName }}</div>
        <div class="admin-role">{{ roleName }}</div>
        <button class="security-link" type="button" @click="securityOpen = true">账号安全</button>
      </div>
    </aside>

    <main class="content" id="main-content">
      <!-- 口令超期：提示改密，不阻断操作（真源 secur/web/防爆破与限流.md §3） -->
      <div v-if="passwordAging" class="aging-notice" role="status">
        当前口令已使用超过 180 天，建议尽快在「账号安全」中修改。
      </div>
      <RouterView v-slot="{ Component }">
        <transition name="view-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </RouterView>
    </main>

    <AccountSecurityDialog :open="securityOpen" @close="securityOpen = false" />
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

/* 品牌区：与下方导航之间用一条分隔线区隔（不用第二个色块 —— 侧栏本身已是浅色玻璃面，
   再加块会把导航压得「碎」）；logo 与名称之间 `--space-3` 与导航项内距同档。 */
.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-2) var(--space-4);
  margin-bottom: var(--space-4);
  border-bottom: 1px solid var(--border-light);
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

/* 导航：组间距由 `gap` 统一承担（`--space-5`），组内项间距由项自身 `margin-bottom` 承担（`--space-1`）
   —— 两者分工明确，避免「组间距与项间距混用同一个值」导致的节奏不匀。 */
.nav {
  flex: 1;
  min-height: 0;
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
/* 导航项：项高统一（`min-height: 36px` + 内距 `--space-2`），圆角 `--radius-sm`（与控件档一致）。
   激活态用「主色浅底 + 主色文字 + 左侧 2px 指示条」三重表达 —— 单靠底色在深色底上辨识度不足。 */
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 36px;
  padding: var(--space-2) var(--space-3);
  margin-bottom: var(--space-1);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: var(--font-base);
  font-weight: var(--weight-medium);
  transition:
    background var(--duration-base) var(--ease-out),
    color var(--duration-base) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.nav-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 2px;
  height: 0;
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  transform: translateY(-50%);
  transition: height var(--duration-base) var(--ease-out);
}
.nav-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
.nav-item.router-link-active {
  background: var(--color-primary-bg);
  color: var(--color-primary-text);
}
.nav-item.router-link-active::before {
  height: 18px;
}
.nav-item:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

/* 底部身份区：与导航区之间留 `--space-4` 呼吸（导航项 margin-bottom 之后再退一档），
   角色以 chip 形态呈现（圆角 `--radius-pill`），与「角色是标签」的语义一致。 */
.sidebar-foot {
  margin-top: var(--space-4);
  padding: var(--space-3) var(--space-2) 0;
  border-top: 1px solid var(--border-light);
}
.admin-name {
  font-size: var(--font-base);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.admin-role {
  display: inline-block;
  margin-top: var(--space-1);
  padding: 1px var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  color: var(--text-muted);
  font-size: var(--font-xs);
}
.security-link {
  margin-top: var(--space-2);
  padding: 0;
  border: none;
  background: none;
  color: var(--color-primary-text);
  font-size: var(--font-xs);
  font-weight: var(--weight-medium);
  cursor: pointer;
}
.security-link:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
  border-radius: var(--radius-sm);
}

.aging-notice {
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  background: var(--color-primary-bg);
  color: var(--color-primary-text);
  font-size: var(--font-sm);
}

.content {
  flex: 1;
  min-width: 0;
  padding: var(--space-6) var(--space-8);
  box-sizing: border-box;
}

.view-fade-enter-active,
.view-fade-leave-active {
  transition: opacity var(--duration-base) var(--ease-out);
}
.view-fade-enter-from,
.view-fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .view-fade-enter-active,
  .view-fade-leave-active {
    transition: opacity var(--duration-base) var(--ease-out);
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
