<template>
  <view class="page my-reviews-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <Header title="我的主页" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y @scrolltolower="loadMore">
      <!-- 用户信息卡：主页的身份版面（头像 / 昵称 / 邮箱），右侧「编辑个人信息」→ 独立个人信息编辑页 -->
      <CardSection class="profile-strip" :class="{ 'is-verified': isVerified }" flush>
        <view class="strip-avatar-wrap">
          <ImageFallback v-if="userInfo?.avatar" :src="userInfo.avatar" class="strip-avatar" />
          <view v-else class="strip-avatar strip-avatar-empty">
            <IconSvg name="user" :size="36" :color="COLOR_MAP['text-tertiary']" />
          </view>
        </view>
        <view class="strip-meta">
          <text class="strip-nickname">{{ isVerified ? (userInfo?.nickname || '食客') : (userInfo?.nickname || '游客') }}</text>
          <text class="strip-sub">{{ isVerified ? (bindEmail || '--') : guestLabel }}</text>
        </view>
        <view class="strip-edit" role="button" aria-label="编辑个人信息" hover-class="pressed" @tap="goProfileEdit">
          <text class="strip-edit-text">编辑个人信息</text>
        </view>
      </CardSection>

      <!-- 评价区：信息卡下方是本人名下评价列表（有数据时才渲染区块标题，避免空榜烘标题）。
           区块标题一律用公共 `SectionTitle`（§4.9 红线）——UI 统一 Loop Round 1：收敛此处手写副本 -->
      <SectionTitle v-if="list.length" title="我的评价" />
      <view class="list">
        <!-- 评价卡 = 公共组件 ReviewItem（与菜品详情评价区**同一实现**）：
             本人视角专属信息经 dishName 可选 prop 注入 -->
        <ReviewItem
          v-for="r in list"
          :key="r.id"
          :review="r"
          :dish-name="r.dishName"
          @more="onMore(r)"
        />
      </view>

      <!-- 加载失败重试块（MP-012 同族，P3-03 上提为公共组件）：首屏请求失败 ≠ 无评价——
           先于空态渲染，避免网络失败被误读；恢复走重试块 @tap -->
      <RetryBlock v-if="loadFailed && !loading" @retry="onRetryLoad" />
      <!-- 游客空态：游客可自由进入本页（用户卡直进、无认证拦截），列表空给认证引导 -->
      <!-- 统一空态组件（UI 统一 Loop Round 2）：不再本页手写 `.empty-tip` -->
      <EmptyState v-else-if="isGuest" title="暂无评价" desc="完成身份认证后可发表评价" />
      <!-- 空态：首次进入无评价保持静默；仅「删除最后一条」触发时给轻提示，避免被误解为加载异常 -->
      <EmptyState v-else-if="emptiedByDelete" title="暂无评价" desc="去菜品详情写一条吧" />
    </scroll-view>

    <!-- 三点菜单（与菜品详情评价区同款交互）：「删除评价」危险红动作项 -->
    <ActionSheet :open="moreOpen" :items="moreItems" @close="moreOpen = false" @select="onSelect" />
  </view>
</template>

<script setup lang="ts">
/**
 * 我的主页：个人信息版面 + 名下评价列表的**复合页**（「我的」页用户卡点击进入）。
 * - 上半部 = 用户信息卡（头像 / 昵称 / 邮箱，游客态为短标识）+ 「编辑个人信息」入口 → 独立编辑页；
 *   下半部 = 本人的评价列表（区块标题「我的评价」），评价卡复用公共组件 ReviewItem（与详情页同一实现），
 *   本人视角专属信息经 dishName 可选 prop 注入
 * - 数据源 GET /my/reviews（后端联表返回 dishName），删除复用 DELETE /reviews/{id}；
 *   删除入口 = 卡片右上角三点 → 底部 ActionSheet「删除评价」→ 二次确认（与详情页同链路）
 * - 空态双口径：首次进入静默；删除导致清空时给轻提示
 * - 失败态（MP-012）：首屏失败渲染「加载失败 · 点击重试」块；分页失败保持静默，可再触底重试
 */
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import CardSection from '@/components/CardSection.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import { useOnShowRefresh } from '@/composables/useOnShowRefresh'
import { useUserStore } from '@/stores/user'
import { getMyReviews, deleteReview } from '@/api/review'
import type { MyReview } from '@/types/review'
import { backToHome } from '@/utils/nav'
import { PATH } from '@/utils/routes'
import { deriveGuestLabel } from '@/utils/guest'
import { usePagedList } from '@/composables/usePagedList'
import { toastError } from '@/utils/error'
// 图标色须传实色（IconSvg 的 color 不解析 var()）
import { COLOR_MAP, MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
const isVerified = computed(() => userStore.isVerified())
const bindEmail = computed(() => userInfo.value?.bindEmail || '')
/** 游客短标识：端上派生 =「食客 + id 尾 4 位」（与「我的」页同口径，走公共 `utils/guest.deriveGuestLabel`） */
const guestLabel = computed(() => deriveGuestLabel(userInfo.value?.id))
/** 游客态（列表空态分支 + 跳过需登录请求）；认证成功返回本页时 onShow 重拉自动切换为真实列表 */
const isGuest = computed(() => !userStore.isVerified())

/** 编辑个人信息：独立页（pages/profile/index），保存后自动返回本页 */
function goProfileEdit() {
  uni.navigateTo({ url: PATH.profile })
}

/** 仅「删除导致列表清空」时为 true，驱动空态轻提示 */
const emptiedByDelete = ref(false)

/**
 * 分页列表（公共 composable，UI 统一 Loop Round 17 抽取）：
 * 第 1 页重拉 / 触底加载更多 / 去重追加 / 失败回退页码 / `loading` 重入守卫 —— 全站一套语义。
 * 本页差异经选项注入：游客跳过（端点需登录，调必 401）、成功后复位「删除导致空列表」标记。
 */
const { list, loading, loadFailed, finished, load, loadMore } = usePagedList<MyReview>({
  fetchPage: async (page, pageSize) => (await getMyReviews({ page, pageSize })).list,
  canLoad: () => userStore.isVerified(),
  onLoadSuccess: () => { emptiedByDelete.value = false },
  loadFailLabel: '[my-reviews] 加载评价失败',
})

/** 重试块 @tap：从第 1 页重拉（与首屏同一条重拉路径）（MP-012） */
function onRetryLoad() {
  load()
}

/* ===== 三点菜单（ReviewItem @more → 页面级 ActionSheet）：删除本人评价的唯一入口 ===== */
const moreOpen = ref(false)
const moreTarget = ref<MyReview | null>(null)

function onMore(r: MyReview) {
  moreTarget.value = r
  moreOpen.value = true
}

/**
 * 动作项：本人恒为「删除评价」（危险红；本页列表全部为本人评价，无举报项）。
 * ⚠️ `iconColor` 必须传实色 `COLOR_MAP['error']`（ActionSheet 契约：IconSvg 不解析 var()）；
 * `textColor` 走 CSS 绑定、`var()` 合法。
 */
const moreItems = [
  { key: 'delete', label: '删除评价', icon: 'delete', iconColor: COLOR_MAP['error'], textColor: 'var(--color-error)' },
]

function onSelect(key: string) {
  const r = moreTarget.value
  if (!r) return
  if (key === 'delete') onDelete(r)
}

/** 删除本人评价：二次确认 → 删除 → 列表移除（删空后给轻提示） */
function onDelete(r: MyReview) {
  uni.showModal({
    title: '删除评价',
    content: '确定删除这条评价吗？删除后不可恢复。',
    confirmText: '删除',
    confirmColor: MODAL_CONFIRM_DANGER_COLOR,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteReview(r.id)
        list.value = list.value.filter(item => item.id !== r.id)
        uni.showToast({ title: '评价已删除', icon: 'none' })
        emptiedByDelete.value = list.value.length === 0
      } catch (e) {
        toastError(e, '删除失败')
      }
    },
  })
}

