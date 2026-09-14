<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import { parseTags, formatTags } from '@/api/adapter'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import PageSection from '@/components/layout/PageSection.vue'
import StatCard from '@/components/common/StatCard.vue'
import FilterSelect from '@/components/layout/FilterSelect.vue'
import FormDialog from '@/components/FormDialog.vue'
import EntityImage from '@/components/EntityImage.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import DataTable from '@/components/DataTable.vue'
import StatusTag from '@/components/StatusTag.vue'
import StarRating from '@/components/StarRating.vue'
import { Trophy, Star, Food, Picture } from '@element-plus/icons-vue'
import { TAG_OPTIONS, SIGNATURE_TAG, tagDisplay } from '@/api/tags'
import { SEC_STATE_META, SEC_FILTER_OPTIONS, SEC_REVIEW, AUDIT_STATUS_META, AUDIT_REJECTED } from '@/constants'
import type { SecAction } from '@/types'

const router = useRouter()
const route = useRoute()
const store = useAdminStore()
const toast = useToastStore()
const confirm = useConfirmStore()

/** 菜品列表页地址（本页唯一上级；食堂/档口仅作为筛选维度，不是独立页面） */
const DISH_LIST_PATH = '/dashboard/content?tab=dish'

function goBack() {
  router.push(DISH_LIST_PATH)
}

const statusOptions = [
  { label: '在售', value: 'active' },
  { label: '已下架', value: 'inactive' },
]

const dishId = computed(() => Number(route.params.dishId))

const dish = computed(() => store.dishes.find(d => Number(d.id) === dishId.value))
// 所属食堂 / 档口：由 dish.stall_id → stall → canteen 派生（只读展示，不跳转实体页）
const stall = computed(() => store.stalls.find(s => Number(s.id) === Number(dish.value?.stall_id)))
const canteen = computed(() => store.canteens.find(c => Number(c.id) === Number(stall.value?.canteen_id)))
// 评论管理本地安检筛选（数据已全量在 store，'' = 全部；v-model 为 string，比较时直接字符串匹配）
const activeSecState = ref('')
const dishReviews = computed(() => store.reviews.filter(r => Number(r.dish_id) === dishId.value))
const reviews = computed(() => {
  if (!activeSecState.value) return dishReviews.value
  return dishReviews.value.filter(r => (r.secState || 'pass') === activeSecState.value)
})
const pendingSecCount = computed(() => dishReviews.value.filter(r => (r.secState || 'pass') === 'review').length)

const activeTab = ref(0)

function getUserName(userId: number | bigint): string {
  const u = store.users.find(u => Number(u.id) === Number(userId))
  return u?.nickname || u?.username || `用户${userId}`
}

// 菜品属性枚举（与后端 DishAttrConst 对齐）
const SPICE_OPTIONS = [
  { value: 0, label: '不辣' },
  { value: 1, label: '微辣' },
  { value: 2, label: '中辣' },
  { value: 3, label: '重辣' },
]
/** 风味/菜系权威值域（project_spec §7.9） */
const REGION_OPTIONS = ['东北', '川湘', '粤式', '西北', '清真', '其他']

function spiceLabel(v?: number): string {
  return SPICE_OPTIONS.find(o => o.value === v)?.label || '不辣'
}

// ===== 审核闭环展示（§4.9）：审核态走 StatusTag，退回时展示原因 =====
const AUDIT_FALLBACK = { type: 'warning' as const, text: '待审核' }
const auditMeta = computed(() => {
  const s = dish.value?.audit_status || 'pending'
  return AUDIT_STATUS_META[s] ?? AUDIT_FALLBACK
})
/** 退回原因仅在 audit_status=rejected 且有值时展示 */
const rejectReason = computed(() =>
  dish.value?.audit_status === AUDIT_REJECTED ? (dish.value?.reject_reason || '') : '',
)

