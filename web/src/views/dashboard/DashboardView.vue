<script setup lang="ts">
import { useAdminStore } from '@/stores/adminStore'
import { usePageStore } from '@/stores/pageStore'
import canteenIcon from '@/static/icon/canteen.svg'
import shopIcon from '@/static/icon/shop.svg'
import foodIcon from '@/static/icon/Food.svg'
import commentIcon from '@/static/icon/comment.svg'
import groupIcon from '@/static/icon/group.svg'
import chartBarIcon from '@/static/icon/chart-bar.svg'

const store = useAdminStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '数据概览' }] })

function getUserName(userId: number | bigint): string {
  const u = store.users.find(u => Number(u.id) === Number(userId))
  return u?.nickname || u?.username || `用户${userId}`
}
function getDishName(dishId: number | bigint): string {
  const d = store.dishes.find(d => Number(d.id) === Number(dishId))
  return d?.name || `菜品${dishId}`
}
</script>

<template>
  <div class="dashboard">
    <!-- KPI 卡片 -->
    <div class="kpi-grid">
      <div class="kpi-card kpi-canteen">
        <div class="kpi-top">
          <span class="kpi-num">{{ store.stats.totalCanteens }}</span>
          <img :src="canteenIcon" class="kpi-icon" />
        </div>
        <span class="kpi-label">食堂</span>
      </div>
      <div class="kpi-card kpi-stall">
        <div class="kpi-top">
          <span class="kpi-num">{{ store.stats.totalStalls }}</span>
          <img :src="shopIcon" class="kpi-icon" />
        </div>
        <span class="kpi-label">档口</span>
      </div>
      <div class="kpi-card kpi-dish">
        <div class="kpi-top">
          <span class="kpi-num">{{ store.stats.totalDishes }}</span>
          <img :src="foodIcon" class="kpi-icon" />
        </div>
        <span class="kpi-label">菜品</span>
      </div>
      <div class="kpi-card kpi-review">
        <div class="kpi-top">
          <span class="kpi-num">{{ store.stats.totalReviews }}</span>
          <img :src="commentIcon" class="kpi-icon" />
        </div>
        <span class="kpi-label">评论</span>
      </div>
      <div class="kpi-card kpi-user">
        <div class="kpi-top">
          <span class="kpi-num">{{ store.stats.totalUsers }}</span>
          <img :src="groupIcon" class="kpi-icon" />
        </div>
        <span class="kpi-label">用户</span>
      </div>
    </div>

    <!-- 下排 -->
    <div class="dash-bottom">
      <!-- 运营概览 -->
      <div class="dash-card">
        <div class="dash-card-header">
          <div class="dash-card-title">
            <img :src="chartBarIcon" class="title-icon" />
            <span>运营概览</span>
          </div>
        </div>
        <div class="dash-card-body">
          <div class="bar-item">
            <div class="bar-label"><span>食堂</span><span>{{ store.activeCanteens.length }} / {{ store.canteens.length }}</span></div>
            <div class="bar-track"><div class="bar-fill" :style="{ width: store.canteens.length ? (store.activeCanteens.length / store.canteens.length * 100) + '%' : '0%' }"></div></div>
          </div>
          <div class="bar-item">
            <div class="bar-label"><span>档口</span><span>{{ store.activeStalls.length }} / {{ store.stalls.length }}</span></div>
            <div class="bar-track"><div class="bar-fill bar-fill-stall" :style="{ width: store.stalls.length ? (store.activeStalls.length / store.stalls.length * 100) + '%' : '0%' }"></div></div>
          </div>
          <div class="bar-item">
            <div class="bar-label"><span>菜品</span><span>{{ store.activeDishes.length }} / {{ store.dishes.length }}</span></div>
            <div class="bar-track"><div class="bar-fill bar-fill-dish" :style="{ width: store.dishes.length ? (store.activeDishes.length / store.dishes.length * 100) + '%' : '0%' }"></div></div>
          </div>
          <div class="bar-item">
            <div class="bar-label"><span>用户</span><span>{{ store.users.filter(u => u.status === 'active').length }} / {{ store.users.length }}</span></div>
            <div class="bar-track"><div class="bar-fill bar-fill-user" :style="{ width: store.users.length ? (store.users.filter(u => u.status === 'active').length / store.users.length * 100) + '%' : '0%' }"></div></div>
          </div>
        </div>
      </div>

      <!-- 最新评论 -->
      <div class="dash-card">
        <div class="dash-card-header">
          <div class="dash-card-title">
            <img :src="commentIcon" class="title-icon" />
            <span>最新评论</span>
          </div>
        </div>
        <div class="dash-card-body">
          <div v-for="r in store.reviews.slice(-3).reverse()" :key="Number(r.id)" class="comment-item">
            <div class="comment-head">
              <span class="comment-user">{{ getUserName(r.user_id) }}</span>
              <span class="comment-score">{{ r.rating }}<span class="comment-score-unit">分</span></span>
            </div>
            <p class="comment-text">{{ r.content }}</p>
            <span class="comment-meta">{{ getDishName(r.dish_id) }} · {{ r.created_at.toLocaleDateString('zh-CN') }}</span>
          </div>
          <div v-if="!store.reviews.length" class="comment-empty">暂无评论</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1200px;
}

