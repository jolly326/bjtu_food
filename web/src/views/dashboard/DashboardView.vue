<script setup lang="ts">
/**
 * DashboardView：进后台第一眼引导页（任务③）。
 * - 顶部 PageHeader 大标题「概览」+ 副标题。
 * - 4 张指标卡：食堂总数 / 菜品总数 / 待审核数 / 待处理反馈数。
 *   · canteens / dishes 取自 useAdminStore（含各 store 自拉取，不阻塞）；
 *   · 待审核数 = 菜品 audit_status=pending 计数（本地计算，不新增 API）；
 *   · 待处理反馈数 = GET /admin/feedbacks?status=pending 的 total（用现有 listFeedbacks）。
 * - 常用操作快捷入口网格：点击 router.push 到对应页。
 * - 加载/空/错误态齐全。
 * - 严格遵循 §4 Apple Design：spring 入场、按下 scale(0.97)、backdrop-filter、reduced-motion 降级。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { usePageStore } from '@/stores/pageStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import { House, PriceTag, ChatDotRound, ChatLineSquare, ArrowRight, Loading } from '@element-plus/icons-vue'
import { listFeedbacks } from '@/api/feedback'

const router = useRouter()
const admin = useAdminStore()
const page = usePageStore()

page.setPage({ breadcrumbs: [{ label: '概览' }] })

// ===== 状态：三态（loading / error / ready） =====
const loading = ref(true)
const error = ref('')
const pendingFeedbackCount = ref(0)

// 指标卡数据（本地计算，不阻塞页面渲染）
const canteenCount = computed(() => admin.canteens.length)
const dishCount = computed(() => admin.dishes.length)
const pendingAuditCount = computed(
  () => admin.dishes.filter(d => d.audit_status === 'pending').length,
)

const metrics = computed(() => [
  { key: 'canteen', label: '食堂总数', value: canteenCount.value, icon: House, accent: 'canteen', to: '/dashboard/canteens' },
  { key: 'dish', label: '菜品总数', value: dishCount.value, icon: PriceTag, accent: 'dish', to: '/dashboard/dishes' },
  { key: 'audit', label: '待审核数', value: pendingAuditCount.value, icon: ChatDotRound, accent: 'audit', to: '/dashboard/reviews' },
  { key: 'feedback', label: '待处理反馈', value: pendingFeedbackCount.value, icon: ChatLineSquare, accent: 'feedback', to: '/dashboard/feedbacks' },
])

// ===== 常用操作快捷入口 =====
const shortcuts = [
  { label: '新增食堂', desc: '创建食堂与档口', to: '/dashboard/canteens', icon: House, primary: true },
  { label: '审核动态', desc: '处理待审社区动态', to: '/dashboard/moments', icon: ChatDotRound, primary: false },
  { label: '处理反馈', desc: '回复举报与建议', to: '/dashboard/feedbacks', icon: ChatLineSquare, primary: false },
  { label: '管理轮播', desc: '配置首页 Banner', to: '/dashboard/banners', icon: PriceTag, primary: false },
]

function navTo(path: string) {
  router.push(path)
}

// ===== 数据拉取（不阻塞页面，三态齐全） =====
async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const res = await listFeedbacks({ status: 'pending', pageSize: 1 })
    pendingFeedbackCount.value = res.total
    // canteens / dishes 由 useAdminStore 内部各 store 自拉取，此处无需重复请求
  } catch (e: any) {
    error.value = e?.message || '概览数据加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <PageContainer title="" padding>
    <PageHeader title="概览" subtitle="交大美食后台数据总览" />

    <!-- 加载态 -->
    <div v-if="loading" class="state-box dash-state">
      <el-icon class="spin"><Loading /></el-icon>
      <p>正在加载概览数据…</p>
    </div>

    <!-- 错误态 -->
    <div v-else-if="error" class="state-box dash-state state-err">
      <p>{{ error }}</p>
      <button class="btn-primary dash-retry" v-press type="button" @click="loadData">重新加载</button>
    </div>

    <!-- 就绪态 -->
    <template v-else>
      <!-- ===== 数据总览卡片区 ===== -->
      <section class="metric-grid">
        <article
          v-for="m in metrics"
          :key="m.key"
          class="metric-card"
          :class="`accent-${m.accent}`"
          v-press
          role="button"
          tabindex="0"
          @click="navTo(m.to)"
          @keyup.enter="navTo(m.to)"
        >
          <div class="metric-icon">
            <el-icon><component :is="m.icon" /></el-icon>
          </div>
          <div class="metric-body">
            <div class="metric-value">{{ m.value }}</div>
            <div class="metric-label">{{ m.label }}</div>
          </div>
        </article>
      </section>

      <!-- ===== 常用操作快捷入口 ===== -->
      <section class="shortcut-section">
        <h2 class="section-title">常用操作</h2>
        <div class="shortcut-grid">
          <article
            v-for="s in shortcuts"
            :key="s.label"
            class="shortcut-card"
            :class="{ 'is-primary': s.primary }"
            v-press
            role="button"
            tabindex="0"
            @click="navTo(s.to)"
            @keyup.enter="navTo(s.to)"
          >
            <div class="shortcut-icon">
              <el-icon><component :is="s.icon" /></el-icon>
            </div>
            <div class="shortcut-text">
              <div class="shortcut-label">{{ s.label }}</div>
              <div class="shortcut-desc">{{ s.desc }}</div>
            </div>
            <el-icon class="shortcut-arrow"><ArrowRight /></el-icon>
          </article>
        </div>
      </section>
    </template>
  </PageContainer>
</template>

<style scoped>
.dash-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-secondary);
}
.dash-state .spin {
  font-size: 28px;
  animation: dash-spin 0.9s linear infinite;
}
@keyframes dash-spin {
  to { transform: rotate(360deg); }
}
.dash-retry { margin-top: var(--space-2); }
@media (prefers-reduced-motion: reduce) {
  .dash-state .spin { animation: none; }
}

/* ===== 指标卡 ===== */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}
.metric-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  user-select: none;
  transition:
    transform 0.2s var(--ease-out),
    box-shadow 0.2s var(--ease-out),
    border-color 0.2s var(--ease-out);
  animation: metric-in 0.4s var(--ease-out) both;
}
.metric-card:nth-child(2) { animation-delay: 0.05s; }
.metric-card:nth-child(3) { animation-delay: 0.1s; }
.metric-card:nth-child(4) { animation-delay: 0.15s; }
@keyframes metric-in {
  from { opacity: 0; transform: scale(0.95) translateY(8px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
@media (prefers-reduced-motion: reduce) {
  .metric-card { animation: none; }
}
@media (hover: hover) {
  .metric-card:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-hover);
  }
}
.metric-card:active { transform: scale(var(--press-scale)); }
.metric-card:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  color: var(--color-primary);
  background: var(--color-primary-bg);
}
.metric-icon .el-icon { font-size: 24px; }
.accent-dish .metric-icon { color: var(--color-accent); background: var(--color-warning-soft); }
.accent-audit .metric-icon { color: var(--color-warning); background: var(--color-warning-soft); }
.accent-feedback .metric-icon { color: var(--color-primary-light); background: var(--color-primary-bg); }

