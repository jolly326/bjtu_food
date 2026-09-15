<script setup lang="ts">
/**
 * AuditManageView：内容审核聚合页。
 * 设计（去冗余）：顶部两张分类卡 = 唯一一级导航（带待办数），点击在本页切换下方列表，无重复 tabbar。
 * - 反馈 / 评价（UGC 申请审核已随 apply 全链路下线，见 change prelaunch-loop-closure）
 * 兼容旧链接 ?tab=xxx → 定位对应分类卡。
 *
 * 2026-09-15（本轮）：工作台下线，两个徽标数改为**各自域的列表接口 total**（pageSize=1 只取计数），
 * 不再依赖 /admin/dashboard（该接口随工作台一并下线）。
 */
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { listReviews } from '@/api/review'
import { listFeedbacks } from '@/api/feedback'
import { FEEDBACK_PENDING, SEC_REVIEW } from '@/constants'
import PageContainer from '@/components/layout/PageContainer.vue'
import FeedbackView from '@/views/audit/FeedbackView.vue'
import ReviewAuditView from '@/views/audit/ReviewAuditView.vue'
import { Document, ChatLineSquare } from '@element-plus/icons-vue'

// ===== 待办数（各自域接口 total；加载失败静默退化为 0，不阻塞切换） =====
/** 待处理反馈数：GET /admin/feedbacks?status=pending 的 total */
const pendingFeedbackCount = ref(0)
/** 评价待复核数：安检流水线 secState=review 的评价首页 total（WA-04，原硬编码 0 恒绿） */
const pendingReviewCount = ref(0)

async function loadBadges() {
  const [feedback, review] = await Promise.allSettled([
    listFeedbacks({ status: FEEDBACK_PENDING, page: 1, pageSize: 1 }),
    listReviews({ secState: SEC_REVIEW, page: 1, pageSize: 1 }),
  ])
  pendingFeedbackCount.value = feedback.status === 'fulfilled' ? feedback.value.total : 0
  pendingReviewCount.value = review.status === 'fulfilled' ? review.value.total : 0
}
onMounted(loadBadges)

// ===== 唯一一级导航：分类卡（带待办数徽标，点击切换当前视图） =====
const sections = [
  {
    key: 'feedback',
    label: '反馈',
    badge: () => pendingFeedbackCount.value,
    icon: Document,
  },
  {
    key: 'review',
    label: '评价',
    badge: () => pendingReviewCount.value,
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
    <!-- 区块标题与分类卡重复，已删除（仅保留分类卡作为当前区块指示） -->
    <FeedbackView v-if="activeKey === 'feedback'" />
    <ReviewAuditView v-else />
  </PageContainer>
</template>

<style scoped>
/* 分类卡样式统一在 shared.css（.sec-grid/.sec-card），保证三页大小 UI 一致 */
</style>
