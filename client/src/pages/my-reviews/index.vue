<template>
  <view class="page my-reviews-page">
    <Header title="我的主页" @back="backToHome" />

    <view class="scroll-wrap">
      <!-- 用户信息卡：主页的身份版面（头像 / 昵称 / 邮箱），右侧「编辑个人信息」→ 独立个人信息编辑页 -->
      <view class="profile-strip" :class="{ 'is-verified': isVerified }">
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
      </view>

      <!-- 评价区：信息卡下方是本人名下评价列表（有数据时才渲染区块标题，避免空榜烘标题） -->
      <view v-if="list.length" class="section-title">
        <text class="section-title-text">我的评价</text>
      </view>
      <view class="list">
        <!-- 评价卡 = 公共组件 ReviewItem（与菜品详情评价区**同一实现**）：
             本人视角专属信息经 dishName 可选 prop 注入 -->
        <ReviewItem
          v-for="r in list"
          :key="r.id"
          :review="r"
          :current-user-id="userStore.userInfo?.id"
          :dish-name="r.dishName"
          @more="onMore(r)"
        />
      </view>

      <!-- 加载失败重试块（MP-012 同族，P3-03 上提为公共组件）：首屏请求失败 ≠ 无评价——
           先于空态渲染，避免网络失败被误读；恢复走重试块 @tap -->
      <RetryBlock v-if="loadFailed && !loading" @retry="onRetryLoad" />
      <!-- 游客空态：游客可自由进入本页（用户卡直进、无认证拦截），列表空给认证引导 -->
      <view v-else-if="isGuest" class="empty-tip">
        <text class="empty-text">暂无评价，完成身份认证后可发表评价</text>
      </view>
      <!-- 空态：首次进入无评价保持静默；仅「删除最后一条」触发时给轻提示，避免被误解为加载异常 -->
      <view v-else-if="emptiedByDelete" class="empty-tip">
        <text class="empty-text">暂无评价，去菜品详情写一条吧</text>
      </view>
    </view>

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
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import Header from '@/components/AppHeader.vue'
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
// 图标色须传实色（IconSvg 的 color 不解析 var()）
import { COLOR_MAP, MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
const isVerified = computed(() => userStore.isVerified())
const bindEmail = computed(() => userInfo.value?.bindEmail || '')
/** 游客短标识：端上派生 = 「食客 + id 尾 4 位」（与「我的」页同口径） */
const guestLabel = computed(() => {
  const id = userInfo.value?.id
  if (!id) return '食客'
  const s = String(id)
  return `食客${s.length > 4 ? s.slice(-4) : s}`
})
/** 游客态（列表空态分支 + 跳过需登录请求）；认证成功返回本页时 onShow 重拉自动切换为真实列表 */
const isGuest = computed(() => !userStore.isVerified())

/** 编辑个人信息：独立页（pages/profile/index），保存后自动返回本页 */
function goProfileEdit() {
  uni.navigateTo({ url: PATH.profile })
}

const list = ref<MyReview[]>([])
const loading = ref(false)
/** 仅「删除导致列表清空」时为 true，驱动空态轻提示 */
const emptiedByDelete = ref(false)
/** 首屏是否失败（MP-012）：失败 ≠ 无评价，失败渲染重试块而非空态 */
const loadFailed = ref(false)
let page = 1
const pageSize = 20
const finished = ref(false)

async function load() {
  if (loading.value) return
  // 游客不发起「我的评价」请求（端点需登录，游客调必 401）：直接落空列表，走游客空态引导
  if (!userStore.isVerified()) {
    list.value = []
    loadFailed.value = false
    finished.value = true
    return
  }
  loading.value = true
  try {
    const res = await getMyReviews({ page: 1, pageSize })
    // 成功即清失败态（重试成功后错误块消失）
    loadFailed.value = false
    list.value = res.list
    page = 1
    finished.value = res.list.length < pageSize
    emptiedByDelete.value = false
  } catch (err) {
    // MP-012：首屏失败不再静默吞——置 loadFailed 渲染「加载失败 · 点击重试」块，恢复走重试块 @tap
    console.error('[my-reviews] 加载评价失败', err)
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/** 重试块 @tap：从第 1 页重拉（与首屏同一条重拉路径）（MP-012） */
function onRetryLoad() {
  load()
}

async function loadMore() {
  if (finished.value || loading.value) return
  loading.value = true
  try {
    page += 1
    const res = await getMyReviews({ page, pageSize })
    const existIds = new Set(list.value.map(r => r.id))
    list.value = list.value.concat(res.list.filter(r => !existIds.has(r.id)))
    if (res.list.length < pageSize) finished.value = true
  } catch {
    page -= 1
  } finally {
    loading.value = false
  }
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
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
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

/** 自然文档滚动触底：加载更多本人评价（替换 scroll-view 的 @scrolltolower，贴合 UI 稿自然滚动口径） */
onReachBottom(() => loadMore())
</script>

<style scoped>
.my-reviews-page { background: var(--bg-page); }
.scroll-wrap { padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg) + env(safe-area-inset-bottom)); box-sizing: border-box; }

/* 列表容器：卡片间距由容器 gap 承担；卡片本体样式（头像/昵称/星级/正文/配图/三点）由 ReviewItem 统一 */
.list { display: flex; flex-direction: column; gap: var(--spacing-sm); }

/* 用户信息卡：头像 + 昵称/副行 + 「编辑个人信息」，白底一级卡（与评价卡同语言） */
/* 区块标题：区别「身份版面」与「名下的评价」两段内容；无评价时不渲染 */
.section-title { margin: var(--spacing-sm) 0 var(--spacing-2xs); }
.section-title-text { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); }
.profile-strip {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  margin-bottom: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
/* 认证态顶部 6rpx 主色软条纹（UI 稿：仅认证态显示；游客态透明） */
.profile-strip.is-verified { border-top: 6rpx solid var(--color-primary-soft); }
.strip-avatar-wrap { flex-shrink: 0; }
.strip-avatar { width: 120rpx; height: 120rpx; border-radius: var(--radius-circle); background: var(--bg-soft); }
.strip-avatar-empty { display: flex; align-items: center; justify-content: center; }
.strip-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2rpx; }
.strip-nickname { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.strip-sub { font-size: var(--font-small); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.strip-edit { flex-shrink: 0; padding: var(--spacing-2xs) var(--spacing-md); border-radius: var(--radius-pill); border: 1rpx solid var(--color-primary); }
.strip-edit.pressed { background-color: var(--bg-soft); }
.strip-edit-text { font-size: var(--font-tiny); color: var(--color-primary-text); font-weight: var(--weight-medium); }

.empty-tip { padding: var(--spacing-xl) 0; display: flex; justify-content: center; }
.empty-text { font-size: var(--font-small); color: var(--text-tertiary); }
</style>