.metric-body { min-width: 0; }
.metric-value {
  font-size: var(--font-3xl);
  font-weight: var(--weight-bold);
  line-height: 1.1;
  color: var(--text-primary);
  letter-spacing: var(--tracking-tight);
}
.metric-label {
  margin-top: var(--space-1);
  font-size: var(--font-sm);
  color: var(--text-muted);
}

/* ===== 常用操作 ===== */
.section-title {
  margin: 0 0 var(--space-4);
  font-size: var(--font-xl);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-4);
}
.shortcut-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-5);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  user-select: none;
  transition:
    transform 0.2s var(--ease-out),
    box-shadow 0.2s var(--ease-out),
    border-color 0.2s var(--ease-out);
}
@media (hover: hover) {
  .shortcut-card:hover {
    transform: translateY(-3px);
    box-shadow: var(--shadow-hover);
    border-color: color-mix(in srgb, var(--color-primary) 30%, transparent);
  }
}
.shortcut-card:active { transform: scale(var(--press-scale)); }
.shortcut-card:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }

.shortcut-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  color: var(--color-primary);
  background: var(--color-primary-bg);
}
.shortcut-icon .el-icon { font-size: 22px; }
.shortcut-card.is-primary .shortcut-icon {
  color: var(--text-white);
  background: var(--color-primary);
}
.shortcut-text { flex: 1; min-width: 0; }
.shortcut-label {
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.shortcut-desc {
  margin-top: 2px;
  font-size: var(--font-sm);
  color: var(--text-muted);
}
.shortcut-arrow {
  color: var(--text-light);
  font-size: 18px;
  flex-shrink: 0;
  transition: transform 0.2s var(--ease-out), color 0.2s var(--ease-out);
}
@media (hover: hover) {
  .shortcut-card:hover .shortcut-arrow {
    color: var(--color-primary);
    transform: translateX(4px);
  }
}

/* ===== 响应式 ===== */
@media (max-width: 959px) {
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 767px) {
  .metric-grid { grid-template-columns: 1fr; }
  .shortcut-grid { grid-template-columns: 1fr; }
}
</style>
