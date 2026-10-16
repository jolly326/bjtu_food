<script setup lang="ts">
/**
 * 运营端布局：左侧固定侧边栏（六个入口）+ 退出登录。
 * 范式见 docs/ui/web/README.md「导航结构」。
 */
import { useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

const MENUS = [
  { path: '/canteens', label: '食堂' },
  { path: '/stalls', label: '档口' },
  { path: '/dishes', label: '菜品' },
  { path: '/records', label: '评分数据' },
  { path: '/issues', label: '纠错处理' },
  { path: '/options', label: '纠错选项' },
]

function onLogout() {
  session.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <aside class="layout__side">
      <div class="layout__brand">知行食记 · 运营端</div>

      <nav class="layout__nav">
        <router-link
          v-for="item in MENUS"
          :key="item.path"
          :to="item.path"
          class="layout__link"
          :class="{ 'layout__link--on': route.path.startsWith(item.path) }"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <button class="layout__logout" @click="onLogout">退出登录</button>
    </aside>

    <main class="layout__main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
}

.layout__side {
  display: flex;
  flex-direction: column;
  width: 200px;
  flex-shrink: 0;
  padding: 16px 12px;
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color-page);
}

.layout__brand {
  padding: 8px 8px 20px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.layout__nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.layout__link {
  padding: 10px 12px;
  border-radius: var(--el-border-radius-base);
  color: var(--el-text-color-regular);
  text-decoration: none;
  font-size: 14px;
}

.layout__link:hover {
  background: var(--el-fill-color-light);
}

.layout__link--on {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.layout__logout {
  margin-top: auto;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.layout__logout:hover {
  color: var(--el-color-danger);
  border-color: var(--el-color-danger);
}

.layout__main {
  flex: 1;
  min-width: 0;
  padding: 20px;
}
</style>
