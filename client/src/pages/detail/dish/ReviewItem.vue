<template>
  <view
    class="review-item"
    :class="{ 'review-item-pressed': pressed, 'review-item--flat': flat }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
  >
    <image v-if="avatarOk && review.userAvatar" class="review-avatar" :src="getImageUrl(review.userAvatar)" mode="aspectFill" @error="avatarOk = false" />
    <view v-else class="review-avatar review-avatar-empty">
      <IconSvg name="user" :size="32" color="var(--text-tertiary)" />
    </view>
    <view class="review-body">
      <view class="review-head">
        <view class="review-head-left">
          <text class="review-nickname">{{ review.userNickname || '匿名用户' }}</text>
          <!-- 机检中间态小标已于 2026-09-15 随「取消人工复核」删除（机检 pass/review 均直接放行、
               仅 risky 拒绝，端上不存在中间态）。「已被隐藏」标注属事后处置口径，仅在「我的评价」页呈现 -->
        </view>
        <!-- 右上角竖三点：举报（他人）/ 删除（本人）收进 ActionSheet -->
        <view v-if="!hideReport || canDelete" class="review-more" role="button" aria-label="更多操作" @tap.stop="onMore">
          <IconSvg name="more-v" :size="28" color="var(--text-tertiary)" />
        </view>
      </view>
      <!-- 第二行：评分（1-5 黄星 + 分值数字）+ 发布时间，小间隙同行 -->
      <view class="review-meta">
        <view v-if="(review.rating || 0) > 0" class="review-stars" role="img" :aria-label="`评分 ${(review.rating || 0).toFixed(1)} 分`">
          <IconSvg
            v-for="n in Math.min(Math.max(Math.round(review.rating || 0), 1), 5)"
            :key="n"
            name="star-filled"
            :size="22"
            color="var(--color-primary)"
            class="review-star"
          />
          <text class="review-rating-num">{{ (review.rating || 0).toFixed(1) }}</text>
        </view>
        <text class="review-time">{{ formatDateTime(review.createTime) }}</text>
      </view>
      <text class="review-content">{{ review.content }}</text>
      <!-- 配图行（≤3 张 COS URL）：等比小方图，点击预览大图；破图兜底 empty 中性占位 -->
      <view v-if="reviewImages.length" class="review-images">
        <view v-for="(img, i) in reviewImages" :key="img" class="review-image-cell">
          <view class="review-image-box">
            <image
              v-if="!brokenImages.has(i)"
              class="review-image"
              :src="getImageUrl(img)"
              mode="aspectFill"
              role="img"
              :aria-label="`评价配图 ${i + 1}`"
              @tap="onPreviewImage(i)"
              @error="onImageError(i)"
            />
            <view v-else class="review-image-fallback" @tap="onPreviewImage(i)">
              <IconSvg name="empty" :size="36" color="var(--text-tertiary)" />
            </view>
          </view>
        </view>
      </view>
      <!-- footer 操作组：仅「有用」（举报/删除已上移右上角）。
           评价卡片不展示点赞/评论类互动组件（UGC 互动仅保留「有用」）。
           「有用」是公开评价列表「按有用数置顶」排序口径的唯一入口（§7.14 第 2 条），必须常驻。 -->
      <view class="review-footer">
        <view class="review-ops">
          <text class="review-op" :class="{ active: usefulActive }" role="button" aria-label="标记有用" @tap.stop="onLike">
            <IconSvg
              name="thumb"
              :size="26"
              :color="usefulActive ? 'var(--color-like)' : 'var(--text-tertiary)'"
            />
            <text class="review-op-label">有用</text>
            <text v-if="likeCount > 0" class="review-op-count">{{ likeCount }}</text>
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { getImageUrl } from '@/utils/image'
import { formatDateTime } from '@/utils/time'
import { toggleUseful } from '@/api/review'
import { SurfacedError } from '@/api/http'
import { useUserStore } from '@/stores/user'
import type { Review } from '@/types/review'

defineOptions({ name: 'ReviewItem' })

const props = defineProps<{
  review: Review
  // 原 `usefulActive` prop（外部注入已赞态）已于 2026-09-14 删除：零消费（无调用点传入），
  // 且已赞态唯一真源为后端 `review.useful`（见下方 usefulActive computed），PR-05 不留悬空 prop。
  /** 隐藏举报（右上角）：我的评价页无需举报自己的评价 */
  hideReport?: boolean
  /** 当前登录用户 ID：用于判定本人评价（本人可删、隐藏举报） */
  currentUserId?: number
  /** 显式允许删除（个人管理页独立开关，不依赖 currentUserId） */
  deletable?: boolean
  /** 扁平模式：嵌套在评价卡片内时去独立卡片样式（bg/shadow/圆角），只保留条目结构 */
  flat?: boolean
}>()

