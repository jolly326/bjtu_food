<script setup lang="ts">
/**
 * DashboardView：工作台（运营全貌 + 待办处理）。
 * 一次请求 /admin/dashboard 返回三块（§0.4.1 契约）：
 *   1) 待办处理：待处理反馈计数卡（点击直达反馈视图）；
 *   2) 数据总览：5 项规模指标卡；
 *   3) 待办明细（待处理反馈最近 5 条，点击直达该条反馈详情）+ 近期操作（日志最近 10 条）。
 * 本页不含 ECharts 图表看板；加载失败显示「工作台加载失败 + 重试」，各块空数据配空态（PR-03）。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard, type DashboardData } from '@/api/dashboard'
import PageContainer from '@/components/layout/PageContainer.vue'
import {
  PriceTag, ChatLineSquare, House, Food, User,
  Refresh, ArrowRight, Check, Clock, Bell,
} from '@element-plus/icons-vue'
import { feedbackTypeText, operationActionText, operationTargetLabel } from '@/constants'

const router = useRouter()

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

// ===== 待办卡（点击直达对应 tab）：仅保留「待处理反馈」一项（apply 已全链路下线，见 change prelaunch-loop-closure） =====
const todoCards = computed(() => [
  { key: 'feedback', label: '待处理反馈', count: data.value?.pendingFeedbackCount ?? 0, icon: ChatLineSquare, to: '/dashboard/audit?tab=feedback&section=feedback' },
])

// ===== 5 项规模指标（食堂 / 档口 / 菜品 / 学生 / 评价；UGC 申请与反馈已下线，不再作为指标，见 §0.4.1） =====
const metrics = computed(() => [
  // 食堂 / 档口已随菜品一起维护（project_spec §7.15），不再有独立入口，跳菜品列表
  { key: 'canteen', label: '食堂', value: data.value?.totalCanteenCount ?? 0, icon: House, to: '/dashboard/content?tab=dish' },
  { key: 'stall', label: '档口', value: data.value?.totalStallCount ?? 0, icon: Food, to: '/dashboard/content?tab=dish' },
  { key: 'dish', label: '菜品', value: data.value?.totalDishCount ?? 0, icon: PriceTag, to: '/dashboard/content?tab=dish' },
  { key: 'user', label: '学生', value: data.value?.totalUserCount ?? 0, icon: User, to: '/dashboard/system?tab=account' },
  { key: 'review', label: '评价', value: data.value?.totalReviewCount ?? 0, icon: ChatLineSquare, to: '/dashboard/audit?tab=review' },
])

// ===== 待办明细（§0.4.1：待处理反馈最近 5 条，按时间倒序）=====
// 点击直达该条反馈的详情抽屉：复用既有 /dashboard/audit 反馈视图，携带 fid 由 FeedbackView 自动打开
// （本页与反馈页均无独立详情路由，抽屉即详情；title 作为不在当前页时的检索兜底）。
const todoItems = computed(() => data.value?.pendingFeedbacks ?? [])
function openTodo(item: { id: number; title: string }) {
  router.push({
    path: '/dashboard/audit',
    query: { tab: 'feedback', section: 'feedback', fid: String(item.id), title: item.title },
  })
}

// ===== 近期操作（§0.4.1：操作日志最近 10 条）=====
// 动作 / 对象枚举文案统一走 constants（与操作日志页同源，杜绝裸英文枚举）。
const recentLogs = computed(() => data.value?.recentLogs ?? [])

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

      <!-- ===== 待办明细（§0.4.1 三块之一：待处理反馈最近 5 条，点击直达该条反馈详情） ===== -->
      <section class="block-section">
        <div class="section-head">
          <h3 class="section-title">待办明细</h3>
          <button v-if="todoItems.length" class="btn-secondary" v-press type="button" @click="navTo('/dashboard/audit?tab=feedback&section=feedback')">
            查看全部<el-icon class="ref-ico"><ArrowRight /></el-icon>
          </button>
        </div>
        <div class="list-card">
          <ul v-if="todoItems.length" class="todo-list">
            <li
              v-for="item in todoItems"
              :key="item.id"
              class="todo-row"
              v-press
              role="button"
              tabindex="0"
              @click="openTodo(item)"
              @keyup.enter="openTodo(item)"
            >
              <span class="todo-tag">{{ feedbackTypeText(item.type) }}</span>
              <span class="todo-text" :title="item.title">{{ item.title || '（无内容）' }}</span>
              <span class="todo-time">{{ item.time || '—' }}</span>
              <el-icon class="todo-go"><ArrowRight /></el-icon>
            </li>
          </ul>
          <!-- 空态（PR-03：入口必须配空态） -->
          <div v-else class="list-empty">
            <el-icon class="empty-ico"><Check /></el-icon>
            <span>暂无待处理反馈</span>
          </div>
        </div>
      </section>

      <!-- ===== 近期操作（§0.4.1 三块之一：操作日志最近 10 条） ===== -->
      <section class="block-section">
        <div class="section-head">
          <h3 class="section-title">近期操作</h3>
          <button v-if="recentLogs.length" class="btn-secondary" v-press type="button" @click="navTo('/dashboard/system?tab=log')">
            查看全部<el-icon class="ref-ico"><ArrowRight /></el-icon>
          </button>
        </div>
        <div class="list-card">
          <ul v-if="recentLogs.length" class="log-list">
            <li v-for="log in recentLogs" :key="log.id" class="log-row">
              <el-icon class="log-ico"><Clock /></el-icon>
              <span class="log-operator">{{ log.operator || '—' }}</span>
              <span class="log-action">{{ operationActionText(log.action) }}</span>
              <span class="log-target">{{ operationTargetLabel(log.target) }}</span>
              <span class="log-time">{{ log.time || '—' }}</span>
            </li>
          </ul>
          <div v-else class="list-empty">
            <el-icon class="empty-ico"><Bell /></el-icon>
            <span>暂无操作记录</span>
          </div>
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
.metric-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: var(--space-5); }
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

/* ===== 列表卡（待办明细 / 近期操作共用，同一设计语言：卡片容器 + 分隔线行） ===== */
.list-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.todo-list, .log-list { list-style: none; margin: 0; padding: 0; }

