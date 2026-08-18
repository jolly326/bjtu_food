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
import DataTable from '@/components/DataTable.vue'
import { Trophy, Star, Food } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()
const page = usePageStore()

function goBack() {
  router.push(`/dashboard/canteens/${canteenId.value}/stalls/${stallId.value}`)
}

const statusOptions = [
  { label: '在售', value: 'active' },
  { label: '已下架', value: 'inactive' },
]

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

// 菜品属性枚举（与后端 DishAttrConst 对齐）
const SPICE_OPTIONS = [
  { value: 0, label: '不辣' },
  { value: 1, label: '微辣' },
  { value: 2, label: '中辣' },
  { value: 3, label: '重辣' },
]
const PORTION_OPTIONS = [
  { value: 0, label: '小份' },
  { value: 1, label: '中份' },
  { value: 2, label: '大份' },
]
const SERVE_PERIOD_OPTIONS = [
  { value: 'breakfast', label: '早餐' },
  { value: 'lunch', label: '午餐' },
  { value: 'dinner', label: '晚餐' },
  { value: 'midnight', label: '夜宵' },
]

function parseServePeriod(sp: string): string[] {
  if (!sp) return []
  return sp.split(',').map(s => s.trim()).filter(Boolean)
}
function servePeriodLabel(key: string): string {
  return SERVE_PERIOD_OPTIONS.find(o => o.value === key)?.label || key
}
function spiceLabel(v?: number): string {
  return SPICE_OPTIONS.find(o => o.value === v)?.label || '不辣'
}
function portionLabel(v?: number): string {
  return PORTION_OPTIONS.find(o => o.value === v)?.label || '小份'
}

const editing = ref(false)
const editForm = ref({ name: '', price: 0, description: '', image: '', tags: '', status: '', spiceLevel: 0, portion: 0, servePeriod: '', limited: 0, originalPrice: 0, promoPrice: 0 })
const editErrors = ref<Record<string, string>>({})

const showImageModal = ref(false)

function parseImages(imgStr: string): string[] { return (imgStr || '').split('|||').filter(Boolean) }
const imageList = computed(() => parseImages(editForm.value.image))
const firstImage = computed(() => imageList.value[0] || '')
const imageCount = computed(() => imageList.value.length)

