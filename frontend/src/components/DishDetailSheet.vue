<template>
  <view class="dish-sheet-root">
    <!-- 半透明遮罩（点击关闭） -->
    <view
      v-if="open"
      class="sheet-mask"
      :class="{ show: maskShow }"
      @tap="requestClose"
    />

    <!-- 底部弹层：复用 ApplySheet 抽屉动画范式（spring 0.8/0.3、仅向下下拉、reduced-motion 交叉淡入） -->
    <view
      class="bottom-sheet"
      :class="{ open: sheetOpen }"
      :style="sheetStyle"
      @touchstart="onSheetTouchStart"
      @touchmove="onSheetTouchMove"
      @touchend="onSheetTouchEnd"
      @touchcancel="onSheetTouchEnd"
    >
      <view class="sheet-grabber" />
      <view class="sheet-head">
        <text class="sheet-title">菜品详情</text>
        <view class="sheet-head-actions">
          <IconSvg name="share" :size="36" color="var(--text-tertiary)" class="sheet-head-action" @tap="onShare" />
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" class="sheet-head-action" @tap="requestClose" />
        </view>
      </view>

      <!-- 滚动内容区 -->
      <scroll-view
        class="sheet-body"
        scroll-y
        :scroll-with-animation="!reduceMotion"
      >
        <template v-if="dishStore.loading && !dish">
          <view class="dish-skeleton">
            <view class="skeleton-swiper"></view>
            <view class="skeleton-card">
              <view class="skeleton-line skeleton-title"></view>
              <view class="skeleton-line skeleton-price"></view>
              <view class="skeleton-tags">
                <view class="skeleton-tag"></view>
                <view class="skeleton-tag"></view>
              </view>
              <view class="skeleton-line skeleton-rating"></view>
            </view>
            <view class="skeleton-card">
              <view class="skeleton-line skeleton-block"></view>
              <view class="skeleton-line skeleton-block"></view>
              <view class="skeleton-line skeleton-block short"></view>
            </view>
          </view>
        </template>

        <template v-else-if="dish">
          <ImageSwiper :images="dish.images || [dish.image]" />

          <CardSection>
            <view class="title-row" @longpress="onDishLongPress">
              <text class="dish-name">{{ dish.name }}</text>
              <view class="price-box">
                <block v-if="hasPromo">
                  <text class="promo-price">¥{{ dish.promoPrice }}</text>
                  <text class="origin-price">¥{{ dish.originalPrice }}</text>
                  <text class="promo-tag"><IconSvg name="clock" :size="22" color="var(--color-hot)" /> 限时</text>
                </block>
                <text v-else class="price-text">¥{{ dish.price }}</text>
              </view>
            </view>

            <view class="tag-row" v-if="dishTagList.length > 0">
              <TagLabel v-for="tag in dishTagList" :key="tag" :text="tag" />
            </view>

            <view class="rating-row">
              <view class="star-num">
                <IconSvg name="star-filled" :size="28" color="var(--color-star)" />
                <text class="star-num-text">{{ dish.rating }}</text>
              </view>
            </view>
          </CardSection>

          <!-- ===== 合并卡片：位置与营业 / 菜品属性 / 菜品介绍，单卡内有序分区 ===== -->
          <CardSection title="菜品信息">
            <!-- 分区一：位置与营业 -->
            <view class="info-block">
              <view class="info-row info-row-tap" @tap="goToCanteen">
                <IconSvg name="location" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
                <text class="info-row-label">所在位置</text>
                <text class="info-row-value">{{ locationText }}</text>
                <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="info-row-arrow" />
              </view>
              <view class="info-row info-row-tap" v-if="dish.businessHours" @tap="goToStall">
                <IconSvg name="clock" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
                <text class="info-row-label">营业时段</text>
                <text class="info-row-value">{{ dish.businessHours }}</text>
                <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="info-row-arrow" />
              </view>
            </view>

            <!-- 分区二：菜品属性 -->
            <view class="info-block info-block-divider" v-if="attrTags.length > 0">
              <view class="info-row" v-if="spiceLabel">
                <IconSvg name="chili" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
                <text class="info-row-label">辣度</text>
                <text class="info-row-value">{{ spiceLevelText }}</text>
              </view>
              <view class="info-row" v-if="portionLabel">
                <IconSvg name="portion" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
                <text class="info-row-label">分量</text>
                <text class="info-row-value">{{ portionLevelText }}</text>
              </view>
              <view class="info-row" v-if="dish.limited">
                <IconSvg name="clock" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
                <text class="info-row-label">供应</text>
                <text class="info-row-value">限量供应</text>
              </view>
              <view class="info-row" v-for="p in servePeriodLabels" :key="p">
                <IconSvg name="clock" :size="28" color="var(--text-tertiary)" class="info-row-icon" />
                <text class="info-row-label">供应时段</text>
                <text class="info-row-value">{{ p }}</text>
              </view>
            </view>

            <!-- 分区三：菜品介绍 -->
            <view class="info-block info-block-divider" v-if="dish.description">
              <text class="desc-content">{{ dish.description }}</text>
            </view>
          </CardSection>

          <!-- ===== 评价区（内联前 3 条） ===== -->
          <CardSection>
            <SectionTitle :title="`用户评价 (${reviewTotal})`" noMargin />
            <view class="review-list" v-if="reviewList.length > 0">
              <ReviewItem
                v-for="rv in reviewList.slice(0, 3)"
                :key="rv.id"
                :review="rv"
                :deletable="rv.userId === currentUserId"
                @delete="onDeleteReview"
              />
            </view>
            <EmptyState v-else text="暂无评价，来写第一条吧" />
          </CardSection>

          <!-- 申请下架/纠错：不常用，降级为底部弱化小文字链接 -->
          <view class="apply-link" @tap="openApply">
            <text class="apply-link-text">反馈 / 申请下架</text>
            <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="apply-link-arrow" />
          </view>

          <!-- 关联动态（低优先级，置底轻量区块；task-12.6 跳动态详情） -->
          <CardSection v-if="relatedMoments.length > 0" title="">
            <SectionTitle title="关联动态" noMargin />
            <view class="related-moment-list">
              <view
                v-for="m in relatedMoments"
                :key="m.id"
                class="related-moment-item"
                :class="{ pressed: pressedRelatedKey === m.id }"
                @touchstart="pressedRelatedKey = m.id"
                @touchend="pressedRelatedKey = ''"
                @touchcancel="pressedRelatedKey = ''"
                @mousedown="pressedRelatedKey = m.id"
                @mouseup="pressedRelatedKey = ''"
                @mouseleave="pressedRelatedKey = ''"
                @tap="goRelatedMoment(m.id)"
              >
                <text class="related-moment-text">{{ m.content }}</text>
                <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="related-moment-arrow" />
              </view>
            </view>
          </CardSection>
        </template>

        <EmptyState v-else text="菜品不存在或已下架" />
        <view style="height: var(--spacing-lg)"></view>
      </scroll-view>

      <!-- 底部操作栏（sheet 内吸底，安全区避让） -->
      <view class="action-bar" v-if="dish">
        <view class="fav-btn" :class="{ active: liked }" @tap="toggleLike">
          <IconSvg :name="liked ? 'heart-filled' : 'heart'" :size="40" :color="liked ? 'var(--color-like)' : 'var(--text-primary)'" class="fav-icon" />
          <text class="fav-text">{{ liked ? '已喜欢' : '喜欢' }}</text>
        </view>
        <view class="action-bar-btns">
          <AppButton text="写评价" type="outline" width="220rpx" margin="0 16rpx 0 0" @click="goToReview" />
          <AppButton text="去档口" width="240rpx" margin="0" @click="goToStall" />
        </view>
      </view>

      <!-- 申请下架/纠错 Sheet（共享组件） -->
      <ApplySheet
        :open="applyOpen"
        entity-type="DISH"
        :entity-id="currentDishId"
        @update:open="applyOpen = $event"
      />

      <!-- 分享面板（简化：复制分享文案） -->
      <view v-if="shareOpen" class="share-mask" :class="{ show: shareMaskShow }" @tap="shareOpen = false"></view>
      <view
        v-if="shareOpen"
        class="share-sheet"
        :class="{ open: shareOpen }"
        :style="shareSheetStyle"
        @touchstart="onShareTouchStart"
        @touchmove="onShareTouchMove"
        @touchend="onShareTouchEnd"
        @touchcancel="onShareTouchEnd"
      >
        <view class="share-sheet-head">
          <text class="share-sheet-title">分享菜品</text>
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" class="share-sheet-close" @tap="shareOpen = false" />
        </view>
        <view class="share-body">
          <view class="share-option" @tap="copyShareText">
            <IconSvg name="share" :size="40" color="var(--color-primary)" />
            <text class="share-option-text">复制分享文案</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import TagLabel from '@/components/TagLabel.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppButton from '@/components/AppButton.vue'
