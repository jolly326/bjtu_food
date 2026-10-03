<script setup lang="ts">
/**
 * D1 运营看板（**登录后首屏**；页面规格见 [运营看板.md](../../../docs/web/ui/运营看板.md) 与
 * [D1-运营看板.md](../../../docs/web/D-运营/D1-运营看板.md)）。
 *
 * <p>定位：**只读聚合页** —— 本页不含任何写操作，所有处置都在各自页面（评价 / 反馈 / 举报 / 纠错 / 主数据）。
 * 三区分区，每区 ≤4 项；待办区**必须含可点击的最近列表**（只有数字的话看板会退化成「数字墙」，
 * 管理员仍得逐个菜单去翻）。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard } from '@/api/dashboard'
import type { DashboardVO, RecentTodoVO } from '@/types/common'
import { isSessionInvalid } from '@/api/http'
import StateBox from '@/components/StateBox.vue'

const router = useRouter()
const data = ref<DashboardVO | null>(null)
const firstLoading = ref(true)
const error = ref<string | null>(null)
const sessionInvalid = ref(false)

async function load(): Promise<void> {
  firstLoading.value = true
  error.value = null
  sessionInvalid.value = false
  try {
    data.value = await getDashboard()
  } catch (e) {
    if (isSessionInvalid(e)) {
      sessionInvalid.value = true
    } else {
      error.value = e instanceof Error ? e.message : '加载失败'
    }
  } finally {
    firstLoading.value = false
  }
}

/** 待办项 → 处置页（`kind` 决定跳哪个页面） */
const KIND_META: Record<RecentTodoVO['kind'], { label: string; to: string }> = {
  feedback: { label: '意见反馈', to: '/feedbacks' },
  report: { label: '举报', to: '/reports' },
  correction: { label: '菜品纠错', to: '/corrections' },
}

function go(path: string): void {
  router.push(path)
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>运营看板</h2>
    </div>

    <!-- 四态：加载 / 会话失效 / 错误（带重试）/ 有数据 -->
    <StateBox v-if="firstLoading" status="loading" />
    <StateBox v-else-if="sessionInvalid" status="session" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="load" />
    <template v-else-if="data">
      <!-- ① 待办：三个计数 + **最近 5 条可点击**（看板的核心价值） -->
      <section class="card zone">
        <h3 class="zone-title">待办</h3>
        <div class="stat-grid">
          <button class="stat-card" type="button" v-press @click="go('/feedbacks')">
            <div class="stat-value">{{ data.todo.pendingFeedbackCount }}</div>
            <div class="stat-label">待处理意见反馈</div>
          </button>
          <button class="stat-card" type="button" v-press @click="go('/reports')">
            <div class="stat-value">{{ data.todo.pendingReportCount }}</div>
            <div class="stat-label">待处理举报</div>
          </button>
          <button class="stat-card" type="button" v-press @click="go('/corrections')">
            <div class="stat-value">{{ data.todo.pendingCorrectionCount }}</div>
            <div class="stat-label">待处理菜品纠错</div>
          </button>
        </div>

        <div class="recent">
          <div class="recent-title">最近待办</div>
          <StateBox v-if="!data.todo.recent.length" status="empty" message="今天没有待办" />
          <ul v-else class="recent-list">
            <li v-for="item in data.todo.recent" :key="`${item.kind}-${item.id}`">
              <button class="recent-item" type="button" @click="go(KIND_META[item.kind].to)">
                <span class="tag recent-kind">{{ KIND_META[item.kind].label }}</span>
                <span class="recent-text">{{ item.title }}</span>
                <span class="recent-time">{{ item.submittedAt }}</span>
              </button>
            </li>
          </ul>
        </div>
      </section>

      <!-- ② 主数据健康度：只收**当场能修**的项（不列允许为空的噪音项） -->
      <section class="card zone">
        <h3 class="zone-title">主数据健康度</h3>
        <div class="stat-grid">
          <button class="stat-card" type="button" v-press @click="go('/dishes')">
            <div class="stat-value">{{ data.health.dishesWithoutImage }}</div>
            <div class="stat-label">无图片的菜品</div>
          </button>
          <button class="stat-card" type="button" v-press @click="go('/dishes')">
            <div class="stat-value">{{ data.health.dishesWithoutStall }}</div>
            <div class="stat-label">未归入档口的菜品</div>
          </button>
          <button class="stat-card" type="button" v-press @click="go('/dishes')">
            <div class="stat-value">{{ data.health.dishesWithoutCategory }}</div>
            <div class="stat-label">分类为空的菜品</div>
          </button>
          <button class="stat-card" type="button" v-press @click="go('/stalls')">
            <div class="stat-value">{{ data.health.stallsWithoutDish }}</div>
            <div class="stat-label">空档口</div>
          </button>
        </div>
      </section>

      <!-- ③ 概况：只给规模、不给时间序列 -->
      <section class="card zone">
        <h3 class="zone-title">概况</h3>
        <div class="stat-grid">
          <button class="stat-card" type="button" v-press @click="go('/users')">
            <div class="stat-value">{{ data.overview.userCount }}</div>
            <div class="stat-label">用户总数（不含已注销）</div>
          </button>
          <button class="stat-card" type="button" v-press @click="go('/users')">
            <div class="stat-value">{{ data.overview.verifiedUserCount }}</div>
            <div class="stat-label">已认证用户</div>
          </button>
          <button class="stat-card" type="button" v-press @click="go('/dishes')">
            <div class="stat-value">{{ data.overview.onSaleDishCount }}</div>
            <div class="stat-label">在售菜品</div>
          </button>
          <button class="stat-card" type="button" v-press @click="go('/reviews')">
            <div class="stat-value">{{ data.overview.reviewCount }}</div>
            <div class="stat-label">评价总数（含已隐藏）</div>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.zone {
  padding: var(--space-5);
  margin-bottom: var(--space-5);
}
.zone-title {
  margin: 0 0 var(--space-4);
  font-size: var(--font-base);
  color: var(--text-secondary);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
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
  font-size: 28px;
  font-weight: var(--weight-bold, 700);
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}
.stat-label {
  font-size: var(--font-sm);
  color: var(--text-muted);
}
.recent {
  margin-top: var(--space-5);
  border-top: 1px solid var(--border-light);
  padding-top: var(--space-4);
}
.recent-title {
  font-size: var(--font-sm);
  color: var(--text-muted);
  margin-bottom: var(--space-2);
}
.recent-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.recent-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-2);
  background: none;
  border: none;
  border-bottom: 1px solid var(--border-soft);
  cursor: pointer;
  text-align: left;
}
.recent-item:hover {
  background: var(--bg-hover);
}
.recent-kind {
  flex: none;
  background: var(--bg-gray);
  color: var(--text-secondary);
  padding: 1px var(--space-2);
  border-radius: var(--radius-pill);
  font-size: var(--font-xs);
}
.recent-text {
  flex: 1;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-time {
  flex: none;
  color: var(--text-muted);
  font-size: var(--font-sm);
  font-variant-numeric: tabular-nums;
}
</style>