const editing = ref(false)
const editForm = ref({ name: '', price: 0, description: '', image: '', alias: '', tags: '', status: '', spiceLevel: 0, region: '', originalPrice: 0, promoPrice: 0 })
const editErrors = ref<Record<string, string>>({})

/**
 * 「他人已修改」轻提示基线（Q-112 ①，PR-13 最简实现）：
 * 进入编辑时记录该菜品当前 updated_at；保存前仅比对该基线是否与当前行一致，
 * 不一致则给一次可「继续保存 / 取消」的**非阻塞**提示；不引版本号 / 乐观锁 / ETag。
 */
const editBaselineUpdatedAt = ref('')
function updatedAtKey(v: unknown): string {
  return v ? new Date(v as any).getTime().toString() : ''
}

/**
 * 搜索别名（后端 DishAdminReq.alias，逗号分隔，保存前后端 trim 去空项去重，总长 ≤255 超限 400）。
 * 输入允许中英文逗号，提交前统一归一为英文逗号并去空项；空串表示清空别名。
 */
function normalizeAlias(raw: string): string {
  return raw.split(/[,，]/).map(s => s.trim()).filter(Boolean).join(',')
}

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

watch([canteen, stall, dish], ([, , d]) => {
  // 后台顶部导航已表达层级（食堂/档口不是独立页面），不再维护面包屑
  if (d) {
    editForm.value = {
      name: d.name, price: d.price, description: d.description || '',
      image: d.image || '', alias: d.alias || '', tags: d.tags || '', status: d.status,
      spiceLevel: d.spiceLevel ?? 0,
      region: d.region || '',
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
      image: dish.value.image || '', alias: dish.value.alias || '', tags: dish.value.tags || '', status: dish.value.status,
      spiceLevel: dish.value.spiceLevel ?? 0,
      region: dish.value.region || '',
      originalPrice: dish.value.originalPrice ? dish.value.originalPrice : 0,
      promoPrice: dish.value.promoPrice ? dish.value.promoPrice : 0,
    }
    editErrors.value = {}
    // 记录编辑基线（进入编辑时的 updated_at），保存前据此做一次轻量「他人已修改」提示
    editBaselineUpdatedAt.value = updatedAtKey(dish.value.updated_at)
  }
}

function toggleFormTag(tag: string) {
  // tags 统一 CSV 格式（WEB-101）：读走 parseTags（兼容历史 JSON 脏数据），写走 formatTags
  const arr = parseTags(editForm.value.tags)
  const i = arr.indexOf(tag)
  if (i === -1) { arr.push(tag) } else { arr.splice(i, 1) }
  editForm.value.tags = formatTags(arr)
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
  // 搜索别名：与后端 DishAdminReq.alias 长度约束（≤255）保持一致，前端提前拦截避免必然 400
  const alias = normalizeAlias(editForm.value.alias || '')
  if (alias.length > 255) errs.alias = '搜索别名过长（不超过 255 字）'
  editErrors.value = errs
  if (Object.keys(errs).length) return
  // 「他人已修改」轻提示（Q-112 ①）：仅提示不阻塞，取消则留在编辑态保留已填内容
  if (editBaselineUpdatedAt.value) {
    const curKey = updatedAtKey(dish.value?.updated_at)
    if (curKey && curKey !== editBaselineUpdatedAt.value) {
      const goOn = await confirm.confirm('该菜品在你编辑期间已被修改，继续保存将覆盖对方的改动。是否继续保存？')
      if (!goOn) return
      editBaselineUpdatedAt.value = curKey
    }
  }
  if (dish.value) {
    const payload: any = { ...editForm.value, alias }
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
      image: dish.value.image || '', alias: dish.value.alias || '', tags: dish.value.tags || '', status: dish.value.status,
      spiceLevel: dish.value.spiceLevel ?? 0,
      region: dish.value.region || '',
      originalPrice: dish.value.originalPrice ? dish.value.originalPrice : 0,
      promoPrice: dish.value.promoPrice ? dish.value.promoPrice : 0,
    }
  }
  editErrors.value = {}
  editing.value = false
}

