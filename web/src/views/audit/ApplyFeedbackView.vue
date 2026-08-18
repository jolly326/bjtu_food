<script setup lang="ts">
/**
 * ApplyFeedbackView：「申请与反馈」内容（UGC 申请审核 + 反馈举报处理）。
 * 上下两个区块展示（无分段切换），一屏看完反馈与申请待办。
 */
import ApplyReviewView from '@/views/admin/ApplyReviewView.vue'
import FeedbackView from '@/views/admin/FeedbackView.vue'
</script>

<template>
  <div>
    <div class="block-title">反馈举报</div>
    <FeedbackView />
    <div class="block-title">UGC 申请</div>
    <ApplyReviewView initial-entity="dish" />
  </div>
</template>

<style scoped>
/* 区块标题：与全站分区卡标题一致的品牌竖条 */
.block-title {
  display: flex;
  align-items: center;
  margin: var(--space-3) 0 var(--space-2);
  font-size: var(--font-sm);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  padding-left: var(--space-3);
  position: relative;
}
.block-title:first-child { margin-top: 0; }
.block-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 3px;
  bottom: 3px;
  width: 3px;
  border-radius: var(--radius-xs);
  background: var(--color-primary);
}
</style>
