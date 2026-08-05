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
import FilterSelect from '@/components/layout/FilterSelect.vue'
import FormDialog from '@/components/FormDialog.vue'
import EntityImage from '@/components/EntityImage.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import { Food, Trophy, Star, Plus } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const page = usePageStore()

function goBack() {
  router.push(`/dashboard/canteens/${canteenId.value}`)
}

const statusOptions = [
  { label: '营业中', value: 'active' },
  { label: '已关闭', value: 'inactive' },
]

const canteenId = computed(() => Number(route.params.canteenId))
const stallId = computed(() => Number(route.params.stallId))
const canteen = computed(() => store.canteens.find(c => Number(c.id) === canteenId.value))
const stall = computed(() => store.stalls.find(s => Number(s.id) === stallId.value))

const activeTab = ref(0)

const editing = ref(false)
const stallForm = ref({ name: '', location: '', description: '', image: '', avg_rating: 0, status: 'active' as 'active' | 'inactive', floor: '', windowNo: '', businessHours: '' })
const stallFormErrors = ref<Record<string, string>>({})

const showImageModal = ref(false)

function parseImages(imgStr: string): string[] { return (imgStr || '').split('|||').filter(Boolean) }
const imageList = computed(() => parseImages(stallForm.value.image))
const firstImage = computed(() => imageList.value[0] || '')
const imageCount = computed(() => imageList.value.length)

const dishes = computed(() => store.dishes.filter(d => Number(d.stall_id) === stallId.value))

function openImageModal() {
  showImageModal.value = true
}
async function saveImageModal() {
  if (stall.value) {
    try {
      await store.updateStall(Number(stall.value.id), { image: stallForm.value.image })
      toast.success('图片已更新')
      showImageModal.value = false
    } catch (err: any) {
      toast.error(err.message || '图片保存失败')
    }
    return
  }
  showImageModal.value = false
}
function closeImageModal() { showImageModal.value = false }

watch([canteen, stall], ([c, s]) => {
  page.setPage({
    breadcrumbs: [{ label: '食堂管理', path: '/dashboard/canteens' }, { label: c?.name || '加载中', path: c ? `/dashboard/canteens/${canteenId.value}` : '' }, { label: s?.name || '加载中' }],
    searchPlaceholder: '搜索菜品名称...',
  })
  if (s) {
    stallForm.value = {
      name: s.name, location: s.location || '', description: s.description || '',
      image: s.image || '', avg_rating: s.avg_rating, status: s.status as 'active' | 'inactive',
      floor: s.floor || '', windowNo: s.windowNo || '', businessHours: s.businessHours || '',
    }
  }
}, { immediate: true })

function toggleEdit() {
  editing.value = true
  activeTab.value = 0
  if (stall.value) {
    stallForm.value = {
      name: stall.value.name, location: stall.value.location || '',
      description: stall.value.description || '', image: stall.value.image || '',
      avg_rating: stall.value.avg_rating, status: stall.value.status as 'active' | 'inactive',
      floor: stall.value.floor || '', windowNo: stall.value.windowNo || '', businessHours: stall.value.businessHours || '',
    }
    stallFormErrors.value = {}
  }
}
async function confirmEdit() {
  const errs: Record<string, string> = {}
  if (!stallForm.value.name.trim()) errs.name = '档口名称不能为空'
  stallFormErrors.value = errs
  if (Object.keys(errs).length) return
  if (stall.value) {
    try {
      await store.updateStall(Number(stall.value.id), { ...stallForm.value })
      toast.success('档口信息已更新')
      stallFormErrors.value = {}
      editing.value = false
    } catch (err: any) {
      toast.error(err.message || '档口信息更新失败')
    }
    return
  }
  stallFormErrors.value = {}
  editing.value = false
}
function cancelEdit() {
  if (stall.value) {
    stallForm.value = {
      name: stall.value.name, location: stall.value.location || '',
      description: stall.value.description || '', image: stall.value.image || '',
      avg_rating: stall.value.avg_rating, status: stall.value.status as 'active' | 'inactive',
      floor: stall.value.floor || '', windowNo: stall.value.windowNo || '', businessHours: stall.value.businessHours || '',
    }
  }
  stallFormErrors.value = {}
  editing.value = false
}
async function deleteStall() {
  if (!stall.value || !canteen.value) return
  if (!await confirm.confirm('确定删除该档口？')) return
  try {
    await store.deleteStall(Number(stall.value.id))
    toast.success('档口已删除')
    router.push(`/dashboard/canteens/${canteenId.value}`)
  } catch (err: any) {
    toast.error(err.message || '档口删除失败')
  }
}

