<script setup lang="ts">
/**
 * SystemManageView：用户与系统聚合页。
 * 分类卡导航（去 tabbar）：账号（学生 / 管理员）/ 操作日志 两张卡。
 */
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useUserStore } from '@/stores/userStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import AccountView from '@/views/system/AccountView.vue'
import OperationLogView from '@/views/system/OperationLogView.vue'
import { User, Document } from '@element-plus/icons-vue'

// WEB-02：进入即按需加载「用户」域（分类卡徽标 + 账号视图），
// 不再经 adminStore.loadAll 连发 5 域全量请求（操作日志域由 OperationLogView 自行加载）
const adminStore = useAdminStore()
const userStore = useUserStore()
onMounted(() => { userStore.loadAll().catch(() => {}) })

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
/* 分类卡样式统一在 shared.css（.sec-grid/.sec-card），保证各聚合页大小 UI 一致 */
</style>
