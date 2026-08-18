<script setup lang="ts">
/**
 * DashboardView：工作台（运营全貌 + 待办处理）。
 * 一次请求 /admin/dashboard 返回：待办数、待办明细（申请/动态/反馈各 5 条）、
 * 8 项规模指标、近期操作（日志 10 条）。点击直达对应管理 tab。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePageStore } from '@/stores/pageStore'
import { getDashboard, type DashboardData } from '@/api/dashboard'
import PageContainer from '@/components/layout/PageContainer.vue'
import {
  PriceTag, ChatDotRound, ChatLineSquare, House, Food, User, Document,
  Refresh, ArrowRight, Check,
} from '@element-plus/icons-vue'

const router = useRouter()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '工作台' }] })

const loading = ref(true)
const failed = ref(false)
const data = ref<DashboardData | null>(null)

async function loadData() {
  loading.value = true
  failed.value = false
  try {
    data.value = await getDashboard('week')
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

function navTo(path: string) { router.push(path) }

// ===== 待办卡（点击直达对应 tab） =====
const todoCards = computed(() => [
  { key: 'apply', label: '待审核申请', count: data.value?.pendingApplyCount ?? 0, icon: Document, to: '/dashboard/audit?tab=feedback&section=apply' },
  { key: 'moment', label: '待审核动态', count: data.value?.pendingMomentCount ?? 0, icon: ChatDotRound, to: '/dashboard/audit?tab=moment' },
  { key: 'feedback', label: '待处理反馈', count: data.value?.pendingFeedbackCount ?? 0, icon: ChatLineSquare, to: '/dashboard/audit?tab=feedback&section=feedback' },
])

// ===== 8 项规模指标 =====
const metrics = computed(() => [
  { key: 'canteen', label: '食堂', value: data.value?.totalCanteenCount ?? 0, icon: House, to: '/dashboard/content?tab=canteen' },
  { key: 'stall', label: '档口', value: data.value?.totalStallCount ?? 0, icon: Food, to: '/dashboard/content?tab=stall' },
  { key: 'dish', label: '菜品', value: data.value?.totalDishCount ?? 0, icon: PriceTag, to: '/dashboard/content?tab=dish' },
  { key: 'user', label: '学生', value: data.value?.totalUserCount ?? 0, icon: User, to: '/dashboard/system?tab=user' },
  { key: 'moment', label: '动态', value: data.value?.totalMomentCount ?? 0, icon: ChatDotRound, to: '/dashboard/audit?tab=moment' },
  { key: 'review', label: '评价', value: data.value?.totalReviewCount ?? 0, icon: ChatLineSquare, to: '/dashboard/audit?tab=review' },
  { key: 'apply', label: '申请', value: data.value?.totalApplyCount ?? 0, icon: Document, to: '/dashboard/audit?tab=feedback&section=apply' },
  { key: 'feedback', label: '反馈', value: data.value?.totalFeedbackCount ?? 0, icon: ChatLineSquare, to: '/dashboard/audit?tab=feedback&section=feedback' },
])

onMounted(loadData)
</script>

<template>
  <PageContainer>
    <div v-if="loading" class="state-box"><span class="spin" />加载中…</div>
    <div v-else-if="failed || !data" class="state-box state-err">工作台加载失败
      <button class="btn-secondary inline" v-press type="button" @click="loadData">重试</button>
    </div>

    <template v-else>
      <!-- ===== 待办处理 ===== -->
      <section class="block-section">
        <div class="section-head">
          <h3 class="section-title">待办处理</h3>
          <button class="btn-secondary" v-press type="button" @click="loadData">
            <el-icon class="ref-ico"><Refresh /></el-icon>刷新
          </button>
        </div>
        <div class="todo-grid">
          <article
            v-for="t in todoCards"
            :key="t.key"
            class="todo-card"
            :class="{ done: t.count === 0 }"
            v-press
            role="button"
            tabindex="0"
            @click="navTo(t.to)"
            @keyup.enter="navTo(t.to)"
          >
            <div class="todo-icon"><el-icon><component :is="t.icon" /></el-icon></div>
            <div class="todo-body">
              <div class="todo-value">{{ t.count }}</div>
              <div class="todo-label">{{ t.label }}</div>
            </div>
            <div class="todo-action">
              <template v-if="t.count > 0">去处理<el-icon class="todo-arrow"><ArrowRight /></el-icon></template>
              <span v-else class="todo-clear"><el-icon class="todo-arrow"><Check /></el-icon>已清空</span>
            </div>
          </article>
        </div>
      </section>

      <!-- ===== 数据总览 ===== -->
      <section class="block-section">
        <h3 class="section-title">数据总览</h3>
        <div class="metric-grid">
          <article
            v-for="m in metrics"
            :key="m.key"
            class="metric-card"
            v-press
            role="button"
            tabindex="0"
            @click="navTo(m.to)"
            @keyup.enter="navTo(m.to)"
          >
            <div class="metric-icon"><el-icon><component :is="m.icon" /></el-icon></div>
            <div class="metric-body">
              <div class="metric-value">{{ m.value }}</div>
              <div class="metric-label">{{ m.label }}</div>
            </div>
          </article>
        </div>
      </section>
    </template>
  </PageContainer>
</template>

<style scoped>
.block-section { margin-bottom: var(--space-8); }
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}
.section-title {
  display: flex;
  align-items: center;
  margin: 0;
  font-size: var(--font-base);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  /* 与全站分区卡标题一致的品牌竖条 */
  padding-left: var(--space-3);
  position: relative;
}
.section-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 3px;
  border-radius: var(--radius-xs);
  background: var(--color-primary);
}
.btn-secondary.inline { margin-left: var(--space-3); }
.ref-ico { width: 14px; height: 14px; }