async function deleteDish() {
  if (!dish.value) return
  // 删除影响说明（Q-112 ②）：用本页已加载的评价数给出具体条数，无则通用文案（不新增接口）
  const n = dishReviews.value.length
  const reviewLine = n > 0
    ? `该菜品下的 ${n} 条评价将一并删除、不可恢复。`
    : '该菜品的评价将一并删除、不可恢复。'
  if (!await confirm.confirm(`确定删除该菜品？删除后不可恢复。${reviewLine}`)) return
  try {
    await store.deleteDish(Number(dish.value.id))
    toast.success('菜品已删除')
    // 删除后回菜品列表（本页已无对应实体）
    router.push(DISH_LIST_PATH)
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
const reviewDetail = ref<any | null>(null)
// 安检复核进行中的评价 id：防重复提交
const secActingId = ref<number | null>(null)
function openReviewDetail(r: any) { reviewDetail.value = r }
function closeReviewDetail() { reviewDetail.value = null }
/** 配图点击：新窗口查看原图（COS 公网地址，noopener 防标签页劫持） */
function openImage(url: string) {
  if (!url) return
  window.open(url, '_blank', 'noopener')
}
async function toggleReviewHidden(r: any, hidden: boolean) {
  try {
    await store.updateReview(Number(r.id), { is_hidden: hidden ? 1 : 0 })
    toast.success(hidden ? '评价已隐藏' : '评价已显示')
    if (reviewDetail.value && Number(reviewDetail.value.id) === Number(r.id)) reviewDetail.value = null
  } catch (err: any) {
    toast.error(err.message || '操作失败')
  }
}

/**
 * 内容安检复核（评论管理）：放行（pass）/ 驳回（rejected），仅对待复核行开放。
 * 两种动作均二次确认（驳回拦截用户内容、放行放行平台内容，均不可静默提交）。
 */
async function reviewSecState(r: any, state: SecAction) {
  if (secActingId.value !== null) return
  if (!await confirm.confirm(state === 'pass'
    ? '确定放行该评价？放行后评价恢复正常展示。'
    : '确定驳回该评价？驳回后评价内容将被拦截，不再对用户展示。')) return
  secActingId.value = Number(r.id)
  try {
    await store.updateReviewSecState(Number(r.id), state)
    toast.success(state === 'pass' ? '评价已放行' : '评价已驳回')
    if (reviewDetail.value && Number(reviewDetail.value.id) === Number(r.id)) reviewDetail.value = null
  } catch (err: any) {
    toast.error(err.message || '安检复核操作失败')
  } finally {
    secActingId.value = null
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
              <span class="detail-label">搜索别名</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value" :class="{ 'text-muted': !editForm.alias }">{{ editForm.alias || '—' }}</span>
                <input v-else v-model="editForm.alias" class="form-input" :class="{ 'input-error': editErrors.alias }" placeholder="选填，逗号分隔，如：麻小,小龙虾" />
                <p v-if="editing && editErrors.alias" class="field-error">{{ editErrors.alias }}</p>
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
                    <span v-for="tag in parseTags(editForm.tags || '')" :key="tag" class="dish-tag" :class="tag === SIGNATURE_TAG ? 'tag-hot' : 'tag-rec'">
                      <el-icon class="tag-icon"><component :is="tag === SIGNATURE_TAG ? Trophy : Star" /></el-icon> {{ tagDisplay(tag) }}
                    </span>
                  </span>
                  <span v-else class="text-muted">无</span>
                </span>
                <div v-else class="tag-selector">
                  <span v-for="opt in TAG_OPTIONS" :key="opt.value" class="tag-option" :class="{ active: parseTags(editForm.tags || '').includes(opt.value) }" @click="toggleFormTag(opt.value)"><el-icon class="tag-icon"><component :is="opt.value === SIGNATURE_TAG ? Trophy : Star" /></el-icon> {{ opt.label }}</span>
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
              <span class="detail-label">风味 / 菜系</span>
              <div class="detail-control">
                <span v-if="!editing" class="detail-value" :class="{ 'text-muted': !editForm.region }">{{ editForm.region || '—' }}</span>
                <el-select v-else v-model="editForm.region" class="form-select-el" placeholder="选择风味 / 菜系" clearable>
                  <el-option v-for="r in REGION_OPTIONS" :key="r" :label="r" :value="r" />
                </el-select>
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
            <div class="detail-row">
              <span class="detail-label">审核</span>
              <div class="detail-control">
                <StatusTag :type="auditMeta.type" :text="auditMeta.text" />
              </div>
            </div>
            <div v-if="rejectReason" class="detail-row detail-row-desc">
              <span class="detail-label">退回原因</span>
              <div class="detail-control">
                <span class="detail-value text-desc reject-reason">{{ rejectReason }}</span>
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

    <!-- Tab 2: 评论管理（直观展示评分/内容/配图/安检/用户，可查看详情/安检复核/删除） -->
    <template v-if="activeTab === 1">
      <PageSection>
        <template #header-extra>
          <div class="panel-actions">
            <FilterSelect v-model="activeSecState" label="安检" :options="SEC_FILTER_OPTIONS" :width="140" />
            <span class="count-tag">共 {{ reviews.length }} 条<template v-if="pendingSecCount"> · 待复核 {{ pendingSecCount }}</template></span>
          </div>
        </template>
        <DataTable
          :columns="[
            { prop: 'user', label: '用户', width: '150px' },
            { prop: 'rating', label: '评分', width: '120px', sortable: true, sortValue: (row) => row.rating },
            { prop: 'content', label: '内容', ellipsis: true },
            { prop: 'secState', label: '安检', width: '90px', align: 'center' },
            { prop: 'status', label: '状态', width: '110px', align: 'center' },
            { prop: 'time', label: '时间', width: '150px', sortable: true, sortValue: (row) => row.created_at },
          ]"
          :rows="reviews"
          actions-width="250px"
          empty-text="暂无评论"
        >
          <template #cell-user="{ row }">{{ getUserName(row.user_id) }}</template>
          <template #cell-rating="{ row }">
            <StarRating :value="row.rating" />
          </template>
          <template #cell-content="{ row }">
            <button class="link" v-press @click="openReviewDetail(row)">{{ row.content || '（无文字内容）' }}</button>
            <span v-if="(row.images || []).length" class="img-flag" title="该评价附有配图">
              <el-icon><Picture /></el-icon>{{ row.images.length }}
            </span>
          </template>
          <template #cell-secState="{ row }">
            <StatusTag :type="SEC_STATE_META[row.secState]?.type || 'success'" :text="SEC_STATE_META[row.secState]?.text || '正常'" />
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
            <template v-if="row.secState === SEC_REVIEW">
              <button class="link primary-text" v-press :disabled="secActingId !== null" @click="reviewSecState(row, 'pass')">放行</button>
              <button class="link warn" v-press :disabled="secActingId !== null" @click="reviewSecState(row, 'rejected')">驳回</button>
            </template>
            <button class="link" v-press @click="openReviewDetail(row)">查看</button>
            <button class="link danger" v-press @click="handleDeleteReview(Number(row.id))">删除</button>
          </template>
        </DataTable>
      </PageSection>
    </template>

    <!-- 评价详情抽屉（图 + 文 + 评分 + 安检 + 用户） -->
    <FormDialog :show="!!reviewDetail" title="评价详情" :width="520" :footer="false" @close="closeReviewDetail">
      <div v-if="reviewDetail" class="detail">
        <div class="detail-row"><span class="dl">用户</span><span class="dv">{{ getUserName(reviewDetail.user_id) }}</span></div>
        <div class="detail-row"><span class="dl">评分</span><span class="dv"><StarRating :value="reviewDetail.rating" /></span></div>
        <div class="detail-row detail-row-desc"><span class="dl">内容</span><span class="dv text-desc">{{ reviewDetail.content || '（无文字内容）' }}</span></div>
        <div class="detail-row detail-row-desc" v-if="(reviewDetail.images || []).length">
          <span class="dl">配图</span>
          <div class="dv">
            <div class="img-list">
              <img
                v-for="(img, i) in reviewDetail.images"
                :key="i"
                :src="img"
                class="img-thumb"
                alt="评价配图"
                loading="lazy"
                @click="openImage(img)"
              />
            </div>
            <p class="img-hint">点击图片在新窗口查看原图</p>
          </div>
        </div>
        <div class="detail-row"><span class="dl">安检</span><span class="dv">
          <StatusTag :type="SEC_STATE_META[reviewDetail.secState]?.type || 'success'" :text="SEC_STATE_META[reviewDetail.secState]?.text || '正常'" />
        </span></div>
        <div class="detail-row"><span class="dl">时间</span><span class="dv">{{ reviewDetail.created_at ? new Date(reviewDetail.created_at).toLocaleString('zh-CN') : '—' }}</span></div>
      </div>
      <div class="modal-actions" v-if="reviewDetail">
        <button class="btn-cancel" v-press @click="closeReviewDetail">关闭</button>
        <template v-if="reviewDetail.secState === SEC_REVIEW">
          <button class="btn-success" v-press :disabled="secActingId !== null" @click="reviewSecState(reviewDetail, 'pass')">放行</button>
          <button class="btn-warn" v-press :disabled="secActingId !== null" @click="reviewSecState(reviewDetail, 'rejected')">驳回</button>
        </template>
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
/* 退回原因：与错误色语义一致的弱化背景，便于一眼识别 */
.reject-reason { color: var(--text-primary); background: var(--color-error-bg); border-radius: var(--radius-sm); padding: var(--space-2) var(--space-3); display: inline-block; }

/* ===== 统计卡片（统一 StatCard） ===== */
.stats-row { display: flex; gap: var(--space-4); }
.stats-row :deep(.stat-card) { flex: 1; min-width: 0; }

.count-tag { font-size: var(--font-sm); font-weight: var(--weight-regular); color: var(--text-muted); margin-left: var(--space-1); }

/* ===== 评论列表 ===== */
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

/* ===== 评价管理（星级样式已收敛至 StarRating 组件，P3-17） ===== */
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--color-error); }
/* ===== 安检复核配图（缩略图：hover 微放大提示可点，active 按压缩放） ===== */
.img-list { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.img-thumb {
  width: 88px; height: 88px; border-radius: var(--radius-md); object-fit: cover;
  border: 1px solid var(--border-light); cursor: zoom-in; background: var(--bg-soft);
  transition: transform 160ms var(--ease-out), box-shadow 160ms var(--ease-out);
}
.img-thumb:hover { transform: scale(1.04); box-shadow: var(--shadow-card); }
.img-thumb:active { transform: scale(var(--press-scale)); }
.img-hint { margin: var(--space-2) 0 0; font-size: var(--font-xs); color: var(--text-light); }
@media (prefers-reduced-motion: reduce) {
  .img-thumb { transition: none; }
  .img-thumb:hover { transform: none; }
}
.detail { display: flex; flex-direction: column; gap: var(--space-3); }
.detail-row { display: flex; gap: var(--space-3); font-size: var(--font-base); }
.detail-row-desc { align-items: flex-start; }
.dl { width: 64px; flex-shrink: 0; color: var(--text-muted); }
.dv { color: var(--text-primary); flex: 1; }
.dv.text-desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); }
.modal-actions { display: flex; justify-content: flex-end; gap: var(--space-3); margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--border-light); }
/* 按钮（btn-primary/btn-cancel/btn-danger）走 shared.css 全局基线，此处不重复覆盖 */
</style>