function parseTags(tags: string): string[] {
  try { return JSON.parse(tags || '[]') } catch { return [] }
}

const showModal = ref(false)
const filtered = computed(() => {
  const q = page.searchQuery.trim().toLowerCase()
  if (!q) return dishes.value
  return dishes.value.filter(d => d.name.toLowerCase().includes(q))
})

const form = ref({ name: '', price: 0, description: '', image: '', tags: '', status: '' })
const formErrors = ref<Record<string, string>>({})

function validate() { const errs: Record<string, string> = {}; if (!form.value.name.trim()) errs.name = '菜品名称不能为空'; if (!form.value.price || form.value.price <= 0) errs.price = '价格必须大于 0'; formErrors.value = errs; return Object.keys(errs).length === 0 }
function toggleTag(tag: string) { const arr: string[] = []; try { arr.push(...JSON.parse(form.value.tags || '[]')) } catch {}; const i = arr.indexOf(tag); i === -1 ? arr.push(tag) : arr.splice(i, 1); form.value.tags = JSON.stringify(arr) }
function openAdd() { form.value = { name: '', price: 0, description: '', image: '', tags: '', status: 'active' }; formErrors.value = {}; showModal.value = true }
function handleSubmit() { if (!validate()) return; store.addDish({ stall_id: stallId.value as unknown as bigint, ...form.value, avg_rating: 0, rating_count: 0, view_count: 0 }); toast.success('菜品已添加'); showModal.value = false }
function enterDish(id: number) { router.push(`/dashboard/canteens/${canteenId.value}/stalls/${stallId.value}/dishes/${id}`) }
</script>

