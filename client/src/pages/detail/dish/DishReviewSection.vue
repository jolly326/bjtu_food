<template>
  <!-- 评价卡：整卡一张（卡头 + flat 条目）；三态齐全（加载中静默 / 失败可重试 / 零评价）
       P3-01：卡头改用 SectionTitle（§4.9「分区标题一律 SectionTitle」）。
       标题行 = 左「评价 + 条数」（合并为一个标题块，数字同色 / 小半号 / 等宽）
       + 右**「写评价」轻量入口**（主色笔形图标 + 文字，**非按钮形态**）；
       数字口径 = **已加载条数**（分页壳只有 `records`，服务端不回传总数）；在途 / 失败态不渲染数字。
       评价卡无「有用」按钮；排序唯一时间倒序、无切换入口；条目之间纯留白、不画分割线。 -->
  <view class="review-section" id="review-section">
    <!-- 卡片壳改用公共 `CardSection`（UI 统一 Loop Round 13 裁决 2B）：
         `flush` = 去掉自身外边距（块间距由外层 `.review-section` 统管），
         内距随即统一到 `--spacing-md`（原先本卡 16/24rpx 与同页另两卡 24rpx 不同轴）。 -->
    <CardSection flush>
      <!-- 标题行：
           左 = 「评价」+ 条数，**合并为一个标题块**（经 SectionTitle 的 `count`）：数字与标题同色、小半号、等宽；
                数字口径 = **已加载条数**；在途 / 失败态不渲染数字（避免 0 值误导，失败 ≠ 零评价）。
           右 = **「写评价」轻量入口**（主色笔形图标 + 文字，**非按钮形态**；替换原两段式筛选胶囊；
                文案恒定，无「重新评价」态）。 -->
      <SectionTitle title="评价" :count="(!pending && !loadFailed) ? count : null" no-margin>
        <template #extra>
          <!-- 轻量入口（**非按钮形态**）：主色线性笔形图标 + 主色文字，**无边框 / 无底色 / 无阴影**；
               仅表达「点击这里可以写评价」，不与页面主操作按钮抢视觉权重。 -->
          <view
            class="write-entry"
            role="button"
            aria-label="写评价"
            hover-class="write-entry--pressed"
            hover-stay-time="80"
            @tap="emit('write')"
          >
            <IconSvg name="edit" :size="26" :color="COLOR_MAP['primary-text']" class="write-entry-icon" />
            <text class="write-entry-text">写评价</text>
          </view>
        </template>
      </SectionTitle>

      <!-- ① spec §4.8 / a11y 红线：不设加载骨架/loading 指示。首屏拉取 / 重试 / 提交后刷新的在途期
           （pending）本区块不渲染任何内容，保持空白静默——不得误闪空态文案。 -->

      <!-- ② 失败态：可重试（§7.20 PR-03 失败态必备，避免误闪空态误导用户） -->
      <RetryBlock v-if="loadFailed" :margin="false" @retry="emit('retry')" />

      <!-- ③ 有数据 / ④ 零评价（在途期整体不渲染） -->
      <template v-else-if="!pending">
        <!-- 有数据：评价列表（无「有用」入口；排序唯一时间倒序） -->
        <view v-if="reviews.length > 0" class="review-list">
          <ReviewItem
            v-for="rv in reviews"
            :key="rv.id"
            :review="rv"
            flat
            @more="emit('more', $event)"
          />
        </view>

        <!-- 零评价空态：**纯文本「暂无评价」**（无副文案、无引导按钮 —— 写评价入口唯一落点 = 标题行右侧按钮） -->
        <!-- 统一空态组件（UI 统一 Loop Round 3）：轻量形态（区块内空态，无底色） -->
        <EmptyState v-else title="暂无评价" />
      </template>
    </CardSection>
  </view>
</template>

<script setup lang="ts">
import ReviewItem from '@/components/ReviewItem.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CardSection from '@/components/CardSection.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
// 图标色须传**实色**（IconSvg 的 color 不解析 var()，data-uri 内为字面量，传 var(...) 恒落近黑）
import { COLOR_MAP } from '@/theme/tokens'
import type { Review, MyReview } from '@/types/review'

