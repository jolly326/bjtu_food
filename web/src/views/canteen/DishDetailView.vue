<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { usePageStore } from '@/stores/pageStore'
import Modal from '@/components/Modal.vue'
import EntityImage from '@/components/EntityImage.vue'
import guanbi from '@/static/icon/guanbi.svg'
import Trophy from '@/static/icon/Trophy.svg'
import yellowStar from '@/static/icon/yellow-star.svg'
import comment from '@/static/icon/comment.svg'
import Food from '@/static/icon/Food.svg'
import { uploadImage } from '@/api/upload'

const router = useRouter()
const route = useRoute()
const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const page = usePageStore()

const canteenId = computed(() => Number(route.params.canteenId))
const stallId = computed(() => Number(route.params.stallId))
const dishId = computed(() => Number(route.params.dishId))

const dish = computed(() => store.dishes.find(d => Number(d.id) === dishId.value))
const canteen = computed(() => store.canteens.find(c => Number(c.id) === canteenId.value))
const stall = computed(() => store.stalls.find(s => Number(s.id) === stallId.value))
const reviews = computed(() => store.reviews.filter(r => Number(r.dish_id) === dishId.value))

const activeTab = ref(0)

function getUserName(userId: number | bigint): string {
  const u = store.users.find(u => Number(u.id) === Number(userId))
  return u?.nickname || u?.username || `用户${userId}`
}
function parseTags(tags: string): string[] {
  try { return JSON.parse(tags || '[]') } catch { return [] }
}

const editing = ref(false)
const editForm = ref({ name: '', price: 0, description: '', image: '', tags: '', status: '' })
const editErrors = ref<Record<string, string>>({})

const showImageModal = ref(false)
const modalImages = ref<string[]>([])
const modalFileInput = ref<HTMLInputElement | null>(null)

function parseImages(imgStr: string): string[] { return (imgStr || '').split('|||').filter(Boolean) }
const imageList = computed(() => parseImages(editForm.value.image))
const firstImage = computed(() => imageList.value[0] || '')
const imageCount = computed(() => imageList.value.length)

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
function saveImageModal() {
  editForm.value.image = modalImages.value.join('|||')
  if (dish.value) {
    store.updateDish(Number(dish.value.id), { image: editForm.value.image })
    toast.success('图片已更新')
  }
  showImageModal.value = false
}
function closeImageModal() { showImageModal.value = false }

watch([canteen, stall, dish], ([c, s, d]) => {
  page.setPage({
    breadcrumbs: [
      { label: '食堂管理', path: '/dashboard/canteens' },
      { label: c?.name || '加载中', path: c ? `/dashboard/canteens/${canteenId.value}` : '' },
      { label: s?.name || '加载中', path: s ? `/dashboard/canteens/${canteenId.value}/stalls/${stallId.value}` : '' },
      { label: d?.name || '加载中' },
    ],
  })
  if (d) {
    editForm.value = {
      name: d.name, price: d.price, description: d.description || '',
      image: d.image || '', tags: d.tags || '', status: d.status,
    }
  }
}, { immediate: true })

function toggleEdit() {
  editing.value = true
  activeTab.value = 0
  if (dish.value) {
    editForm.value = {
      name: dish.value.name, price: dish.value.price, description: dish.value.description || '',
      image: dish.value.image || '', tags: dish.value.tags || '', status: dish.value.status,
    }
    editErrors.value = {}
  }
}

function toggleFormTag(tag: string) {
  const arr: string[] = []
  try { arr.push(...JSON.parse(editForm.value.tags || '[]')) } catch {}
  const i = arr.indexOf(tag)
  if (i === -1) { arr.push(tag) } else { arr.splice(i, 1) }
  editForm.value.tags = JSON.stringify(arr)
}

function confirmEdit() {
  const errs: Record<string, string> = {}
  if (!editForm.value.name.trim()) errs.name = '菜品名称不能为空'
  if (!editForm.value.price || editForm.value.price <= 0) errs.price = '价格必须大于 0'
  editErrors.value = errs
  if (Object.keys(errs).length) return
  if (dish.value) {
    store.updateDish(Number(dish.value.id), { ...editForm.value })
    toast.success('菜品已更新')
  }
  editErrors.value = {}
  editing.value = false
}

function cancelEdit() {
  if (dish.value) {
    editForm.value = {
      name: dish.value.name, price: dish.value.price, description: dish.value.description || '',
      image: dish.value.image || '', tags: dish.value.tags || '', status: dish.value.status,
    }
  }
  editErrors.value = {}
  editing.value = false
}

