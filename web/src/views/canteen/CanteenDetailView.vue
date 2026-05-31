<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { usePageStore } from '@/stores/pageStore'
import Modal from '@/components/Modal.vue'
import EntityImage from '@/components/EntityImage.vue'
import guanbi from '@/static/icon/guanbi.svg'
import shop from '@/static/icon/shop.svg'

const router = useRouter()
const route = useRoute()
const store = useAdminStore()
const toast = useToastStore()
const page = usePageStore()

const canteenId = computed(() => Number(route.params.canteenId))
const canteen = computed(() => store.canteens.find(c => Number(c.id) === canteenId.value))

const editing = ref(false)
const canteenForm = ref({ name: '', location: '', description: '', image: '' })
const originalCanteen = ref<Record<string, any>>({})
const canteenFormErrors = ref<Record<string, string>>({})

const showImageModal = ref(false)
const modalImages = ref<string[]>([])
const modalFileInput = ref<HTMLInputElement | null>(null)

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
  modalImages.value = [...imageList.value]
  showImageModal.value = true
}
function modalAddImage() { modalFileInput.value?.click() }
function modalHandleFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { toast.error('请选择图片文件'); return }
  const reader = new FileReader()
  reader.onload = () => { modalImages.value.push(reader.result as string) }
  reader.readAsDataURL(file)
  input.value = ''
}
function modalRemoveImage(idx: number) { modalImages.value.splice(idx, 1) }
function saveImageModal() {
  canteenForm.value.image = modalImages.value.join('|||')
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
    showSearch: true,
    searchPlaceholder: '搜索档口名称...',
  })
  if (c) {
    canteenForm.value = { name: c.name, location: c.location || '', description: c.description || '', image: c.image || '' }
    originalCanteen.value = { ...canteenForm.value }
  }
}, { immediate: true })

function toggleEdit() {
  editing.value = true
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

function deleteCanteen() {
  if (!canteen.value) return
  toast.success('食堂已删除')
  store.deleteCanteen(Number(canteen.value.id))
  router.push('/dashboard/canteens')
}

const showModal = ref(false)
const filtered = computed(() => {
  const q = page.searchQuery.trim().toLowerCase()
  if (!q) return stalls.value
  return stalls.value.filter(s => s.name.toLowerCase().includes(q) || (s.description || '').toLowerCase().includes(q))
})

const form = ref({ name: '', description: '', image: '', status: 'active' as 'active' | 'inactive' })
const formErrors = ref<Record<string, string>>({})
const imagePreviews = ref<string[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

function handleAddImage() { fileInput.value?.click() }
function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { toast.error('请选择图片文件'); return }
  const reader = new FileReader()
  reader.onload = () => {
    imagePreviews.value.push(reader.result as string)
    form.value.image = imagePreviews.value.join('|||')
  }
  reader.readAsDataURL(file)
  input.value = ''
}
function removeImage(idx: number) {
  imagePreviews.value.splice(idx, 1)
  form.value.image = imagePreviews.value.join('|||')
}

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.name.trim()) errs.name = '档口名称不能为空'
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

function openAdd() { form.value = { name: '', description: '', image: '', status: 'active' }; formErrors.value = {}; imagePreviews.value = []; showModal.value = true }
function handleSubmit() {
  if (!validate()) return
  store.addStall({ canteen_id: canteenId.value as unknown as bigint, ...form.value, sort_order: 0, avg_rating: 0 })
  toast.success('档口已添加')
  showModal.value = false
}
function enterStall(id: number) { router.push(`/dashboard/canteens/${canteenId.value}/stalls/${id}`) }
</script>

