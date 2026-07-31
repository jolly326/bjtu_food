<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { usePageStore } from '@/stores/pageStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import StatusTag from '@/components/StatusTag.vue'
import { Location, Food, Plus } from '@element-plus/icons-vue'

const router = useRouter()
const store = useAdminStore()
const toast = useToastStore()
const page = usePageStore()

page.setPage({ breadcrumbs: [{ label: '食堂管理' }], searchPlaceholder: '搜索食堂名称或位置...' })

const showModal = ref(false)
const statusFilter = ref<string>('')

const canteenStats = computed(() => ({
  total: store.canteens.length,
  totalStalls: store.stalls.length,
  totalDishes: store.dishes.length,
}))

const filtered = computed(() => {
  let list = store.canteens
  if (statusFilter.value) list = list.filter(c => (c.status || 'active') === statusFilter.value)
  const q = page.searchQuery.trim().toLowerCase()
  if (q) list = list.filter(c => c.name.toLowerCase().includes(q) || (c.location || '').toLowerCase().includes(q))
  return list
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '营业中', value: 'active' },
  { label: '已关闭', value: 'inactive' },
]

const form = ref({ name: '', location: '', description: '', image: '', sort_order: 0, status: 'active' as 'active' | 'inactive' })
const formErrors = ref<Record<string, string>>({})

function getFirstImage(img: string): string {
  return img.split('|||')[0] || img
}

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.name.trim()) errs.name = '食堂名称不能为空'
  if (!form.value.location.trim()) errs.location = '位置不能为空'
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

function openAdd() {
  form.value = { name: '', location: '', description: '', image: '', sort_order: 0, status: 'active' }
  formErrors.value = {}
  showModal.value = true
}

function handleSubmit() {
  if (!validate()) return
  store.addCanteen({ ...form.value })
  toast.success('食堂已添加')
  showModal.value = false
}

function enterCanteen(id: number) { router.push(`/dashboard/canteens/${id}`) }
</script>

<template>
  <PageContainer>
    <PageHeader title="食堂管理" :count="filtered.length">
      <template #actions>
        <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增食堂</button>
      </template>
    </PageHeader>

    <FilterBar>
      <template #default>
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
      </template>
    </FilterBar>

    <div v-if="!filtered.length" class="empty-card">暂无匹配的食堂</div>

    <div v-else class="card-grid">
      <div v-for="(c, i) in filtered" :key="Number(c.id)" class="pk-card" :style="{ animationDelay: (i % 12) * 30 + 'ms' }" v-press @click="enterCanteen(Number(c.id))">
        <div class="pk-img-wrap">
          <img v-if="c.image" :src="getFirstImage(c.image)" :alt="c.name" />
          <div v-else class="pk-emoji"><el-icon :size="40" class="pk-emoji-img"><Food /></el-icon></div>
        </div>
        <div class="pk-body">
          <div class="pk-title-row">
            <h3 class="pk-title">{{ c.name }}</h3>
            <StatusTag :type="(c.status || 'active') === 'active' ? 'success' : 'gray'" :text="(c.status || 'active') === 'active' ? '营业中' : '已关闭'" />
          </div>
          <p class="pk-loc"><el-icon class="icon-inline"><Location /></el-icon> {{ c.location }}</p>
          <p class="pk-desc">{{ c.description }}</p>
          <div class="pk-stats">
            <span>档口 {{ store.stalls.filter(s => Number(s.canteen_id) === Number(c.id)).length }} 个</span>
            <span>菜品 {{ store.dishes.filter(d => store.stalls.some(s => Number(s.canteen_id) === Number(c.id) && Number(s.id) === Number(d.stall_id))).length }} 道</span>
          </div>
        </div>
      </div>
    </div>

    <FormDialog :show="showModal" title="新增食堂" :width="580" confirm-text="保存" @close="showModal = false" @confirm="handleSubmit">
      <div class="modal-form">
        <div class="modal-row">
          <div class="field flex-1"><label>食堂名称 <span class="required">*</span></label><input v-model="form.name" placeholder="输入食堂名称" /><p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p></div>
          <div class="field flex-1"><label>位置 <span class="required">*</span></label><input v-model="form.location" placeholder="输入食堂位置" /><p v-if="formErrors.location" class="field-error">{{ formErrors.location }}</p></div>
        </div>
        <div class="field"><label>描述</label><textarea v-model="form.description" rows="2" placeholder="输入食堂描述"></textarea></div>
        <div class="field">
          <label>图片 <span class="text-muted">（至多 3 张）</span></label>
          <ImageUpload v-model="form.image" :max="3" />
        </div>
      </div>
    </FormDialog>
  </PageContainer>
</template>

<style scoped>


.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: var(--space-4); }
.pk-card { position: relative; background: var(--bg-card); border-radius: var(--radius-card); overflow: hidden; box-shadow: var(--shadow-card); cursor: pointer; transition: transform .2s var(--ease-out), box-shadow .2s var(--ease-out), border-color .2s var(--ease-out); border: 1px solid var(--border-color); animation: card-enter 0.3s var(--ease-out) both; }
@media (hover: hover) {
  .pk-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); border-color: var(--color-primary); }
}
.pk-img-wrap { width: 100%; height: 180px; overflow: hidden; background: var(--bg-soft); display: flex; align-items: center; justify-content: center; }
.pk-img-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pk-emoji { width: 50px; height: 50px; opacity: .35; font-size: 40px; }
.pk-body { padding: var(--space-4); }
.pk-title-row { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
.pk-title { margin: 0; font-size: var(--font-xl); color: var(--text-primary); font-weight: var(--weight-semibold); }
.pk-loc { margin: var(--space-1) 0 var(--space-2); font-size: var(--font-sm); color: var(--text-secondary); }
.pk-desc { margin: 0 0 var(--space-3); font-size: var(--font-sm); color: var(--text-secondary); line-height: var(--leading-base); overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.pk-stats { display: flex; gap: var(--space-3); font-size: var(--font-xs); color: var(--text-light); }
.empty-card { grid-column: 1 / -1; text-align: center; color: var(--text-light); font-size: var(--font-base); padding: var(--space-10) 0; }

.modal-form { display: flex; flex-direction: column; gap: var(--space-3); }
.modal-row { display: flex; gap: var(--space-3); }
.flex-1 { flex: 1; }
.text-muted { color: var(--text-light); font-size: var(--font-xs); font-weight: var(--weight-regular); }
.icon-inline { width: 1em; height: 1em; vertical-align: -0.125em; display: inline; }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }

@keyframes card-enter { from { opacity: 0; transform: scale(0.95) translateY(8px); } to { opacity: 1; transform: scale(1) translateY(0); } }
@media (prefers-reduced-motion: reduce) {
  .pk-card { animation: none; }
}
</style>
