<script setup lang="ts">
/**
 * DishDetailView：菜品详情（扁平两层结构）。
 *
 * 结构（本轮 UI 收敛，无层层嵌套）：
 *   PageContainer → 页头（返回 + 菜名 + 副标题「食堂 · 档口 · ★评分（N 人）」+ 右[编辑][删除]）
 *                 → 主体（信息卡 + 评价列表）
 *
 * 本轮删除的层与重复入口：
 *  - `detail-tabs` 两个 tab（详情概览 / 评论管理）→ 信息与评价同屏直读，不再需要切换；
 *  - 「数据统计」分区 + 2 张 StatCard → 评分/人数并入页头副标题（同一个数字只出现一次）；
 *  - 页内 inline 编辑态（名称 / 价格（现价 + 原价划线）/ 荤素 / 主料 / 口味 / 冷热 一整段读写实现）
 *    → 统一走列表页同一个 DishFormDialog
 *    （同一实体不得两套编辑实现）；
 *  - 头部缩略图（与信息卡图片同一功能的第二个入口）→ 只保留信息卡图片入口；
 *  - 评价详情抽屉改用公共组件 ReviewDetailDialog（与「评价管理」页共用唯一实现），且该抽屉为只读：
 *    评价的所有动作（显隐开关 / 删除）只保留在列表行内一处入口。
 *
 * 接口与字段保持不变：菜品取 dishStore.loadAll()，评价取 reviewStore.loadAll()，
 * 用户名降级取 userStore.loadAll()（WEB-02 / WEB-03）。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from '@/stores/adminStore'
import { useDishStore } from '@/stores/dishStore'
import { useReviewStore } from '@/stores/reviewStore'
import { useUserStore } from '@/stores/userStore'
import { useToastStore } from '@/stores/toastStore'
import { useConfirmStore } from '@/stores/confirmStore'
import {
  useDishAttributeStore,
  ATTR_DIET_TYPE,
  ATTR_INGREDIENTS,
  ATTR_FLAVOR_TAGS,
  ATTR_SERVE_TEMP,
} from '@/stores/dishAttributeStore'
import PageContainer from '@/components/layout/PageContainer.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import DataTable from '@/components/DataTable.vue'
import EntityImage from '@/components/EntityImage.vue'
import StatusTag from '@/components/StatusTag.vue'
import StarRating from '@/components/StarRating.vue'
import DishFormDialog from '@/components/DishFormDialog.vue'
import ReviewDetailDialog from '@/components/ReviewDetailDialog.vue'
import { Delete, Picture } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const store = useAdminStore()
const dishStore = useDishStore()
const reviewStore = useReviewStore()
const userStore = useUserStore()
const attrStore = useDishAttributeStore()
const toast = useToastStore()
const confirm = useConfirmStore()

/** 菜品列表页地址（本页唯一上级；食堂/档口仅作为筛选维度，不是独立页面） */
const DISH_LIST_PATH = '/dashboard/content?tab=dish'

function goBack() {
  router.push(DISH_LIST_PATH)
}

const dishId = computed(() => Number(route.params.dishId))
const dish = computed(() => store.dishes.find(d => Number(d.id) === dishId.value))

// ===== 三态（WEB-02：按需加载本页所需域；菜品为主域，失败才算页面失败） =====
const loading = ref(true)
const loadError = ref('')
onMounted(async () => {
  // 域间独立容错：菜品为主域，失败即页面级错误；评价 / 用户字典为附属域，失败只影响各自的降级显示
  reviewStore.loadAll().catch(() => {})
  userStore.loadAll().catch(() => {})
  // 描述四维字典（§7.40 R4）：附属域——失败只让四维退化为「原样透出枚举键」，不拖垮菜品主域（WEB-09 口径）
  attrStore.ensureLoaded().catch(() => {})
  try {
    await dishStore.loadAll()
  } catch (e: any) {
    loadError.value = e?.message || '加载菜品失败'
  }
  loading.value = false
})

// ===== 页头副标题：食堂 · 档口 · ★4.6（128 人）（原「数据统计」两张 StatCard 的数字已并入此处） =====
const headerSubtitle = computed(() => {
  const parts: string[] = []
  if (dish.value?.canteenName) parts.push(dish.value.canteenName)
  if (dish.value?.stallName) parts.push(dish.value.stallName)
  const count = Number(dish.value?.rating_count ?? 0)
  if (count > 0) parts.push(`★${Number(dish.value?.avg_rating ?? 0).toFixed(1)}（${count} 人）`)
  return parts.join(' · ')
})

// ===== 信息卡字段（只读；编辑一律走 DishFormDialog） =====
/** 展示值恒取现价 price（§7.26：禁止双源切换；原 promoPrice 已删除） */
function formatPrice(d: any): string {
  return `¥${Number(d?.price ?? 0).toFixed(2)}`
}

