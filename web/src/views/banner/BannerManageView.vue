<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useBannerStore } from '@/stores/bannerStore'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { usePageStore } from '@/stores/pageStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import FilterBar from '@/components/layout/FilterBar.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import FormDialog from '@/components/FormDialog.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import StatusTag from '@/components/StatusTag.vue'
import { Plus, Picture, CaretTop, CaretBottom } from '@element-plus/icons-vue'

const store = useBannerStore()
const admin = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: 'Banner 管理' }], searchPlaceholder: '搜索 Banner 标题...' })

const showModal = ref(false)
const editingId = ref<number | null>(null)
const dishes = computed(() => admin.dishes)
const statusFilter = ref<string>('')

const TARGET_TYPES: { value: 'DISH' | 'URL' | 'NONE'; label: string; hint: string }[] = [
  { value: 'DISH', label: '跳转菜品', hint: '需选择菜品' },
  { value: 'URL', label: '跳转链接', hint: '需填写外部链接' },
  { value: 'NONE', label: '不跳转', hint: '纯展示' },
]

const form = ref({
  title: '', image: '', target_type: 'DISH' as 'DISH' | 'URL' | 'NONE',
  target_id: '' as string | number, target_url: '', sort_order: 1, status: 'active' as 'active' | 'inactive',
})
const formErrors = ref<Record<string, string>>({})

const targetTypeOptions = TARGET_TYPES.map(t => ({ label: t.label, value: t.value }))
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '轮播中', value: 'active' },
  { label: '已停用', value: 'inactive' },
]

const filtered = computed(() => {
  let list = store.sortedList
  if (statusFilter.value) list = list.filter(b => (b.status || 'active') === statusFilter.value)
  const q = page.searchQuery.trim().toLowerCase()
  if (q) list = list.filter(b => b.title.toLowerCase().includes(q))
  return list
})

