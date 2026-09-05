<template>
  <view class="moment-card" :aria-label="ariaLabel" @tap="goDetail" role="button" tabindex="0">
    <view class="m-head">
      <image v-if="moment.userAvatar" class="m-avatar" :src="getImageUrl(moment.userAvatar)" mode="aspectFill" lazy-load />
      <view v-else class="m-avatar m-avatar-empty">
        <IconSvg name="user" :size="40" color="var(--text-tertiary)" class="m-avatar-fallback" />
      </view>
      <view class="m-head-right">
        <text class="m-nickname">{{ moment.userNickname || '匿名用户' }}</text>
        <!-- 第二行：发布时间 -->
        <view class="m-meta">
          <text class="m-time">{{ formatDateTime(moment.createdAt) }}</text>
        </view>
      </view>
      <!-- 审核态徽标（作者本人可见：审核中 / 已退回） -->
      <view v-if="moment.auditStatus && moment.auditStatus !== 'approved'" class="m-audit" :class="auditClass">
        <text class="m-audit-text">{{ auditLabel }}</text>
      </view>
      <!-- 右上角三点菜单：分享（自己的动态不提供举报）——经页面级 ActionSheet（allow-report=false）；
           仅触发 emit，弹层由父页面在 scroll-view 外渲染 -->
      <view class="m-more" role="button" aria-label="更多操作" @tap.stop="emit('more', props.moment)">
        <IconSvg name="more-v" :size="28" color="var(--text-tertiary)" />
      </view>
    </view>

    <!-- 正文 -->
    <text class="m-content" :class="{ clamped: !expanded }">{{ moment.content }}</text>
    <text v-if="needClamp" class="m-expand" @tap.stop="expanded = !expanded">{{ expanded ? '收起' : '展开' }}</text>

    <!-- 图片九宫格 -->
    <view v-if="moment.images.length > 0" class="m-images" :class="`img-${Math.min(moment.images.length, 9)}`">
      <view
        v-for="(img, idx) in moment.images.slice(0, 9)"
        :key="idx"
        class="m-image-wrap"
        @tap.stop="previewImage(idx)"
      >
        <image
          class="m-image"
          :class="{ loaded: loadedSet.has(idx) }"
          :src="getImageUrl(getThumbUrl(img))"
          mode="aspectFill"
          lazy-load
          @load="loadedSet.add(idx)"
        />
      </view>
    </view>

    <!-- 已退回：退回原因 + 「编辑重提」作者主入口 -->
    <view v-if="isRejected && moment.rejectReason" class="m-reject">
      <view class="m-reject-row">
        <text class="m-reject-label">已退回：</text>
        <text class="m-reject-reason">{{ moment.rejectReason }}</text>
      </view>
      <view class="m-reject-edit" role="button" aria-label="编辑重提" @tap.stop="goEdit">
        <IconSvg name="edit" :size="26" color="var(--color-on-primary)" />
        <text class="m-reject-edit-text">编辑重提</text>
      </view>
    </view>

    <!-- 关联对象 chip + 互动栏（同一行，互动靠右）；仅已过审动态展示，避免审核中/已退回出现他人互动误导 -->
    <view v-if="isApproved" class="m-foot">
      <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="m-related" @tap.stop="goRelated">
        <image v-if="moment.relatedImage" class="m-related-thumb" :src="getImageUrl(moment.relatedImage)" mode="aspectFill" lazy-load />
        <view v-else class="m-related-thumb m-related-thumb--empty">
          <IconSvg name="dish" :size="20" color="var(--text-tertiary)" />
        </view>
        <text class="m-related-text">{{ relatedLabel }}</text>
      </view>
      <view class="m-actions">
        <view class="m-action" :class="{ active: usefulActive }" @tap.stop="onUseful">
          <IconSvg :name="usefulActive ? 'thumb-filled' : 'thumb'" :size="36" class="m-action-icon" :color="usefulActive ? 'var(--color-primary)' : 'var(--text-secondary)'" />
          <text class="m-action-count">{{ moment.usefulCount > 0 ? moment.usefulCount : 0 }}</text>
        </view>
        <view class="m-action" @tap.stop="goDetail">
          <IconSvg name="comment" :size="36" color="var(--text-secondary)" class="m-action-icon" />
          <text class="m-action-count">{{ moment.commentCount > 0 ? moment.commentCount : 0 }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { formatDateTime } from '@/utils/time'
import { previewImages, getImageUrl, getThumbUrl } from '@/utils/image'
import type { Moment } from '@/types/moment'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'

const props = defineProps<{
  moment: Moment
}>()

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件的 @tap 会被编译为原生 bindtap，emit 参数丢失，点击跳转 id 变 undefined。
const emit = defineEmits<{
  (e: 'useful', moment: Moment): void
  (e: 'select', moment: Moment): void
  (e: 'go-related', moment: Moment): void
  (e: 'more', moment: Moment): void
  /** 已退回动态的「编辑重提」主入口 */
  (e: 'edit', moment: Moment): void
}>()

const userStore = useUserStore()

/** 卡片无障碍语义标签 */
const ariaLabel = computed(() => `${(props.moment.userNickname || '匿名用户')}的动态`)
/** 图片淡入：记录已加载下标，配合 .m-image.loaded 做 opacity 过渡 */
const loadedSet = reactive(new Set<number>())

// 正文展开态（超长折叠，粗判长度显示展开入口）
const expanded = ref(false)
const needClamp = computed(() => (props.moment.content?.length || 0) > 80)

/** 审核态（作者视角）：审核中 / 已退回 */
const isRejected = computed(() => props.moment.auditStatus === 'rejected')
/** 已过审（含历史无审核字段数据）才展示互动区 */
const isApproved = computed(() => props.moment.auditStatus !== 'pending' && props.moment.auditStatus !== 'rejected')
const auditLabel = computed(() => {
  if (props.moment.auditStatus === 'pending') return '审核中'
  if (props.moment.auditStatus === 'rejected') return '已退回'
  return ''
})
const auditClass = computed(() => `audit-${props.moment.auditStatus}`)

const relatedLabel = computed(() => {
  // 动态仅可关联菜品：直接展示菜品名，不再加「菜品·」前缀
  return props.moment.relatedName || ''
})

// 有用 toggle 本地状态（乐观 UI）
const usefulActive = ref(!!props.moment.useful)
watch(() => props.moment.useful, (v) => {
  usefulActive.value = !!v
})

function goDetail() {
  emit('select', props.moment)
}

function goRelated() {
  emit('go-related', props.moment)
}

/** 已退回：编辑重提（页面接到后跳 publish-content?id= 编辑重提） */
function goEdit() {
  emit('edit', props.moment)
}

function previewImage(idx: number) {
  previewImages(props.moment.images, idx)
}

/** pending 锁防连点（P0 防重复请求 / 计数漂移） */
const pendingUseful = ref(false)
async function onUseful() {
  if (!userStore.requireAuth(() => onUseful())) return
  if (pendingUseful.value) return
  pendingUseful.value = true
  const prevActive = usefulActive.value
  const prevCount = props.moment.usefulCount || 0
  usefulActive.value = !prevActive
  props.moment.usefulCount = prevActive ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await momentApi.toggleUseful(props.moment.id)
    usefulActive.value = res.useful
    props.moment.usefulCount = res.usefulCount
    props.moment.useful = res.useful
    emit('useful', props.moment)
  } catch {
    usefulActive.value = prevActive
    props.moment.usefulCount = prevCount
    uni.showToast({ title: '操作失败', icon: 'none' })
  } finally {
    pendingUseful.value = false
  }
}
</script>