function openImageModal() {
  showImageModal.value = true
}
async function saveImageModal() {
  if (dish.value) {
    try {
      await store.updateDish(Number(dish.value.id), { image: editForm.value.image })
      toast.success('图片已更新')
    } catch (err: any) {
      toast.error(err.message || '图片保存失败')
      return
    }
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
      spiceLevel: d.spiceLevel ?? 0, portion: d.portion ?? 0,
      servePeriod: d.servePeriod || '', limited: d.limited ?? 0,
      originalPrice: d.originalPrice ? d.originalPrice : 0,
      promoPrice: d.promoPrice ? d.promoPrice : 0,
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
      spiceLevel: dish.value.spiceLevel ?? 0, portion: dish.value.portion ?? 0,
      servePeriod: dish.value.servePeriod || '', limited: dish.value.limited ?? 0,
      originalPrice: dish.value.originalPrice ? dish.value.originalPrice : 0,
      promoPrice: dish.value.promoPrice ? dish.value.promoPrice : 0,
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

async function confirmEdit() {
  const errs: Record<string, string> = {}
  if (!editForm.value.name.trim()) errs.name = '菜品名称不能为空'
  if (!editForm.value.price || editForm.value.price <= 0) errs.price = '价格必须大于 0'
  // 折扣价校验：促销价必须低于原价（无原价时低于常规价），且不为负
  const basePrice = editForm.value.originalPrice > 0 ? editForm.value.originalPrice : editForm.value.price
  if (editForm.value.originalPrice < 0) errs.originalPrice = '原价不能为负'
  if (editForm.value.promoPrice < 0) errs.promoPrice = '促销价不能为负'
  if (editForm.value.promoPrice > 0 && editForm.value.promoPrice >= basePrice) {
    errs.promoPrice = '促销价须低于原价/常规价'
  }
  editErrors.value = errs
  if (Object.keys(errs).length) return
  if (dish.value) {
    const payload: any = { ...editForm.value }
    // promoPrice 为空（0）时置 null，表示无折扣
    if (!payload.promoPrice) payload.promoPrice = null
    try {
      await store.updateDish(Number(dish.value.id), payload)
      toast.success('菜品已更新')
    } catch (err: any) {
      toast.error(err.message || '菜品更新失败')
      return
    }
  }
  editErrors.value = {}
  editing.value = false
}

function cancelEdit() {
  if (dish.value) {
    editForm.value = {
      name: dish.value.name, price: dish.value.price, description: dish.value.description || '',
      image: dish.value.image || '', tags: dish.value.tags || '', status: dish.value.status,
      spiceLevel: dish.value.spiceLevel ?? 0, portion: dish.value.portion ?? 0,
      servePeriod: dish.value.servePeriod || '', limited: dish.value.limited ?? 0,
      originalPrice: dish.value.originalPrice ? dish.value.originalPrice : 0,
      promoPrice: dish.value.promoPrice ? dish.value.promoPrice : 0,
    }
  }
  editErrors.value = {}
  editing.value = false
}

// 供应时段多选切换（逗号分隔存储）
function toggleServePeriod(key: string) {
  const arr = parseServePeriod(editForm.value.servePeriod)
  const i = arr.indexOf(key)
  if (i === -1) arr.push(key)
  else arr.splice(i, 1)
  editForm.value.servePeriod = arr.join(',')
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
    if (reviewDetail.value && Number(reviewDetail.value.id) === Number(id)) reviewDetail.value = null
  } catch (err: any) {
    toast.error(err.message || '评论删除失败')
  }
}
function parseReviewImages(img?: string): string[] {
  return (img || '').split('|||').map(s => s.trim()).filter(Boolean)
}
const reviewDetail = ref<any | null>(null)
function openReviewDetail(r: any) { reviewDetail.value = r }
function closeReviewDetail() { reviewDetail.value = null }
async function toggleReviewHidden(r: any, hidden: boolean) {
  try {
    await store.updateReview(Number(r.id), { is_hidden: hidden ? 1 : 0 })
    toast.success(hidden ? '评价已隐藏' : '评价已显示')
    if (reviewDetail.value && Number(reviewDetail.value.id) === Number(r.id)) reviewDetail.value = null
  } catch (err: any) {
    toast.error(err.message || '操作失败')
  }
}
</script>

<template>
  <PageContainer v-if="dish">
    <PageHeader
      :back="true"
      :title="dish?.name || '加载中'"
      :subtitle="(canteen?.name || '') + ' · ' + (stall?.name || '')"
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
        评论管理
      </div>
    </div>

    <!-- Tab 1: 详情概览 -->
    <template v-if="activeTab === 0">
      <PageSection title="基本信息">
        <template #header-extra>
          <div class="panel-actions">
            <template v-if="!editing">
              <button class="btn-primary btn-sm" v-press @click="toggleEdit">编辑</button>
              <button class="btn-danger btn-sm" v-press @click="deleteDish">删除</button>
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
                <span v-if="!editing" class="detail-value">{{ editForm.name }}</span>
                <input v-else v-model="editForm.name" class="form-input" :class="{ 'input-error': editErrors.name }" placeholder="菜品名称" />
                <p v-if="editing && editErrors.name" class="field-error">{{ editErrors.name }}</p>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">价格</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value price">¥{{ editForm.price }}</span>
                <input v-else v-model.number="editForm.price" type="number" min="0" step="0.5" class="form-input price-input" :class="{ 'input-error': editErrors.price }" placeholder="价格" />
                <p v-if="editing && editErrors.price" class="field-error">{{ editErrors.price }}</p>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">折扣价</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">
                  <template v-if="editForm.promoPrice > 0">
                    <span class="price">¥{{ editForm.promoPrice }}</span>
                    <span class="origin-price" v-if="editForm.originalPrice > 0">¥{{ editForm.originalPrice }}</span>
                    <span class="promo-badge" v-else>促销</span>
                  </template>
                  <span v-else class="text-muted">无折扣</span>
                </span>
                <div v-else class="discount-edit">
                  <label class="mini-label">原价（元）</label>
                  <input v-model.number="editForm.originalPrice" type="number" min="0" step="0.5" class="form-input price-input" :class="{ 'input-error': editErrors.originalPrice }" placeholder="选填" />
                  <label class="mini-label">促销价（元）</label>
                  <input v-model.number="editForm.promoPrice" type="number" min="0" step="0.5" class="form-input price-input" :class="{ 'input-error': editErrors.promoPrice }" placeholder="选填，留空=无折扣" />
                  <p v-if="editErrors.originalPrice" class="field-error">{{ editErrors.originalPrice }}</p>
                  <p v-if="editErrors.promoPrice" class="field-error">{{ editErrors.promoPrice }}</p>
                </div>
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
                      <el-icon class="tag-icon"><component :is="tag === '招牌菜' ? Trophy : Star" /></el-icon> {{ tag }}
                    </span>
                  </span>
                  <span v-else class="text-muted">无</span>
                </span>
                <div v-else class="tag-selector">
                  <span class="tag-option" :class="{ active: parseTags(editForm.tags || '').includes('招牌菜') }" @click="toggleFormTag('招牌菜')"><el-icon class="tag-icon"><Trophy /></el-icon> 招牌菜</span>
                  <span class="tag-option" :class="{ active: parseTags(editForm.tags || '').includes('必吃推荐') }" @click="toggleFormTag('必吃推荐')"><el-icon class="tag-icon"><Star /></el-icon> 必吃推荐</span>
                </div>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">辣度</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ spiceLabel(editForm.spiceLevel) }}</span>
                <el-select v-else v-model="editForm.spiceLevel" class="form-select-el" placeholder="选择辣度">
                  <el-option v-for="opt in SPICE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">分量</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ portionLabel(editForm.portion) }}</span>
                <el-select v-else v-model="editForm.portion" class="form-select-el" placeholder="选择分量">
                  <el-option v-for="opt in PORTION_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </div>
            </div>
            <div class="detail-row detail-row-desc">
              <span class="detail-label">供应时段</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">
                  <span v-if="parseServePeriod(editForm.servePeriod).length" class="tag-group">
                    <span v-for="p in parseServePeriod(editForm.servePeriod)" :key="p" class="dish-tag tag-rec">{{ servePeriodLabel(p) }}</span>
                  </span>
                  <span v-else class="text-muted">无</span>
                </span>
                <div v-else class="tag-selector">
                  <span
                    v-for="opt in SERVE_PERIOD_OPTIONS"
                    :key="opt.value"
                    class="tag-option"
                    :class="{ active: parseServePeriod(editForm.servePeriod).includes(opt.value) }"
                    @click="toggleServePeriod(opt.value)"
                  >{{ opt.label }}</span>
                </div>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">是否限量</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value">{{ editForm.limited ? '限量供应' : '不限量' }}</span>
                <el-switch v-else v-model="editForm.limited" :active-value="1" :inactive-value="0" active-text="限量" inactive-text="不限" />
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">状态</span>
              <div class="detail-control">
                <span v-if="!editing" class="tag" :class="editForm.status === 'active' ? 'tag-green' : 'tag-red'">{{ editForm.status === 'active' ? '在售' : '已下架' }}</span>
                <FilterSelect
                  v-else
                  v-model="editForm.status"
                  :options="statusOptions"
                  :clearable="false"
                  width="160"
                />
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
      </PageSection>

      <PageSection title="数据统计">
        <div class="stats-row">
          <StatCard label="评分" :value="dish?.avg_rating ?? 0" tone="star" />
          <StatCard label="人评分" :value="dish?.rating_count ?? 0" />
        </div>
      </PageSection>
    </template>

    <!-- Tab 2: 评论管理（直观展示评分/内容/图片/用户，可查看详情/删除） -->
    <template v-if="activeTab === 1">
      <PageSection>
        <template #header-extra>
          <span class="count-tag">共 {{ reviews.length }} 条</span>
        </template>
        <DataTable
          :columns="[
            { prop: 'user', label: '用户', width: '150px' },
            { prop: 'rating', label: '评分', width: '120px', sortable: true, sortValue: (row) => row.rating },
            { prop: 'content', label: '内容', ellipsis: true },
            { prop: 'status', label: '状态', width: '110px', align: 'center' },
            { prop: 'time', label: '时间', width: '150px', sortable: true, sortValue: (row) => row.created_at },
          ]"
          :rows="reviews"
          empty-text="暂无评论"
        >
          <template #cell-user="{ row }">{{ getUserName(row.user_id) }}</template>
          <template #cell-rating="{ row }">
            <span class="stars">{{ '★'.repeat(row.rating) }}<span class="star-off">{{ '★'.repeat(5 - row.rating) }}</span></span>
          </template>
          <template #cell-content="{ row }">
            <button class="link" v-press @click="openReviewDetail(row)">{{ row.content || '（无文字内容）' }}</button>
          </template>
          <template #cell-status="{ row }">
            <div class="status-cell">
              <el-switch
                :model-value="!row.is_hidden"
                @change="(v: any) => toggleReviewHidden(row, !v)"
              />
              <span class="status-text" :class="!row.is_hidden ? 'on' : 'off'">{{ row.is_hidden ? '已隐藏' : '显示中' }}</span>
            </div>
          </template>
          <template #cell-time="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString('zh-CN') : '—' }}</template>
          <template #actions="{ row }">
            <button class="link" v-press @click="openReviewDetail(row)">查看</button>
            <button class="link danger" v-press @click="handleDeleteReview(Number(row.id))">删除</button>
          </template>
        </DataTable>
      </PageSection>
    </template>

    <!-- 评价详情抽屉（图 + 文 + 评分 + 用户） -->
    <FormDialog :show="!!reviewDetail" title="评价详情" :width="520" :footer="false" @close="closeReviewDetail">
      <div v-if="reviewDetail" class="detail">
        <div class="detail-row"><span class="dl">用户</span><span class="dv">{{ getUserName(reviewDetail.user_id) }}</span></div>
        <div class="detail-row"><span class="dl">评分</span><span class="dv stars">{{ '★'.repeat(reviewDetail.rating) }}<span class="star-off">{{ '★'.repeat(5 - reviewDetail.rating) }}</span></span></div>
        <div class="detail-row detail-row-desc"><span class="dl">内容</span><span class="dv text-desc">{{ reviewDetail.content || '（无文字内容）' }}</span></div>
        <div class="detail-row detail-row-desc" v-if="parseReviewImages(reviewDetail.images).length">
          <span class="dl">图片</span>
          <span class="dv detail-imgs">
            <img v-for="(img, i) in parseReviewImages(reviewDetail.images)" :key="i" :src="img" class="detail-img" />
          </span>
        </div>
        <div class="detail-row"><span class="dl">时间</span><span class="dv">{{ reviewDetail.created_at ? new Date(reviewDetail.created_at).toLocaleString('zh-CN') : '—' }}</span></div>
      </div>
      <div class="modal-actions" v-if="reviewDetail">
        <button class="btn-cancel" v-press @click="closeReviewDetail">关闭</button>
        <button v-if="!reviewDetail.is_hidden" class="btn-danger" v-press @click="toggleReviewHidden(reviewDetail, true)">隐藏</button>
        <button v-else class="btn-primary" v-press @click="toggleReviewHidden(reviewDetail, false)">显示</button>
        <button class="btn-danger" v-press @click="handleDeleteReview(Number(reviewDetail.id))">删除</button>
      </div>
    </FormDialog>

    <FormDialog :show="showImageModal" title="图片管理" :width="480" confirm-text="保存" @close="closeImageModal" @confirm="saveImageModal">
      <ImageUpload v-model="editForm.image" :max="3" />
    </FormDialog>
  </PageContainer>
  <div v-else class="empty-state big">菜品不存在</div>
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
.detail-value.price { color: var(--color-price); font-weight: var(--weight-bold); font-size: var(--font-lg); }
.detail-value.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); }
.detail-value.text-muted { font-weight: var(--weight-regular); color: var(--text-light); }
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
.list-bar h3 { margin: 0; font-size: var(--font-lg); color: var(--text-primary); font-weight: var(--weight-semibold); display: flex; align-items: center; gap: var(--space-2); }
.icon-inline { width: 1em; height: 1em; vertical-align: -0.125em; display: inline; }
.count-tag { font-size: var(--font-sm); font-weight: var(--weight-regular); color: var(--text-muted); margin-left: var(--space-1); }

