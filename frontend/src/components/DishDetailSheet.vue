<template>
  <view class="dish-sheet-root">
    <!-- 半透明遮罩（点击关闭；touchmove.stop 防背景滚动穿透，小程序 catchtouchmove） -->
    <view
      v-if="open"
      class="sheet-mask"
      :class="{ show: maskShow }"
      @tap="requestClose"
      @touchmove.stop.prevent="noop"
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
      <!-- 头部按钮：覆盖在图片上方（圆形半透明底），返回箭头向下（提示下拉关闭）。
           用 arrow-down 图标直接指下，不依赖 transform 旋转（微信小程序方向不可靠） -->
      <view class="sheet-head">
        <view class="sheet-round-btn" @tap="requestClose">
          <IconSvg name="arrow-down" :size="34" color="var(--text-white)" />
        </view>
        <!-- 分享：微信原生组件（open-type=share → 页面 onShareAppMessage，task todo#5） -->
        <button class="sheet-round-btn sheet-round-btn-share" open-type="share" @tap="onShareTap">
          <IconSvg name="share" :size="30" color="var(--text-white)" />
        </button>
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

          <!-- hero 卡（2026-08-03 精简）：名称 +「信息有误？」/ 价格 / 标徽 / 评分 / 一句话简介；无"菜品信息"卡片 -->
          <CardSection>
            <view class="title-row">
              <text class="dish-name" @longpress="onDishLongPress">{{ dish.name }}</text>
              <text class="feedback-link" @tap="openApply">信息有误？</text>
            </view>
            <view class="price-row">
              <block v-if="hasPromo">
                <text class="promo-price">¥{{ dish.promoPrice }}</text>
                <text class="origin-price">¥{{ dish.originalPrice }}</text>
                <text class="promo-tag"><IconSvg name="clock" :size="22" color="var(--color-accent)" /> 限时</text>
              </block>
              <text v-else class="price-text">¥{{ dish.price }}</text>
            </view>
            <view class="tag-row" v-if="dishTagList.length > 0">
              <TagLabel v-for="tag in dishTagList" :key="tag" :text="tag" />
            </view>
            <view class="rating-row" v-if="dish.rating > 0">
              <view class="star-num">
                <IconSvg name="star-filled" :size="28" color="var(--color-star)" />
                <text class="star-num-text">{{ dish.rating }}</text>
              </view>
            </view>
            <!-- 位置（2026-08-03 恢复）：食堂 › 楼层 › 档口 › 窗口；从档口详情页打开时（hideLocation）仅展示不可跳转，避免循环 -->
            <view class="loc-row" :class="{ 'loc-row-static': hideLocation }" @tap="!hideLocation && goToStall()">
              <IconSvg name="location" :size="24" color="var(--color-primary)" class="loc-icon" />
              <text class="loc-text">{{ locationText }}</text>
              <IconSvg v-if="!hideLocation" name="arrow" :size="24" color="var(--text-tertiary)" class="loc-arrow" />
            </view>
            <view class="desc-row" v-if="dish.description">
              <text class="desc-content">{{ dish.description }}</text>
            </view>
          </CardSection>

          <!-- ===== 评价区：与动态详情评论区同款结构（comment-section + comment-title），三处评论区域视觉完全一致 ===== -->
          <view class="comment-section">
            <text class="comment-title">评价 ({{ reviewTotal }})</text>
            <view class="review-list" v-if="reviewList.length > 0">
              <ReviewItem
                v-for="rv in reviewList.slice(0, 3)"
                :key="rv.id"
                :review="rv"
                hide-useful
                :deletable="rv.userId === currentUserId"
                @delete="onDeleteReview"
              />
            </view>
            <EmptyState v-else text="暂无评价，来写第一条吧" icon="comment" />
          </view>
        </template>

        <EmptyState v-else text="菜品不存在或已下架" />
        <view style="height: var(--spacing-lg)"></view>
      </scroll-view>

      <!-- 底部操作栏（sheet 内吸底，安全区避让；已移除「喜欢」——favorite 模块无数据源，仅本地切换无意义） -->
      <view class="action-bar" v-if="dish">
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

    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import TagLabel from '@/components/TagLabel.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppButton from '@/components/AppButton.vue'