<template>
  <PageContainer>
    <PageHeader
      :back="true"
      :title="stall?.name || '加载中'"
      :subtitle="(canteen?.name || '') + (stall?.location ? ' · ' + stall.location : '')"
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
              <button class="btn-danger btn-sm" v-press @click="deleteStall">删除</button>
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
            <div class="detail-row detail-row-desc">
              <span class="detail-label">名称</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ stallForm.name }}</span>
                <input v-else v-model="stallForm.name" class="form-input" :class="{ 'input-error': stallFormErrors.name }" placeholder="档口名称" />
                <p v-if="editing && stallFormErrors.name" class="field-error">{{ stallFormErrors.name }}</p>
              </div>
            </div>
            <div class="detail-row detail-row-desc">
              <span class="detail-label">位置</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ stallForm.location || '-' }}</span>
                <input v-else v-model="stallForm.location" class="form-input" placeholder="档口位置" />
              </div>
            </div>
            <div class="detail-row detail-row-desc">
              <span class="detail-label">楼层</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ stallForm.floor || '-' }}</span>
                <input v-else v-model="stallForm.floor" class="form-input" placeholder="如 1F / 2F" />
              </div>
            </div>
            <div class="detail-row detail-row-desc">
              <span class="detail-label">窗口号</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ stallForm.windowNo || '-' }}</span>
                <input v-else v-model="stallForm.windowNo" class="form-input" placeholder="如 12 号窗口" />
              </div>
            </div>
            <div class="detail-row detail-row-desc">
              <span class="detail-label">营业时间</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ stallForm.businessHours || '-' }}</span>
                <input v-else v-model="stallForm.businessHours" class="form-input" placeholder="如 10:00-20:00" />
              </div>
            </div>
            <div class="detail-row detail-row-desc">
              <span class="detail-label">描述</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value text-desc">{{ stallForm.description || '-' }}</span>
                <textarea v-else v-model="stallForm.description" class="form-textarea" rows="2" placeholder="档口描述"></textarea>
              </div>
            </div>
            <div class="detail-row detail-row-desc">
              <span class="detail-label">状态</span>
              <div class="detail-control">
                <span v-if="!editing" class="tag" :class="stallForm.status === 'active' ? 'tag-green' : 'tag-red'">{{ stallForm.status === 'active' ? '营业中' : '已关闭' }}</span>
                <FilterSelect
                  v-else
                  v-model="stallForm.status"
                  :options="statusOptions"
                  :clearable="false"
                  width="160"
                />
              </div>
            </div>
          </div>
        </div>
      </PageSection>

      <PageSection title="数据统计">
        <div class="stats-row">
          <StatCard label="菜品数" :value="dishes.length" sub="道菜品" />
          <StatCard label="评分" :value="stallForm.avg_rating" tone="star" sub="平均分" />
        </div>
      </PageSection>
    </template>

    <!-- Tab 2: 菜品管理 -->
    <template v-if="activeTab === 1">
      <PageSection>
        <template #header-extra>
          <button class="btn-primary" v-press @click="openAdd"><el-icon class="btn-plus-icon"><Plus /></el-icon>新增菜品</button>
        </template>
        <div class="card-grid">
          <div v-for="d in filtered" :key="Number(d.id)" class="pk-card" @click="enterDish(Number(d.id))">
            <div class="pk-img-wrap">
              <img v-if="d.image" :src="d.image" :alt="d.name" />
              <div v-else class="pk-emoji"><el-icon :size="40"><Food /></el-icon></div>
              <span class="pk-status" :class="d.status === 'active' ? 'status-on' : 'status-off'">{{ d.status === 'active' ? '在售' : '已下架' }}</span>
            </div>
            <div class="pk-body">
              <div class="pk-title-row">
                <h4 class="pk-title">{{ d.name }}</h4>
                <div class="pk-price">¥{{ d.price }}</div>
              </div>
              <div class="pk-tags" v-if="parseTags(d.tags || '').length">
                <span v-for="tag in parseTags(d.tags || '')" :key="tag" class="pk-tag" :class="tag === '招牌菜' ? 'tag-hot' : 'tag-rec'">
                  <el-icon class="tag-icon"><component :is="tag === '招牌菜' ? Trophy : Star" /></el-icon>
                  {{ tag }}
                </span>
              </div>
              <p class="pk-desc">{{ d.description }}</p>
            </div>
          </div>
          <div v-if="!filtered.length" class="empty-card">暂无菜品</div>
        </div>
      </PageSection>
    </template>

    <FormDialog :show="showImageModal" title="图片管理" :width="480" confirm-text="保存" @close="closeImageModal" @confirm="saveImageModal">
      <ImageUpload v-model="stallForm.image" :max="3" />
    </FormDialog>

    <FormDialog :show="showModal" title="新增菜品" confirm-text="保存" @close="showModal = false" :on-confirm="handleSubmit">
      <div class="modal-form">
        <div class="field"><label>名称 <span class="required">*</span></label><input v-model="form.name" /><p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p></div>
        <div class="field"><label>价格 (元) <span class="required">*</span></label><input v-model.number="form.price" type="number" min="0" step="0.5" /><p v-if="formErrors.price" class="field-error">{{ formErrors.price }}</p></div>
        <div class="field"><label>介绍</label><textarea v-model="form.description" rows="3"></textarea></div>
        <div class="field">
          <label>图片 <span class="text-muted">（至多 3 张）</span></label>
          <ImageUpload v-model="form.image" :max="3" />
        </div>
        <div class="field"><label>特色标签</label><div class="tag-selector"><span class="tag-option" :class="{active: parseTags(form.tags).includes('招牌菜')}" @click="toggleTag('招牌菜')"><el-icon class="tag-icon"><Trophy /></el-icon> 招牌菜</span><span class="tag-option" :class="{active: parseTags(form.tags).includes('必吃推荐')}" @click="toggleTag('必吃推荐')"><el-icon class="tag-icon"><Star /></el-icon> 必吃推荐</span></div></div>
      </div>
    </FormDialog>
  </PageContainer>
