<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { usePageStore } from '@/stores/pageStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import PageSection from '@/components/layout/PageSection.vue'
import StatCard from '@/components/common/StatCard.vue'
import FormDialog from '@/components/FormDialog.vue'
import EntityImage from '@/components/EntityImage.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import DataTable from '@/components/DataTable.vue'
import DishFormDialog from '@/components/DishFormDialog.vue'
import { Food, Plus } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const page = usePageStore()

function goBack() {
  router.push('/dashboard/canteens')
}

const canteenId = computed(() => Number(route.params.canteenId))
const canteen = computed(() => store.canteens.find(c => Number(c.id) === canteenId.value))

const activeTab = ref(0)

const editing = ref(false)
const canteenForm = ref({ name: '', location: '', description: '', image: '' })
const originalCanteen = ref<Record<string, any>>({})
const canteenFormErrors = ref<Record<string, string>>({})

const showImageModal = ref(false)

function parseImages(imgStr: string): string[] {
  return (imgStr || '').split('|||').filter(Boolean)
}

const imageList = computed(() => parseImages(canteenForm.value.image))
const firstImage = computed(() => imageList.value[0] || '')
const imageCount = computed(() => imageList.value.length)

const stalls = computed(() => store.stalls
  .filter(s => Number(s.canteen_id) === canteenId.value)
  // 按创建顺序展示（无需手动排序）
  .sort((a, b) => Number(a.id) - Number(b.id)))
const dishCount = computed(() => {
  const ids = stalls.value.map(s => Number(s.id))
  return store.dishes.filter(d => ids.includes(Number(d.stall_id))).length
})

function openImageModal() {
  showImageModal.value = true
}
function saveImageModal() {
  if (canteen.value) {
    store.updateCanteen(Number(canteen.value.id), { image: canteenForm.value.image })
    toast.success('图片已更新')
    originalCanteen.value.image = canteenForm.value.image
  }
  showImageModal.value = false
}
function closeImageModal() { showImageModal.value = false }

watch(canteen, (c) => {
  page.setPage({
    breadcrumbs: [
      { label: '食堂管理', path: '/dashboard/canteens' },
      { label: c?.name || '加载中' },
    ],
    searchPlaceholder: '搜索档口名称...',
  })
  if (c) {
    canteenForm.value = { name: c.name, location: c.location || '', description: c.description || '', image: c.image || '' }
    originalCanteen.value = { ...canteenForm.value }
  }
}, { immediate: true })

function toggleEdit() {
  editing.value = true
  activeTab.value = 0
  if (canteen.value) {
    canteenForm.value = { name: canteen.value.name, location: canteen.value.location || '', description: canteen.value.description || '', image: canteen.value.image || '' }
    canteenFormErrors.value = {}
  }
}

function confirmEdit() {
  const errs: Record<string, string> = {}
  if (!canteenForm.value.name.trim()) errs.name = '食堂名称不能为空'
  if (!canteenForm.value.location.trim()) errs.location = '位置不能为空'
  canteenFormErrors.value = errs
  if (Object.keys(errs).length) return
  if (canteen.value) {
    store.updateCanteen(Number(canteen.value.id), { ...canteenForm.value })
    toast.success('食堂信息已更新')
    originalCanteen.value = { ...canteenForm.value }
  }
  canteenFormErrors.value = {}
  editing.value = false
}

function cancelEdit() {
  if (canteen.value) {
    canteenForm.value = { name: canteen.value.name, location: canteen.value.location || '', description: canteen.value.description || '', image: canteen.value.image || '' }
  }
  canteenFormErrors.value = {}
  editing.value = false
}

async function deleteCanteen() {
  if (!canteen.value) return
  if (!await confirm.confirm('确定删除该食堂？此操作不可撤销')) return
  try {
    await store.deleteCanteen(Number(canteen.value.id))
    toast.success('食堂已删除')
    router.push('/dashboard/canteens')
  } catch (err: any) {
    toast.error(err.message || '食堂删除失败')
  }
}