import IconSvg from '@/components/IconSvg.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { deleteDish, addView } from '@/api/dish'
import { deleteReview } from '@/api/review'
import type { Review } from '@/types/review'
import { sharedDish } from '@/utils/shareState'

const props = defineProps<{
  /** 是否展示弹层 */
  open: boolean
  /** 菜品 ID，watch 驱动加载 */
  dishId: number
  /** 顶部边界偏移（弹层顶部不越过页面 Header 底部；如档口页传 176rpx） */
  topOffset?: string
  /** 隐藏位置跳档口（从档口详情页打开时传 true，避免 菜品→档口→菜品 循环跳转） */
  hideLocation?: boolean
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

/** 位置文案：食堂 › 楼层 › 档口 › 窗口（2026-08-03 恢复，hero 卡展示；点击跳档口详情） */
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



/** ===== 弹层开关动画（ApplySheet 抽屉范式） ===== */
const sheetOpen = ref(false)
const maskShow = ref(false)
const dragOffset = ref(0)
const dragging = ref(false)

/** 空处理器：mask touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}

const sheetStyle = computed(() => ({
  '--sheet-top-offset': props.topOffset || '0rpx',
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
  }
})

/** watch dishId：驱动数据加载（无 onLoad 残留；打开不同菜品正确刷新） */
let loadedDishId = 0
watch(() => props.dishId, (id) => {
  if (!id) return
  loadedDishId = id
  loadDishData()
})

async function loadDishData() {
  if (!loadedDishId) return
  // 浏览埋点（POST /dishes/{id}/view）：供热度排序/猜你喜欢；失败静默
  addView(loadedDishId)
  await Promise.all([
    dishStore.fetchDetail(loadedDishId),
    dishStore.fetchReviews(loadedDishId, { sort: 'latest', isWithImage: false }),
  ])
}

function requestClose() {
  emit('update:open', false)
}

let startY = 0
let lastY = 0
let lastTime = 0
let velocity = 0
function onSheetTouchStart(e: any) {
  startY = e.touches?.[0]?.clientY ?? 0
  lastY = startY
  lastTime = Date.now()
  velocity = 0
  dragging.value = true
}
function onSheetTouchMove(e: any) {
  if (!dragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const now = Date.now()
  // 1:1 跟随手指 + 记录瞬时速度（apple-design §5 velocity handoff）
  const dt = Math.max(now - lastTime, 1)
  velocity = ((y - lastY) / dt) * 1000 // px/s
  lastY = y
  lastTime = now
  dragOffset.value = Math.max(y - startY, 0)
}
function onSheetTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  // 松手速度 > 480px/s 视为向下甩动，直接关闭（momentum projection，apple §5/§6）
  if (velocity > 480 || dragOffset.value > 120) {
    requestClose()
  }
  dragOffset.value = 0
}

/** ===== 分享面板（简化：复制分享文案） ===== */
/** 分享菜品（微信原生分享：记录待分享菜品，页面 onShareAppMessage 读取生成卡片） */
function onShareTap() {
  const d = dish.value
  if (!d) return
  sharedDish.value = {
    id: d.id,
    name: d.name,
    price: d.price,
    stallId: d.stallId,
    canteen: d.canteen,
    stallName: d.stallName,
  }
}

/** 申请下架/纠错 Sheet（与反馈中心一致：游客可直接打开填写，提交时再由后端/API 引导登录） */
const applyOpen = ref(false)
function openApply() {
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
  if (!userStore.requireAuth(() => onDeleteReview(rv))) return
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
  if (!userStore.requireAuth(() => goToReview())) return
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
  /* 顶部边界：不超过页面 Header（sticky 固定组件）底部，避免遮挡页面顶栏 */
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 210;
  transform: translateY(100%);
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - var(--sheet-top-offset, 176rpx));
  /* 圆角裁剪内容（图片贴顶时被弹层圆角裁掉直角，图片与上边沿无间隙） */
  overflow: hidden;
  /* 注意：不再在此加 padding-bottom——操作栏 .action-bar 自带安全区，避免双重叠加导致按钮离底过远 */
  will-change: transform;
}
.bottom-sheet.open { transform: translateY(0); }

