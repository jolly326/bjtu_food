<script setup lang="ts">
/**
 * AuditManageView：内容审核聚合页。
 * 设计（去冗余）：顶部两张分类卡 = 唯一一级导航（带待办数），点击在本页切换下方列表，无重复 tabbar。
 * - 反馈 / 评价（UGC 申请审核已随 apply 全链路下线，见 change prelaunch-loop-closure）
 * 兼容旧链接 ?tab=xxx → 定位对应分类卡。
 */
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getDashboard, type DashboardData } from '@/api/dashboard'
import PageContainer from '@/components/layout/PageContainer.vue'
import FeedbackView from '@/views/admin/FeedbackView.vue'
import ApplyReviewView from '@/views/admin/ApplyReviewView.vue'
import { Document, ChatLineSquare } from '@element-plus/icons-vue'

// ===== 待办数（来自 dashboard，加载失败静默不影响切换） =====
const todo = ref<DashboardData | null>(null)
onMounted(async () => {
  try { todo.value = await getDashboard('week') } catch { todo.value = null }
})

// ===== 唯一一级导航：分类卡（带待办数徽标，点击切换当前视图） =====
const sections = [
  {
    key: 'feedback',
    label: '反馈',
    badge: () => (todo.value?.pendingFeedbackCount ?? 0),
    icon: Document,
  },
  {
    key: 'review',
    label: '评价',
    badge: () => 0,
    icon: ChatLineSquare,
  },
]
const KEYS = ['feedback', 'review']
const route = useRoute()

function resolveKey(q: unknown): string {
  // 兼容历史链接：apply / apply-feedback 均已并入「反馈」卡（UGC 申请审核全链路下线）
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
    <template v-if="activeKey === 'feedback'">
      <div class="block-title">反馈</div>
      <FeedbackView />
    </template>
    <ApplyReviewView v-else />
  </PageContainer>
</template>

<style scoped>
/* ===== 分类卡（唯一一级导航） ===== */
/* 分类卡样式统一在 shared.css（.sec-grid/.sec-card），保证三页大小 UI 一致 */

/* 区块标题：与全站分区卡标题一致的品牌竖条（原 ApplyFeedbackView 中转文件收敛至此） */
.block-title {
  display: flex;
  align-items: center;
  margin: var(--space-3) 0 var(--space-2);
  font-size: var(--font-sm);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  padding-left: var(--space-3);
  position: relative;
}
.block-title:first-child { margin-top: 0; }
.block-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 3px;
  bottom: 3px;
  width: 3px;
  border-radius: var(--radius-xs);
  background: var(--color-primary);
}
</style>