async function deleteDish() {
  if (!dish.value) return
  if (!await confirm.confirm('确定删除该菜品？')) return
  try {
    await store.deleteDish(Number(dish.value.id))
    toast.success('菜品已删除')
    router.push(`/dashboard/canteens/${canteenId.value}/stalls/${stallId.value}`)
  } catch (err: any) {
    toast.error(err.message || '菜品删除失败')
  }
}

async function handleDeleteReview(id: number) {
  if (!await confirm.confirm('确定删除该评论？')) return
  try {
    await store.deleteReview(id)
    toast.success('评论已删除')
  } catch (err: any) {
    toast.error(err.message || '评论删除失败')
  }
}
</script>

<template>
  <div class="page" v-if="dish">
    <!-- 合并头栏 + Tab -->
    <div class="headerbar">
      <div class="headerbar-top">
        <div class="headerbar-thumb" @click="openImageModal">
          <img v-if="firstImage" :src="firstImage" alt="" />
          <img v-else :src="Food" class="thumb-ph" alt="" />
        </div>
        <div class="headerbar-body">
          <div class="headerbar-title-row">
            <h2 class="headerbar-name">{{ dish?.name || '加载中' }}</h2>
            <span class="headerbar-sub">{{ canteen?.name }} · {{ stall?.name }}</span>
          </div>
        </div>
      </div>
      <div class="headerbar-tabs">
        <div class="tab-item" :class="{ active: activeTab === 0 }" @click="activeTab = 0">详情概览</div>
        <div class="tab-count-item" :class="{ active: activeTab === 1 }" @click="activeTab = 1">
          评论管理
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
              <button class="btn-danger btn-sm" @click="deleteDish">删除</button>
            </template>
            <template v-else>
              <button class="btn-cancel btn-sm" @click="cancelEdit">取消</button>
              <button class="btn-primary btn-sm" @click="confirmEdit">保存</button>
            </template>
          </div>
        </div>
        <div class="detail-body">
          <EntityImage :image-url="firstImage" :image-count="imageCount" placeholder-emoji="🥘" @click="openImageModal" />
          <div class="detail-fields">
            <div class="detail-row">
              <span class="detail-label">名称</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ editForm.name }}</span>
                <input v-else v-model="editForm.name" class="form-input" :class="{ 'input-error': editErrors.name }" placeholder="菜品名称" />
                <p v-if="editing && editErrors.name" class="field-error">{{ editErrors.name }}</p>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">价格</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value price">¥{{ editForm.price }}</span>
                <input v-else v-model.number="editForm.price" type="number" min="0" step="0.5" class="form-input" style="width:120px" :class="{ 'input-error': editErrors.price }" placeholder="价格" />
                <p v-if="editing && editErrors.price" class="field-error">{{ editErrors.price }}</p>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">所属</span>
              <div class="detail-control"><span class="detail-value text-muted">{{ canteen?.name }} · {{ stall?.name }}</span></div>
            </div>
            <div class="detail-row">
              <span class="detail-label">标签</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">
                  <span v-if="parseTags(editForm.tags || '').length" class="tag-group">
                    <span v-for="tag in parseTags(editForm.tags || '')" :key="tag" class="dish-tag" :class="tag === '招牌菜' ? 'tag-hot' : 'tag-rec'">
                      <img :src="tag === '招牌菜' ? Trophy : yellowStar" class="tag-icon" alt="" /> {{ tag }}
                    </span>
                  </span>
                  <span v-else class="text-muted">无</span>
                </span>
                <div v-else class="tag-selector">
                  <span class="tag-option" :class="{ active: parseTags(editForm.tags || '').includes('招牌菜') }" @click="toggleFormTag('招牌菜')"><img :src="Trophy" class="tag-icon" alt="" /> 招牌菜</span>
                  <span class="tag-option" :class="{ active: parseTags(editForm.tags || '').includes('必吃推荐') }" @click="toggleFormTag('必吃推荐')"><img :src="yellowStar" class="tag-icon" alt="" /> 必吃推荐</span>
                </div>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">状态</span>
              <div class="detail-control">
                <span v-if="!editing" class="tag" :class="editForm.status === 'active' ? 'tag-green' : 'tag-red'">{{ editForm.status === 'active' ? '在售' : '已下架' }}</span>
                <select v-else v-model="editForm.status" class="form-select">
                  <option value="active">在售</option>
                  <option value="inactive">已下架</option>
                </select>
              </div>
            </div>
            <div class="detail-row detail-row-desc">
              <span class="detail-label">介绍</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value text-desc">{{ editForm.description || '暂无介绍' }}</span>
                <textarea v-else v-model="editForm.description" class="form-textarea" rows="2" placeholder="菜品详细介绍"></textarea>
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
            <span class="stat-num" style="color:#F5A623">{{ dish?.avg_rating ?? 0 }}</span>
            <span class="stat-unit">评分</span>
          </div>
          <div class="stat-card">
            <span class="stat-num">{{ dish?.rating_count ?? 0 }}</span>
            <span class="stat-unit">人评分</span>
          </div>
          <div class="stat-card">
            <span class="stat-num">{{ dish?.favorite_count ?? 0 }}</span>
            <span class="stat-unit">人收藏</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 2: 评论管理 -->
    <div v-show="activeTab === 1" class="tab-panel">
      <div class="list-bar">
        <h3><img :src="comment" class="icon-inline" alt="" /> 评论管理 <span class="count-tag">共 {{ reviews.length }} 条</span></h3>
      </div>
      <div class="table-wrap" v-if="reviews.length">
        <table class="table">
          <thead><tr><th>ID</th><th>用户</th><th>评分</th><th>内容</th><th>时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="r in reviews" :key="Number(r.id)">
              <td>{{ r.id }}</td><td>{{ getUserName(r.user_id) }}</td>
              <td class="stars">
                <img v-for="n in r.rating" :key="n" :src="yellowStar" class="star-icon" alt="" />
              </td>
              <td class="ellipsis" :title="r.content">{{ r.content }}</td>
              <td>{{ r.created_at.toLocaleDateString('zh-CN') }}</td>
              <td class="actions"><button class="link danger" @click="handleDeleteReview(Number(r.id))">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">暂无评论</div>
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
  </div>
  <div class="page" v-else><div class="empty-state big">菜品不存在</div></div>
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
.detail-value.price { color: #ff4d4f; font-weight: 700; font-size: 17px; }
.detail-value.text-desc { font-weight: 400; color: #555; line-height: 1.6; }
.detail-value.text-muted { font-weight: 400; color: #888; }
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
.list-bar h3 { margin: 0; font-size: 16px; color: #333; font-weight: 600; display: flex; align-items: center; gap: 6px; }
.icon-inline { width: 1em; height: 1em; vertical-align: -0.125em; display: inline; }
.count-tag { font-size: 13px; font-weight: 400; color: #999; margin-left: 4px; }

/* ===== 评论列表 ===== */
.table-wrap { overflow-x: auto; }
.table { width: 100%; border-collapse: collapse; font-size: 14px; }
.table th, .table td { text-align: left; padding: 10px 12px; border-bottom: 1px solid #f0f0f0; }
.table th { background: #fafafa; color: #666; font-weight: 500; white-space: nowrap; }
.table td { color: #333; }
.table tbody tr:hover { background: #fafafa; }
.table .ellipsis { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.table .actions { white-space: nowrap; }
.stars { display: flex; gap: 2px; }
.star-icon { width: 14px; height: 14px; display: inline; }
.link { background: none; border: none; color: var(--color-primary); cursor: pointer; font-size: 13px; padding: 2px 0; }
.link:hover { opacity: .8; }
.danger { color: var(--color-error) !important; }
.empty-state { text-align: center; color: #ccc; padding: 40px 0; font-size: 14px; }
.empty-state.big { font-size: 16px; padding: 100px 0; }

/* ===== 标签样式 ===== */
.tag-group { display: flex; gap: 4px; flex-wrap: wrap; }
.dish-tag { font-size: 12px; padding: 2px 10px; border-radius: 8px; font-weight: 500; display: inline-flex; align-items: center; gap: 3px; }
.tag-hot { background: #fff7e6; color: #fa8c16; }
.tag-rec { background: #f6ffed; color: #52c41a; }
.tag-icon { width: 12px; height: 12px; display: inline; }
.tag-selector { display: flex; gap: 8px; flex-wrap: wrap; }
.tag-option { padding: 6px 14px; border: 1px solid #d9d9d9; border-radius: 16px; font-size: 13px; cursor: pointer; transition: all .2s; user-select: none; display: inline-flex; align-items: center; gap: 4px; }
.tag-option:hover { border-color: var(--color-primary); color: var(--color-primary); }
.tag-option.active { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary); font-weight: 500; }
.text-muted { color: #bbb; font-weight: 400; }

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