.state-box { text-align: center; color: var(--text-light); padding: var(--space-10) var(--space-4); display: flex; align-items: center; justify-content: center; gap: var(--space-2); }
.state-err { color: var(--color-error); }
.spin { width: 16px; height: 16px; border: 2px solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spin { animation-duration: 1.4s; } }

/* ===== 待办卡 ===== */
.todo-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-5); }
.todo-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  cursor: pointer;
  user-select: none;
  transition: transform 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out), border-color 0.2s var(--ease-out);
}
@media (hover: hover) { .todo-card:hover { box-shadow: var(--card-hover-shadow); border-color: var(--border-strong); } }
.todo-card:active { transform: scale(var(--press-scale)); }
.todo-card:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
.todo-icon { display: inline-flex; align-items: center; justify-content: center; width: 48px; height: 48px; border-radius: var(--radius-md); flex-shrink: 0; color: var(--color-primary); background: var(--color-primary-bg); }
.todo-icon .el-icon { font-size: 24px; }
.todo-card.done .todo-icon { color: var(--color-success); background: var(--color-success-bg); }
.todo-body { flex: 1; min-width: 0; }
.todo-value { font-size: var(--font-4xl); font-weight: var(--weight-bold); line-height: 1.1; color: var(--text-primary); letter-spacing: var(--tracking-tight); font-variant-numeric: tabular-nums; }
.todo-label { margin-top: var(--space-1); font-size: var(--font-md); font-weight: var(--weight-medium); color: var(--text-secondary); }
.todo-action { display: flex; align-items: center; gap: var(--space-1); font-size: var(--font-sm); color: var(--color-primary); font-weight: var(--weight-medium); flex-shrink: 0; }
.todo-arrow { width: 14px; height: 14px; }
.todo-clear { display: inline-flex; align-items: center; gap: 2px; color: var(--color-success); }

/* ===== 指标卡 ===== */
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-5); }
.metric-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  cursor: pointer;
  user-select: none;
  transition: transform 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out), border-color 0.2s var(--ease-out);
}
@media (hover: hover) { .metric-card:hover { box-shadow: var(--card-hover-shadow); border-color: var(--border-strong); } }
.metric-card:active { transform: scale(var(--press-scale)); }
.metric-card:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
.metric-icon { display: inline-flex; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: var(--radius-md); flex-shrink: 0; color: var(--color-primary); background: var(--color-primary-bg); }
.metric-icon .el-icon { font-size: 20px; }
.metric-body { min-width: 0; }
.metric-value { font-size: var(--font-2xl); font-weight: var(--weight-bold); line-height: 1.1; color: var(--text-primary); letter-spacing: var(--tracking-tight); font-variant-numeric: tabular-nums; }
.metric-label { margin-top: 2px; font-size: var(--font-xs); color: var(--text-muted); }

/* ===== 响应式 ===== */
/* 1280–960px：8 指标卡 4→3 列平滑过渡，避免 4 列偏挤 */
@media (max-width: 1279px) {
  .metric-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 959px) {
  .todo-grid { grid-template-columns: 1fr; }
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
