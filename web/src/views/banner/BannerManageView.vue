<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { usePageStore } from '@/stores/pageStore'
import Modal from '@/components/Modal.vue'
import guanbi from '@/static/icon/guanbi.svg'

const store = useAdminStore()
const toast = useToastStore()
const page = usePageStore()
page.setPage({ breadcrumbs: [{ label: '轮播管理' }], showSearch: true, searchPlaceholder: '搜索Banner标题...' })

const showModal = ref(false)
const editingId = ref<number | null>(null)

const filtered = computed(() => {
  const q = page.searchQuery.trim().toLowerCase()
  if (!q) return store.banners
  return store.banners.filter(b => b.title.toLowerCase().includes(q))
})

const form = ref({ title: '', image: '', status: 'active' as 'active' | 'inactive' })
const formErrors = ref<Record<string, string>>({})
const fileInput = ref<HTMLInputElement | null>(null)

/** 图片是单张 base64 字符串 */
const hasImage = computed(() => !!form.value.image)
const imageUrl = computed(() => form.value.image)

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { toast.error('请选择图片文件'); return }
  form.value.image = URL.createObjectURL(file)
  input.value = ''
}

function removeImage() {
  form.value.image = ''
}

function validate() {
  const errs: Record<string, string> = {}
  if (!form.value.title.trim()) errs.title = 'Banner标题不能为空'
  formErrors.value = errs
  return Object.keys(errs).length === 0
}

function openAdd() {
  editingId.value = null
  form.value = { title: '', image: '', status: 'active' }
  formErrors.value = {}
  showModal.value = true
}

function openEdit(id: number) {
  const banner = store.banners.find(b => Number(b.id) === id)
  if (!banner) return
  editingId.value = id
  form.value = { title: banner.title, image: banner.image || '', status: banner.status as 'active' | 'inactive' }
  formErrors.value = {}
  showModal.value = true
}

function handleSubmit() {
  if (!validate()) return
  if (editingId.value !== null) {
    store.updateBanner(editingId.value, { title: form.value.title, image: form.value.image, status: form.value.status })
    toast.success('Banner已更新')
  } else {
    store.addBanner({ title: form.value.title, image: form.value.image, status: form.value.status, sort_order: store.maxBannerSortOrder + 1, type: 'carousel' })
    toast.success('Banner已添加')
  }
  showModal.value = false
}

function handleDelete(id: number) {
  store.deleteBanner(id)
  toast.success('Banner已删除')
}
</script>

<template>
  <div class="page">
    <div class="card-hd">
      <h3>轮播列表</h3>
      <button class="btn-primary" @click="openAdd">+ 新增轮播图</button>
    </div>

    <div class="card-grid">
      <div v-for="b in filtered" :key="Number(b.id)" class="banner-card">
        <div class="banner-img-wrap">
          <img v-if="b.image" :src="b.image" :alt="b.title" />
          <div v-else class="banner-img-placeholder">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
          </div>
        </div>
        <div class="banner-body">
          <h4 class="banner-title">{{ b.title }}</h4>
          <div class="banner-footer">
            <span class="tag" :class="b.status === 'active' ? 'tag-green' : 'tag-gray'">{{ b.status === 'active' ? '轮播中' : '已停用' }}</span>
            <div class="banner-actions">
              <button class="link btn-sm" @click.stop="openEdit(Number(b.id))">编辑</button>
              <button class="link danger btn-sm" @click.stop="handleDelete(Number(b.id))">删除</button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="!filtered.length" class="empty-card">暂无轮播Banner，点击上方按钮添加</div>
    </div>

    <Modal :show="showModal" :title="editingId !== null ? '编辑轮播图' : '新增轮播图'" :width="480" @close="showModal = false">
      <div class="modal-form">
        <div class="field">
          <label>标题 <span class="required">*</span></label>
          <input v-model="form.title" placeholder="输入Banner标题，如：开学季优惠" />
          <p v-if="formErrors.title" class="field-error">{{ formErrors.title }}</p>
        </div>
        <div class="field">
          <label>状态</label>
          <select v-model="form.status">
            <option value="active">轮播中</option>
            <option value="inactive">已停用</option>
          </select>
        </div>
        <div class="field">
          <label>轮播图片</label>
          <div class="single-image-upload">
            <template v-if="hasImage">
              <div class="image-preview">
                <img :src="imageUrl" alt="预览" />
                <span class="image-remove-btn" @click="removeImage"><img :src="guanbi" class="icon-x" alt="" /></span>
              </div>
            </template>
            <template v-else>
              <div class="image-upload-box" @click="fileInput?.click()">
                <span class="upload-icon">+</span>
                <span class="upload-text">点击上传图片</span>
                <span class="upload-hint">建议尺寸 750×360px</span>
              </div>
            </template>
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


.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }

.banner-card { background: #fff; border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-card); border: 1px solid var(--border-color); transition: all .2s; }
.banner-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); border-color: var(--color-primary); }

.banner-img-wrap { position: relative; width: 100%; height: 160px; overflow: hidden; background: #fafafa; display: flex; align-items: center; justify-content: center; }
.banner-img-wrap img { width: 100%; height: 100%; object-fit: cover; display: block; }
.banner-img-placeholder { width: 48px; height: 48px; opacity: .4; display: flex; align-items: center; justify-content: center; }

.banner-body { padding: 14px 16px; }
.banner-title { margin: 0 0 4px; font-size: 16px; color: var(--text-primary); font-weight: 600; }

.banner-footer { display: flex; align-items: center; justify-content: space-between; padding-top: 10px; border-top: 1px solid var(--border-color); }
.banner-actions { display: flex; gap: 8px; }

.empty-card { grid-column: 1 / -1; text-align: center; color: var(--text-light); font-size: 14px; padding: 60px 0; }

/* 弹窗 */
.modal-form { display: flex; flex-direction: column; gap: 4px; }

/* 单张图片上传 */
.single-image-upload { min-height: 120px; }
.image-preview { position: relative; width: 160px; height: 120px; border-radius: var(--radius-md); overflow: hidden; border: 1px solid var(--border-color); }
.image-preview img { width: 100%; height: 100%; object-fit: cover; display: block; }
.image-remove-btn { position: absolute; top: 4px; right: 4px; width: 22px; height: 22px; border-radius: 50%; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: background .2s; }
.image-remove-btn:hover { background: rgba(0,0,0,.7); }
.image-remove-btn .icon-x { width: 13px; height: 13px; display: block; filter: brightness(10); }

.image-upload-box { width: 200px; height: 120px; border: 1px dashed #d9d9d9; border-radius: var(--radius-md); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; cursor: pointer; transition: border-color .2s, background .2s; background: #fafafa; }
.image-upload-box:hover { border-color: var(--color-primary); background: var(--color-primary-bg); }
.upload-icon { font-size: 28px; color: var(--text-light); line-height: 1; }
.upload-text { font-size: 13px; color: var(--text-light); }
.upload-hint { font-size: 11px; color: var(--text-light); opacity: .7; }

.file-hidden { display: none; }
</style>