</template>

<style scoped>
/* ===== 头部缩略图 ===== */
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

/* ===== 统计卡片（统一 StatCard） ===== */
.stats-row { display: flex; gap: var(--space-4); }
.stats-row :deep(.stat-card) { flex: 1; min-width: 0; }

/* ===== 列表头 ===== */
.list-bar {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-4);
}
.list-bar h3 { margin: 0; font-size: var(--font-lg); color: var(--text-primary); font-weight: var(--weight-semibold); }

/* ===== 卡片网格 ===== */
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: var(--space-4); }
.pk-card { position: relative; background: var(--bg-card); border-radius: var(--radius-card); overflow: hidden; box-shadow: var(--shadow-card); border: 1px solid var(--border-light); cursor: pointer; transition: transform .2s var(--ease-out), box-shadow .2s var(--ease-out), border-color .2s var(--ease-out); }
.pk-card:hover { box-shadow: var(--shadow-hover); border-color: var(--color-primary); }
.pk-img-wrap { position: relative; width: 100%; height: 150px; overflow: hidden; background: var(--bg-soft); display: flex; align-items: center; justify-content: center; }
.pk-img-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pk-emoji { width: 48px; height: 48px; opacity: .35; font-size: 40px; }
.pk-status { position: absolute; top: var(--space-2); right: var(--space-2); padding: var(--space-1) var(--space-3); border-radius: var(--radius-sm); font-size: var(--font-xs); font-weight: var(--weight-medium); line-height: 1.6; }
.status-on { background: var(--color-success-bg); color: var(--color-success); }
.status-off { background: var(--color-danger-soft); color: var(--color-error); }
.pk-body { padding: var(--space-4); }
.pk-title-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-2); }
.pk-title { margin: 0; font-size: var(--font-lg); color: var(--text-primary); font-weight: var(--weight-semibold); }
.pk-price { font-size: var(--font-xl); font-weight: var(--weight-bold); color: var(--color-price); }
.pk-tags { display: flex; gap: var(--space-1); margin-bottom: var(--space-2); flex-wrap: wrap; }
.pk-tag { font-size: var(--font-xs); padding: var(--space-1) var(--space-3); border-radius: var(--radius-sm); font-weight: var(--weight-medium); display: inline-flex; align-items: center; gap: var(--space-1); }
.tag-hot { background: var(--color-warning-soft); color: var(--color-warning); }
.tag-rec { background: var(--color-price-soft); color: var(--color-price); }
.tag-icon { width: 12px; height: 12px; display: inline; }
.pk-desc { margin: 0 0 var(--space-2); font-size: var(--font-sm); color: var(--text-secondary); line-height: var(--leading-base); overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.empty-card { grid-column: 1 / -1; text-align: center; color: var(--text-light); padding: var(--space-10) 0; }

.tag-selector { display: flex; gap: var(--space-2); }
.tag-option { padding: var(--space-2) var(--space-4); border: 1px solid var(--border-strong); border-radius: var(--radius-pill); font-size: var(--font-sm); cursor: pointer; transition: background 0.2s var(--ease-out), border-color 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out); user-select: none; display: inline-flex; align-items: center; gap: var(--space-1); }
.tag-option:hover { border-color: var(--color-primary); color: var(--color-primary); }
.tag-option:active { transform: scale(var(--press-scale)); }
.tag-option.active { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary); font-weight: var(--weight-medium); }

/* ===== 全局覆盖（仅保留页面特有细微项，按钮/表单继承全局基线） ===== */
.text-muted { color: var(--text-light); font-size: var(--font-xs); font-weight: var(--weight-regular); }
.btn-plus-icon { width: 14px; height: 14px; display: inline-flex; vertical-align: -2px; margin-right: var(--space-1); }
.input-error { border-color: var(--color-error) !important; }
.field-error { margin: var(--space-1) 0 0; font-size: var(--font-sm); color: var(--color-error); }
.required { color: var(--color-error); }
</style>
