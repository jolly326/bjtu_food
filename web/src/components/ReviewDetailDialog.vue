<script setup lang="ts">
/**
 * ReviewDetailDialog：评价详情弹窗（菜品详情页 / 评价管理页**唯一共用实现**）。
 *
 * 设计约束（本轮 UI 收敛）：
 *  - **只读**：仅展示 用户 / 评分 / 内容 / 配图 / [菜品] / 状态 / 时间，不含任何动作按钮。
 *    评价的动作（显隐开关、删除）全站只保留在列表行内一处入口 → 天然消除「行内删除 + 抽屉删除」双入口。
 *  - 菜品行由 `dishName` 决定是否展示：菜品详情页内该行冗余（不传），评价管理页传入。
 *  - 用户名 / 菜品名的字典降级由调用方解析后以字符串传入（本组件不持有 store，避免两页各自再实现一份）。
 */
import FormDialog from '@/components/FormDialog.vue'
import StarRating from '@/components/StarRating.vue'
import StatusTag from '@/components/StatusTag.vue'

withDefaults(
  defineProps<{
    show: boolean
    /** 评价实体（null = 未打开） */
    review?: any | null
    /** 已解析好的用户名（调用方按 users 字典降级） */
    userName?: string
    /** 菜品名（传值才渲染「菜品」行） */
    dishName?: string
  }>(),
  { review: null, userName: '', dishName: '' },
)

const emit = defineEmits<{ close: [] }>()

/** 配图点击：新窗口查看原图（COS 公网地址，noopener 防标签页劫持） */
function openImage(url: string) {
  if (!url) return
  window.open(url, '_blank', 'noopener')
}

function formatTime(v: unknown): string {
  return v ? new Date(v as any).toLocaleString('zh-CN') : '—'
}
</script>

<template>
  <FormDialog :show="show" title="评价详情" :width="520" :footer="false" @close="emit('close')">
    <div v-if="review" class="rd">
      <div class="rd-row"><span class="rd-label">用户</span><span class="rd-value">{{ userName }}</span></div>
      <div class="rd-row"><span class="rd-label">评分</span><span class="rd-value"><StarRating :value="review.rating" /></span></div>
      <div class="rd-row rd-row-top"><span class="rd-label">内容</span><span class="rd-value rd-text">{{ review.content || '（无文字内容）' }}</span></div>
      <div class="rd-row rd-row-top" v-if="(review.images || []).length">
        <span class="rd-label">配图</span>
        <div class="rd-value">
          <div class="rd-images">
            <img
              v-for="(img, i) in review.images"
              :key="i"
              :src="img"
              class="rd-thumb"
              alt="评价配图"
              loading="lazy"
              decoding="async"
              @click="openImage(img)"
            />
          </div>
          <p class="rd-hint">点击图片在新窗口查看原图</p>
        </div>
      </div>
      <div class="rd-row" v-if="dishName"><span class="rd-label">菜品</span><span class="rd-value">{{ dishName }}</span></div>
      <div class="rd-row">
        <span class="rd-label">状态</span>
        <span class="rd-value"><StatusTag :type="review.is_hidden ? 'danger' : 'success'" :text="review.is_hidden ? '已隐藏' : '显示中'" /></span>
      </div>
      <div class="rd-row"><span class="rd-label">时间</span><span class="rd-value">{{ formatTime(review.created_at) }}</span></div>
    </div>

    <div class="modal-actions" v-if="review">
      <button class="btn-cancel" v-press type="button" @click="emit('close')">关闭</button>
    </div>
  </FormDialog>
</template>

<style scoped>
.rd { display: flex; flex-direction: column; gap: var(--space-3); }
.rd-row { display: flex; gap: var(--space-3); font-size: var(--font-base); }
.rd-row-top { align-items: flex-start; }
.rd-label { width: 72px; flex-shrink: 0; color: var(--text-muted); }
.rd-value { color: var(--text-primary); flex: 1; min-width: 0; }
.rd-text { font-weight: var(--weight-regular); color: var(--text-secondary); line-height: var(--leading-loose); white-space: pre-wrap; }

/* 配图缩略图：hover 微放大提示可点，按压走统一 --press-scale */
.rd-images { display: flex; flex-wrap: wrap; gap: var(--space-2); }
.rd-thumb {
  width: 88px; height: 88px; border-radius: var(--radius-md); object-fit: cover;
  border: 1px solid var(--border-light); cursor: zoom-in; background: var(--bg-soft);
  transition: transform 160ms var(--ease-out), box-shadow 160ms var(--ease-out);
}
@media (hover: hover) {
  .rd-thumb:hover { transform: scale(1.04); box-shadow: var(--shadow-card); }
}
.rd-thumb:active { transform: scale(var(--press-scale)); }
.rd-hint { margin: var(--space-2) 0 0; font-size: var(--font-xs); color: var(--text-light); }

/* 减弱动效（§4.7）：去缩放，仅保留静态呈现 */
@media (prefers-reduced-motion: reduce) {
  .rd-thumb { transition: none; }
  .rd-thumb:hover { transform: none; }
  .rd-thumb:active { transform: none; }
}
</style>
