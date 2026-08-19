<template>
  <!-- 评价卡三点菜单：底部 ActionSheet（页面根级挂载，fixed 遮罩才能正确覆盖全屏）。
       与 AuthSheet/ApplySheet 同一底部抽屉范式：grabber 横条 + 圆角卡片 + 阴影 + 过渡动画。
       注意：组件必须放在 scroll-view 之外（微信小程序 scroll-view 内 fixed 层级会被压扁/裁剪） -->
  <view v-if="open" class="more-mask" @tap.stop="close">
    <view class="more-sheet" @tap.stop>
      <view class="sheet-grabber" />
      <!-- 头部：与登录弹窗对齐（仅右上角关闭按钮 + 分隔线，不再显示「操作」标题） -->
      <view class="sheet-head">
        <view class="sheet-close" role="button" aria-label="关闭" @tap.stop="close">
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
        </view>
      </view>
      <!-- 本人：删除评价；他人：举报评价（危险操作红色高亮）。选项不设按钮背景，与其他弹层一致 -->
      <view
        v-if="isOwn"
        class="more-item more-item--danger"
        role="button"
        aria-label="删除评价"
        @tap="onDelete"
      >
        <IconSvg name="delete" :size="34" color="var(--color-error)" class="more-item-icon" />
        <text class="more-item-text">删除评价</text>
      </view>
      <view v-else class="more-item more-item--danger" role="button" aria-label="举报评价" @tap="onReport">
        <IconSvg name="report" :size="34" color="var(--color-error)" class="more-item-icon" />
        <text class="more-item-text">举报评价</text>
      </view>
      <view class="more-cancel" role="button" aria-label="取消" @tap.stop="close">取消</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'

const props = defineProps<{
  open: boolean
  /** 当前评价是否属于本人（true 显示「删除评价」，false 显示「举报评价」） */
  isOwn: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'delete'): void
  (e: 'report'): void
}>()

function close() {
  emit('update:open', false)
}

function onDelete() {
  close()
  emit('delete')
}

function onReport() {
  close()
  emit('report')
}
</script>

<style scoped>
/* 底部 ActionSheet：与 AuthSheet/ApplySheet 统一抽屉范式（grabber + 圆角卡片 + shadow-modal + 过渡动画）。
   选项不设置按钮背景（去背景块），与登录/认证等弹层的选项视觉一致 */
.more-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: var(--overlay-scrim);
  z-index: var(--z-actionsheet);
  display: flex;
  align-items: flex-end;
  animation: mask-in var(--duration-slow) var(--ease-out);
}
.more-sheet {
  width: 100%;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-md) + env(safe-area-inset-bottom));
  animation: sheet-up var(--duration-slow) var(--ease-drawer);
}
.sheet-grabber {
  width: 72rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: var(--overlay-dark-soft);
  margin: var(--spacing-sm) auto 0;
}
/* 头部：与登录弹窗同构（右上角关闭按钮 + 底部分隔线） */
.sheet-head { display: flex; align-items: center; justify-content: flex-end; padding: var(--spacing-sm) var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-close { padding: 0 var(--spacing-xs); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.sheet-close:active { opacity: 0.5; }
/* 选项：无背景色块，仅图标 + 文字 + 分隔线（iOS ActionSheet 风格，与其他弹层一致） */
.more-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  width: 100%;
  height: 104rpx;
  font-size: var(--font-body);
  color: var(--text-primary);
  border-bottom: 2rpx solid var(--border-color);
  background: transparent;
}
.more-item:active { opacity: 0.7; }
.more-item-icon { flex-shrink: 0; }
.more-item-text { font-weight: var(--weight-medium); }
.more-item--danger .more-item-text { color: var(--color-error); }
/* 取消：无背景、无分隔线，顶部留白与选项区分 */
.more-cancel {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 104rpx;
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  background: transparent;
}
.more-cancel:active { opacity: 0.7; }

@keyframes mask-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes sheet-up {
  from { transform: translateY(20%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .more-mask, .more-sheet { animation: none !important; }
}
</style>