/** 有折扣（§7.26）：原价有值且高于现价 → 端上原价划线 */
function hasPromo(d: any): boolean {
  return d?.originalPrice != null && Number(d.originalPrice) > Number(d.price)
}

/** 菜品图片（多图以 ||| 分隔，首图作封面） */
const imageList = computed(() => (dish.value?.image || '').split('|||').filter(Boolean))
const firstImage = computed(() => imageList.value[0] || '')
const imageCount = computed(() => imageList.value.length)

// ===== 编辑：与列表页同一个 DishFormDialog（唯一编辑实现） =====
const dishModal = ref(false)
function openEditDish() {
  dishModal.value = true
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

// ===== 评价列表（数据已全量在 store，无需二次请求） =====
const dishReviews = computed(() => store.reviews.filter(r => Number(r.dish_id) === dishId.value))

function getUserName(userId: number | bigint): string {
  const u = store.users.find(u => Number(u.id) === Number(userId))
  return u?.nickname || u?.username || `用户${userId}`
}

// 评价详情（只读弹窗：公共组件 ReviewDetailDialog，本页与「评价管理」页共用）
const reviewDetail = ref<any | null>(null)
const reviewDetailUserName = computed(() =>
  reviewDetail.value ? getUserName(reviewDetail.value.user_id) : '',
)
function openReviewDetail(r: any) { reviewDetail.value = r }
function closeReviewDetail() { reviewDetail.value = null }

/** 行内显隐（评价的唯一显隐入口；详情弹窗为只读，不再提供第二个开关） */
async function toggleReviewHidden(r: any, hidden: boolean) {
  try {
    await store.updateReview(Number(r.id), { is_hidden: hidden ? 1 : 0 })
    toast.success(hidden ? '评价已隐藏' : '评价已显示')
  } catch (err: any) {
    toast.error(err.message || '操作失败')
  }
}

/** 行内删除（评价的唯一删除入口） */
async function handleDeleteReview(id: number) {
  if (!await confirm.confirm('确定删除该评价？此操作不可恢复。')) return
  try {
    await store.deleteReview(id)
    toast.success('评价已删除')
  } catch (err: any) {
    toast.error(err.message || '评价删除失败')
  }
}
</script>

<template>
  <PageContainer v-if="dish">
    <PageHeader
      :back="true"
      :title="dish.name"
      :subtitle="headerSubtitle"
      @back="goBack"
    >
      <template #actions>
        <button class="btn-primary btn-sm" v-press type="button" @click="openEditDish">编辑</button>
        <button class="btn-danger btn-sm" v-press type="button" @click="deleteDish">删除</button>
      </template>
    </PageHeader>

    <!-- 信息卡：单层卡片，字段平铺为定义列表（原「基本信息 / 数据统计」两个分区已合并） -->
    <section class="card info-card">
      <!-- 图片属于菜品表单的一部分：点击直接进入同一个编辑弹窗（不再单独维护一套「图片管理」实现） -->
      <EntityImage
        :image-url="firstImage"
        :image-count="imageCount"
        title="点击编辑菜品（更换图片）"
        @click="openEditDish"
      />
      <dl class="info-grid">
        <div class="info-item">
          <dt>名称</dt>
          <dd>{{ dish.name }}</dd>
        </div>
        <div class="info-item">
          <dt>价格</dt>
          <dd>
            <span class="price">{{ formatPrice(dish) }}</span>
            <span v-if="hasPromo(dish)" class="origin">原价 ¥{{ Number(dish.originalPrice).toFixed(2) }}</span>
          </dd>
        </div>
        <div class="info-item">
          <dt>状态</dt>
          <dd><StatusTag :type="dish.status === 'active' ? 'success' : 'gray'" :text="dish.status === 'active' ? '在售' : '已下架'" /></dd>
        </div>
        <!-- 描述四维（§7.28 描述维度替换，2026-09-20）：荤素 / 主料 / 口味 / 冷热；缺项显示「—」。
             中文标签由后端字典下发（§7.40 R4，`GET /dishes/attributes`）——本层零硬编码映射；
             字段名与字典 field 一一对应（R13），故直接传维度常量。 -->
        <div class="info-item">
          <dt>荤素</dt>
          <dd :class="{ muted: !dish.dietType }">{{ attrStore.labelOf(ATTR_DIET_TYPE, dish.dietType) }}</dd>
        </div>
        <div class="info-item">
          <dt>主料</dt>
          <dd :class="{ muted: !dish.ingredients }">{{ attrStore.labelsText(ATTR_INGREDIENTS, dish.ingredients) }}</dd>
        </div>
        <div class="info-item">
          <dt>口味</dt>
          <dd :class="{ muted: !dish.flavorTags }">{{ attrStore.labelsText(ATTR_FLAVOR_TAGS, dish.flavorTags) }}</dd>
        </div>
        <div class="info-item">
          <dt>冷热</dt>
          <dd :class="{ muted: !dish.serveTemp }">{{ attrStore.labelOf(ATTR_SERVE_TEMP, dish.serveTemp) }}</dd>
        </div>
        <div class="info-item info-item-wide">
          <dt>介绍</dt>
          <dd class="desc" :class="{ muted: !dish.description }">{{ dish.description || '暂无介绍' }}</dd>
        </div>
      </dl>
    </section>

    <h2 class="section-title">评价</h2>
    <DataTable
      :columns="[
        { prop: 'user', label: '用户', width: '150px' },
        { prop: 'rating', label: '评分', width: '120px', align: 'center', sortable: true, sortValue: (row) => row.rating },
        { prop: 'content', label: '内容', ellipsis: true },
        { prop: 'status', label: '状态', width: '110px', align: 'center' },
        { prop: 'time', label: '时间', width: '150px', sortable: true, sortValue: (row) => row.created_at },
      ]"
      :rows="dishReviews"
      actions-width="160px"
      empty-text="该菜品暂无评价">
      <template #cell-user="{ row }">{{ getUserName(row.user_id) }}</template>
      <template #cell-rating="{ row }">
        <StarRating :value="row.rating" />
      </template>
      <template #cell-content="{ row }">
        <span class="cell-text" :title="row.content || '（无文字内容）'">{{ row.content || '（无文字内容）' }}</span>
        <span v-if="(row.images || []).length" class="img-flag" title="该评价附有配图">
          <el-icon><Picture /></el-icon>{{ row.images.length }}
        </span>
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
        <button class="link" v-press type="button" @click="openReviewDetail(row)">查看</button>
        <button class="link danger" v-press type="button" @click="handleDeleteReview(Number(row.id))">
          <el-icon class="act-ico"><Delete /></el-icon>删除
        </button>
      </template>
    </DataTable>

    <!-- 编辑：与菜品列表页同一个表单弹窗 -->
    <DishFormDialog
      :show="dishModal"
      :editing-id="dishId"
      @close="dishModal = false"
    />

    <!-- 评价详情（只读，公共组件） -->
    <ReviewDetailDialog
      :show="!!reviewDetail"
      :review="reviewDetail"
      :user-name="reviewDetailUserName"
      @close="closeReviewDetail"
    />
  </PageContainer>

  <div v-else-if="loading" class="state-box"><span class="spin" />加载中…</div>
  <div v-else-if="loadError" class="state-box state-err">
    <span>{{ loadError }}，请返回列表重试</span>
    <button class="link" v-press type="button" @click="goBack">返回菜品列表</button>
  </div>
  <div v-else class="state-box">
    <span>菜品不存在或已删除</span>
    <button class="link" v-press type="button" @click="goBack">返回菜品列表</button>
  </div>
