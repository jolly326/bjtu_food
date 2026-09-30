<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard } from '@/api/dashboard'
import type { DashboardVO } from '@/types/common'
import StateBox from '@/components/StateBox.vue'

const router = useRouter()
const data = ref<DashboardVO | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    data.value = await getDashboard()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)

function pct(n: number, d: number): string {
  return d > 0 ? `${Math.round((n / d) * 100)}%` : '—'
}
function go(path: string): void {
  router.push(path)
}

function groups(d: DashboardVO) {
  return [
    {
      title: '待办',
      items: [
        { label: '待处理反馈', value: d.pendingFeedbackCount, to: '/feedbacks?status=pending' },
        { label: '待处理举报', value: d.pendingReportCount, to: '/reports?status=pending' },
        { label: '待处理纠错', value: d.pendingCorrectionCount, to: '/corrections?status=pending' },
      ],
    },
    {
      title: '菜品主数据健康度',
      items: [
        { label: '在售菜品', value: d.dishOnSaleCount, to: '/dishes' },
        { label: '有图率', value: pct(d.dishWithImageCount, d.dishOnSaleCount), to: '/dishes' },
        { label: '有评价率', value: pct(d.dishWithReviewCount, d.dishOnSaleCount), to: '/dishes' },
      ],
    },
    {
      title: '用户与内容概况',
      items: [
        { label: '用户总数', value: d.userTotal, to: '/users' },
        { label: '今日新增', value: d.newUserToday, to: '/users' },
        { label: '近7日活跃', value: d.activeUser7d, to: '/users' },
        { label: '评价总数', value: d.reviewTotal, to: '/reviews' },
        { label: '全站均分', value: d.avgRating ?? '—', to: '/reviews' },
      ],
    },
  ]
}
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>运营看板</h2>
    </div>

    <StateBox v-if="loading" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" />
    <template v-else-if="data">
      <section v-for="g in groups(data)" :key="g.title" class="card stat-group">
        <h3 class="group-title">{{ g.title }}</h3>
        <div class="stat-grid">
          <button
            v-for="it in g.items"
            :key="it.label"
            class="stat-card"
            type="button"
            v-press
            @click="go(it.to)"
          >
            <div class="stat-value">{{ it.value }}</div>
            <div class="stat-label">{{ it.label }}</div>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.stat-group {
  padding: var(--space-5);
  margin-bottom: var(--space-5);
}
.group-title {
  margin: 0 0 var(--space-4);
  font-size: var(--font-base);
  color: var(--text-secondary);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--space-4);
}
.stat-card {
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  padding: var(--space-5);
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.stat-value {
  font-size: var(--font-3xl, 28px);
  font-weight: var(--weight-bold, 700);
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
.stat-label {
  font-size: var(--font-sm);
  color: var(--text-muted);
}
</style>