/* ===== KPI 卡片 ===== */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.kpi-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
}
.kpi-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.kpi-icon {
  width: 22px;
  height: 22px;
  opacity: .5;
}
.kpi-num {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
}
.kpi-label {
  font-size: 13px;
  color: var(--text-muted);
}
.kpi-canteen .kpi-num { color: var(--color-primary); }
.kpi-canteen .kpi-icon { filter: brightness(0) saturate(100%) invert(27%) sepia(35%) saturate(1252%) hue-rotate(345deg) brightness(90%) contrast(85%); }
.kpi-stall .kpi-num { color: var(--color-success); }
.kpi-stall .kpi-icon { filter: brightness(0) saturate(100%) invert(52%) sepia(68%) saturate(430%) hue-rotate(92deg) brightness(93%) contrast(89%); }
.kpi-dish .kpi-num { color: var(--color-warning); }
.kpi-dish .kpi-icon { filter: brightness(0) saturate(100%) invert(68%) sepia(79%) saturate(444%) hue-rotate(358deg) brightness(98%) contrast(94%); }
.kpi-review .kpi-num { color: #1976d2; }
.kpi-review .kpi-icon { filter: brightness(0) saturate(100%) invert(31%) sepia(98%) saturate(1042%) hue-rotate(200deg) brightness(97%) contrast(89%); }
.kpi-user .kpi-num { color: #7b1fa2; }
.kpi-user .kpi-icon { filter: brightness(0) saturate(100%) invert(22%) sepia(98%) saturate(2299%) hue-rotate(270deg) brightness(91%) contrast(97%); }

/* ===== 底部 ===== */
.dash-bottom {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.dash-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,.06);
  overflow: hidden;
}
.dash-card-header {
  padding: 16px 24px;
  border-bottom: 1px solid #f0f0f0;
}
.dash-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.title-icon {
  width: 16px;
  height: 16px;
  display: block;
  filter: brightness(0) saturate(100%) invert(27%) sepia(35%) saturate(1252%) hue-rotate(345deg) brightness(90%) contrast(85%);
}
.dash-card-body {
  padding: 12px 24px;
}

/* 进度条 */
.bar-item {
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}
.bar-item:last-child {
  border-bottom: none;
}
.bar-label {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.bar-label span:last-child {
  font-weight: 600;
  color: var(--text-primary);
}
.bar-track {
  height: 6px;
  background: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--color-primary);
  transition: width .4s ease;
}
.bar-fill-stall { background: var(--color-success); }
.bar-fill-dish  { background: var(--color-warning); }
.bar-fill-user  { background: #1976d2; }

/* 评论 */
.comment-item {
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}
.comment-item:last-child {
  border-bottom: none;
}
.comment-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.comment-user {
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}
.comment-score {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-star);
}
.comment-score-unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-light);
  margin-left: 1px;
}
.comment-text {
  margin: 4px 0;
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.5;
}
.comment-meta {
  font-size: 12px;
  color: var(--text-light);
}
.comment-empty {
  text-align: center;
  color: var(--text-light);
  padding: 24px 0;
  font-size: 14px;
}
</style>