const emit = defineEmits<{
  (e: 'like', review: Review): void
  (e: 'report', review: Review): void
  (e: 'delete', review: Review): void
  (e: 'more', review: Review): void
}>()

const pressed = ref(false)
const avatarOk = ref(true)
const userStore = useUserStore()

// 有用计数直接用后端 usefulCount（语义已含当前用户：toggleUseful 切换 ±1 均反映在计数中），
// 不再另加本地计数偏移。usefulActive 仅控制填充态显示。
const likeCount = computed(() => props.review.usefulCount || 0)
/**
 * 「有用」已赞态：**唯一真源 = 后端 `review.useful`**（`GET /reviews` 登录态逐条回写），
 * 不另造本地状态字段；乐观更新/回滚/成功均写回该字段本身，故与后端始终一致
 * （forceLogout → store.resetUserScopedData 清 `useful` 后本态自动跟随复位）。
 */
const usefulActive = computed(() => !!props.review.useful)
// pending 锁防连点（防重复提交 / 计数漂移）
const pendingUseful = ref(false)

/**
 * 评价「有用」：乐观更新 + 失败回滚 + 连点锁 + 已赞不重复提交。
 * 准入：**只需登录**（点赞不产生公开内容、不涉机检）——未登录给登录引导；
 * 其余准入（`4031` 需邮箱认证 / `403` 需微信登录 / 网络异常）由请求层统一提示，
 * 本组件 catch 到已提示错误（SurfacedError）时只回滚，不重复 Toast。
 */
function onLike() {
  if (!userStore.isLoggedIn()) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    void userStore.silentLogin()
    return
  }
  if (pendingUseful.value) return
  pendingUseful.value = true
  const prevActive = usefulActive.value
  const prevCount = likeCount.value
  // 乐观更新：计数 + 已赞态同步翻转
  props.review.useful = !prevActive
  props.review.usefulCount = prevActive ? Math.max(0, prevCount - 1) : prevCount + 1
  toggleUseful(props.review.id)
    .then((res) => {
      // 以后端返回为准（useful / usefulCount 同一响应写回，避免两端口径漂移）
      props.review.useful = res.useful
      props.review.usefulCount = res.usefulCount
    })
    .catch((e: unknown) => {
      // 回滚到点击前状态
      props.review.useful = prevActive
      props.review.usefulCount = prevCount
      // 请求层已提示过的错误（4031 认证引导 / 403 / 网络）不再重复弹
      if (!(e instanceof SurfacedError)) {
        uni.showToast({ title: (e as Error)?.message || '操作失败', icon: 'none' })
      }
    })
    .finally(() => {
      pendingUseful.value = false
    })
}

// 本人评价：当前登录用户 ID 命中即本人
const isOwn = computed(() => props.currentUserId != null && props.review.userId === props.currentUserId)
// 可删除：显式 deletable（个人管理页）或本人评价（详情页）
const canDelete = computed(() => !!props.deletable || isOwn.value)

/* ===== 配图展示（2026-09 恢复 UGC 配图）：≤3 张 COS URL，点击预览大图 ===== */
const reviewImages = computed(() =>
  Array.isArray(props.review.images) ? props.review.images.filter(Boolean) : [],
)
/** 破图下标集合：error 后切 empty 中性占位；images 变化（列表重拉）时重置 */
const brokenImages = ref<Set<number>>(new Set())
watch(
  () => props.review.images,
  () => { brokenImages.value = new Set() },
)
function onImageError(i: number) {
  const next = new Set(brokenImages.value)
  next.add(i)
  brokenImages.value = next
}
/** 预览大图（仅未破图可进入；current 定位到点击那张） */
function onPreviewImage(i: number) {
  if (brokenImages.value.has(i)) return
  const okIdx = reviewImages.value.map((_, idx) => idx).filter((idx) => !brokenImages.value.has(idx))
  const okUrls = okIdx.map((idx) => getImageUrl(reviewImages.value[idx]))
  if (!okUrls.length) return
  const cur = okIdx.indexOf(i)
  uni.previewImage({ urls: okUrls, current: okUrls[Math.max(cur, 0)] })
}

function onDelete() {
  if (!canDelete.value) return
  emit('delete', props.review)
}
function onReport(r: Review) { emit('report', r) }
/** 右上角三点菜单：操作由父页面以 ActionSheet 呈现（举报他人 / 删除本人） */
function onMore() {
  emit('more', props.review)
}

</script>