const showModal = ref(false)
const filtered = computed(() => {
  const q = page.searchQuery.trim().toLowerCase()
  if (!q) return stalls.value
  return stalls.value.filter(s => s.name.toLowerCase().includes(q) || (s.description || '').toLowerCase().includes(q))
})

const form = ref({ name: '', description: '', image: '', status: 'active' as 'active' | 'inactive' })
const formErrors = ref<Record<string, string>>({})

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.name.trim()) errs.name = '档口名称不能为空'
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

const editingStallId = ref<number | null>(null)

function openAdd() { editingStallId.value = null; form.value = { name: '', description: '', image: '', status: 'active' }; formErrors.value = {}; showModal.value = true }

function openEditStall(id: number) {
  const s = stalls.value.find(x => Number(x.id) === id)
  if (!s) return
  editingStallId.value = id
  form.value = { name: s.name, description: s.description || '', image: s.image || '', status: s.status as 'active' | 'inactive' }
  formErrors.value = {}
  showModal.value = true
}

function handleSubmit() {
  if (!validate()) return
  try {
    if (editingStallId.value !== null) {
      store.updateStall(editingStallId.value, { ...form.value })
      toast.success('档口已更新')
    } else {
      store.addStall({ canteen_id: canteenId.value as unknown as bigint, ...form.value, sort_order: 0, avg_rating: 0 })
      toast.success('档口已添加')
    }
    showModal.value = false
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}

async function handleDeleteStall(id: number) {
  const s = stalls.value.find(x => Number(x.id) === id)
  if (!await confirm.confirm(`确定删除档口「${s?.name || ''}」？`)) return
  try {
    await store.deleteStall(id)
    toast.success('档口已删除')
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}

function enterStall(id: number) { router.push(`/dashboard/canteens/${canteenId.value}/stalls/${id}`) }

// ===== 菜品管理（食堂详情页内统一管理，弹窗编辑，不跳档口详情） =====
const dishStallFilter = ref<string>('')
const dishModal = ref(false)
const editingDishId = ref<number | null>(null)

const dishStallOptions = computed(() => stalls.value.map(s => ({ label: s.name, value: Number(s.id) })))
const canteenDishes = computed(() => {
  let list = store.dishes.filter(d => stalls.value.some(s => Number(s.id) === Number(d.stall_id)))
  if (dishStallFilter.value) list = list.filter(d => String(d.stall_id) === dishStallFilter.value)
  const q = page.searchQuery.trim().toLowerCase()
  if (q) list = list.filter(d => (d.name || '').toLowerCase().includes(q))
  return list
})

function openAddDish() {
  editingDishId.value = null
  dishModal.value = true
}
function openEditDish(id: number) {
  editingDishId.value = id
  dishModal.value = true
}
function onDishSaved() {
  editingDishId.value = null
}
async function handleDeleteDish(d: any) {
  if (!await confirm.confirm(`确定删除菜品「${d.name}」？删除后不可恢复。`)) return
  try {
    await store.deleteDish(Number(d.id))
    toast.success('菜品已删除')
  } catch (e: any) {
    toast.error(e.message || '删除失败')
  }
}
function stallNameOf(stallId: number | bigint): string {
  const s = store.stalls.find(x => Number(x.id) === Number(stallId))
  return s?.name || `档口${stallId}`
}
</script>

<template>
  <PageContainer>
    <PageHeader
      :back="true"
      :title="canteen?.name || '加载中'"
      :subtitle="canteen?.location || canteen?.description || ''"
      @back="goBack"
    >
      <template #extra>
        <div class="header-thumb" @click="openImageModal">
          <img v-if="firstImage" :src="firstImage" alt="" />
          <el-icon v-else :size="20" class="thumb-ph"><Food /></el-icon>
        </div>
      </template>
    </PageHeader>

    <div class="detail-tabs">
      <div class="tab-item" :class="{ active: activeTab === 0 }" @click="activeTab = 0">详情概览</div>
      <div class="tab-count-item" :class="{ active: activeTab === 1 }" @click="activeTab = 1">
        档口管理
      </div>
      <div class="tab-count-item" :class="{ active: activeTab === 2 }" @click="activeTab = 2">
        菜品管理
      </div>
    </div>

    <!-- Tab 1: 详情概览 -->
    <template v-if="activeTab === 0">
      <PageSection title="基本信息">
        <template #header-extra>
          <div class="panel-actions">
            <template v-if="!editing">
              <button class="btn-primary btn-sm" v-press @click="toggleEdit">编辑</button>
              <button class="btn-danger btn-sm" v-press @click="deleteCanteen">删除</button>
            </template>
            <template v-else>
              <button class="btn-cancel btn-sm" v-press @click="cancelEdit">取消</button>
              <button class="btn-primary btn-sm" v-press @click="confirmEdit">保存</button>
            </template>
          </div>
        </template>
        <div class="detail-body">
          <EntityImage :image-url="firstImage" :image-count="imageCount" @click="openImageModal" />
          <div class="detail-fields">
            <div class="detail-row">
              <span class="detail-label">名称</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ canteenForm.name }}</span>
                <input v-else v-model="canteenForm.name" class="form-input" :class="{ 'input-error': canteenFormErrors.name }" placeholder="食堂名称" />
                <p v-if="editing && canteenFormErrors.name" class="field-error">{{ canteenFormErrors.name }}</p>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">位置</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ canteenForm.location }}</span>
                <input v-else v-model="canteenForm.location" class="form-input" :class="{ 'input-error': canteenFormErrors.location }" placeholder="食堂位置" />
                <p v-if="editing && canteenFormErrors.location" class="field-error">{{ canteenFormErrors.location }}</p>
              </div>
            </div>
            <div class="detail-row detail-row-desc">
              <span class="detail-label">描述</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value text-desc">{{ canteenForm.description || '-' }}</span>
                <textarea v-else v-model="canteenForm.description" class="form-textarea" rows="2" placeholder="食堂描述"></textarea>
              </div>
            </div>
          </div>
        </div>
      </PageSection>

      <PageSection title="数据统计">
        <div class="stats-row">
          <StatCard label="档口数" :value="stalls.length" sub="个档口" />
          <StatCard label="菜品数" :value="dishCount" sub="道菜品" />
        </div>
      </PageSection>
    </template>

    <!-- Tab 2: 档口管理 -->
    <template v-if="activeTab === 1">
      <PageSection>
        <template #header-extra>
          <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增档口</button>
        </template>
        <DataTable
          :columns="[
            { prop: 'image', label: '图片', width: '72px' },
            { prop: 'name', label: '档口名称' },
            { prop: 'desc', label: '描述', ellipsis: true },
            { prop: 'stats', label: '菜品数', width: '90px', align: 'center' },
            { prop: 'status', label: '状态', width: '90px', align: 'center' },
            { prop: 'actions', label: '操作', width: '180px', align: 'center' },
          ]"
          :rows="filtered"
          empty-text="暂无档口">
          <template #cell-image="{ row }">
            <img v-if="row.image" :src="row.image.split('|||')[0]" :alt="row.name" class="cell-thumb" />
            <span v-else class="cell-thumb cell-thumb-empty">图</span>
          </template>
          <template #cell-name="{ row }"><span class="cell-title">{{ row.name }}</span></template>
          <template #cell-desc="{ row }"><span class="cell-sub">{{ row.description || '—' }}</span></template>
          <template #cell-stats="{ row }">{{ store.dishes.filter(d => Number(d.stall_id) === Number(row.id)).length }}</template>
          <template #cell-status="{ row }">
            <StatusTag :type="(row.status || 'active') === 'active' ? 'success' : 'gray'" :text="(row.status || 'active') === 'active' ? '营业中' : '已关闭'" />
          </template>
          <template #actions="{ row }">
            <button class="link" v-press @click="enterStall(Number(row.id))">菜品</button>
            <button class="link" v-press @click="openEditStall(Number(row.id))">编辑</button>
            <button class="link danger" v-press @click="handleDeleteStall(Number(row.id))">删除</button>
          </template>
        </DataTable>
      </PageSection>
    </template>

    <!-- Tab 3: 菜品管理（食堂详情页内统一管理，弹窗编辑） -->
    <template v-if="activeTab === 2">
      <PageSection>
        <template #header-extra>
          <div class="dish-bar">
            <FilterSelect
              v-model="dishStallFilter"
              label="档口"
              :options="[{ label: '全部档口', value: '' }, ...dishStallOptions.map(s => ({ label: s.label, value: String(s.value) }))]"
              :width="180"
              :clearable="false"
            />
            <button class="btn-primary" v-press @click="openAddDish"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增菜品</button>
          </div>
        </template>
        <DataTable
          :columns="[
            { prop: 'image', label: '图片', width: '72px' },
            { prop: 'name', label: '菜品名称' },
            { prop: 'stall', label: '所属档口' },
            { prop: 'price', label: '价格', width: '110px', align: 'center' },
            { prop: 'status', label: '状态', width: '90px', align: 'center' },
            { prop: 'actions', label: '操作', width: '140px', align: 'center' },
          ]"
          :rows="canteenDishes"
          empty-text="暂无菜品">
          <template #cell-image="{ row }">
            <img v-if="row.image" :src="row.image.split('|||')[0]" :alt="row.name" class="cell-thumb" />
            <span v-else class="cell-thumb cell-thumb-empty">图</span>
          </template>
          <template #cell-name="{ row }"><span class="cell-title">{{ row.name }}</span></template>
          <template #cell-stall="{ row }"><span class="cell-sub">{{ stallNameOf(row.stall_id) }}</span></template>
          <template #cell-price="{ row }"><span class="price-cell">{{ row.promoPrice ? `¥${row.promoPrice.toFixed(2)}` : `¥${row.price}` }}</span></template>
          <template #cell-status="{ row }">
            <StatusTag :type="row.status === 'active' ? 'success' : 'danger'" :text="row.status === 'active' ? '在售' : '已下架'" />
          </template>
          <template #actions="{ row }">
            <button class="link" v-press @click="openEditDish(Number(row.id))">编辑</button>
            <button class="link danger" v-press @click="handleDeleteDish(row)">删除</button>
          </template>
        </DataTable>
      </PageSection>
    </template>

    <FormDialog :show="showImageModal" title="图片管理" :width="480" confirm-text="保存" @close="closeImageModal" @confirm="saveImageModal">
      <ImageUpload v-model="canteenForm.image" :max="3" />
    </FormDialog>

    <FormDialog :show="showModal" :title="editingStallId !== null ? '编辑档口' : '新增档口'" confirm-text="保存" @close="showModal = false" :on-confirm="handleSubmit">
      <div class="modal-form">
        <div class="field"><label>名称 <span class="required">*</span></label><input v-model="form.name" /><p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p></div>
        <div class="field"><label>描述</label><textarea v-model="form.description" rows="3"></textarea></div>
        <div class="field">
          <label>图片</label>
          <ImageUpload v-model="form.image" :max="3" />
        </div>
      </div>
    </FormDialog>

    <DishFormDialog
      :show="dishModal"
      :editing-id="editingDishId"
      :default-stall-id="dishStallFilter ? Number(dishStallFilter) : null"
      :stall-options="dishStallOptions"
      @close="dishModal = false"
      @saved="onDishSaved"
    />
  </PageContainer>
