<script setup lang="ts">
/**
 * SystemManageView：用户与系统聚合页。
 * 分类卡导航（去 tabbar）：账号（学生 / 管理员）/ 操作日志 两张卡。
 */
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePageStore } from '@/stores/pageStore'
import { useAdminStore } from '@/stores/adminStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import AccountView from '@/views/system/AccountView.vue'
import OperationLogView from '@/views/admin/OperationLogView.vue'
import { User, Document } from '@element-plus/icons-vue'

const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '用户与系统' }] })

// 进入即重载用户数据，保证账号视图有数据
const adminStore = useAdminStore()
onMounted(() => { adminStore.loadAll() })

const sections = [
  { key: 'account', label: '账号', badge: () => adminStore.users.length, icon: User },
  { key: 'log', label: '操作日志', badge: () => 0, icon: Document },
]
const KEYS = ['account', 'log']
const route = useRoute()
const activeKey = ref(
  typeof route.query.tab === 'string' && KEYS.includes(route.query.tab) ? route.query.tab : 'account',
)
watch(() => route.query.tab, (t) => {
  if (typeof t === 'string' && KEYS.includes(t)) activeKey.value = t
})
</script>

<template>
  <PageContainer>
    <!-- 分类卡导航：唯一入口，点击切换下方视图 -->
    <div class="sec-grid cols-2">
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

    <AccountView v-if="activeKey === 'account'" />
    <OperationLogView v-else />
  </PageContainer>
</template>

<style scoped>
/* 分类卡样式统一在 shared.css（.sec-grid/.sec-card），保证三页大小 UI 一致 */
</style>