<style scoped>
.moment-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  /* 表面：白卡 + 品牌淡色柔和投影（与动态页卡片同一表面语言）。
     ⚠️ overflow:hidden 不可移除：微信 WXSS 渲染「border-radius + background」时，
     圆角外侧会残留一圈背景色方角，必须由本属性裁掉。 */
  box-shadow: var(--shadow-card);
  /* moment-card-visual-polish：水平 lg(32rpx=16px)、上下 36rpx(=18px，lg+2xs)，对 8 基网格（D2） */
  padding: 36rpx var(--spacing-lg);
  overflow: hidden;
  -webkit-tap-highlight-color: transparent;
}
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); }
/* 默认头像：正圆 + 浅底色 */
.m-avatar { width: 64rpx; height: 64rpx; border-radius: var(--radius-circle); background: var(--bg-soft); flex-shrink: 0; overflow: hidden; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-avatar-fallback { font-size: var(--font-subtitle); line-height: 1; }
/* 昵称-时间行距收紧（2rpx 属 4pt 网格内微调，moment-card-visual-polish D3） */
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2rpx; }
/* 昵称：一级标题档（32rpx / 600） */
.m-nickname {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
/* 第二行：发布时间（辅助信息档 24rpx / 400 / 三级文字） */
.m-meta { display: flex; align-items: center; gap: var(--spacing-sm); }
/* 发布时间较昵称小一档（aux 22rpx）、三级浅灰弱化，moment-card-visual-polish D3 */
.m-time { flex-shrink: 0; font-size: var(--font-aux); font-weight: var(--weight-regular); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }
/* 审核徽标：圆角 + 底色（audit-pending / audit-rejected 提供） */
.m-audit { padding: var(--spacing-2xs) var(--spacing-sm); border-radius: var(--radius-tag); flex-shrink: 0; overflow: hidden; }
.m-audit-text { font-size: var(--font-aux); font-weight: var(--weight-bold); }
.audit-pending { background: var(--color-warning-soft); }
.audit-pending .m-audit-text { color: var(--color-warning); }
.audit-rejected { background: var(--color-error-soft); }
.audit-rejected .m-audit-text { color: var(--color-error); }
/* 正文：正文档（28rpx / 400），行高约 1.5 */
/* 正文：头部→正文 md(24rpx=12px)、行高 1.55、二级灰（moment-card-visual-polish D2/D4） */
.m-content { display: block; margin-top: var(--spacing-md); font-size: var(--font-body); font-weight: var(--weight-regular); color: var(--text-secondary); line-height: 1.55; word-break: break-word; }
.m-content.clamped { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 4; overflow: hidden; }
.m-expand { margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); align-self: flex-start; }
/* 正文→图片 28rpx(≈14px，md/lg 间 4rpx 网格裸值，moment-card-visual-polish D2） */
.m-images { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--spacing-xs); margin-top: 28rpx; }
/* 缩略图：圆角正方形（16rpx） */
.m-image-wrap { aspect-ratio: 1 / 1; width: 100%; border-radius: var(--radius-xs); overflow: hidden; background: var(--bg-page); }
.m-image { width: 100%; height: 100%; opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); }
.m-image.loaded { opacity: 1; }
/* 已退回提示 + 编辑重提主入口 */
.m-reject { margin-top: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-tag); overflow: hidden; display: flex; flex-direction: column; gap: var(--spacing-sm); }
.m-reject-row { display: flex; align-items: flex-start; }
.m-reject-label { font-size: var(--font-aux); color: var(--color-error); font-weight: var(--weight-semibold); flex-shrink: 0; }
.m-reject-reason { font-size: var(--font-aux); color: var(--color-error); line-height: 1.5; }
.m-reject-edit { display: flex; align-items: center; justify-content: center; gap: var(--spacing-2xs); height: 64rpx; border-radius: var(--radius-tag); background: var(--color-primary); align-self: flex-start; padding: 0 var(--spacing-md); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-reject-edit:active { opacity: 0.85; }
.m-reject-edit-text { font-size: var(--font-body); font-weight: var(--weight-bold); color: var(--color-on-primary); }
/* 关联菜品 chip：浅灰 #F5F5F5 底、深灰 #333 文字、中灰图标、矮高、8px(16rpx) 圆角（用户口径） */
.m-related { display: inline-flex; align-items: center; gap: var(--spacing-xs); height: 48rpx; padding: 4rpx var(--spacing-sm) 4rpx 4rpx; background: #F5F5F5; border-radius: var(--radius-tag); flex-shrink: 0; overflow: hidden; transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-related:active { opacity: 0.7; }
.m-related-thumb { width: 40rpx; height: 40rpx; border-radius: var(--radius-xs); background: #F5F5F5; flex-shrink: 0; overflow: hidden; }
.m-related-thumb--empty { display: flex; align-items: center; justify-content: center; background: #F5F5F5; }
.m-related-text { font-size: var(--font-small); color: #333333; font-weight: var(--weight-medium); line-height: 1.2; }
/* 关联 chip + 互动栏同一行（m-foot），互动靠右 */
/* 正文→标签/互动行 28rpx（≈14px，梯度大于头部-正文 24rpx；D2/D4） */
.m-foot { display: flex; align-items: center; gap: var(--spacing-sm); margin-top: 28rpx; }
/* 两按钮间距 xl(48rpx=24px)，D2 */
.m-actions { display: flex; align-items: center; gap: var(--spacing-xl); margin-left: auto; flex-shrink: 0; }
.m-action { display: inline-flex; align-items: center; justify-content: center; gap: var(--spacing-sm); height: 64rpx; padding: 0 var(--spacing-sm); border-radius: var(--radius-tag); box-sizing: border-box; transition: opacity var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
/* 按压：图标+数字整组降至约 80%（moment-card-visual-polish D5） */
.m-action:active { opacity: 0.8; }
.m-action-icon { font-size: var(--font-caption); line-height: 1; color: var(--text-secondary); }
.m-action.active .m-action-icon { color: var(--color-primary); }
/* 互动数：500 字重 + 二级灰，弱于昵称（caption 30rpx，moment-card-visual-polish D5） */
.m-action-count { font-size: var(--font-caption); font-weight: var(--weight-medium); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.m-action.active .m-action-count { color: var(--color-primary); }
/* 右上角三点菜单按钮（作者自己的动态分享入口；举报已按作者语义移除） */
.m-more { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 64rpx; flex-shrink: 0; transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-more:active { opacity: 0.5; }
</style>
