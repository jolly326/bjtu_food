<template>
  <view class="feed-wrap">
    <template v-if="dishStore.filterList.length > 0">
      <WaterfallList :list="dishStore.filterList" @card-click="goToDetail" />

      <view v-if="dishStore.filterLoadingMore" class="list-footer loading">
        <view class="footer-spinner" />
        <text class="footer-text">加载中…</text>
      </view>
      <view v-else-if="dishStore.filterFinished" class="list-footer finished">
        <text class="footer-text">— 已经到底啦 —</text>
      </view>
    </template>

    <view v-else class="home-empty">
      <IconSvg name="empty" :size="120" color="var(--text-tertiary)" />
      <text class="empty-tip">{{ waterfallFailed ? '加载失败' : '暂时没有内容' }}</text>
      <text class="empty-sub">{{ waterfallFailed ? '网络异常，请稍后重试' : '下拉刷新，或检查网络后重试' }}</text>
      <view v-if="waterfallFailed" class="home-retry" hover-class="pressed" hover-stay-time="80" @tap="emit('retry')">
        <text class="home-retry-text">重新加载</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import WaterfallList from '@/components/WaterfallList.vue'
import IconSvg from '@/components/IconSvg.vue'
import { useDishStore } from '@/stores/dish'

const props = defineProps<{
  /** 瀑布流加载失败（首屏网络异常），用于空态文案降级 */
  loadFailed?: boolean
}>()

const emit = defineEmits<{
  (e: 'retry'): void
}>()

const dishStore = useDishStore()

/** 空态失败判定：广播/万能区（loadFailed）或瀑布流自身（filterLoadFailed）任一失败即展示失败态，避免「moments 成功但瀑布流失败」被误判为「没有内容」 */
const waterfallFailed = computed(() => props.loadFailed || dishStore.filterLoadFailed)

/** 菜品卡片点击 → 独立详情页（pages-detail/dish） */
function goToDetail(dish: { id: number }) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}
</script>

<style scoped lang="scss">
.feed-wrap {
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
}

.list-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-md) 0;
  gap: var(--spacing-xs);

  .footer-spinner {
    width: 28rpx;
    height: 28rpx;
    border: 3rpx solid var(--border-color);
    border-top-color: var(--color-primary);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }

  .footer-text {
    font-size: var(--font-body);
    color: var(--text-tertiary);
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.home-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--spacing-xl) var(--spacing-md);

  .empty-tip {
    margin-top: var(--spacing-sm);
    font-size: var(--font-subheading);
    color: var(--text-primary);
  }

  .empty-sub {
    margin-top: var(--spacing-xs);
    font-size: var(--font-body);
    color: var(--text-tertiary);
    text-align: center;
  }

  .home-retry {
    margin-top: var(--spacing-md);
    padding: var(--spacing-sm) var(--spacing-lg);
    background: var(--color-primary);
    border-radius: var(--radius-btn);
    transition: var(--press-transition);
    -webkit-tap-highlight-color: transparent;

    &.pressed {
      background: var(--color-primary);
      opacity: 0.85;
    }

    .home-retry-text {
      font-size: var(--font-body);
      font-weight: var(--weight-medium);
      color: var(--color-on-primary);
    }
  }
}
</style>
