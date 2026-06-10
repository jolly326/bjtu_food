<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { usePageStore } from '@/stores/pageStore'
import Modal from '@/components/Modal.vue'
import EntityImage from '@/components/EntityImage.vue'
import guanbi from '@/static/icon/guanbi.svg'
import Food from '@/static/icon/Food.svg'
import Trophy from '@/static/icon/Trophy.svg'
import yellowStar from '@/static/icon/yellow-star.svg'
import { uploadImage } from '@/api/upload'

const router = useRouter()
const route = useRoute()
const store = useAdminStore()
const toast = useToastStore()
const page = usePageStore()

const canteenId = computed(() => Number(route.params.canteenId))
const stallId = computed(() => Number(route.params.stallId))
const canteen = computed(() => store.canteens.find(c => Number(c.id) === canteenId.value))
const stall = computed(() => store.stalls.find(s => Number(s.id) === stallId.value))

const activeTab = ref(0)

const editing = ref(false)
const stallForm = ref({ name: '', location: '', description: '', image: '', avg_rating: 0, status: 'active' as 'active' | 'inactive' })
const stallFormErrors = ref<Record<string, string>>({})

const showImageModal = ref(false)
const modalImages = ref<string[]>([])
const modalFileInput = ref<HTMLInputElement | null>(null)

function parseImages(imgStr: string): string[] { return (imgStr || '').split('|||').filter(Boolean) }
const imageList = computed(() => parseImages(stallForm.value.image))
const firstImage = computed(() => imageList.value[0] || '')
const imageCount = computed(() => imageList.value.length)

const dishes = computed(() => store.dishes.filter(d => Number(d.stall_id) === stallId.value))

