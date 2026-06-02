<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { usePageStore } from '@/stores/pageStore'
import Modal from '@/components/Modal.vue'
import guanbi from '@/static/icon/guanbi.svg'
import location from '@/static/icon/location.svg'
import canteen from '@/static/icon/canteen.svg'

const router = useRouter()
const store = useAdminStore()
const toast = useToastStore()
const page = usePageStore()

page.setPage({ breadcrumbs: [{ label: '食堂管理' }], showSearch: true, searchPlaceholder: '搜索食堂名称或位置...' })

const showModal = ref(false)

const canteenStats = computed(() => ({
  total: store.canteens.length,
  totalStalls: store.stalls.length,
  totalDishes: store.dishes.length,
}))

const filtered = computed(() => {
  const q = page.searchQuery.trim().toLowerCase()
  if (!q) return store.canteens
  return store.canteens.filter(c => c.name.toLowerCase().includes(q) || (c.location || '').toLowerCase().includes(q))
})

const form = ref({ name: '', location: '', description: '', image: '', sort_order: 0, status: 'active' as 'active' | 'inactive' })
const formErrors = ref<Record<string, string>>({})
const imagePreviews = ref<string[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

function getFirstImage(img: string): string {
  return img.split('|||')[0] || img
}

function handleAddImage() {
  if (imagePreviews.value.length >= 3) return
  fileInput.value?.click()
}

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
  if (!form.value.name.trim()) errs.name = '食堂名称不能为空'
  if (!form.value.location.trim()) errs.location = '位置不能为空'
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

function openAdd() {
  form.value = { name: '', location: '', description: '', image: '', sort_order: 0, status: 'active' }
  imagePreviews.value = []
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
  <div class="page">
    <div class="info-section">
      <div class="section-header">
        <div>
          <h3>食堂概况</h3>
        </div>
      </div>
      <div class="stats-overview">
        <div class="stat-item stat-primary">
          <span class="stat-num">{{ canteenStats.total }}</span>
          <span class="stat-label">食堂总数</span>
        </div>
        <div class="stats-divider"></div>
        <div class="stat-item">
          <span class="stat-num stat-sub">{{ canteenStats.totalStalls }}</span>
          <span class="stat-label">档口</span>
        </div>
        <div class="stats-divider"></div>
        <div class="stat-item">
          <span class="stat-num stat-sub">{{ canteenStats.totalDishes }}</span>
          <span class="stat-label">菜品</span>
        </div>
      </div>
    </div>

    <div class="section-header">
      <h3>食堂列表（{{ canteenStats.total }}）</h3>
      <button class="btn-primary" @click="openAdd">+ 新增食堂</button>
    </div>
    <div class="card-grid">
      <div v-for="c in filtered" :key="Number(c.id)" class="pk-card" @click="enterCanteen(Number(c.id))">
        <div class="pk-img-wrap">
          <img v-if="c.image" :src="getFirstImage(c.image)" :alt="c.name" />
          <div v-else class="pk-emoji"><img :src="canteen" class="pk-emoji-img" alt="" /></div>
        </div>
        <div class="pk-body">
          <h3 class="pk-title">{{ c.name }}</h3>
          <p class="pk-loc"><img :src="location" class="icon-inline" alt="" /> {{ c.location }}</p>
          <p class="pk-desc">{{ c.description }}</p>
          <div class="pk-stats">
            <span>档口 {{ store.stalls.filter(s => Number(s.canteen_id) === Number(c.id)).length }} 个</span>
            <span>菜品 {{ store.dishes.filter(d => store.stalls.some(s => Number(s.canteen_id) === Number(c.id) && Number(s.id) === Number(d.stall_id))).length }} 道</span>
          </div>
        </div>
      </div>
      <div v-if="!filtered.length" class="empty-card">暂无匹配的食堂</div>
    </div>

    <Modal :show="showModal" title="新增食堂" :width="580" @close="showModal = false">
      <div class="modal-form">
        <div class="modal-row">
          <div class="field flex-1"><label>食堂名称 <span class="required">*</span></label><input v-model="form.name" placeholder="输入食堂名称" /><p v-if="formErrors.name" class="field-error">{{ formErrors.name }}</p></div>
          <div class="field flex-1"><label>位置 <span class="required">*</span></label><input v-model="form.location" placeholder="输入食堂位置" /><p v-if="formErrors.location" class="field-error">{{ formErrors.location }}</p></div>
        </div>
        <div class="field"><label>描述</label><textarea v-model="form.description" rows="2" placeholder="输入食堂描述"></textarea></div>
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
      </div>
      <div class="modal-actions">
        <button class="btn-cancel" @click="showModal = false">取消</button>
        <button class="btn-primary" @click="handleSubmit">保存</button>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.info-section { background: #fff; border-radius: var(--radius-lg); box-shadow: var(--shadow-card); margin-bottom: 24px; overflow: hidden; }
.info-section .section-header { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px; border-bottom: 1px solid var(--border-color); }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--border-color); }
.section-header h3 { margin: 0; font-size: 16px; color: var(--text-primary); font-weight: 600; }
.stats-overview { display: flex; align-items: center; justify-content: center; padding: 28px 24px; gap: 32px; }
.stat-item { display: flex; flex-direction: column; align-items: center; min-width: 80px; }
.stat-num { font-size: 36px; font-weight: 700; line-height: 1.1; color: var(--color-primary); }
.stat-sub { font-size: 24px; color: var(--text-primary); }
.stat-label { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }
.stats-divider { width: 1px; height: 40px; background: var(--border-color); flex-shrink: 0; }

.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.pk-card { position: relative; background: #fff; border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-card); cursor: pointer; transition: all .2s; border: 1px solid var(--border-color); }
.pk-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); border-color: var(--color-primary); }
.pk-img-wrap { width: 100%; height: 180px; overflow: hidden; background: #fafafa; display: flex; align-items: center; justify-content: center; }
.pk-img-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pk-emoji { width: 50px; height: 50px; opacity: .35; }
.pk-body { padding: 16px; }
.pk-title { margin: 0 0 4px; font-size: 17px; color: var(--text-primary); font-weight: 600; }
.pk-loc { margin: 0 0 6px; font-size: 13px; color: var(--text-secondary); }
.pk-desc { margin: 0 0 10px; font-size: 13px; color: var(--text-secondary); line-height: 1.5; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.pk-stats { display: flex; gap: 12px; font-size: 12px; color: var(--text-light); }
.empty-card { grid-column: 1 / -1; text-align: center; color: var(--text-light); font-size: 14px; padding: 60px 0; }

.modal-form { display: flex; flex-direction: column; gap: 12px; }
.modal-row { display: flex; gap: 12px; }
.flex-1 { flex: 1; }
.text-muted { color: var(--text-light); font-size: 12px; font-weight: 400; }
.image-list { display: flex; gap: 10px; flex-wrap: wrap; }
.image-item { position: relative; width: 100px; height: 100px; border-radius: var(--radius-md); overflow: hidden; border: 1px solid var(--border-color); flex-shrink: 0; }
.image-item img { width: 100%; height: 100%; object-fit: cover; display: block; }
.image-remove { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; border-radius: 50%; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: background .2s; }
.image-remove:hover { background: rgba(0,0,0,.7); }
.image-remove .icon-x { width: 12px; height: 12px; display: block; filter: brightness(10); }
.image-add { width: 100px; height: 100px; border: 1px dashed #d9d9d9; border-radius: var(--radius-md); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; cursor: pointer; transition: border-color .2s, background .2s; background: #fafafa; flex-shrink: 0; }
.image-add:hover { border-color: var(--color-primary); background: var(--color-primary-bg); }
.add-icon { font-size: 28px; color: var(--text-light); line-height: 1; }
.add-text { font-size: 12px; color: var(--text-light); }
.file-hidden { display: none; }
.icon-inline { width: 1em; height: 1em; vertical-align: -0.125em; display: inline; }
</style>