function onTargetTypeChange() {
  form.value.target_id = ''
  form.value.target_url = ''
}

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.title.trim()) errs.title = 'Banner 标题不能为空'
  if (!form.value.image) errs.image = '请上传 Banner 图片'
  if (form.value.target_type === 'DISH') {
    if (!form.value.target_id) errs.target_id = '请选择关联菜品'
  }
  if (form.value.target_type === 'URL') {
    if (!form.value.target_url.trim()) errs.target_url = '请填写外部链接'
    else if (!/^https?:\/\//i.test(form.value.target_url.trim())) errs.target_url = '链接须以 http(s):// 开头'
  }
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

function openAdd() {
  editingId.value = null
  form.value = { title: '', image: '', target_type: 'DISH', target_id: '', target_url: '', sort_order: store.maxSortOrder + 1, status: 'active' }
  formErrors.value = {}
  showModal.value = true
}
function openEdit(id: number) {
  const b = store.list.find(x => Number(x.id) === id)
  if (!b) return
  editingId.value = id
  form.value = {
    title: b.title, image: b.image || '',
    target_type: (b.target_type as any) || 'NONE',
    target_id: String(b.target_id ?? ''), target_url: (b as any).target_url || '',
    sort_order: b.sort_order, status: b.status as 'active' | 'inactive',
  }
  formErrors.value = {}
  showModal.value = true
}

function handleSubmit() {
  if (!validate()) return
  const payload: any = {
    title: form.value.title.trim(),
    image: form.value.image,
    target_type: form.value.target_type,
    sort_order: Number(form.value.sort_order) || 0,
    status: form.value.status,
  }
  if (form.value.target_type === 'DISH') {
    payload.target_id = Number(form.value.target_id)
  } else if (form.value.target_type === 'URL') {
    payload.target_url = form.value.target_url.trim()
  }
  try {
    if (editingId.value !== null) store.update(editingId.value, payload)
    else store.add(payload)
    toast.success(editingId.value !== null ? 'Banner 已更新' : 'Banner 已添加')
    showModal.value = false
  } catch (e: any) {
    toast.error(e.message || '保存失败')
  }
}

async function handleDelete(id: number) {
  if (!await confirm.confirm('确定删除该 Banner？')) return
  try { await store.remove(id); toast.success('Banner 已删除') } catch (e: any) { toast.error(e.message || '删除失败') }
}

async function moveOrder(id: number, dir: -1 | 1) {
  const list = store.sortedList
  const idx = list.findIndex(b => Number(b.id) === id)
  const cur = list[idx]
  const swap = list[idx + dir]
  if (!cur || !swap) return
  try {
    const a = Number(cur.sort_order)
    const b = Number(swap.sort_order)
    await store.update(Number(cur.id), { sort_order: b } as any)
    await store.update(Number(swap.id), { sort_order: a } as any)
  } catch (e: any) { toast.error(e.message || '排序调整失败') }
}

function targetText(b: any): string {
  switch (b.target_type) {
    case 'DISH': return '菜品 #' + b.target_id
    case 'URL': return (b.target_url || '').slice(0, 24)
    default: return '不跳转'
  }
}

onMounted(() => {})
</script>

<template>
  <PageContainer>
    <PageHeader title="轮播（Banner）" :count="filtered.length">
      <template #actions>
        <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增 Banner</button>
      </template>
    </PageHeader>

    <FilterBar>
      <template #default>
        <FilterSelect v-model="statusFilter" label="状态" :options="statusOptions" :width="150" />
      </template>
    </FilterBar>

    <div v-if="!filtered.length" class="empty-state">暂无 Banner，点击上方按钮添加</div>

    <div class="card-grid" v-else>
      <div v-for="(b, i) in filtered" :key="Number(b.id)" class="banner-card" :style="{ animationDelay: (i % 12) * 30 + 'ms' }">
        <div class="banner-img-wrap">
          <img v-if="b.image" :src="b.image" :alt="b.title" />
          <div v-else class="banner-img-placeholder">
            <el-icon class="placeholder-svg"><Picture /></el-icon>
          </div>
          <span class="sort-badge">排序 {{ b.sort_order }}</span>
        </div>
        <div class="banner-body">
          <h4 class="banner-title">{{ b.title }}</h4>
          <p class="banner-target">跳转：{{ targetText(b) }} · <span class="tt-tag">{{ (TARGET_TYPES.find(t => t.value === b.target_type) || {}).label || b.target_type }}</span></p>
          <div class="banner-footer">
            <StatusTag :type="(b.status || 'active') === 'active' ? 'success' : 'gray'" :text="(b.status || 'active') === 'active' ? '轮播中' : '已停用'" />
            <div class="banner-actions">
              <button class="link btn-sm" v-press aria-label="上移" @click="moveOrder(Number(b.id), -1)">
                <el-icon class="sort-svg"><CaretTop /></el-icon>
              </button>
              <button class="link btn-sm" v-press aria-label="下移" @click="moveOrder(Number(b.id), 1)">
                <el-icon class="sort-svg"><CaretBottom /></el-icon>
              </button>
              <button class="link btn-sm" v-press @click.stop="openEdit(Number(b.id))">编辑</button>
              <button class="link danger btn-sm" v-press @click.stop="handleDelete(Number(b.id))">删除</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <FormDialog
      :show="showModal"
      :title="editingId !== null ? '编辑 Banner' : '新增 Banner'"
      :width="520"
      confirm-text="保存"
      @close="showModal = false"
      @confirm="handleSubmit"
    >
      <div class="modal-form">
        <div class="field"><label>标题 <span class="required">*</span></label>
          <input v-model="form.title" placeholder="如：开学季优惠" />
          <p v-if="formErrors.title" class="field-error">{{ formErrors.title }}</p>
        </div>
        <div class="field"><label>跳转类型 <span class="required">*</span></label>
          <div class="type-group">
            <button v-for="t in TARGET_TYPES" :key="t.value" type="button"
              class="type-opt" :class="{ on: form.target_type === t.value }" @click="onTargetTypeChange(); form.target_type = t.value">
              {{ t.label }}
            </button>
          </div>
          <p class="type-hint">{{ TARGET_TYPES.find(t => t.value === form.target_type)?.hint }}</p>
        </div>
        <div class="field" v-if="form.target_type === 'DISH'">
          <label>关联菜品 <span class="required">*</span></label>
          <FilterSelect v-model="form.target_id" :options="dishes.map(d => ({ label: d.name, value: Number(d.id) }))" placeholder="请选择菜品" :width="'100%'" />
          <p v-if="formErrors.target_id" class="field-error">{{ formErrors.target_id }}</p>
        </div>
        <div class="field" v-else-if="form.target_type === 'URL'">
          <label>外部链接 <span class="required">*</span></label>
          <input v-model="form.target_url" placeholder="https://..." />
          <p v-if="formErrors.target_url" class="field-error">{{ formErrors.target_url }}</p>
        </div>
        <div class="modal-row">
          <div class="field"><label>排序</label><input v-model.number="form.sort_order" type="number" min="0" /></div>
          <div class="field"><label>状态</label>
            <FilterSelect v-model="form.status" :options="statusOptions.filter(o => o.value !== '')" :width="'100%'" />
          </div>
        </div>
        <div class="field"><label>轮播图片 <span class="required">*</span></label>
          <ImageUpload v-model="form.image" :single="true" />
          <p v-if="formErrors.image" class="field-error">{{ formErrors.image }}</p>
        </div>
      </div>
    </FormDialog>
  </PageContainer>
</template>

<style scoped>
.empty-state { text-align: center; color: var(--text-light); font-size: var(--font-base); padding: var(--space-10) 0; background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); }