function openImageModal() {
  modalImages.value = [...imageList.value]
  showImageModal.value = true
}
function modalAddImage() { modalFileInput.value?.click() }
async function modalHandleFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { toast.error('请选择图片文件'); return }
  try {
    const result = await uploadImage(file)
    modalImages.value.push(result.relativeUrl)
    toast.success('图片上传成功')
  } catch (err: any) {
    toast.error(err.message || '图片上传失败')
  } finally {
    input.value = ''
  }
}
function modalRemoveImage(idx: number) { modalImages.value.splice(idx, 1) }
async function saveImageModal() {
  stallForm.value.image = modalImages.value.join('|||')
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
    showSearch: true, searchPlaceholder: '搜索菜品名称...',
  })
  if (s) {
    stallForm.value = {
      name: s.name, location: s.location || '', description: s.description || '',
      image: s.image || '', avg_rating: s.avg_rating, status: s.status as 'active' | 'inactive',
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
    }
  }
  stallFormErrors.value = {}
  editing.value = false
}
async function deleteStall() {
  if (!stall.value || !canteen.value) return
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
const imagePreviews = ref<string[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

function handleAddImage() { fileInput.value?.click() }
async function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { toast.error('请选择图片文件'); return }
  try {
    const result = await uploadImage(file)
    imagePreviews.value.push(result.url)
    const saved = form.value.image ? form.value.image.split('|||').filter(Boolean) : []
    saved.push(result.relativeUrl)
    form.value.image = saved.join('|||')
    toast.success('图片上传成功')
  } catch (err: any) {
    toast.error(err.message || '图片上传失败')
  } finally {
    input.value = ''
  }
}
function removeImage(idx: number) {
  imagePreviews.value.splice(idx, 1)
  form.value.image = imagePreviews.value.join('|||')
}

function validate() { const errs: Record<string, string> = {}; if (!form.value.name.trim()) errs.name = '菜品名称不能为空'; if (!form.value.price || form.value.price <= 0) errs.price = '价格必须大于 0'; formErrors.value = errs; return Object.keys(errs).length === 0 }
function toggleTag(tag: string) { const arr: string[] = []; try { arr.push(...JSON.parse(form.value.tags || '[]')) } catch {}; const i = arr.indexOf(tag); i === -1 ? arr.push(tag) : arr.splice(i, 1); form.value.tags = JSON.stringify(arr) }
function openAdd() { form.value = { name: '', price: 0, description: '', image: '', tags: '', status: 'active' }; formErrors.value = {}; imagePreviews.value = []; showModal.value = true }
function handleSubmit() { if (!validate()) return; store.addDish({ stall_id: stallId.value as unknown as bigint, ...form.value, avg_rating: 0, rating_count: 0, favorite_count: 0, view_count: 0 }); toast.success('菜品已添加'); showModal.value = false }
function enterDish(id: number) { router.push(`/dashboard/canteens/${canteenId.value}/stalls/${stallId.value}/dishes/${id}`) }
</script>

<template>
  <div class="page">
    <!-- 合并头栏 + Tab -->
    <div class="headerbar">
      <div class="headerbar-top">
        <div class="headerbar-thumb" @click="openImageModal">
          <img v-if="firstImage" :src="firstImage" alt="" />
          <img v-else :src="Food" class="thumb-ph" alt="" />
        </div>
        <div class="headerbar-body">
          <div class="headerbar-title-row">
            <h2 class="headerbar-name">{{ stall?.name || '加载中' }}</h2>
            <span class="headerbar-sub">{{ canteen?.name }}{{ stall?.location ? ' · ' + stall.location : '' }}</span>
          </div>
        </div>
      </div>
      <div class="headerbar-tabs">
        <div class="tab-item" :class="{ active: activeTab === 0 }" @click="activeTab = 0">详情概览</div>
        <div class="tab-count-item" :class="{ active: activeTab === 1 }" @click="activeTab = 1">
          菜品管理
        </div>
      </div>
    </div>

    <!-- Tab 1: 详情概览 -->
    <div v-show="activeTab === 0" class="tab-panel">
      <div class="panel-section">
        <div class="panel-header">
          <h4 class="panel-title">基本信息</h4>
          <div class="panel-actions">
            <template v-if="!editing">
              <button class="btn-primary btn-sm" @click="toggleEdit">编辑</button>
              <button class="btn-danger btn-sm" @click="deleteStall">删除</button>
            </template>
            <template v-else>
              <button class="btn-cancel btn-sm" @click="cancelEdit">取消</button>
              <button class="btn-primary btn-sm" @click="confirmEdit">保存</button>
            </template>
          </div>
        </div>
        <div class="detail-body">
          <EntityImage :image-url="firstImage" :image-count="imageCount" placeholder-emoji="🍳" @click="openImageModal" />
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
                <select v-else v-model="stallForm.status" class="form-select">
                  <option value="active">营业中</option>
                  <option value="inactive">已关闭</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="panel-section">
        <div class="panel-header">
          <h4 class="panel-title">数据统计</h4>
        </div>
        <div class="stats-row">
          <div class="stat-card">
            <span class="stat-num">{{ dishes.length }}</span>
            <span class="stat-unit">道菜品</span>
          </div>
          <div class="stat-card">
            <span class="stat-num" style="color:#F5A623">{{ stallForm.avg_rating }}</span>
            <span class="stat-unit">评分</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 2: 菜品管理 -->
    <div v-show="activeTab === 1" class="tab-panel">
      <div class="list-bar">
        <h3>菜品列表（{{ dishes.length }}）</h3>
        <button class="btn-primary" @click="openAdd">+ 新增菜品</button>
      </div>
      <div class="card-grid">
        <div v-for="d in filtered" :key="Number(d.id)" class="pk-card" @click="enterDish(Number(d.id))">
          <div class="pk-img-wrap">
            <img v-if="d.image" :src="d.image" :alt="d.name" />
            <div v-else class="pk-emoji"><img :src="Food" alt="" /></div>
            <span class="pk-status" :class="d.status === 'active' ? 'status-on' : 'status-off'">{{ d.status === 'active' ? '在售' : '已下架' }}</span>
          </div>
          <div class="pk-body">
            <div class="pk-title-row">
              <h4 class="pk-title">{{ d.name }}</h4>
              <div class="pk-price">¥{{ d.price }}</div>
            </div>
            <div class="pk-tags" v-if="parseTags(d.tags || '').length">
              <span v-for="tag in parseTags(d.tags || '')" :key="tag" class="pk-tag" :class="tag === '招牌菜' ? 'tag-hot' : 'tag-rec'">
                <img v-if="tag === '招牌菜'" :src="Trophy" class="tag-icon" alt="" />
                <img v-else :src="yellowStar" class="tag-icon" alt="" />
                {{ tag }}
              </span>
            </div>
            <p class="pk-desc">{{ d.description }}</p>
          </div>
        </div>
        <div v-if="!filtered.length" class="empty-card">暂无菜品</div>
      </div>
    </div>

    <Modal :show="showImageModal" title="图片管理" :width="480" @close="closeImageModal">
      <div class="modal-img-grid">
        <div v-for="(img, idx) in modalImages" :key="idx" class="modal-img-item">
          <img :src="img" alt="" /><span v-if="idx === 0" class="modal-img-cover">封面</span>
          <span class="modal-img-remove" @click="modalRemoveImage(idx)"><img :src="guanbi" class="icon-x" alt="" /></span>
        </div>
        <div class="modal-img-add" @click="modalAddImage"><span class="add-icon">+</span><span class="add-tip">添加图片</span></div>
      </div>
      <input ref="modalFileInput" type="file" accept="image/*" class="file-hidden" @change="modalHandleFile" />
      <div class="modal-actions"><button class="btn-cancel" @click="closeImageModal">取消</button><button class="btn-primary" @click="saveImageModal">保存</button></div>
    </Modal>

    <Modal :show="showModal" title="新增菜品" @close="showModal = false">
      <div class="modal-form">
        <div class="field"><label>名称 <span class="required">*</span></label><input v-model="form.name" /><p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p></div>
        <div class="field"><label>价格 (元) <span class="required">*</span></label><input v-model.number="form.price" type="number" min="0" step="0.5" /><p v-if="formErrors.price" class="field-error">{{ formErrors.price }}</p></div>
        <div class="field"><label>介绍</label><textarea v-model="form.description" rows="3"></textarea></div>
        <div class="field">
          <label>图片 <span class="text-muted">（至多 3 张）</span></label>
          <div class="image-list">
            <div v-for="(preview, idx) in imagePreviews" :key="idx" class="image-item">
              <img :src="preview" alt="预览" />
              <span class="image-remove" @click="removeImage(idx)"><img :src="guanbi" class="icon-x" alt="" /></span>
            </div>
            <div v-if="imagePreviews.length < 3" class="image-add" @click="handleAddImage">
              <span class="add-icon">+</span>
              <span class="add-text">添加图片</span>
            </div>
          </div>
          <input ref="fileInput" type="file" accept="image/*" class="file-hidden" @change="handleFileChange" />
        </div>
        <div class="field"><label>特色标签</label><div class="tag-selector"><span class="tag-option" :class="{active: parseTags(form.tags).includes('招牌菜')}" @click="toggleTag('招牌菜')"><img :src="Trophy" class="tag-icon" alt="" /> 招牌菜</span><span class="tag-option" :class="{active: parseTags(form.tags).includes('必吃推荐')}" @click="toggleTag('必吃推荐')"><img :src="yellowStar" class="tag-icon" alt="" /> 必吃推荐</span></div></div>
      </div>
      <div class="modal-actions"><button class="btn-cancel" @click="showModal = false">取消</button><button class="btn-primary" @click="handleSubmit">保存</button></div>
    </Modal>
  </div>
</template>

<style scoped>
/* ===== 合并头栏 + Tab ===== */
.headerbar {
  display: flex; flex-direction: column; background: #fff; border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,.06); padding: 12px 24px; margin-bottom: 20px;
}
.headerbar-top { display: flex; gap: 14px; align-items: flex-start; }
.headerbar-thumb {
  display: flex; width: 60px; height: 60px; border-radius: 8px; overflow: hidden;
  align-items: center; justify-content: center; background: #fafafa;
  border: 1px solid #f0f0f0; flex-shrink: 0; cursor: pointer;
}
.headerbar-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.headerbar-thumb .thumb-ph { width: 20px; height: 20px; object-fit: contain; display: block; margin: 10px; opacity: .35; }
.headerbar-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.headerbar-title-row { display: flex; gap: 10px; flex-wrap: wrap; flex-direction: column; align-items: baseline; }
.headerbar-name { margin: 0; font-size: 18px; color: #1a1a1a; font-weight: 700; line-height: 1.3; }
.headerbar-sub { font-size: 13px; color: #999; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.headerbar-tabs { display: flex; gap: 0; margin-top: 10px; border-top: 1px solid #f0f0f0; padding-top: 6px; }
.headerbar-tabs .tab-item,
.headerbar-tabs .tab-count-item {
  padding: 4px 16px 6px; font-size: 13px; color: #666; cursor: pointer;
  border-bottom: 2px solid transparent; transition: all .2s; user-select: none;
  display: flex; align-items: center; gap: 4px;
}
.headerbar-tabs .tab-item:hover,
.headerbar-tabs .tab-count-item:hover { color: var(--color-primary); }
.headerbar-tabs .tab-item.active,
.headerbar-tabs .tab-count-item.active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: 600; }


/* ===== Tab 面板 ===== */
.tab-panel {
  background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,.06);
  padding: 24px; margin-bottom: 20px;
}
.panel-section { margin-bottom: 28px; }
.panel-section:last-child { margin-bottom: 0; }
.panel-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px; padding-bottom: 10px; border-bottom: 1px solid #f0f0f0;
}
.panel-title { margin: 0; font-size: 15px; color: #333; font-weight: 600; }
.panel-actions { display: flex; gap: 8px; }

/* ===== 基本信息 ===== */
.detail-body { display: flex; gap: 24px; align-items: flex-start; }
.detail-fields { flex: 1; display: flex; flex-direction: column; gap: 12px; }
.detail-row { display: flex; align-items: center; gap: 12px; }
.detail-row-desc { align-items: flex-start; }
.detail-label { font-size: 13px; color: #999; width: 48px; flex-shrink: 0; line-height: 28px; }
.detail-control { flex: 1; min-width: 0; }
.detail-value { font-size: 15px; color: #333; font-weight: 500; line-height: 28px; }
.detail-value.text-desc { font-weight: 400; color: #555; line-height: 1.6; }
.form-input { padding: 5px 10px; border: 1px solid #d9d9d9; border-radius: 6px; font-size: 14px; font-weight: 500; color: #333; outline: none; transition: border-color .2s; background: #fff; width: 100%; box-sizing: border-box; }
.form-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px rgba(139,58,43,.15); }
.form-textarea { padding: 6px 10px; border: 1px solid #d9d9d9; border-radius: 6px; font-size: 14px; color: #333; outline: none; transition: border-color .2s; background: #fff; width: 100%; box-sizing: border-box; resize: vertical; min-height: 50px; }
.form-textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px rgba(139,58,43,.15); }
.form-select { padding: 5px 10px; border: 1px solid #d9d9d9; border-radius: 6px; font-size: 14px; outline: none; background: #fff; cursor: pointer; }

/* ===== 统计卡片 ===== */
.stats-row { display: flex; gap: 16px; }
.stat-card {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px;
  padding: 20px; background: #fafafa; border-radius: 10px; border: 1px solid #f0f0f0;
}
.stat-num { font-size: 28px; font-weight: 700; color: var(--color-primary); line-height: 1.2; }
.stat-unit { font-size: 13px; color: #999; }

/* ===== 列表头 ===== */
.list-bar {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;
}
.list-bar h3 { margin: 0; font-size: 16px; color: #333; font-weight: 600; }

/* ===== 卡片网格 ===== */
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
.pk-card { position: relative; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,.06); cursor: pointer; transition: all .2s; border: 1px solid #f0f0f0; }
.pk-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); border-color: var(--color-accent); }
.pk-img-wrap { position: relative; width: 100%; height: 150px; overflow: hidden; background: #fafafa; display: flex; align-items: center; justify-content: center; }
.pk-img-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pk-emoji { width: 48px; height: 48px; opacity: .35; }
.pk-status { position: absolute; top: 8px; right: 8px; padding: 2px 10px; border-radius: 8px; font-size: 11px; font-weight: 500; line-height: 1.6; }
.status-on { background: var(--color-success-bg); color: var(--color-success); }
.status-off { background: var(--color-error-bg); color: var(--color-error); }
.pk-body { padding: 14px 16px; }
.pk-title-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.pk-title { margin: 0; font-size: 15px; color: #333; font-weight: 600; }
.pk-price { font-size: 18px; font-weight: 700; color: #ff4d4f; }
.pk-tags { display: flex; gap: 4px; margin-bottom: 6px; flex-wrap: wrap; }
.pk-tag { font-size: 11px; padding: 2px 10px; border-radius: 8px; font-weight: 500; display: inline-flex; align-items: center; gap: 2px; }
.tag-hot { background: #fff7e6; color: #fa8c16; }
.tag-rec { background: #f6ffed; color: #52c41a; }
.tag-icon { width: 12px; height: 12px; display: inline; }
.pk-desc { margin: 0 0 6px; font-size: 13px; color: rgba(0,0,0,.45); line-height: 1.5; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.empty-card { grid-column: 1 / -1; text-align: center; color: #ccc; padding: 60px 0; }

.tag-selector { display: flex; gap: 8px; }
.tag-option { padding: 6px 14px; border: 1px solid #d9d9d9; border-radius: 16px; font-size: 13px; cursor: pointer; transition: all .2s; user-select: none; display: inline-flex; align-items: center; gap: 4px; }
.tag-option:hover { border-color: var(--color-primary); color: var(--color-primary); }
.tag-option.active { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary); font-weight: 500; }

/* ===== Modal 图片管理 ===== */
.modal-img-grid { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.modal-img-item { position: relative; width: 100px; height: 100px; border-radius: 8px; overflow: hidden; border: 1px solid #f0f0f0; flex-shrink: 0; }
.modal-img-item img { width: 100%; height: 100%; object-fit: cover; display: block; }
.modal-img-cover { position: absolute; bottom: 0; left: 0; right: 0; background: var(--color-primary); color: #fff; font-size: 11px; text-align: center; padding: 2px 0; opacity: .85; }
.modal-img-remove { position: absolute; top: 3px; right: 3px; width: 20px; height: 20px; border-radius: 50%; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: background .2s; }
.modal-img-remove:hover { background: rgba(0,0,0,.7); }
.modal-img-remove .icon-x { width: 12px; height: 12px; display: block; filter: brightness(10); }
.modal-img-add { width: 100px; height: 100px; border: 1px dashed #d9d9d9; border-radius: 8px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; cursor: pointer; transition: border-color .2s, background .2s; background: #fafafa; flex-shrink: 0; }
.modal-img-add:hover { border-color: var(--color-primary); background: var(--color-primary-bg); }
.modal-img-add .add-icon { font-size: 28px; color: #bbb; line-height: 1; }
.modal-img-add .add-tip { font-size: 12px; color: #bbb; }
.file-hidden { display: none; }
.modal-actions { display: flex; gap: 8px; justify-content: flex-end; padding-top: 12px; border-top: 1px solid #f0f0f0; }

/* ===== Modal 新增 ===== */
.modal-form { display: flex; flex-direction: column; gap: 12px; }
.text-muted { color: #bbb; font-size: 12px; font-weight: 400; }
.image-list { display: flex; gap: 10px; flex-wrap: wrap; }
.image-item { position: relative; width: 100px; height: 100px; border-radius: 8px; overflow: hidden; border: 1px solid #f0f0f0; flex-shrink: 0; }
.image-item img { width: 100%; height: 100%; object-fit: cover; display: block; }
.image-remove { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; border-radius: 50%; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: background .2s; }
.image-remove:hover { background: rgba(0,0,0,.7); }
.image-remove .icon-x { width: 12px; height: 12px; display: block; filter: brightness(10); }
.image-add { width: 100px; height: 100px; border: 1px dashed #d9d9d9; border-radius: 8px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; cursor: pointer; transition: border-color .2s, background .2s; background: #fafafa; flex-shrink: 0; }
.image-add:hover { border-color: var(--color-primary); background: var(--color-primary-bg); }
.add-icon { font-size: 28px; color: #bbb; line-height: 1; }
.add-text { font-size: 12px; color: #bbb; }

/* ===== 全局覆盖 ===== */
.btn-cancel { padding: 6px 16px; border: 1px solid #d9d9d9; border-radius: 6px; background: #fff; color: #333; font-size: 13px; cursor: pointer; transition: all .2s; }
.btn-cancel:hover { border-color: var(--color-primary); }
.btn-primary { padding: 6px 16px; border: none; border-radius: 6px; background: var(--color-primary); color: #fff; font-size: 13px; cursor: pointer; transition: background .2s; }
.btn-primary:hover { background: var(--color-primary-light); }
.btn-danger { padding: 4px 14px; border: 1px solid var(--color-error); border-radius: 6px; background: #fff; color: var(--color-error); font-size: 13px; cursor: pointer; transition: all .2s; }
.btn-danger:hover { background: var(--color-error); color: #fff; }
.btn-sm { padding: 4px 14px; font-size: 13px; }
.input-error { border-color: #ff4d4f !important; }
.field-error { margin: 2px 0 0; font-size: 12px; color: #ff4d4f; }
.required { color: #ff4d4f; }
</style>
