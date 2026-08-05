<script setup lang="ts">
/**
 * ContentManageView：信息管理聚合页。
 * 分类卡导航（去 tabbar）：食堂（含档口）/ 菜品 / 首页配置（轮播·广播）三张卡，点击切换视图。
 */
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePageStore } from '@/stores/pageStore'
import { useAdminStore } from '@/stores/adminStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import CanteensView from '@/views/canteen/CanteensView.vue'
import DishManageView from '@/views/canteen/DishManageView.vue'
import HomeConfigView from '@/views/content/HomeConfigView.vue'
import { House, Food, Picture } from '@element-plus/icons-vue'

const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '信息管理' }] })

// 进入信息管理即重新加载全部业务数据（食堂/档口/菜品/轮播），保证各视图有数据
const adminStore = useAdminStore()
onMounted(() => { adminStore.loadAll() })

const sections = [
  { key: 'canteen', label: '食堂', badge: () => adminStore.canteens.length, icon: House },
  { key: 'dish', label: '菜品', badge: () => adminStore.dishes.length, icon: Food },
  { key: 'home', label: '首页配置', badge: () => adminStore.banners.length, icon: Picture },
]
const KEYS = ['canteen', 'dish', 'home']
const route = useRoute()
const activeKey = ref(
  typeof route.query.tab === 'string' && KEYS.includes(route.query.tab) ? route.query.tab : 'canteen',
)
watch(() => route.query.tab, (t) => {
  if (typeof t === 'string' && KEYS.includes(t)) activeKey.value = t
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
        <span v-if="s.badge() > 0" class="sec-badge">{{ s.badge() }}</span>
      </button>
    </div>

    <!-- 纯 v-if 切换：组件挂载时读取响应式 store，切换回来必显示 -->
    <CanteensView v-if="activeKey === 'canteen'" />
    <DishManageView v-else-if="activeKey === 'dish'" />
    <HomeConfigView v-else />
  </PageContainer>
</template>

<style scoped>
/* 分类卡样式统一在 shared.css（.sec-grid/.sec-card），保证三页大小 UI 一致 */
</style>