<style scoped>
/* ===== 评价项（口碑卡片：独立卡片 + 圆角 + 阴影）。
   三处评价区共用（菜品详情 / 全部评价 / 我的评价），打磨一处即统一全部。
   口碑层扁平：不设评论/回复入口，互动仅「有用」标记。
   设计要点：卡片层级、touch 物理反馈、层级对比（昵称黑/正文黑/时间灰/操作灰）、星级展示 */
.review-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  transition: opacity var(--duration-fast) var(--ease-out);
}
/* 扁平模式：嵌套在评价卡片内（菜品详情），去独立卡样式，保留条目结构 + 分隔线 */
.review-item--flat {
  background: transparent;
  border-radius: var(--radius-none);
  box-shadow: none;
  padding: var(--spacing-md) 0;
  border-bottom: 2rpx solid var(--border-color);
}
.review-item--flat:last-child { border-bottom: none; }
.review-item--flat.review-item-pressed { opacity: 0.5; }
/* 轻反馈：整卡由 scale 改为 opacity，避免整块塌陷感。
   类名用 review-item-pressed 而非 pressed，避免与 App.vue 全局 .pressed（scale !important）同名冲突。 */
.review-item.review-item-pressed { opacity: 0.6; }

/* 头像：圆形浅灰底（content-flow-visual-polish 5.2；dish-detail-visual-polish 对齐 64rpx） */
.review-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: var(--radius-circle);
  background: var(--bg-soft);
  flex-shrink: 0;
}
.review-avatar-empty { display: flex; align-items: center; justify-content: center; }

/* 右侧内容：行距 2xs，昵称与第二行不再因叠加间距拉开 */
.review-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xs);
}

/* 头部：昵称（左）+ 竖三点（右，绝对定位不撑高头行，保证昵称与第二行间距紧凑） */
.review-head {
  position: relative;
  display: flex;
  align-items: center;
}
/* 头部第一行：昵称，右侧预留三点按钮空间 */
.review-head-left {
  flex: 1;
  min-width: 0;
  padding-right: 64rpx;
  display: flex;
  align-items: center;
}
.review-nickname {
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-caption);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 机检中间态小标（.review-sec-badge / .review-sec-badge-text，warning 浅底胶囊）于 2026-09-15
   随「取消人工复核」删除：机检 pass/review 均直接放行、仅 risky 拒绝，端上不存在中间态，样式一并收敛 */
/* 第二行：评分（星星+数字）与发布时间小间隙同行（不推右）；间距由 review-body gap 提供，不叠加 margin */
.review-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.review-time {
  flex-shrink: 0;
  font-size: var(--font-small);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

/* 评分：黄色实星（1-5 颗，最低 1 颗）+ 右侧分值数字 */
.review-stars { display: inline-flex; align-items: center; gap: 2rpx; flex-shrink: 0; }
.review-star { display: inline-block; }
.review-rating-num { font-size: var(--font-aux); color: var(--text-secondary); margin-left: var(--spacing-xs); font-variant-numeric: tabular-nums; }

/* 右上角竖三点：绝对定位于头行右上，不参与行高（否则 64rpx 会撑开昵称与第二行的间距） */
.review-more {
  position: absolute;
  top: 50%;
  right: 0;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  flex-shrink: 0;
  transition: opacity var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.review-more:active { opacity: 0.5; }

/* 正文：二级灰、行高 1.5（content-flow-visual-polish 5.2） */
.review-content {
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

/* 配图行（≤3 张等比小方图）：与 ImagePicker 同一网格语言（3 等分 + 16rpx gap） */
.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: var(--spacing-2xs);
}
.review-image-cell {
  width: calc((100% - 32rpx) / 3);
}
/* 正方形容器：padding-bottom 等比盒（与 ImagePicker 同法，小程序 aspect-ratio 支持不稳） */
.review-image-box {
  position: relative;
  width: 100%;
  height: 0;
  padding-bottom: 100%;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--bg-placeholder);
  -webkit-tap-highlight-color: transparent;
}
.review-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.review-image:active { opacity: 0.6; }
/* 破图兜底：empty 中性占位（浅底居中），可点击但不进预览 */
.review-image-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* footer：操作组（有用·举报·删除，纯文字链无背景） */
.review-footer { margin-top: var(--spacing-xs); }
.review-ops { display: inline-flex; align-items: center; gap: var(--spacing-lg); }
.review-op {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2xs);
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  font-weight: var(--weight-medium);
  padding: var(--spacing-2xs) var(--spacing-xs);
  border-radius: var(--radius-card);
  transition: opacity var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.review-op:active { opacity: 0.6; }
.review-op.active { color: var(--color-like); }
.review-op-label { font-size: var(--font-aux); color: var(--text-tertiary); font-weight: var(--weight-medium); }
.review-op.active .review-op-label { color: var(--color-like); }
</style>