/* ===== 评论列表 ===== */
.table .ellipsis { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.table .actions { white-space: nowrap; }
.stars { display: flex; gap: var(--space-1); }
.star-icon { width: 14px; height: 14px; display: inline; }
.empty-state { text-align: center; color: var(--text-light); padding: var(--space-5) 0; font-size: var(--font-base); }
.empty-state.big { font-size: var(--font-lg); padding: var(--space-10) 0; }

/* ===== 标签样式 ===== */
.tag-group { display: flex; gap: var(--space-1); flex-wrap: wrap; }
.dish-tag { font-size: var(--font-xs); padding: var(--space-1) var(--space-3); border-radius: var(--radius-sm); font-weight: var(--weight-medium); display: inline-flex; align-items: center; gap: var(--space-1); }
.tag-hot { background: var(--color-warning-soft); color: var(--color-warning); }
.tag-rec { background: var(--color-price-soft); color: var(--color-price); }
.tag-icon { width: 12px; height: 12px; display: inline; }
.tag-selector { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.tag-option { padding: var(--space-2) var(--space-4); border: 1px solid var(--border-strong); border-radius: var(--radius-pill); font-size: var(--font-sm); cursor: pointer; transition: background 0.2s var(--ease-out), border-color 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out); user-select: none; display: inline-flex; align-items: center; gap: var(--space-1); }
.tag-option:hover { border-color: var(--color-primary); color: var(--color-primary); }
.tag-option:active { transform: scale(var(--press-scale)); }
.tag-option.active { background: var(--color-primary-bg); border-color: var(--color-primary); color: var(--color-primary); font-weight: var(--weight-medium); }
.text-muted { color: var(--text-light); font-weight: var(--weight-regular); }

/* ===== 全局覆盖（仅保留页面特有细微项，按钮/表单继承全局基线） ===== */
.price-input { width: 120px !important; }
.origin-price { margin-left: var(--space-2); color: var(--text-light); text-decoration: line-through; font-weight: var(--weight-regular); font-size: var(--font-sm); }
.promo-badge { margin-left: var(--space-2); font-size: var(--font-xs); color: var(--color-error); background: var(--color-error-bg); padding: 1px var(--space-2); border-radius: var(--radius-sm); }
.discount-edit { display: flex; flex-direction: column; gap: var(--space-1); }
.mini-label { font-size: var(--font-xs); color: var(--text-muted); }
.input-error { border-color: var(--color-error) !important; }
.field-error { margin: var(--space-1) 0 0; font-size: var(--font-sm); color: var(--color-error); }
.required { color: var(--color-error); }
.form-select-el { width: 100%; }

/* ===== 评价管理 ===== */
.stars { color: var(--color-star); letter-spacing: 1px; }
.star-off { color: var(--border-strong); }
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--color-error); }
.detail-imgs { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.detail-img { width: 96px; height: 96px; border-radius: var(--radius-md); object-fit: cover; border: 1px solid var(--border-color); }
.detail { display: flex; flex-direction: column; gap: var(--space-3); }
.detail-row { display: flex; gap: var(--space-3); font-size: var(--font-base); }
.detail-row-desc { align-items: flex-start; }
.dl { width: 64px; flex-shrink: 0; color: var(--text-muted); }
.dv { color: var(--text-primary); flex: 1; }
.dv.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); }
.modal-actions { display: flex; justify-content: flex-end; gap: var(--space-3); margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--border-light); }
.btn-cancel { padding: var(--space-2) var(--space-5); background: var(--bg-card); color: var(--text-secondary); border: 1px solid var(--border-color); border-radius: var(--radius); font-size: var(--font-base); cursor: pointer; font-weight: var(--weight-medium); }
.btn-cancel:hover { color: var(--color-primary); border-color: var(--color-primary); }
.btn-primary { padding: var(--space-2) var(--space-5); border: none; border-radius: var(--radius); background: var(--color-primary); color: var(--text-white); font-size: var(--font-base); cursor: pointer; font-weight: var(--weight-medium); }
.btn-primary:hover { opacity: .9; }
.btn-danger { padding: var(--space-2) var(--space-5); border: 1px solid var(--color-error); border-radius: var(--radius); background: var(--bg-card); color: var(--color-error); font-size: var(--font-base); cursor: pointer; font-weight: var(--weight-medium); display: inline-flex; align-items: center; gap: var(--space-1); }
.btn-danger:hover { background: var(--color-error); color: var(--text-white); }
</style>
