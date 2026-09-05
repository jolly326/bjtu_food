<template>
  <!-- 整卡副本（moment 包本地维护）：复制自 pages/dynamic/MomentCard.vue，供「动态详情」页使用。
       与列表版差异仅限交互语义：
         - 详情页自身即详情，去掉「整卡点击跳详情」；
         - 评论按钮改发 comment 事件（父页 focusComment 滚动评论区）而非 goDetail。 -->
  <view class="moment-card">
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
      <view v-if="isAuthor && moment.auditStatus && moment.auditStatus !== 'approved'" class="m-audit" :class="auditClass">
        <text class="m-audit-text">{{ auditLabel }}</text>
      </view>
      <!-- 右上角三点：分享/举报收进页面级 MomentActionSheet（仅触发 emit，弹层由父页面渲染） -->
      <view class="m-more" role="button" aria-label="更多操作" @tap.stop="emit('more', props.moment)">
        <IconSvg name="more-v" :size="28" color="var(--text-tertiary)" />
      </view>
    </view>

    <!-- 正文 -->
    <text class="m-content">{{ moment.content }}</text>

    <!-- 图片九宫格（共享组件） -->
    <MomentImageGrid :images="moment.images" />

    <!-- 关联对象 chip + 互动栏（同一行，互动靠右） -->
    <view class="m-foot">
      <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="m-related" @tap.stop="goRelated">
        <text class="m-related-text">{{ relatedLabel }}</text>
      </view>
      <!-- 关联价格：红色 ¥ 跟在 chip 右侧，与 chip 同一跳转 -->
      <text v-if="moment.relatedPrice" class="m-related-price" @tap.stop="goRelated">¥{{ moment.relatedPrice }}</text>
      <!-- 互动区仅保留点赞（comment 图标已移除：详情页评论区在下方 + 底部输入栏承载评论入口）；
           点赞即原 comment 图标的最右位（margin-left:auto 推靠右端） -->
      <view class="m-actions">
        <view class="m-action" :class="{ active: usefulActive }" @tap.stop="onUseful">
          <IconSvg :name="usefulActive ? 'thumb-filled' : 'thumb'" :size="36" class="m-action-icon" :color="usefulActive ? 'var(--color-primary)' : 'var(--text-secondary)'" />
          <text class="m-action-count">{{ moment.usefulCount > 0 ? moment.usefulCount : 0 }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import MomentImageGrid from '@/components/MomentImageGrid.vue'
import { formatDateTime } from '@/utils/time'
import { getImageUrl } from '@/utils/image'
import type { Moment } from '@/types/moment'
import { useMomentUseful } from '@/composables/useMomentUseful'

const props = withDefaults(defineProps<{
  moment: Moment
  /** 当前用户是否为作者：作者可看到审核中/已退回胶囊（moment 本地副本增量） */
  isAuthor?: boolean
}>(), {
  isAuthor: false,
})

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件的 @tap 会被编译为原生 bindtap，emit 参数丢失。
const emit = defineEmits<{
  (e: 'useful', moment: Moment): void
  (e: 'go-related', moment: Moment): void
  (e: 'more', moment: Moment): void
}>()

const relatedLabel = computed(() => {
  // 关联菜品 chip：只展示菜品名，不加「相关菜品·」前缀（用户口径）
  return props.moment.relatedName || ''
})

const auditLabel = computed(() => props.moment.auditStatus === 'pending' ? '审核中' : '已退回')
const auditClass = computed(() => `audit-${props.moment.auditStatus}`)

const { usefulActive, onUseful } = useMomentUseful(props.moment)

function goRelated() {
  emit('go-related', props.moment)
}
</script>

<style scoped>
/* 整卡视觉与列表 MomentCard 对齐（moment 包本地副本）：白卡 + 圆角 + 柔和投影 */
.moment-card {
  margin: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 28rpx var(--spacing-lg);
  overflow: hidden;
  -webkit-tap-highlight-color: transparent;
}
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); }
/* 头像：正圆 + 浅底色 */
.m-avatar { width: 64rpx; height: 64rpx; border-radius: var(--radius-circle); background: var(--bg-soft); flex-shrink: 0; align-self: center; overflow: hidden; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-avatar-fallback { font-size: var(--font-subtitle); line-height: 1; }
/* 昵称-时间行距 4px(=8rpx)；行高固定为 1，使「昵称(32)+间距(8)+时间(24)」堆叠=头像 64rpx，
   头像纵向精确覆盖昵称/时间两行并垂直居中（moment 包副本延续定稿口径） */
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
/* 昵称：一级标题档（32rpx / 600） */
.m-nickname {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h3);
  line-height: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
/* 第二行：发布时间（辅助信息档 24rpx / 400 / 三级文字） */
.m-meta { display: flex; align-items: center; gap: var(--spacing-sm); }
.m-time { flex-shrink: 0; font-size: var(--font-small); font-weight: var(--weight-regular); color: var(--text-tertiary); font-variant-numeric: tabular-nums; line-height: 1; }
/* 审核胶囊（moment 副本增量：作者本人可见 审核中/已退回） */
.m-audit { padding: 4rpx 12rpx; border-radius: var(--radius-tag); }
.m-audit-text { font-size: var(--font-tiny); font-weight: var(--weight-bold); }
.audit-pending { background: var(--color-warning-soft); }
.audit-pending .m-audit-text { color: var(--color-warning); }
.audit-rejected { background: var(--color-error-soft); }
.audit-rejected .m-audit-text { color: var(--color-error); }
/* 正文：14px(--font-body) 二级灰、行高 1.45；头部-正文 10px(=20rpx) */
.m-content { display: block; margin-top: 20rpx; font-size: var(--font-body); font-weight: var(--weight-regular); color: var(--text-secondary); line-height: 1.45; word-break: break-word; }
/* 关联菜品 chip：纯白底 + 1px 纯主色细描边标签 */
.m-related { display: inline-flex; align-items: center; gap: var(--spacing-xs); height: 48rpx; padding: 0 var(--spacing-sm); background: #FFFFFF; border: 1px solid var(--color-primary); border-radius: var(--radius-tag); flex-shrink: 0; overflow: hidden; transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-related:active { opacity: 0.7; }
.m-related-text { font-size: var(--font-small); color: var(--color-primary); font-weight: var(--weight-medium); line-height: 1.2; }
/* 关联价格：--color-price 红色跟在 chip 右侧 */
.m-related-price { flex-shrink: 0; font-size: var(--font-small); font-weight: var(--weight-semibold); color: var(--color-price); font-variant-numeric: tabular-nums; transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-related-price:active { opacity: 0.7; }
/* 关联 chip + 互动栏同一行（m-foot），互动靠右；上方细浅灰横线分隔 */
.m-foot {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-sm);
  border-top: 2rpx solid var(--border-color);
}
.m-actions { display: flex; align-items: center; gap: var(--spacing-lg); margin-left: auto; flex-shrink: 0; }
/* 互动按钮：icon + 数字纯文字链，统一 64rpx 触控高度 */
.m-action { display: inline-flex; align-items: center; justify-content: center; gap: 12rpx; height: 64rpx; padding: 0 var(--spacing-sm); border-radius: var(--radius-tag); box-sizing: border-box; transition: opacity var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
.m-action:active { opacity: 0.8; }
.m-action-icon { font-size: var(--font-caption); line-height: 1; color: var(--text-secondary); }
.m-action.active .m-action-icon { color: var(--color-primary); }
.m-action-count { font-size: var(--font-caption); font-weight: var(--weight-medium); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.m-action.active .m-action-count { color: var(--color-primary); }
/* 右上角三点菜单按钮：图标按钮（无胶囊背景） */
.m-more { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 64rpx; flex-shrink: 0; transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.m-more:active { opacity: 0.5; }
</style>