.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: var(--space-4); }
.banner-card { background: var(--bg-card); border-radius: var(--radius-card); overflow: hidden; box-shadow: var(--shadow-card); border: 1px solid var(--border-light); transition: transform .2s var(--ease-out), box-shadow .2s var(--ease-out), border-color .2s var(--ease-out); animation: card-enter 0.3s var(--ease-out) both; }
@media (hover: hover) {
  .banner-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); border-color: var(--color-primary); }
}
.banner-img-wrap { position: relative; width: 100%; height: 160px; overflow: hidden; background: var(--bg-soft); display: flex; align-items: center; justify-content: center; }
.banner-img-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.banner-img-placeholder { width: 48px; height: 48px; opacity: .4; display: flex; align-items: center; justify-content: center; }
.placeholder-svg { width: 36px; height: 36px; display: inline-flex; }
.sort-badge { position: absolute; top: var(--space-2); left: var(--space-2); background: color-mix(in srgb, #000 55%, transparent); color: var(--text-white); font-size: var(--font-xs); padding: var(--space-1) var(--space-2); border-radius: var(--radius-sm); }
.banner-body { padding: var(--space-4) var(--space-4); }
.banner-title { margin: 0 0 var(--space-1); font-size: var(--font-lg); color: var(--text-primary); font-weight: var(--weight-semibold); }
.banner-target { margin: 0 0 var(--space-2); font-size: var(--font-xs); color: var(--text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tt-tag { display: inline-block; padding: 0 var(--space-2); border-radius: var(--radius-sm); background: var(--bg-gray); color: var(--text-secondary); }
.banner-footer { display: flex; align-items: center; justify-content: space-between; padding-top: var(--space-3); border-top: 1px solid var(--border-color); }
.banner-actions { display: flex; gap: var(--space-2); align-items: center; }
.btn-sm { padding: var(--space-1) var(--space-2); font-size: var(--font-sm); }
.sort-svg { width: 16px; height: 16px; display: inline-flex; }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }

.modal-form { display: flex; flex-direction: column; gap: var(--space-3); }
.modal-row { display: flex; gap: var(--space-3); }
.modal-row .field { flex: 1; }

.type-group { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.type-opt { padding: var(--space-2) var(--space-4); border: 1px solid var(--border-strong); border-radius: var(--radius-pill); font-size: var(--font-sm); cursor: pointer; background: var(--bg-card); color: var(--text-secondary); transition: background 0.2s var(--ease-out), border-color 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out); }
.type-opt.on { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary); font-weight: var(--weight-medium); }
.type-opt:active { transform: scale(var(--press-scale)); }
.type-hint { font-size: var(--font-xs); color: var(--text-light); margin: var(--space-2) 0 0; }

@keyframes card-enter { from { opacity: 0; transform: scale(0.95) translateY(8px); } to { opacity: 1; transform: scale(1) translateY(0); } }
@media (prefers-reduced-motion: reduce) {
  .banner-card { animation: none; }
}
</style>
