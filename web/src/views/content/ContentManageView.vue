<script setup lang="ts">
/**
 * ContentManageView：信息管理聚合页。
 * 卡片导航（去 tabbar）：**菜品 / 首页配置（分类）两张卡**。
 * 2026-09-14 拍板（project_spec §7.15）：食堂与档口是「菜品」的附属维度，
 * 随菜品表单一起维护 → 移除「食堂（含档口）」独立入口（详见 project_spec §7.15）。
 */
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import DishManageView from '@/views/content/DishManageView.vue'
import HomeConfigView from '@/views/content/HomeConfigView.vue'
import { Food, Picture } from '@element-plus/icons-vue'

// WEB-02：本页不再顶层 loadAll——菜品/食堂/档口/品类域由 DishManageView 挂载时按需加载，
// 品类域由 CategoryManage 自行加载，避免聚合页进页连发多轮全量请求
const adminStore = useAdminStore()

const sections = [
  { key: 'dish', label: '菜品', badge: () => adminStore.dishes.length, icon: Food },
  { key: 'home', label: '首页配置', icon: Picture },
]
const KEYS = ['dish', 'home']
const route = useRoute()

/**
 * 深链降级：历史 `?tab=canteen`（含各页旧返回链接 / 书签）不再有对应视图，
 * 统一降级到「菜品」，不出现空白页。
 */
function normalizeKey(raw: unknown): string {
  if (typeof raw !== 'string') return 'dish'
  if (KEYS.includes(raw)) return raw
  if (raw === 'canteen' || raw === 'stall' || raw === 'stalls') return 'dish'
  return 'dish'
}

const activeKey = ref(normalizeKey(route.query.tab))
watch(() => route.query.tab, (t) => { activeKey.value = normalizeKey(t) })
watch(activeKey, (k) => {
  // 同步地址栏：把非法 tab（如 canteen）改写为实际生效的 tab，避免刷新后又走降级分支
  if (route.query.tab !== k) {
    const query = { ...route.query, tab: k }
    // 仅更新 query，不新增历史记录
    history.replaceState(history.state, '', `${route.path}?${new URLSearchParams(query as Record<string, string>).toString()}`)
  }
})
</script>

<template>
  <PageContainer>
    <!-- 分类卡导航：唯一入口，点击切换下方视图 -->
    <div class="sec-grid">
      <button
        v-for="s in sections"
        :key="s.key"
        class="sec-card"
        :class="{ on: activeKey === s.key }"
        v-press
        type="button"
        @click="activeKey = s.key"
      >
        <el-icon class="sec-ico"><component :is="s.icon" /></el-icon>
        <span class="sec-label">{{ s.label }}</span>
        <span v-if="s.badge && s.badge() > 0" class="sec-badge">{{ s.badge() }}</span>
      </button>
    </div>

    <!-- 纯 v-if 切换：组件挂载时读取响应式 store，切换回来必显示 -->
    <DishManageView v-if="activeKey === 'dish'" />
    <HomeConfigView v-else />
  </PageContainer>
</template>

<style scoped>
/* 分类卡样式统一在 shared.css（.sec-grid/.sec-card），保证各页大小 UI 一致 */
</style>
