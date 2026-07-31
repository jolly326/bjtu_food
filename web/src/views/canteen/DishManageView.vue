<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { usePageStore } from '@/stores/pageStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import EntityImage from '@/components/EntityImage.vue'
import { Food, Plus, Star } from '@element-plus/icons-vue'

const router = useRouter()
const store = useAdminStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '菜品管理' }], showSearch: true, searchPlaceholder: '搜索菜品名称...' })

const loading = ref(false)
const error = ref('')
const statusFilter = ref<string>('')
const typeFilter = ref<string>('')

const allRows = computed(() => store.dishes)
const rows = computed(() => {
  let list = allRows.value
  if (statusFilter.value) list = list.filter(r => r.status === statusFilter.value)
  if (typeFilter.value === 'discount') list = list.filter(r => !!r.promoPrice)
  else if (typeFilter.value === 'normal') list = list.filter(r => !r.promoPrice)
  const q = page.searchQuery.trim().toLowerCase()
  if (q) list = list.filter(r => (r.name || '').toLowerCase().includes(q))
  return list
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '在售', value: 'active' },
  { label: '已下架', value: 'inactive' },
]
const typeOptions = [
  { label: '全部类型', value: '' },
  { label: '折扣菜品', value: 'discount' },
  { label: '常规菜品', value: 'normal' },
]

function dishImage(row: any): string {
  return (row.image || '').split('|||')[0] || ''
}
function stallName(stallId: number | bigint): string {
  const s = store.stalls.find(s => Number(s.id) === Number(stallId))
  return s?.name || `档口${stallId}`
}
function goEdit(row: any) {
  const s = store.stalls.find(s => Number(s.id) === Number(row.stall_id))
  const canteenId = s ? Number(s.canteen_id) : undefined
  if (canteenId === undefined) return
  router.push({ name: 'dishDetail', params: { canteenId, stallId: Number(row.stall_id), dishId: Number(row.id) } })
}

onMounted(() => {})
</script>

<template>
  <PageContainer>
    <PageHeader title="菜品管理" :count="rows.length">
      <template #actions>
        <button class="btn-primary" v-press @click="router.push('/dashboard/canteens')">
          <el-icon class="btn-plus-icon"><Plus /></el-icon>去档口添加
        </button>
      </template>
    </PageHeader>

    <FilterBar>
      <template #default>
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
        <FilterSelect v-model="typeFilter" label="类型" :options="typeOptions" :width="150" />
      </template>
    </FilterBar>

    <!-- 三态 -->
    <div v-if="loading" class="state-box"><span class="spin" />加载中…</div>
    <div v-else-if="error" class="state-box state-err">{{ error }}</div>
    <div v-else-if="!rows.length" class="state-box">暂无菜品</div>

    <div v-else class="card-grid">
      <div v-for="(row, i) in rows" :key="Number(row.id)" class="dish-card" :style="{ animationDelay: (i % 12) * 30 + 'ms' }" v-press @click="goEdit(row)">
        <div class="dish-img-wrap">
          <img v-if="dishImage(row)" :src="dishImage(row)" :alt="row.name" />
          <div v-else class="dish-img-placeholder"><el-icon class="ph-svg"><Food /></el-icon></div>
          <span v-if="row.promoPrice" class="promo-flag">折扣</span>
        </div>
        <div class="dish-body">
          <h4 class="dish-title">{{ row.name }}</h4>
          <p class="dish-belong">{{ stallName(row.stall_id) }}</p>
          <div class="dish-price-row">
            <span v-if="row.promoPrice" class="promo">
              ¥{{ (row.promoPrice / 100).toFixed(2) }}
              <span v-if="row.originalPrice" class="origin">¥{{ (row.originalPrice / 100).toFixed(2) }}</span>
            </span>
            <span v-else class="price">¥{{ row.price }}</span>
          </div>
          <div class="dish-foot">
            <StatusTag :type="row.status === 'active' ? 'success' : 'danger'" :text="row.status === 'active' ? '在售' : '已下架'" />
            <span v-if="row.avg_rating" class="rating"><el-icon class="star"><Star /></el-icon>{{ Number(row.avg_rating).toFixed(1) }}</span>
          </div>
        </div>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: var(--space-4); }
.dish-card {
  background: var(--bg-card); border-radius: var(--radius-card); overflow: hidden;
  box-shadow: var(--shadow-card); border: 1px solid var(--border-light);
  cursor: pointer;
  transition: transform .2s var(--ease-out), box-shadow .2s var(--ease-out), border-color .2s var(--ease-out);
  animation: card-enter 0.3s var(--ease-out) both;
}
@media (hover: hover) {
  .dish-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); border-color: var(--color-primary); }
}
.dish-img-wrap { position: relative; width: 100%; aspect-ratio: 4 / 3; overflow: hidden; background: var(--bg-soft); display: flex; align-items: center; justify-content: center; }
.dish-img-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.dish-img-placeholder { opacity: .35; }
.ph-svg { width: 40px; height: 40px; }
.promo-flag { position: absolute; top: var(--space-2); left: var(--space-2); background: var(--color-error); color: var(--text-white); font-size: var(--font-xs); padding: 1px var(--space-2); border-radius: var(--radius-sm); }
.dish-body { padding: var(--space-4); }
.dish-title { margin: 0 0 var(--space-1); font-size: var(--font-lg); color: var(--text-primary); font-weight: var(--weight-semibold); }
.dish-belong { margin: 0 0 var(--space-2); font-size: var(--font-xs); color: var(--text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dish-price-row { margin-bottom: var(--space-3); }
.price { color: var(--color-price); font-weight: var(--weight-bold); }
.promo { color: var(--color-error); font-weight: var(--weight-bold); }
.origin { color: var(--text-light); text-decoration: line-through; font-size: var(--font-xs); margin-left: var(--space-1); }
.dish-foot { display: flex; align-items: center; justify-content: space-between; }
.rating { display: inline-flex; align-items: center; gap: 2px; font-size: var(--font-sm); color: var(--color-star); font-weight: var(--weight-medium); }
.star { width: 14px; height: 14px; }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }

.state-box { text-align: center; color: var(--text-light); padding: var(--space-10) var(--space-4); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); display: flex; align-items: center; justify-content: center; gap: var(--space-2); }
.state-err { color: var(--color-error); }
.spin { width: 16px; height: 16px; border: 2px solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin .7s linear infinite; }

@keyframes card-enter { from { opacity: 0; transform: scale(0.95) translateY(8px); } to { opacity: 1; transform: scale(1) translateY(0); } }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) {
  .dish-card { animation: none; }
  .spin { animation-duration: 1.4s; }
}
</style>
