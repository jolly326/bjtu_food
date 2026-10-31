<script setup lang="ts">
/**
 * 菜品信息页（DishSheet 滑动区第 1 页）。
 *
 * 行结构与纠错页对齐（菜名+点赞 / 档口 / 分割线 / 食堂+楼层+纠错），
 * 保证横向切换时分割线垂直位置不动、弹层高度不变。
 */
import { computed } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import DashedRule from '@/components/DashedRule.vue'
import type { DishVO } from '@/api'

const props = withDefaults(
  defineProps<{
    dish: DishVO | null
    likeBusy: boolean
    likeCooling: boolean
    /** 点赞 Q 弹（成功后由组合器触发一次） */
    popping: boolean
  }>(),
  {
    dish: null,
    likeBusy: false,
    likeCooling: false,
    popping: false,
  },
)

const emit = defineEmits<{
  (e: 'toggleLike'): void
  (e: 'openFeedback'): void
}>()

const likeLabel = computed(() => (props.dish?.liked ? '取消点赞' : '点赞'))
/** 🔴 该位恒为点赞数（口径见 docs/func/client/B1）：冷却期也不换成倒计时秒数 */
const displayCount = computed(() => props.dish?.likeCount ?? 0)
</script>

<template>
  <view v-if="dish" class="dish">
    <!-- 上方块：菜名 + 点赞 / 档口 -->
    <view class="dish__block">
      <view class="dish__head">
        <text class="dish__name">{{ dish.name }}</text>
        <view
          class="like"
          :class="{
            'like--on': dish.liked,
            'like--busy': likeBusy || likeCooling,
            'like--pop': popping,
          }"
          hover-class="like--pressed"
          aria-role="button"
          :aria-label="likeLabel"
          @tap.stop="emit('toggleLike')"
        >
          <AppIcon :name="dish.liked ? 'heart-on' : 'heart'" :size="40" />
          <text class="like__count">{{ displayCount }}</text>
        </view>
      </view>

      <text v-if="dish.stallName" class="dish__stall">{{ dish.stallName }}</text>
    </view>

    <!-- 虚线：固定在滑块中线 -->
    <DashedRule />

    <!-- 下方块：食堂 + 楼层 / 纠错入口 -->
    <view class="dish__block dish__block--bottom">
      <view class="dish__loc-row">
        <view class="dish__loc-left">
          <text class="dish__canteen">{{ dish.canteenName }}</text>
          <text v-if="dish.floor" class="dish__floor">{{ dish.floor }}</text>
        </view>
        <view
          class="issue-entry"
          aria-role="button"
          aria-label="纠错"
          @tap.stop="emit('openFeedback')"
        >
          <text class="issue-entry__text">纠错</text>
          <view class="issue-entry__arrow">
            <AppIcon name="chevron" :size="24" />
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.dish {
  display: block;
}

/* 上方块：min-height 定高 + 内容垂直居中（两页取同一值 ⇒ 虚线同一位置） */
.dish__block {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: var(--sheet-block-top);
}

/* 下方块：略矮，同样居中 */
.dish__block--bottom {
  min-height: var(--sheet-block-bottom);
}

/* 第 1 行：内容高度自适应，块内垂直居中（点击热区由 .like::after 提供） */
.dish__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.dish__name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  color: var(--text-title);
  font-family: var(--font-sans);
  font-size: var(--font-title);
  font-weight: var(--weight-regular);
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 第 2 行：固定行高（与纠错页留白同高） */
.dish__stall {
  display: flex;
  align-items: center;
  margin-top: var(--spacing-xs);
  overflow: hidden;
  color: var(--text-body);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 第 3 行：短横线虚线由 DashedRule 承载；本页仅此一条 */

/* 第 4 行：固定行高，与纠错页导航同高 */
.dish__loc-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  min-height: var(--sheet-block-bottom);
}

.dish__loc-left {
  display: flex;
  flex: 1;
  align-items: baseline;
  gap: var(--spacing-xs);
  min-width: 0;
}

.dish__canteen,
.dish__floor {
  overflow: hidden;
  color: var(--text-body);
  font-family: var(--font-sans);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dish__canteen {
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-body);
  line-height: 1.3;
}

.dish__floor {
  flex: 0 0 auto;
  font-size: var(--font-subtitle);
  line-height: 1.3;
}

/* 纠错入口：文字 + 图标，无底色、无边框（下方按钮统一不加承载底） */
.issue-entry {
  position: relative;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--spacing-2xs);
  min-height: var(--sheet-block-bottom);
  margin-right: calc(-1 * var(--spacing-xs));
  padding: 0 var(--spacing-xs);
}

/* 视觉高度随下方块压缩，命中区经 ::after 纵向补足到 96rpx（不改布局高度） */
.issue-entry::after {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  height: var(--tap-target-size);
  transform: translateY(-50%);
}

.issue-entry__text {
  color: var(--text-body);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  line-height: 1.3;
}

/* 右箭头：箭头本体为向上，旋转 90° */
.issue-entry__arrow {
  display: flex;
  transform: rotate(90deg);
}

/* 点赞：图标 + 数字，纯组件（无底色、无边框），与菜名 space-between */
.like {
  position: relative;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 0 var(--spacing-2xs);
}

.like::after {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  height: var(--tap-target-size);
  transform: translateY(-50%);
}

/* 两态用不同图标文件区分：heart 暖橙空心轮廓 / heart-on 珊瑚红实心（点赞激活态）*/
.like--pressed {
  opacity: 0.7;
}

.like--busy {
  opacity: 0.5;
}

.like--pop {
  animation: like-pop var(--duration-base) var(--ease-bounce);
}

@keyframes like-pop {
  0% {
    transform: scale(1);
  }

  45% {
    transform: scale(1.16);
  }

  100% {
    transform: scale(1);
  }
}

.like__count {
  color: var(--text-title);
  font-family: var(--font-sans);
  font-size: var(--font-title);
  line-height: 1;
}
</style>
