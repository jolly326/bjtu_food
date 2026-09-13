<script setup lang="ts">
/**
 * ApplyFeedbackView：反馈举报处理聚合（UGC 申请审核已随 apply 全链路下线，见 change prelaunch-loop-closure）。
 * 仅保留反馈举报处理区块，不再内嵌「UGC 申请」段。
 */
import FeedbackView from '@/views/admin/FeedbackView.vue'
</script>

<template>
  <div>
    <div class="block-title">反馈</div>
    <FeedbackView />
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