import IconSvg from '@/components/IconSvg.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { deleteDish } from '@/api/dish'
import { deleteReview } from '@/api/review'
import * as momentApi from '@/api/moment'
import { SPICE_LEVELS, PORTION_LEVELS, SERVE_PERIOD_MAP } from '@/constants/categories'
import type { Review } from '@/types/review'
import type { Moment } from '@/types/moment'

const props = defineProps<{
  /** 是否展示弹层 */
  open: boolean
  /** 菜品 ID，watch 驱动加载 */
  dishId: number
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
}>()

const dishStore = useDishStore()
const userStore = useUserStore()

const dish = computed(() => dishStore.currentDish)
const reviewList = computed(() => dishStore.reviewList)
const reviewTotal = computed(() => dishStore.reviewTotal)
const currentDishId = computed(() => props.dishId)
const currentUserId = computed(() => userStore.userInfo?.id)
const liked = ref(false)

/** ===== 关联动态（task-12.6：GET /moments?dishId=，跳动态详情） ===== */
const relatedMoments = ref<Moment[]>([])
const pressedRelatedKey = ref<number | ''>('')

async function loadRelatedMoments() {
  const id = loadedDishId || currentDishId.value
  if (!id) return
  try {
    const { list } = await momentApi.getMoments({ dishId: id, pageSize: 5 })
    relatedMoments.value = list
  } catch {
    relatedMoments.value = []
  }
}