defineProps<{
  reviews: Review[]
  /**
   * 标题行展示的评价条数 = **已加载条数**。
   * 分页壳只有 `records`（服务端不回传总数），故「总数」口径退化为当前已加载量；
   * 触底加载更多时该数字随之上浮，属契约既定形态。
   */
  count: number
  /** 评价首屏/刷新是否失败（失败 ≠ 零评价，渲染可重试失败态） */
  loadFailed?: boolean
  /**
   * 重置式评价请求在途（首屏 / 重试 / 提交后刷新）：
   * 为真时本区块空白静默，不渲染列表与空态（避免在途瞬间误闪「暂无评价」）。
   */
  pending: boolean
}>()

/* 评价条数口径：**恒为已加载条数**（分页壳只有 `records`，服务端不回传总数）；
   数字经 `SectionTitle` 的 `count` 与标题合并渲染为「评价 12」（同色 / 小半号 / 等宽）。 */

/* `delete` / `report` 两个转发事件已移除（UI 统一 Loop Round 17）：
   其唯一来源是 `ReviewItem` 的同名事件，而该事件在组件内从未触发 ⇒ 转发链整体为死代码；
   删除 / 举报现由页面 `ActionSheet`（经 `more` 事件）统一处理。 */
const emit = defineEmits<{
  (e: 'more', review: Review | MyReview): void
  /** 失败态点击重试：页面侧重拉评价列表（与进入页面同路径） */
  (e: 'retry'): void
  /** 写评价（标题行右侧唯一入口；页面侧走 requireAuth → ReviewComposer） */
  (e: 'write'): void
}>()
</script>

<style scoped>
/* 纵向间距：块间距统管在外层（卡壳本身 `flush`，见模板）；同页两卡内距由此统一到 `--spacing-md`
   （UI 统一 Loop Round 19：原「综合评分」卡并入信息卡 ⇒ 同页三卡 → 两卡） */
.review-section { margin: var(--spacing-sm) var(--spacing-md) 0; }
/* 条目之间**纯留白**分隔（Round 24，用户口径：不加分割线）—— 间距由列表容器统一给，
   条目自身 `--flat` 无 padding / 无 border（见 ReviewItem） */
.review-list { display: flex; flex-direction: column; gap: var(--spacing-lg); }

/* ===== 标题行右位：「写评价」轻量入口（**文字 + 图标，非按钮形态**）=====
   评价数已并入标题块（SectionTitle 的 `count`），右位只放写评价入口。
   **无边框 / 无底色 / 无阴影 / 无胶囊槽** —— 仅主色笔形图标 + 主色文字（视觉权重低于任何按钮）；
   按压反馈 = opacity 微降（**禁 `transform: scale`** —— 全站红线）。 */
.write-entry {
  position: relative;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-3xs);
  -webkit-tap-highlight-color: transparent;
}
/* 命中区经 ::after **仅纵向**扩至 ≥88rpx（a11y 44pt 下限；视觉尺寸不变） */
.write-entry::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 88rpx;
  transform: translateY(-50%);
}
.write-entry-icon {
  flex: none;
  width: 26rpx;
  height: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.write-entry-text {
  font-size: var(--font-small);
  font-weight: var(--weight-medium);
  color: var(--color-primary-text);
  line-height: 1.2;
  white-space: nowrap;
}
.write-entry--pressed { opacity: 0.6; }

/* ===== 失败态：视觉由公共 RetryBlock 承担，此处仅补卡内上下呼吸 =====
   （选择器随 2B 收敛调整：卡壳已改 `CardSection`，`.review-card` 不复存在 ⇒ 改挂外层 `.review-section`） */
.review-section :deep(.retry-block) { margin: var(--spacing-sm) 0; }

/* 空态已上提为公共组件 components/EmptyState.vue（UI 统一 Loop Round 3），此处不再保留副本 */
</style>