</template>

<style scoped>
/* ===== 头部缩略图（PageHeader 右侧 #extra） ===== */
.header-thumb {
  display: flex; width: 60px; height: 60px; border-radius: var(--radius-md); overflow: hidden;
  align-items: center; justify-content: center; background: var(--bg-soft);
  border: 1px solid var(--border-light); flex-shrink: 0; cursor: pointer;
  transition: transform 160ms var(--ease-out);
}
.header-thumb:hover { transform: scale(1.03); }
.header-thumb:active { transform: scale(var(--press-scale)); }
.header-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.header-thumb .thumb-ph { width: 20px; height: 20px; object-fit: contain; display: block; margin: var(--space-3); opacity: .35; }

/* ===== 详情 Tab 切换条 ===== */
.detail-tabs { display: flex; gap: 0; margin-bottom: var(--space-5); border-bottom: 1px solid var(--border-light); }
.detail-tabs .tab-item,
.detail-tabs .tab-count-item {
  padding: var(--space-2) var(--space-5); font-size: var(--font-sm); color: var(--text-secondary); cursor: pointer;
  border-bottom: 2px solid transparent; transition: color 0.2s var(--ease-out), border-color 0.2s var(--ease-out); user-select: none;
  display: flex; align-items: center; gap: var(--space-1); margin-bottom: -1px;
}
.detail-tabs .tab-item:hover,
.detail-tabs .tab-count-item:hover { color: var(--color-primary); }
.detail-tabs .tab-item.active,
.detail-tabs .tab-count-item.active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: var(--weight-semibold); }

