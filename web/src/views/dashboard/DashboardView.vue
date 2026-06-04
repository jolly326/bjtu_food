<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { useAdminStore } from '@/stores/adminStore'
import { usePageStore } from '@/stores/pageStore'
import starIcon from '@/static/icon/yellow-star.svg'
import canteenIcon from '@/static/icon/canteen.svg'
import shopIcon from '@/static/icon/shop.svg'
import foodIcon from '@/static/icon/Food.svg'
import accountIcon from '@/static/icon/account.svg'
import chartBarIcon from '@/static/icon/chart-bar.svg'
import commentIcon from '@/static/icon/comment.svg'

const store = useAdminStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '数据概览' }] })

const trendRef = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null
const tabActive = ref<'canteen' | 'dish'>('canteen')
const trendMode = ref<'day' | 'week' | 'month'>('month')

function fmtDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
function getWeekKey(d: Date) {
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  const m = new Date(d)
  m.setDate(diff)
  return `${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, '0')}-${String(m.getDate()).padStart(2, '0')}`
}

const trendData = computed(() => {
  const map = new Map<string, number>()
  for (const r of store.reviews as any[]) {
    const d = r.created_at as Date
    const k = trendMode.value === 'day' ? fmtDate(d)
            : trendMode.value === 'week' ? getWeekKey(d)
            : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    map.set(k, (map.get(k) || 0) + 1)
  }
  const entries = Array.from(map).sort((a, b) => a[0].localeCompare(b[0]))
  return {
    labels: entries.map(e => {
      const s = e[0].slice(5)
      return trendMode.value === 'week' ? s.slice(0, 2) + '-' + s.slice(3) : s
    }),
    counts: entries.map(e => e[1]),
  }
})

const canteenRank = computed(() => {
  return store.canteens
    .map(c => {
      const stalls = store.stalls.filter(s => Number(s.canteen_id) === Number(c.id))
      const dishes = store.dishes.filter(d => stalls.some(s => Number(s.id) === Number(d.stall_id)))
      const rs = store.reviews.filter(r => dishes.some(d => Number(d.id) === Number(r.dish_id)))
      const avg = rs.length ? Math.round((rs.reduce((s, r) => s + r.rating, 0) / rs.length) * 10) / 10 : 0
      return { name: c.name, avg, stallCount: stalls.length, activeStalls: stalls.filter(s => s.status === 'active').length, status: c.status }
    })
    .sort((a, b) => b.avg - a.avg || b.stallCount - a.stallCount)
})

const topDishes = computed(() => {
  return store.dishes
    .map(d => {
      const stall = store.stalls.find(s => Number(s.id) === Number(d.stall_id))
      const canteen = stall ? store.canteens.find(c => Number(c.id) === Number(stall.canteen_id)) : null
      const rc = store.reviews.filter(r => Number(r.dish_id) === Number(d.id)).length
      return { name: d.name, rc, avg: d.avg_rating, loc: canteen?.name || '' }
    })
    .sort((a, b) => b.rc - a.rc || b.avg - a.avg)
    .slice(0, 3)
})

function getTrendOpt() {
  const max = Math.max(...trendData.value.counts, 1)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 44, right: 16, top: 6, bottom: 14 },
    xAxis: {
      type: 'category', data: trendData.value.labels,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#999', fontSize: 11 },
    },
    yAxis: {
      type: 'value', splitLine: { lineStyle: { color: '#f0f0f0' } },
      axisLabel: { color: '#999', fontSize: 11 },
      min: 0, max: max + 1,
    },
    series: [{
      type: 'bar', data: trendData.value.counts, barWidth: '50%',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#e74c1f' }, { offset: 1, color: '#ffb088' },
        ]),
        borderRadius: [4, 4, 0, 0],
      },
    }],
  }
}

function initTrend() {
  if (!trendRef.value || !trendData.value.labels.length) return
  trendChart = echarts.init(trendRef.value!)
  trendChart.setOption(getTrendOpt())
}

function resize() { trendChart?.resize() }
function cleanup() { trendChart?.dispose(); trendChart = null }

watch(trendMode, () => {
  if (trendChart) {
    trendChart.setOption(getTrendOpt(), true)
  } else {
    nextTick(() => initTrend())
  }
})

