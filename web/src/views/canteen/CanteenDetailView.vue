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

const stalls = computed(() => store.stalls.filter(s => Number(s.canteen_id) === canteenId.value))
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

function openAdd() { form.value = { name: '', description: '', image: '', status: 'active' }; formErrors.value = {}; showModal.value = true }
function handleSubmit() {
  if (!validate()) return
  store.addStall({ canteen_id: canteenId.value as unknown as bigint, ...form.value, sort_order: 0, avg_rating: 0 })
  toast.success('档口已添加')
  showModal.value = false
}
function enterStall(id: number) { router.push(`/dashboard/canteens/${canteenId.value}/stalls/${id}`) }
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
        <div class="card-grid">
          <div v-for="s in filtered" :key="Number(s.id)" class="pk-card" @click="enterStall(Number(s.id))">
            <div class="pk-img-wrap">
              <img v-if="s.image" :src="s.image" :alt="s.name" />
              <div v-else class="pk-emoji"><el-icon :size="40"><Food /></el-icon></div>
              <span class="pk-status" :class="s.status === 'active' ? 'status-on' : 'status-off'">{{ s.status === 'active' ? '营业中' : '已关闭' }}</span>
            </div>
            <div class="pk-body">
              <h4 class="pk-title">{{ s.name }}</h4>
              <p class="pk-desc">{{ s.description }}</p>
              <div class="pk-stats">菜品 {{ store.dishes.filter(d => Number(d.stall_id) === Number(s.id)).length }} 道</div>
            </div>
          </div>
          <div v-if="!filtered.length" class="empty-card">暂无档口</div>
        </div>
      </PageSection>
    </template>

    <FormDialog :show="showImageModal" title="图片管理" :width="480" confirm-text="保存" @close="closeImageModal" @confirm="saveImageModal">
      <ImageUpload v-model="canteenForm.image" :max="3" />
    </FormDialog>

    <FormDialog :show="showModal" title="新增档口" confirm-text="保存" @close="showModal = false" @confirm="handleSubmit">
      <div class="modal-form">
        <div class="field"><label>名称 <span class="required">*</span></label><input v-model="form.name" /><p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p></div>
        <div class="field"><label>描述</label><textarea v-model="form.description" rows="3"></textarea></div>
        <div class="field">
          <label>图片</label>
          <ImageUpload v-model="form.image" :max="3" />
        </div>
      </div>
    </FormDialog>
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
.form-input { padding: var(--space-2) var(--space-3); border: 1px solid var(--border-strong); border-radius: var(--radius); font-size: var(--font-base); font-weight: var(--weight-medium); color: var(--text-primary); outline: none; transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out); background: var(--bg-card); width: 100%; box-sizing: border-box; }
.form-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent); }
.form-textarea { padding: var(--space-2) var(--space-3); border: 1px solid var(--border-strong); border-radius: var(--radius); font-size: var(--font-base); color: var(--text-primary); outline: none; transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out); background: var(--bg-card); width: 100%; box-sizing: border-box; resize: vertical; min-height: 50px; }
.form-textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent); }

/* ===== 统计卡片（统一 StatCard 组件） ===== */
.stats-row { display: flex; gap: var(--space-4); }

/* ===== 列表头 ===== */
.list-bar {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-4);
}
.list-bar h3 { margin: 0; font-size: var(--font-lg); color: var(--text-primary); font-weight: var(--weight-semibold); }

/* ===== 卡片网格 ===== */
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: var(--space-4); }
.pk-card { position: relative; background: var(--bg-card); border-radius: var(--radius-card); overflow: hidden; box-shadow: var(--shadow-card); border: 1px solid var(--border-light); cursor: pointer; transition: transform .2s var(--ease-out), box-shadow .2s var(--ease-out), border-color .2s var(--ease-out); }
.pk-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); border-color: var(--color-primary); }
.pk-img-wrap { position: relative; width: 100%; height: 160px; overflow: hidden; background: var(--bg-soft); display: flex; align-items: center; justify-content: center; }
.pk-img-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pk-emoji { width: 48px; height: 48px; opacity: .35; font-size: 40px; }
.pk-status { position: absolute; top: var(--space-2); right: var(--space-2); padding: var(--space-1) var(--space-3); border-radius: var(--radius-sm); font-size: var(--font-xs); font-weight: var(--weight-medium); line-height: 1.6; }
.status-on { background: var(--color-success-bg); color: var(--color-success); }
.status-off { background: var(--color-danger-soft); color: var(--color-error); }
.pk-body { padding: var(--space-4); }
.pk-title { margin: 0 0 var(--space-2); font-size: var(--font-lg); color: var(--text-primary); font-weight: var(--weight-semibold); }
.pk-desc { margin: 0 0 var(--space-2); font-size: var(--font-sm); color: var(--text-secondary); line-height: var(--leading-base); overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.pk-stats { font-size: var(--font-xs); color: var(--text-light); }
.empty-card { grid-column: 1 / -1; text-align: center; color: var(--text-light); padding: var(--space-10) 0; }

/* ===== 全局覆盖（仅保留页面特有细微项，按钮/表单继承全局基线） ===== */
.input-error { border-color: var(--color-error) !important; }
.field-error { margin: var(--space-1) 0 0; font-size: var(--font-sm); color: var(--color-error); }
.required { color: var(--color-error); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
</style>