/* --- 待办明细行（整行可点，行尾箭头引导） --- */
.todo-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s var(--ease-out), transform 160ms var(--ease-out);
}
.todo-row:last-child { border-bottom: none; }
.todo-row:hover { background: var(--table-row-hover); }
.todo-row:active { transform: scale(var(--press-scale)); }
.todo-row:focus-visible { outline: 2px solid var(--color-primary); outline-offset: -2px; }
.todo-tag {
  flex-shrink: 0;
  padding: 1px var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-size: var(--font-xs);
  font-weight: var(--weight-medium);
}
.todo-text {
  flex: 1;
  min-width: 0;
  color: var(--text-primary);
  font-size: var(--font-base);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.todo-time { flex-shrink: 0; font-size: var(--font-sm); color: var(--text-muted); font-variant-numeric: tabular-nums; }
.todo-go { width: 14px; height: 14px; flex-shrink: 0; color: var(--text-light); transition: color 0.15s var(--ease-out); }
.todo-row:hover .todo-go { color: var(--color-primary); }

/* --- 近期操作行（只读流水，无点击语义） --- */
.log-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--border-light);
  font-size: var(--font-base);
}
.log-row:last-child { border-bottom: none; }
.log-ico { width: 14px; height: 14px; flex-shrink: 0; color: var(--text-light); }
.log-operator { flex-shrink: 0; color: var(--text-secondary); }
.log-action {
  flex-shrink: 0;
  padding: 1px var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  color: var(--text-secondary);
  font-size: var(--font-xs);
  font-weight: var(--weight-medium);
}
.log-target {
  flex: 1;
  min-width: 0;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.log-time { flex-shrink: 0; font-size: var(--font-sm); color: var(--text-muted); font-variant-numeric: tabular-nums; }

/* --- 空态（PR-03：入口配空态，不塌陷） --- */
.list-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-8) var(--space-4);
  color: var(--text-muted);
  font-size: var(--font-base);
}
.empty-ico { width: 18px; height: 18px; color: var(--color-success); }

/* 降低动效：按压缩放属「运动」，降级为无位移（保留 hover/背景等非运动反馈） */
@media (prefers-reduced-motion: reduce) {
  .todo-row, .todo-row:active, .metric-card, .metric-card:active, .todo-card, .todo-card:active { transition: none; transform: none; }
}

/* ===== 响应式 ===== */
/* 1280–960px：8 指标卡 4→3 列平滑过渡，避免 4 列偏挤 */
@media (max-width: 1279px) {
  .metric-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 959px) {
  .todo-grid { grid-template-columns: 1fr; }
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
  /* 窄屏：待办/日志行收起时间列，保住主体信息不挤压 */
  .todo-time, .log-time { display: none; }
}
</style>