onMounted(async () => {
  await nextTick()
  setTimeout(() => { initTrend(); window.addEventListener('resize', resize) }, 80)
})
onUnmounted(() => { window.removeEventListener('resize', resize); cleanup() })

function uname(id: number | bigint) {
  const u = store.users.find(u => Number(u.id) === Number(id))
  return u?.nickname || u?.username || `用户${id}`
}
function dname(id: number | bigint) {
  const d = store.dishes.find(d => Number(d.id) === Number(id))
  return d?.name || `菜品${id}`
}
</script>

<template>
  <div class="dash">

    <!-- 顶栏 -->
    <div class="topbar">
      <div class="topbar-l">
        <div class="topbar-title">数据概览</div>
        <div class="topbar-meta">{{ store.stats.totalCanteens }} 个食堂 · {{ store.stats.totalStalls }} 个档口 · {{ store.stats.totalDishes }} 道菜品</div>
      </div>
      <div class="topbar-r">
        <div class="tb-stat">
          <span class="tb-val">{{ store.reviews.length ? (store.reviews.reduce((s, r) => s + r.rating, 0) / store.reviews.length).toFixed(1) : '0.0' }}</span>
          <span class="tb-lbl">综合评分</span>
        </div>
        <div class="tb-stat">
          <span class="tb-val">{{ store.stats.totalReviews }}</span>
          <span class="tb-lbl">评论</span>
        </div>
        <div class="tb-stat">
          <span class="tb-val">{{ store.users.filter(u => u.role === 'user' && u.status === 'active').length }}</span>
          <span class="tb-lbl">活跃学生</span>
        </div>
      </div>
    </div>

    <!-- 指标行 -->
    <div class="kpis">
      <div class="kpi" v-for="k in [
        { i: canteenIcon, n: store.stats.totalCanteens, l: '食堂', a: `${store.activeCanteens.length} 活跃` },
        { i: shopIcon, n: store.stats.totalStalls, l: '档口', a: `${store.activeStalls.length} 活跃` },
        { i: foodIcon, n: store.stats.totalDishes, l: '菜品', a: `${store.activeDishes.length} 上架` },
        { i: accountIcon, n: store.stats.totalUsers - 1, l: '学生', a: `${store.users.filter(u => u.status === 'active').length} 在线` },
      ]" :key="k.l">
        <img :src="k.i" class="kpi-icon" />
        <div class="kpi-num">{{ k.n }}</div>
        <div class="kpi-info">
          <div class="kpi-lbl">{{ k.l }}</div>
          <div class="kpi-sub">{{ k.a }}</div>
        </div>
      </div>
    </div>

    <!-- 主区域 -->
    <div class="main">
      <!-- 评论趋势 -->
      <div class="card">
        <div class="card-hd">
          <span class="card-tt">
            <img :src="chartBarIcon" class="h-icon" />
            评论趋势
          </span>
          <div class="tabs">
            <button class="tab" :class="{ 'tab-on': trendMode === 'day' }" @click="trendMode = 'day'">按天</button>
            <button class="tab" :class="{ 'tab-on': trendMode === 'week' }" @click="trendMode = 'week'">按周</button>
            <button class="tab" :class="{ 'tab-on': trendMode === 'month' }" @click="trendMode = 'month'">按月</button>
          </div>
        </div>
        <div ref="trendRef" class="card-body card-body-chart">
          <div v-if="!trendData.labels.length" class="empty-msg">暂无数据</div>
        </div>
      </div>

      <!-- 右侧 Tab -->
      <div class="card">
        <div class="card-hd">
          <div class="tabs">
            <button class="tab" :class="{ 'tab-on': tabActive === 'canteen' }" @click="tabActive = 'canteen'">食堂排行</button>
            <button class="tab" :class="{ 'tab-on': tabActive === 'dish' }" @click="tabActive = 'dish'">热门菜品</button>
          </div>
        </div>
        <div class="card-body card-body-list">
          <div v-if="tabActive === 'canteen'">
            <div v-if="canteenRank.length" class="clist">
              <div v-for="(c, i) in canteenRank" :key="c.name" class="citem">
                <span class="cnum" :class="'cnum-' + (i + 1)">{{ i + 1 }}</span>
                <div class="cinfo">
                  <span class="cinfo-n">{{ c.name }}</span>
                  <span class="cinfo-s">{{ c.activeStalls }}/{{ c.stallCount }} 档口</span>
                </div>
                <div class="cright">
                  <span class="cstar" v-if="c.avg">
                    <img :src="starIcon" class="star-svg" />{{ c.avg }}
                  </span>
                  <span class="cstar cstar-na" v-else>—</span>
                </div>
              </div>
            </div>
            <div v-else class="empty-msg" style="position:static;padding:16px">暂无食堂</div>
          </div>
          <div v-if="tabActive === 'dish'">
            <div v-if="topDishes.length" class="dlist">
              <div v-for="(d, i) in topDishes" :key="d.name" class="ditem">
                <span class="dbadge" :class="'dbadge-' + (i + 1)">{{ i + 1 }}</span>
                <div class="dinfo">
                  <span class="dinfo-n">{{ d.name }}</span>
                  <span class="dinfo-l">{{ d.loc }}</span>
                </div>
                <div class="dmeta">
                  <span class="dstar">
                    <img :src="starIcon" class="star-svg" />{{ d.avg }}
                  </span>
                  <span class="dcnt">{{ d.rc }} 评</span>
                </div>
              </div>
            </div>
            <div v-else class="empty-msg" style="position:static;padding:16px">暂无菜品</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 最新反馈 -->
    <div class="card">
      <div class="card-hd">
        <span class="card-tt">
          <img :src="commentIcon" class="h-icon" />
          最新反馈
        </span>
        <span class="card-tag">{{ store.reviews.length }} 条</span>
      </div>
      <div class="card-body" style="padding:4px 20px 8px">
        <div v-if="store.reviews.length" class="rlist">
          <div v-for="r in store.reviews.slice(-3).reverse()" :key="Number(r.id)" class="ritem">
            <span class="rav">{{ uname(r.user_id)[0] }}</span>
            <div class="rbody">
              <div class="rhead">
                <span class="rname">{{ uname(r.user_id) }}</span>
                <span class="rstars">
                  <span v-for="s in 5" :key="s" class="rstar" :class="{ 'rstar-on': s <= r.rating }"></span>
                </span>
              </div>
              <p class="rtext">{{ r.content }}</p>
              <div class="rmeta">
                <span>{{ dname(r.dish_id) }}</span>
                <span class="rdot">·</span>
                <span>{{ r.created_at.toLocaleDateString('zh-CN') }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-msg" style="position:static;padding:16px">暂无评论</div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.dash {font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif; }

/* ===== 顶栏 ===== */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  background: var(--color-primary);
  border-radius: 10px;
  margin-bottom: 16px;
}
.topbar-title { font-size: 18px; font-weight: 700; color: #fff; letter-spacing: .5px; }
.topbar-meta { font-size: 12px; color: rgba(255,255,255,.5); margin-top: 3px; }
.topbar-r { display: flex; gap: 28px; }
.tb-stat { text-align: center; }
.tb-val { display: block; font-size: 22px; font-weight: 700; color: #fff; line-height: 1.2; }
.tb-lbl { font-size: 11px; color: rgba(255,255,255,.5); margin-top: 2px; }

/* ===== 指标行 ===== */
.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.kpi {
  background: #fff;
  border-radius: 8px;
  padding: 14px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 1px 3px rgba(0,0,0,.03);
}
.kpi-icon { width: 22px; height: 22px; opacity: .5; flex-shrink: 0; }
.kpi-num { font-size: 24px; font-weight: 700; color: var(--text-primary); line-height: 1; min-width: 30px; }
.kpi-lbl { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.kpi-sub { font-size: 11px; color: var(--text-light); margin-top: 2px; }

/* ===== 主布局 ===== */
.main {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

/* ===== 卡片 ===== */
.card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,.04);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.card-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}
.card-tt { font-size: 14px; font-weight: 600; color: var(--text-primary); display: flex; align-items: center; gap: 6px; }
.h-icon { width: 16px; height: 16px;flex-shrink: 0; filter: brightness(0); }
.card-tag { font-size: 11px; color: var(--text-light); background: #f5f5f5; padding: 2px 10px; border-radius: 8px; }
.card-body { padding: 6px; }
.card-body-chart { flex: 1; display: flex; align-items: center; justify-content: center; padding: 8px 0; position: relative; min-height: 140px; }
.card-body-list { padding: 4px 16px 8px; }

/* ===== 趋势图 ===== */
.empty-msg {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  color: var(--text-light); font-size: 13px;
  background: rgba(255,255,255,.7);
  pointer-events: none;
}

/* ===== Tab ===== */
.tabs { display: flex; }
.tab {
  padding: 5px 16px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid #e8e8e8;
  background: #fafafa;
  color: var(--text-muted);
  cursor: pointer;
  transition: all .15s;
  line-height: 1.4;
}
.tab:first-child { border-radius: 6px 0 0 6px; }
.tab:last-child { border-radius: 0 6px 6px 0; }
.tab + .tab { border-left: none; }
.tab-on {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}

/* ===== 食堂排行 ===== */
.clist { display: flex; flex-direction: column; }
.citem {
  display: flex; align-items: center; justify-content: space-between;
  padding: 9px 0; border-bottom: 1px solid #f5f5f5;
}
.citem:last-child { border-bottom: none; }
.cnum {
  width: 22px; height: 22px; border-radius: 5px;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; flex-shrink: 0;
  background: #f5f5f5; color: var(--text-muted);
}
.cnum-1 { background: linear-gradient(135deg, #ffd700, #ffb300); color: #fff; }
.cnum-2 { background: linear-gradient(135deg, #e8e8e8, #d4d4d4); color: #666; }
.cnum-3 { background: #e8a87c; color: #fff; }
.cinfo { flex: 1; margin-left: 10px; min-width: 0; }
.cinfo-n { font-size: 14px; font-weight: 600; color: var(--text-primary); display: block; }
.cinfo-s { font-size: 11px; color: var(--text-light); margin-top: 1px; }
.cright { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.star-svg { width: 12px; height: 12px; display: inline-block; vertical-align: middle; margin-right: 2px; }
.cstar { font-size: 13px; font-weight: 700; color: var(--color-star); display: inline-flex; align-items: center; }
.cstar-na { color: var(--text-light); }
/* ===== 热门菜品 ===== */
.dlist { display: flex; flex-direction: column; }
.ditem {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 0; border-bottom: 1px solid #f5f5f5;
}
.ditem:last-child { border-bottom: none; }
.dbadge {
  width: 22px; height: 22px; border-radius: 5px;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700; flex-shrink: 0;
  background: #f5f5f5; color: var(--text-muted);
}
.dbadge-1 { background: linear-gradient(135deg, #ffd700, #ffb300); color: #fff; }
.dbadge-2 { background: linear-gradient(135deg, #e8e8e8, #d4d4d4); color: #666; }
.dbadge-3 { background: #f5f5f5; color: var(--text-muted); }
.dinfo { flex: 1; min-width: 0; }
.dinfo-n { font-size: 14px; font-weight: 600; color: var(--text-primary); display: block; }
.dinfo-l { font-size: 11px; color: var(--text-light); margin-top: 1px; }
.dmeta { text-align: right; flex-shrink: 0; }
.dstar { font-size: 13px; font-weight: 700; color: var(--color-star); display: inline-flex; align-items: center; }
.dcnt { font-size: 11px; color: var(--text-muted); margin-top: 2px; display: block; text-align: right; }

/* ===== 反馈列表 ===== */
.rlist { display: flex; flex-direction: column; }
.ritem {
  display: flex; align-items: flex-start; gap: 10px;
  padding: 9px 0; border-bottom: 1px solid #f5f5f5;
}
.ritem:last-child { border-bottom: none; }
.rav {
  width: 28px; height: 28px; border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; flex-shrink: 0; margin-top: 2px;
}
.rbody { flex: 1; min-width: 0; }
.rhead { display: flex; align-items: center; justify-content: space-between; margin-bottom: 3px; }
.rname { font-size: 13px; font-weight: 600; color: var(--text-primary); }
.rstars { display: flex; gap: 2px; }
.rstar {
  width: 12px; height: 12px; background: #e8e8e8;
  clip-path: polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%);
}
.rstar-on { background: var(--color-star); }
.rtext { margin: 3px 0 4px; font-size: 13px; color: var(--text-secondary); line-height: 1.5; }
.rmeta { font-size: 11px; color: var(--text-light); display: flex; align-items: center; gap: 4px; }
.rdot { color: #ddd; }
</style>