<template>
  <div class="page">
    <div class="info-section">
      <div class="section-header">
        <h3>食堂信息</h3>
        <div class="header-actions">
          <template v-if="!editing">
            <button class="btn-primary btn-sm" @click="toggleEdit">编辑</button>
            <button class="btn-danger btn-sm" @click="deleteCanteen">删除食堂</button>
          </template>
          <template v-else>
            <button class="btn-cancel btn-sm" @click="cancelEdit">取消</button>
            <button class="btn-primary btn-sm" @click="confirmEdit">保存</button>
          </template>
        </div>
      </div>

      <div class="info-main">
        <div class="info-left">
          <EntityImage :image-url="firstImage" :image-count="imageCount" placeholder-emoji="🏪" @click="openImageModal" />
          <div class="info-fields">
            <div class="field-row">
              <span class="field-label">名称</span>
              <div class="field-control">
                <span v-if="!editing" class="field-value">{{ canteenForm.name }}</span>
                <input v-else v-model="canteenForm.name" class="inline-input" :class="{ 'input-error': canteenFormErrors.name }" placeholder="食堂名称" />
                <p v-if="editing && canteenFormErrors.name" class="field-error">{{ canteenFormErrors.name }}</p>
              </div>
            </div>
            <div class="field-row">
              <span class="field-label">位置</span>
              <div class="field-control">
                <span v-if="!editing" class="field-value">{{ canteenForm.location }}</span>
                <input v-else v-model="canteenForm.location" class="inline-input" :class="{ 'input-error': canteenFormErrors.location }" placeholder="食堂位置" />
                <p v-if="editing && canteenFormErrors.location" class="field-error">{{ canteenFormErrors.location }}</p>
              </div>
            </div>
            <div class="field-row field-row-desc">
              <span class="field-label">描述</span>
              <div class="field-control">
                <span v-if="!editing" class="field-value text-desc">{{ canteenForm.description || '-' }}</span>
                <textarea v-else v-model="canteenForm.description" class="inline-textarea" rows="2" placeholder="食堂描述"></textarea>
              </div>
            </div>
          </div>
        </div>
        <div class="info-stats-side">
          <div class="side-stat">
            <span class="side-stat-num">{{ stalls.length }}</span>
            <span class="side-stat-unit">个档口</span>
          </div>
          <div class="side-stat-divider"></div>
          <div class="side-stat">
            <span class="side-stat-num">{{ dishCount }}</span>
            <span class="side-stat-unit">道菜品</span>
          </div>
        </div>
      </div>
    </div>

    <div class="section-header">
      <h3>档口列表（{{ stalls.length }}）</h3>
      <button class="btn-primary" @click="openAdd">+ 新增档口</button>
    </div>
    <div class="card-grid">
      <div v-for="s in filtered" :key="Number(s.id)" class="pk-card" @click="enterStall(Number(s.id))">
        <div class="pk-img-wrap">
          <img v-if="s.image" :src="s.image" :alt="s.name" />
          <div v-else class="pk-emoji"><img :src="shop" class="pk-emoji-img" alt="" /></div>
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

    <Modal :show="showImageModal" title="图片管理" :width="480" @close="closeImageModal">
      <div class="modal-img-grid">
        <div v-for="(img, idx) in modalImages" :key="idx" class="modal-img-item">
          <img :src="img" alt="" />
          <span v-if="idx === 0" class="modal-img-cover">封面</span>
          <span class="modal-img-remove" @click="modalRemoveImage(idx)"><img :src="guanbi" class="icon-x" alt="" /></span>
        </div>
        <div class="modal-img-add" @click="modalAddImage">
          <span class="add-icon">+</span>
          <span class="add-tip">添加图片</span>
        </div>
      </div>
      <input ref="modalFileInput" type="file" accept="image/*" class="file-hidden" @change="modalHandleFile" />
      <div class="modal-actions">
        <button class="btn-cancel" @click="closeImageModal">取消</button>
        <button class="btn-primary" @click="saveImageModal">保存</button>
      </div>
    </Modal>

    <Modal :show="showModal" title="新增档口" @close="showModal = false">
      <div class="modal-form">
        <div class="field"><label>名称 <span class="required">*</span></label><input v-model="form.name" /><p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p></div>
        <div class="field"><label>描述</label><textarea v-model="form.description" rows="3"></textarea></div>
        <div class="field">
          <label>图片</label>
          <div class="image-list">
            <div v-for="(preview, idx) in imagePreviews" :key="idx" class="image-item">
              <img :src="preview" alt="预览" />
              <span class="image-remove" @click="removeImage(idx)"><img :src="guanbi" class="icon-x" alt="" /></span>
            </div>
            <div class="image-add" @click="handleAddImage">
              <span class="add-icon">+</span>
              <span class="add-text">添加图片</span>
            </div>
          </div>
          <input ref="fileInput" type="file" accept="image/*" class="file-hidden" @change="handleFileChange" />
        </div>
      </div>
      <div class="modal-actions"><button class="btn-cancel" @click="showModal = false">取消</button><button class="btn-primary" @click="handleSubmit">保存</button></div>
    </Modal>
  </div>
</template>