</template>

<style scoped>
/* ===== 信息卡：单层卡片（复用全局 .card 材质），左图 + 右字段网格 ===== */
.info-card {
  display: flex;
  gap: var(--space-6);
  align-items: flex-start;
  padding: var(--space-5) var(--space-6);
  margin-bottom: var(--space-5);
}
.info-grid {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-4) var(--space-6);
}
.info-item { min-width: 0; }
.info-item-wide { grid-column: 1 / -1; }
.info-item dt { margin-bottom: var(--space-1); font-size: var(--font-sm); color: var(--text-muted); }
.info-item dd { margin: 0; font-size: var(--font-base); color: var(--text-primary); font-weight: var(--weight-medium); word-break: break-word; }
.info-item dd.desc { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); }
.info-item dd.muted { font-weight: var(--weight-regular); color: var(--text-light); }
.price { color: var(--color-price); font-weight: var(--weight-bold); }
.origin { margin-left: var(--space-2); color: var(--text-light); text-decoration: line-through; font-weight: var(--weight-regular); font-size: var(--font-sm); }

/* 窄屏：图片与字段上下排布（不横向滚动） */
@media (max-width: 767px) {
  .info-card { flex-direction: column; }
}

/* ===== 分区标题（纯文本分隔，非卡片：评价列表自带卡） ===== */
.section-title {
  margin: var(--space-2) 0 var(--space-3);
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-tight);
}

/* ===== 评价列表 ===== */
.cell-text { display: inline-block; max-width: 360px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; }
.status-cell { display: inline-flex; align-items: center; gap: var(--space-2); }
.status-text { font-size: var(--font-xs); color: var(--text-muted); font-weight: var(--weight-medium); }
.status-text.on { color: var(--color-success); }
.status-text.off { color: var(--color-error); }
</style>