/**
 * onShow 重拉闸门（MP-07）：首次进入必拉；之后 30s 内返回本页不再全量重拉第 1 页、
 * 也不重置分页（本页无跨页写操作入口，删除已在本地移除条目）。
 * 失败重试块不经过闸门（用户显式意图 → 直接 load）。
 */
const { refreshOnShow } = useOnShowRefresh(load)

onShow(() => {
  refreshOnShow()
})

/* 触底加载更多：由模板上 `scroll-view` 的 `@scrolltolower="loadMore"` 触发。
   ⚠️ Round 17 修复缺陷：本页自 Round 12-A 起为「顶栏 + `scroll-view`（`flex: 1`）+ 页面 `height: 100vh`」
   ⇒ **页面自身不再滚动**，原先的页面级 `onReachBottom` 永远不会触发（触底分页失效）；现统一走滚动区事件。 */
</script>

<style scoped>
/* 页面根不带底色（UI 统一 Loop Round 11）：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层。
   结构化收口（Round 12-A，用户裁决）：页面 = 顶栏 + `scroll-view` 滚动区（`flex: 1`）——
   内容被裁在滚动区内，**不会**从透明的标题带背后经过（与首页 §11 同一结构性原则，零表面）。 */
.my-reviews-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }
.scroll-wrap { flex: 1; min-height: 0; padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg) + env(safe-area-inset-bottom)); box-sizing: border-box; }

/* 列表容器：卡片间距由容器 gap 承担；卡片本体样式（头像/昵称/星级/正文/配图/三点）由 ReviewItem 统一 */
.list { display: flex; flex-direction: column; gap: var(--spacing-sm); }

/* 用户信息卡：头像 + 昵称/副行 + 「编辑个人信息」，白底一级卡（与评价卡同语言） */
/* 区块标题已改用公共 `SectionTitle`（§4.9 红线）——此处不再保留手写副本样式 */
/* 卡片壳走公共 `CardSection`（UI 统一 Loop Round 14 裁决 2B-A 收敛）：内距统一到 `--spacing-md`
   （原 `--spacing-sm --spacing-md`）；`flush` ⇒ 本处自管块间距。 */
.profile-strip {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}
/* 认证态顶部 6rpx 主色软条纹（UI 稿：仅认证态显示；游客态透明） */
.profile-strip.is-verified { border-top: 6rpx solid var(--color-primary-soft); }
.strip-avatar-wrap { flex-shrink: 0; }
.strip-avatar { width: 120rpx; height: 120rpx; border-radius: var(--radius-circle); background: var(--bg-soft); }
.strip-avatar-empty { display: flex; align-items: center; justify-content: center; }
.strip-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-3xs); }
.strip-nickname { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.strip-sub { font-size: var(--font-small); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.strip-edit { flex-shrink: 0; padding: var(--spacing-2xs) var(--spacing-md); border-radius: var(--radius-pill); border: 1rpx solid var(--color-primary); }
.strip-edit.pressed { background-color: var(--bg-soft); }
.strip-edit-text { font-size: var(--font-tiny); color: var(--color-primary-text); font-weight: var(--weight-medium); }

/* 空态已上提为公共组件 components/EmptyState.vue（UI 统一 Loop Round 2），此处不再保留副本 */
</style>
