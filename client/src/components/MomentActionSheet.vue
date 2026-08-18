<template>
  <!-- 动态卡片三点菜单：底部 ActionSheet（页面根级挂载，fixed 遮罩才能正确覆盖全屏）。
       注意：组件必须放在 scroll-view 之外（微信小程序 scroll-view 内 fixed 层级会被压扁/裁剪） -->
  <view v-if="open" class="more-mask" @tap.stop="close">
    <view class="more-sheet" @tap.stop>
      <view class="more-title">操作</view>
      <!-- 分享：微信原生分享（open-type=share → 页面 onShareAppMessage） -->
      <button class="more-item" open-type="share" @tap="onShareTap">
        <IconSvg name="share" :size="34" color="var(--text-primary)" class="more-item-icon" />
        <text class="more-item-text">分享</text>
      </button>
      <!-- 举报：emit 给父页面弹 ReportModal -->
      <view class="more-item more-item--danger" role="button" aria-label="举报动态" @tap="onReport">
        <IconSvg name="report" :size="34" color="var(--color-error)" class="more-item-icon" />
        <text class="more-item-text">举报</text>
      </view>
      <view class="more-cancel" role="button" aria-label="取消" @tap.stop="close">取消</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import IconSvg from './IconSvg.vue'
import { sharedMoment } from '@/utils/shareState'
import type { Moment } from '@/types/moment'

const props = defineProps<{
  open: boolean
  moment?: Moment | null
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'report', moment: Moment): void
}>()

function close() {
  emit('update:open', false)
}

function onShareTap() {
  // 记录待分享动态，页面 onShareAppMessage 据此生成分享卡片（微信原生分享）
  if (props.moment) sharedMoment.value = props.moment
  close()
}

function onReport() {
  close()
  if (props.moment) emit('report', props.moment)
}

// 打开时禁止页面滚动（微信原生 scroll-view 不响应 scroll-y 阻止，但遮罩全屏已拦截点击）
watch(() => props.open, (v) => {
  if (v) {
    // 空操作占位：保留 watch 以便后续扩展（如页面级滚动锁定）
  }
})
</script>

<style scoped>
/* 底部 ActionSheet：分享 / 举报（mask + 底部弹层，Apple 风格圆角卡片）
   挂载在页面根（scroll-view 外），fixed 才能覆盖 Header/FAB 全屏且层级最高 */
.more-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 9999;
  display: flex;
  align-items: flex-end;
}
.more-sheet {
  width: 100%;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + env(safe-area-inset-bottom));
}
.more-title { text-align: center; font-size: var(--font-aux); color: var(--text-tertiary); padding-bottom: var(--spacing-sm); }
.more-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  width: 100%;
  height: 96rpx;
  border-radius: var(--radius-tag);
  font-size: var(--font-body);
  color: var(--text-primary);
  background: var(--bg-soft);
  margin-bottom: var(--spacing-xs);
}
.more-item::after { border: none; }
.more-item:active { opacity: 0.7; }
.more-item-icon { flex-shrink: 0; }
.more-item-text { font-weight: var(--weight-medium); }
.more-item--danger .more-item-text { color: var(--color-error); }
.more-cancel {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 96rpx;
  border-radius: var(--radius-tag);
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  background: var(--bg-soft);
  margin-top: var(--spacing-xs);
}
.more-cancel:active { opacity: 0.7; }
@media (prefers-reduced-motion: reduce) {
  .more-sheet { animation: none !important; }
}
</style>
