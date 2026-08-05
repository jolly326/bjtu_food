<script setup lang="ts">
/**
 * AuditManageView：内容审核聚合页。
 * 设计（去冗余）：顶部三张分类卡 = 唯一一级导航（带待办数），点击在本页切换下方列表，无重复 tabbar。
 * - 申请与反馈 / 评价 / 动态（UGC 申请并入"申请与反馈"卡内分段）
 * 兼容旧链接 ?tab=xxx → 定位对应分类卡。
 */
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePageStore } from '@/stores/pageStore'
import { getDashboard, type DashboardData } from '@/api/dashboard'
import PageContainer from '@/components/layout/PageContainer.vue'
import ApplyFeedbackView from '@/views/audit/ApplyFeedbackView.vue'
import ApplyReviewView from '@/views/admin/ApplyReviewView.vue'
import MomentManageView from '@/views/admin/MomentManageView.vue'
import { Document, ChatDotRound, ChatLineSquare } from '@element-plus/icons-vue'

const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '内容审核' }] })

// ===== 待办数（来自 dashboard，加载失败静默不影响切换） =====
const todo = ref<DashboardData | null>(null)
onMounted(async () => {
  try { todo.value = await getDashboard('week') } catch { todo.value = null }
})

// ===== 唯一一级导航：分类卡（带待办数徽标，点击切换当前视图） =====
const sections = [
  {
    key: 'feedback',
    label: '申请与反馈',
    badge: () => (todo.value?.pendingApplyCount ?? 0) + (todo.value?.pendingFeedbackCount ?? 0),
    icon: Document,
  },
  {
    key: 'review',
    label: '评价',
    badge: () => 0,
    icon: ChatLineSquare,
  },
  {
    key: 'moment',
    label: '动态',
    badge: () => todo.value?.pendingMomentCount ?? 0,
    icon: ChatDotRound,
  },
]
const KEYS = ['feedback', 'review', 'moment']
const route = useRoute()

function resolveKey(q: unknown): string {
  if (q === 'apply' || q === 'apply-feedback') return 'feedback'
  return typeof q === 'string' && KEYS.includes(q) ? q : 'feedback'
}
const activeKey = ref(resolveKey(route.query.tab))
watch(() => route.query.tab, (t) => { activeKey.value = resolveKey(t) })
</script>

<template>
  <PageContainer>
    <!-- 唯一一级导航：分类卡（待办数徽标），点击切换下方列表 -->
    <div class="sec-grid">
      <button
        v-for="s in sections"
        :key="s.key"
        class="sec-card"
        :class="{ on: activeKey === s.key, done: s.badge() === 0 }"
        v-press
        type="button"
        @click="activeKey = s.key"
      >
        <el-icon class="sec-ico"><component :is="s.icon" /></el-icon>
        <span class="sec-label">{{ s.label }}</span>
        <span v-if="s.badge() > 0" class="sec-badge">{{ s.badge() }}</span>
      </button>
    </div>

    <!-- 纯 v-if 切换：组件挂载时重新读取数据，切换回来必显示 -->
    <ApplyFeedbackView v-if="activeKey === 'feedback'" />
    <ApplyReviewView v-else-if="activeKey === 'review'" initial-entity="review" />
    <MomentManageView v-else />
  </PageContainer>
</template>

<style scoped>
/* ===== 分类卡（唯一一级导航） ===== */
/* 分类卡样式统一在 shared.css（.sec-grid/.sec-card），保证三页大小 UI 一致 */
</style>