<style scoped>
.info-section { background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,.06); margin-bottom: 24px; overflow: hidden; }
.info-section .section-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 24px; border-bottom: 1px solid #f0f0f0; }
.section-header { display: flex; align-items: center; justify-content: space-between; padding-bottom: 12px; margin-bottom: 16px; border-bottom: 1px solid #f0f0f0; }
.section-header h3 { margin: 0; font-size: 16px; color: #333; font-weight: 600; }
.header-actions { display: flex; gap: 8px; align-items: center; }
.info-main { display: flex; gap: 0; padding: 0 24px 24px 24px; }
.info-left { display: flex; gap: 24px; flex: 1; min-width: 0; }
.info-stats-side { width: 140px; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; border-left: 1px solid var(--border-light); margin-left: 24px; padding-left: 24px; }
.side-stat { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.side-stat-num { font-size: 24px; font-weight: 700; color: var(--color-primary); line-height: 1.2; }
.side-stat-unit { font-size: 12px; color: var(--text-muted); }
.side-stat-divider { width: 32px; height: 1px; background: var(--border-light); }
.info-fields { flex: 1; display: flex; flex-direction: column; gap: 10px; }
.field-row { display: flex; align-items: center; gap: 12px; }
.field-row-desc { align-items: flex-start; }
.field-label { font-size: 13px; color: #999; width: 48px; flex-shrink: 0; line-height: 28px; }
.field-control { flex: 1; min-width: 0; }
.field-value { font-size: 15px; color: #333; font-weight: 500; line-height: 28px; }
.field-value.text-desc { font-weight: 400; color: #555; line-height: 1.6; }
.field-error { margin: 2px 0 0; font-size: 12px; color: #ff4d4f; }
.inline-input { padding: 5px 10px; border: 1px solid #d9d9d9; border-radius: 6px; font-size: 14px; font-weight: 500; color: #333; outline: none; transition: border-color .2s; background: #fff; width: 100%; box-sizing: border-box; }
.inline-input:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px rgba(139,58,43,.15); }
.inline-textarea { padding: 6px 10px; border: 1px solid #d9d9d9; border-radius: 6px; font-size: 14px; color: #333; outline: none; transition: border-color .2s; background: #fff; width: 100%; box-sizing: border-box; resize: vertical; min-height: 50px; font-weight: 400; }
.inline-textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 2px rgba(139,58,43,.15); }
.input-error { border-color: #ff4d4f !important; }
.btn-cancel { padding: 6px 16px; border: 1px solid #d9d9d9; border-radius: 6px; background: #fff; color: #333; font-size: 13px; cursor: pointer; transition: all .2s; }
.btn-cancel:hover { border-color: var(--color-primary); }
.btn-primary { padding: 6px 16px; border: none; border-radius: 6px; background: var(--color-primary); color: #fff; font-size: 13px; cursor: pointer; transition: background .2s; }
.btn-primary:hover { background: var(--color-primary-light); }
.btn-danger { padding: 4px 14px; border: 1px solid var(--color-error); border-radius: 6px; background: #fff; color: var(--color-error); font-size: 13px; cursor: pointer; transition: all .2s; }
.btn-danger:hover { background: var(--color-error); color: #fff; }
.required { color: #ff4d4f; }

/* 弹窗图片管理 */
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

/* Poke 卡片 */
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.pk-card { position: relative; background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,.06); cursor: pointer; transition: all .2s; border: 1px solid #f0f0f0; }
.pk-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); border-color: var(--color-primary); }
.pk-img-wrap { position: relative; width: 100%; height: 160px; overflow: hidden; background: #fafafa; display: flex; align-items: center; justify-content: center; }
.pk-img-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pk-emoji { width: 48px; height: 48px; opacity: .35; }
.pk-status { position: absolute; top: 8px; right: 8px; padding: 2px 10px; border-radius: 8px; font-size: 11px; font-weight: 500; line-height: 1.6; }
.status-on { background: var(--color-success-bg); color: var(--color-success); }
.status-off { background: var(--color-error-bg); color: var(--color-error); }
.pk-body { padding: 14px 16px; }
.pk-title { margin: 0 0 6px; font-size: 16px; color: #333; font-weight: 600; }
.pk-desc { margin: 0 0 8px; font-size: 13px; color: rgba(0,0,0,.45); line-height: 1.5; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.pk-stats { font-size: 12px; color: #bbb; }
.empty-card { grid-column: 1 / -1; text-align: center; color: #ccc; padding: 60px 0; }

/* 新增档口弹窗图片上传 */
.modal-form { display: flex; flex-direction: column; gap: 12px; }
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
</style>