.panel-actions { display: flex; gap: var(--space-2); }

/* ===== 基本信息 ===== */
.detail-body { display: flex; gap: var(--space-6); align-items: flex-start; }
.detail-fields { flex: 1; display: flex; flex-direction: column; gap: var(--space-3); }
.detail-row { display: flex; align-items: center; gap: var(--space-3); }
.detail-row-desc { align-items: flex-start; }
.detail-label { font-size: var(--font-sm); color: var(--text-muted); width: 48px; flex-shrink: 0; line-height: 28px; }
.detail-control { flex: 1; min-width: 0; }
.detail-value { font-size: var(--font-md); color: var(--text-primary); font-weight: var(--weight-medium); line-height: 28px; }
.detail-value.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); }
.form-input { padding: var(--space-2) var(--space-3); border: 1px solid var(--border-strong); border-radius: var(--radius); font-size: var(--font-base); font-weight: var(--weight-medium); color: var(--text-primary); outline: none; transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out); background: var(--bg-card); width: 100%; max-width: 320px; box-sizing: border-box; }
.form-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent); }
.form-textarea { padding: var(--space-2) var(--space-3); border: 1px solid var(--border-strong); border-radius: var(--radius); font-size: var(--font-base); color: var(--text-primary); outline: none; transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out); background: var(--bg-card); width: 100%; box-sizing: border-box; resize: vertical; min-height: 50px; }
.form-textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent); }