function goRelatedMoment(id: number) {
  emit('update:open', false)
  uni.navigateTo({ url: `/pages/pages-detail/moment?id=${id}` })
}

/** reduced-motion 降级 */
const reduceMotion = ref(false)
if (typeof window !== 'undefined') {
  reduceMotion.value = !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

/** 折扣价展示 */
const hasPromo = computed(() => !!dish.value?.promoPrice)

/** 标签列表 */
const dishTagList = computed(() => {
  const d = dish.value
  if (!d) return []
  const list: string[] = []
  if (d.isNew) list.push('新品')
  for (const t of d.tags) {
    if (!list.includes(t)) list.push(t)
  }
  return list
})

const spiceLabel = computed(() => {
  const lv = dish.value?.spiceLevel
  if (lv == null) return ''
  return `辣度·${SPICE_LEVELS[lv] ?? '未知'}`
})
const portionLabel = computed(() => {
  const lv = dish.value?.portion
  if (lv == null) return ''
  return `分量·${PORTION_LEVELS[lv] ?? '未知'}`
})
const servePeriodLabels = computed(() => {
  const raw = dish.value?.servePeriod || ''
  if (!raw) return []
  return raw.split(',').map(s => s.trim()).filter(Boolean).map(key => SERVE_PERIOD_MAP[key] || key)
})
const attrTags = computed(() => [
  spiceLabel.value,
  portionLabel.value,
  dish.value?.limited ? 'limited' : '',
  ...servePeriodLabels.value,
].filter(Boolean))

const locationText = computed(() => {
  const d = dish.value
  if (!d) return ''
  const nodes: string[] = []
  if (d.canteen) nodes.push(d.canteen)
  if (d.floor) nodes.push(String(d.floor))
  if (d.stallName) nodes.push(d.stallName)
  if (d.windowNo) nodes.push(`窗口 ${d.windowNo}`)
  return nodes.join(' › ') || '未知位置'
})
const spiceLevelText = computed(() => spiceLabel.value.replace(/^辣度·/, ''))
const portionLevelText = computed(() => portionLabel.value.replace(/^分量·/, ''))

/** ===== 弹层开关动画（ApplySheet 抽屉范式） ===== */
const sheetOpen = ref(false)
const maskShow = ref(false)
const dragOffset = ref(0)
const dragging = ref(false)

const sheetStyle = computed(() => ({
  transform: `translateY(calc(${sheetOpen.value ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  transition: dragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
}))

/** watch open：开启动画；关闭时复位 */
watch(() => props.open, (v) => {
  if (v) {
    nextTick(() => {
      maskShow.value = true
      sheetOpen.value = true
    })
  } else {
    maskShow.value = false
    sheetOpen.value = false
    dragOffset.value = 0
    shareOpen.value = false
  }
})

/** watch dishId：驱动数据加载（无 onLoad 残留；打开不同菜品正确刷新） */
let loadedDishId = 0
watch(() => props.dishId, (id) => {
  if (!id) return
  loadedDishId = id
  liked.value = false
  loadDishData()
})

async function loadDishData() {
  if (!loadedDishId) return
  relatedMoments.value = []
  await Promise.all([
    dishStore.fetchDetail(loadedDishId),
    dishStore.fetchReviews(loadedDishId, { sort: 'latest', isWithImage: false }),
    loadRelatedMoments(),
  ])
}

function requestClose() {
  emit('update:open', false)
}

let startY = 0
function onSheetTouchStart(e: any) {
  startY = e.touches?.[0]?.clientY ?? 0
  dragging.value = true
}
function onSheetTouchMove(e: any) {
  if (!dragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const delta = y - startY
  dragOffset.value = delta > 0 ? delta : 0
}
function onSheetTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (dragOffset.value > 120) requestClose()
  dragOffset.value = 0
}

/** 整页喜欢态（乐观切换；未登录引导） */
async function toggleLike() {
  if (!userStore.requireAuth()) return
  liked.value = !liked.value
}

/** ===== 分享面板（简化：复制分享文案） ===== */
const shareOpen = ref(false)
const shareMaskShow = ref(false)
const shareDragging = ref(false)
const shareDragOffset = ref(0)

function onShare() {
  shareOpen.value = true
  nextTick(() => { shareMaskShow.value = true })
}
function closeShare() {
  shareOpen.value = false
  shareMaskShow.value = false
  shareDragOffset.value = 0
}
function copyShareText() {
  const d = dish.value
  if (!d) return
  // 独立菜品页已移除（task-10 sheet 化），复制人类可读的分享文案而非失效链接
  const text = `推荐一道好菜「${d.name}」¥${d.price}，在${d.canteen || ''} · ${d.stallName || ''}，来自食在交大`
  uni.setClipboardData({
    data: text,
    success: () => uni.showToast({ title: '分享文案已复制', icon: 'none' }),
  })
  closeShare()
}

let shareStartY = 0
function onShareTouchStart(e: any) {
  shareStartY = e.touches?.[0]?.clientY ?? 0
  shareDragging.value = true
}
function onShareTouchMove(e: any) {
  if (!shareDragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const delta = y - shareStartY
  shareDragOffset.value = delta > 0 ? delta : 0
}
function onShareTouchEnd() {
  if (!shareDragging.value) return
  shareDragging.value = false
  if (shareDragOffset.value > 120) closeShare()
  shareDragOffset.value = 0
}

const shareSheetStyle = computed(() => ({
  transform: `translateY(calc(${shareDragging.value ? shareDragOffset.value : 0}px))`,
  transition: shareDragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
}))

/** 申请下架/纠错 Sheet */
const applyOpen = ref(false)
function openApply() {
  if (!userStore.requireAuth()) return
  applyOpen.value = true
}

/** 删除本人菜品 */
function onDishLongPress() {
  const d = dish.value
  if (!d) return
  if (!userStore.userInfo || (d.createdBy != null && d.createdBy !== userStore.userInfo.id)) return
  uni.showModal({
    title: '删除菜品',
    content: '确定删除你发布的这道菜品吗？删除后不可恢复。',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteDish(d.id)
          uni.showToast({ title: '已删除', icon: 'none' })
          requestClose()
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },
  })
}

/** 删除本人评价（仅本人 userId；task-12.5 DELETE /my/reviews/{id}） */
function onDeleteReview(rv: Review) {
  if (!userStore.requireAuth()) return
  if (userStore.userInfo?.id && rv.userId !== userStore.userInfo.id) return
  uni.showModal({
    title: '删除评价',
    content: '确定删除这条评价吗？删除后不可恢复。',
    confirmText: '删除',
    confirmColor: '#e54d42',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteReview(rv.id)
        uni.showToast({ title: '评价已删除', icon: 'none' })
        // 通过 store 状态（reviewList 为 store ref）移除并校正计数
        dishStore.reviewList = dishStore.reviewList.filter(x => x.id !== rv.id)
        dishStore.reviewTotal = Math.max(0, dishStore.reviewTotal - 1)
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}

function goToReview() {
  if (!userStore.requireAuth()) return
  requestClose()
  uni.navigateTo({ url: `/pages/pages-detail/review?dishId=${currentDishId.value}` })
}

function goToStall() {
  if (dish.value) {
    dishStore.navParams.stallName = dish.value.stallName
    dishStore.navParams.canteen = dish.value.canteen
    requestClose()
    uni.navigateTo({ url: '/pages/pages-detail/stall' })
  }
}

function goToCanteen() {
  if (dish.value?.canteen) {
    requestClose()
    uni.navigateTo({ url: `/pages/pages-detail/canteen?canteen=${encodeURIComponent(dish.value.canteen)}` })
  }
}
</script>

<style scoped>
.dish-sheet-root { z-index: 200; }

/* 遮罩 */
.sheet-mask {
  position: fixed; inset: 0; background: var(--overlay-scrim);
  opacity: 0; transition: opacity 0.3s ease; z-index: 200;
}
.sheet-mask.show { opacity: 1; }

/* 底部弹层 */
.bottom-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 210;
  transform: translateY(100%);
  display: flex;
  flex-direction: column;
  max-height: 92vh;
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
  will-change: transform;
}
.bottom-sheet.open { transform: translateY(0); }

.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--border-color); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); flex-shrink: 0; }
.sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.sheet-head-actions { display: flex; align-items: center; gap: var(--spacing-xs); }
.sheet-head-action { padding: 0 var(--spacing-xs); }

.sheet-body { flex: 1; overflow-y: auto; padding: var(--spacing-md) var(--spacing-md) 0; box-sizing: border-box; }

/* ===== 内容样式（迁移自原 dish 页） ===== */
.title-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.dish-name { font-size: var(--font-h1); font-weight: 700; letter-spacing: var(--tracking-h3); line-height: 1.2; color: var(--text-primary); flex: 1; min-width: 0; }
.price-text { font-size: var(--font-h2); font-weight: 700; color: var(--color-price); flex-shrink: 0; font-variant-numeric: tabular-nums; }
.price-box { display: flex; align-items: baseline; gap: var(--spacing-xs); flex-shrink: 0; flex-wrap: wrap; justify-content: flex-end; }
.promo-price { font-size: var(--font-h2); font-weight: 800; color: var(--color-error); font-variant-numeric: tabular-nums; }
.origin-price { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
.promo-tag { font-size: 20rpx; font-weight: 700; color: var(--text-white); background: var(--color-error); padding: 0 var(--spacing-xs); border-radius: var(--radius-icon); display: inline-flex; align-items: center; gap: 4rpx; }
.tag-row { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.rating-row { display: flex; align-items: center; gap: var(--spacing-xs); margin-top: var(--spacing-md); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.star-num { display: inline-flex; align-items: center; gap: 4rpx; }
.star-num-text { font-size: 30rpx; color: var(--text-secondary); font-weight: 600; font-variant-numeric: tabular-nums; }

.info-row { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-xs) 0; transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.info-row-tap:active { transform: scale(var(--press-scale)); }
.info-row-icon { width: 28rpx; height: 28rpx; line-height: 1; flex-shrink: 0; }
.info-row-label { flex-shrink: 0; font-size: var(--font-aux); color: var(--text-tertiary); font-weight: 600; }
.info-row-value { flex: 1; min-width: 0; font-size: var(--font-body); color: var(--text-primary); font-weight: 500; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.info-row-arrow { font-size: var(--icon-sm); color: var(--text-tertiary); flex-shrink: 0; }

.desc-content { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.6; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 4; overflow: hidden; }

.info-block { padding: 0; }
.info-block-divider { margin-top: var(--spacing-md); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }

.review-list { margin-top: var(--spacing-sm); }

/* ===== 关联动态（低优先级置底区块，task-12.6） ===== */
.related-moment-list { margin-top: var(--spacing-sm); }
.related-moment-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
  border-bottom: 2rpx solid var(--border-color);
  transition: transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.related-moment-item:last-child { border-bottom: none; }
.related-moment-item.pressed { transform: scale(var(--press-scale)); }
.related-moment-text {
  flex: 1;
  min-width: 0;
  font-size: var(--font-aux);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.related-moment-arrow { flex-shrink: 0; }

.apply-link { display: flex; align-items: center; justify-content: center; gap: 4rpx; padding: var(--spacing-md) 0 var(--spacing-sm); -webkit-tap-highlight-color: transparent; }
.apply-link:active { opacity: 0.6; }
.apply-link-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.apply-link-arrow { flex-shrink: 0; }

/* 底部操作栏（sheet 内吸底） */
.action-bar { flex-shrink: 0; display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--glass-highlight-soft); }
.fav-btn { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 96rpx; min-width: 96rpx; gap: var(--spacing-xs); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.fav-btn:active { transform: scale(var(--press-scale)); }
.fav-icon { width: 40rpx; height: 40rpx; line-height: 1; flex-shrink: 0; }
.fav-text { font-size: 20rpx; color: var(--text-primary); white-space: nowrap; line-height: 1.2; }
.fav-btn.active .fav-text { color: var(--color-like); }
.action-bar-btns { flex: 1; display: flex; justify-content: flex-end; }

/* 分享面板 */
.share-mask { position: fixed; inset: 0; background: var(--overlay-scrim); z-index: 300; opacity: 0; transition: opacity 0.3s ease; }
.share-mask.show { opacity: 1; }
.share-sheet { position: fixed; left: 0; right: 0; bottom: 0; background: var(--bg-card); border-radius: var(--radius-modal) var(--radius-modal) 0 0; box-shadow: var(--shadow-modal); z-index: 310; transform: translateY(100%); transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }
.share-sheet.open { transform: translateY(0); }
.share-sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.share-sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.share-sheet-close { padding: 0 var(--spacing-xs); }
.share-body { padding: var(--spacing-md) var(--spacing-lg); }
.share-option { display: flex; align-items: center; gap: var(--spacing-md); padding: var(--spacing-sm) 0; transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.share-option:active { transform: scale(var(--press-scale)); }
.share-option-text { font-size: var(--font-body); color: var(--text-primary); font-weight: 600; }

/* 加载骨架 */
.dish-skeleton { display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-swiper { width: 100%; height: 460rpx; border-radius: var(--radius-card); background: var(--bg-soft); animation: skeleton-pulse 1.2s ease-in-out infinite; }
.skeleton-card { background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-sm); }
.skeleton-line { height: 28rpx; border-radius: var(--radius-tag); background: var(--bg-soft); animation: skeleton-pulse 1.2s ease-in-out infinite; }
.skeleton-title { width: 60%; height: 40rpx; }
.skeleton-price { width: 36%; }
.skeleton-rating { width: 44%; }
.skeleton-block { height: 24rpx; }
.skeleton-block.short { width: 70%; }
.skeleton-tags { display: flex; gap: var(--spacing-xs); }
.skeleton-tag { width: 96rpx; height: 36rpx; border-radius: var(--radius-tag); background: var(--bg-soft); animation: skeleton-pulse 1.2s ease-in-out infinite; }
@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@media (prefers-reduced-motion: reduce) {
  .sheet-mask { transition: opacity 0.2s ease; }
  .bottom-sheet { transition: opacity 0.2s ease; transform: none !important; }
  .share-sheet { transition: opacity 0.2s ease; transform: none !important; }
  .share-mask { transition: opacity 0.2s ease; }
  .skeleton-swiper, .skeleton-line, .skeleton-tag { animation: none; }
}
</style>