/* 下拉条：绝对定位覆盖在图片上方（不占位，保证图片贴弹层顶沿、无间隙）；
   will-change 预提示合成层，避免拖动跟手时抖动（apple-design §11） */
.sheet-grabber { position: absolute; top: var(--spacing-sm); left: 50%; transform: translateX(-50%); will-change: transform; z-index: 31; width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--overlay-dark-soft); }
/* 头部按钮：绝对定位覆盖在图片上方，圆形半透明深底 + 白图标 */
.sheet-head { position: absolute; top: var(--spacing-sm); left: 0; right: 0; display: flex; align-items: center; justify-content: space-between; padding: 0 var(--spacing-md); z-index: 30; pointer-events: none; }
.sheet-round-btn {
  pointer-events: auto;
  width: 72rpx; height: 72rpx; border-radius: 50%;
  background: var(--overlay-dark-soft);
  display: flex; align-items: center; justify-content: center;
  transition: transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.sheet-round-btn:active { transform: scale(var(--press-scale)); }
/* button 重置（分享按钮是原生 button，需去掉默认 padding/边框，保证圆形且位置正确） */
.sheet-round-btn.sheet-round-btn-share { margin: 0; padding: 0; border: none; line-height: 1; }
.sheet-round-btn.sheet-round-btn-share::after { border: none; }

/* 滚动内容：图片贴屏幕左右边缘（顶部 padding 为 0，卡片自带左右 margin）；
   底部留白接管安全区兜底（操作栏存在时由 .action-bar 自带安全区） */
.sheet-body { flex: 1; overflow-y: auto; padding: 0 0 calc(var(--spacing-lg) + env(safe-area-inset-bottom)); box-sizing: border-box; }

/* ===== 内容样式（2026-08-03 精简：hero 卡 = 名称+信息有误/价格/标徽/评分/一句话简介） ===== */
.title-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.dish-name { font-size: var(--font-h1); font-weight: var(--weight-heavy); letter-spacing: var(--tracking-h1); line-height: 1.2; color: var(--text-primary); flex: 1; min-width: 0; }
/* 「信息有误？」：名称行最右弱链接（2026-08-03 起，原底部 apply-link 移除）。
   按压反馈：加内边距扩大命中区（Apple：44×44 最小触摸目标）+ opacity 反馈 */
.feedback-link { font-size: var(--font-aux); color: var(--text-tertiary); flex-shrink: 0; padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); transition: opacity 120ms ease, background-color 120ms ease; -webkit-tap-highlight-color: transparent; }
.feedback-link:active { opacity: 0.55; background-color: var(--bg-soft); }
.price-row { display: flex; align-items: baseline; gap: var(--spacing-xs); flex-wrap: wrap; margin-top: var(--spacing-xs); }
.price-text { font-size: var(--font-h2); font-weight: var(--weight-bold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.promo-price { font-size: var(--font-h2); font-weight: var(--weight-heavy); color: var(--color-error); font-variant-numeric: tabular-nums; }
.origin-price { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
.promo-tag { font-size: var(--font-tiny); font-weight: var(--weight-bold); color: var(--text-white); background: var(--color-error); padding: 0 var(--spacing-xs); border-radius: var(--radius-icon); display: inline-flex; align-items: center; gap: var(--spacing-xs); }
.tag-row { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.rating-row { display: flex; align-items: center; gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.star-num { display: inline-flex; align-items: center; gap: var(--spacing-xs); }
.star-num-text { font-size: var(--font-body); color: var(--text-secondary); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; }
/* 位置行（2026-08-03 恢复）：location 图标 + 文本 + arrow，可点击跳档口 */
.loc-row { display: flex; align-items: center; gap: var(--spacing-xs); margin-top: var(--spacing-sm); padding: var(--spacing-xs) var(--spacing-sm) var(--spacing-xs) 0; border-radius: var(--radius-tag); transition: background-color 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.loc-row:active { background-color: var(--bg-soft); }
/* 静态位置行（从档口详情页打开）：仅展示、无按压反馈、不可跳转 */
.loc-row-static { -webkit-tap-highlight-color: transparent; }
.loc-row-static:active { background-color: transparent; }
.loc-icon { width: 24rpx; height: 24rpx; line-height: 1; flex-shrink: 0; }
.loc-text { flex: 1; min-width: 0; font-size: var(--font-small); color: var(--text-secondary); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.loc-arrow { width: 24rpx; height: 24rpx; line-height: 1; flex-shrink: 0; }
.desc-row { margin-top: var(--spacing-sm); }
.desc-content { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.6; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; }

/* 菜品评价卡：与动态详情评论区 comment-section 完全同款（结构、类名、样式值一致），
   卡片圆角 24px + 标题 34rpx / weight 800 / letter-spacing -0.02em（Apple Design Typography：大字负 tracking），
   背景用 var(--bg-card)（= #FFFFFF）与阴影与 moment.vue 评论区一致，token 化避免裸 hex */
.comment-section { margin: 0 var(--spacing-md); padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card-soft); }
.comment-title { display: block; font-size: var(--font-h3); font-weight: var(--weight-heavy); color: var(--text-primary); letter-spacing: var(--tracking-h3); margin-bottom: var(--spacing-md); }
.review-list { margin-top: var(--spacing-xs); }

/* 底部操作栏（sheet 内吸底）。Apple 材质：半透明白 + backdrop-filter 毛玻璃，
   内容滚动在下方透出（§12 Materials）；不支持 backdrop-filter 的环境回退实色 bg-card */
.action-bar { flex-shrink: 0; display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--blur-bg); backdrop-filter: blur(20px) saturate(180%); -webkit-backdrop-filter: blur(20px) saturate(180%); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--glass-highlight-soft); }
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .action-bar { background: var(--blur-bg-solid); }
}
.action-bar-btns { flex: 1; display: flex; justify-content: flex-end; }

/* 加载骨架：复用全局 shimmer（App.vue 1.4s）流光，与全站加载节奏统一（原 skeleton-pulse 脉冲已弃） */
.dish-skeleton { display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-swiper { width: 100%; height: 460rpx; border-radius: var(--radius-card); background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }
.skeleton-card { background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-sm); }
.skeleton-line { height: 28rpx; border-radius: var(--radius-tag); background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }
.skeleton-title { width: 60%; height: 40rpx; }
.skeleton-price { width: 36%; }
.skeleton-rating { width: 44%; }
.skeleton-block { height: 24rpx; }
.skeleton-block.short { width: 70%; }
.skeleton-tags { display: flex; gap: var(--spacing-xs); }
.skeleton-tag { width: 96rpx; height: 36rpx; border-radius: var(--radius-tag); background: linear-gradient(90deg, var(--bg-soft) 25%, var(--border-color) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }

@media (prefers-reduced-motion: reduce) {
  .sheet-mask { transition: opacity 0.2s ease; }
  .bottom-sheet { transition: opacity 0.2s ease; transform: none !important; }
  .skeleton-swiper, .skeleton-line, .skeleton-tag { animation: none; }
}
</style>