/* ===== 统计卡片（统一 StatCard 组件） ===== */
.stats-row { display: flex; gap: var(--space-4); }
.stats-row :deep(.stat-card) { flex: 1; min-width: 0; }

/* ===== 列表头 ===== */
.list-bar {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-4);
}
.list-bar h3 { margin: 0; font-size: var(--font-lg); color: var(--text-primary); font-weight: var(--weight-semibold); }

/* ===== 档口表格 ===== */
.cell-thumb { width: 52px; height: 40px; border-radius: var(--radius-sm); object-fit: cover; display: inline-block; vertical-align: middle; background: var(--bg-soft); }
.cell-thumb-empty { display: inline-flex; align-items: center; justify-content: center; font-size: var(--font-xs); color: var(--text-light); }
.cell-title { font-weight: var(--weight-semibold); color: var(--text-primary); }
.cell-sub { font-size: var(--font-sm); color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 320px; display: inline-block; vertical-align: middle; }
.dish-bar { display: flex; align-items: center; gap: var(--space-3); }
.price-cell { color: var(--color-price); font-weight: var(--weight-bold); }

/* ===== 全局覆盖（仅保留页面特有细微项，按钮/表单继承全局基线） ===== */
.input-error { border-color: var(--color-error) !important; }
.field-error { margin: var(--space-1) 0 0; font-size: var(--font-sm); color: var(--color-error); }
.required { color: var(--color-error); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
</style>
