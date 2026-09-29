<template>
  <!-- 评价卡：整卡一张（卡头 + flat 条目）；三态齐全（加载中静默 / 失败可重试 / 零评价鼓励态）
       P3-01：卡头改用 SectionTitle（§4.9「分区标题一律 SectionTitle」）。
       Round 24 重排：标题行 = 左「评价 + 条数」（合并为一个标题块，数字同色 / 小半号 / 等宽）
       + 右**两段式筛选胶囊「全部 / 有图」**（替换原自定义 switch —— 视图过滤 ≠ 常驻偏好）；
       数字口径 = **已加载条数**（分页壳只有 `records`，服务端不回传总数）；在途 / 失败态不渲染数字。
       评价卡无「有用」按钮；排序唯一时间倒序、无切换入口；条目之间纯留白、不画分割线。 -->
  <view class="review-section" id="review-section">
    <!-- 卡片壳改用公共 `CardSection`（UI 统一 Loop Round 13 裁决 2B）：
         `flush` = 去掉自身外边距（块间距由外层 `.review-section` 统管），
         内距随即统一到 `--spacing-md`（原先本卡 16/24rpx 与同页另两卡 24rpx 不同轴）。 -->
    <CardSection flush>
      <!-- 标题行（Round 24 重排）：
           左 = 「评价」+ 总数，**合并为一个标题块**（经 SectionTitle 的 `count`）：数字与标题同色、小半号、等宽；
                数字口径**恒为 total**（不再出现「有图 N」—— 随筛选变形的数字会让总数读作跳变）；
                在途 / 失败态不渲染数字（避免 0 值误导，失败 ≠ 零评价）。
           右 = **两段式筛选胶囊「全部 / 有图」**（替换原自定义 switch）：
                「只看有图」是**视图过滤**（看完即走），不是常驻偏好设置 ⇒ tab 语义比 switch 准确。 -->
      <SectionTitle title="评价" :count="(!pending && !loadFailed) ? count : null" no-margin>
        <template #extra>
          <view class="seg" role="tablist" aria-label="评价筛选">
            <view
              class="seg-item"
              :class="{ on: !imageOnly }"
              role="tab"
              :aria-selected="(!imageOnly) ? 'true' : 'false'"
              hover-class="seg-item--pressed"
              hover-stay-time="80"
              @tap="onFilter('all')"
            >
              <text class="seg-text">全部</text>
            </view>
            <view
              class="seg-item"
              :class="{ on: imageOnly }"
              role="tab"
              :aria-selected="imageOnly ? 'true' : 'false'"
              hover-class="seg-item--pressed"
              hover-stay-time="80"
              @tap="onFilter('image')"
            >
              <text class="seg-text">有图</text>
            </view>
          </view>
        </template>
      </SectionTitle>

      <!-- ① spec §4.8 / a11y 红线：不设加载骨架/loading 指示。首屏拉取与「有图」筛选切换的在途期
           （pending）本区块不渲染任何内容，保持空白静默——不得误闪空态文案。 -->

      <!-- ② 失败态：可重试（§7.20 PR-03 失败态必备，避免误闪空态误导用户） -->
      <RetryBlock v-if="loadFailed" :margin="false" @retry="emit('retry')" />

      <!-- ③ 有数据 / ④ 「有图」筛选无结果 / ⑤ 零评价鼓励态（在途期整体不渲染） -->
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

        <!-- 「有图」筛选下无带图评价：文案指向「切回全部」（Round 24：筛选入口由开关改为胶囊 tab，
             文案随之改口径）；仍不给「写评价」动作 —— 该菜可能已有无图评价 -->
        <!-- 统一空态组件（UI 统一 Loop Round 3）：轻量形态（区块内空态，无底色） -->
        <EmptyState
          v-else-if="imageOnly"
          title="暂无带图评价"
          desc="切换到「全部」查看所有评价"
        />

        <!-- 零评价鼓励态：明确「还没有人评」+ 给出可执行入口（写评价，不新增页面；
             未认证点击由页面侧 requireAuth 跳独立认证页） -->
        <EmptyState
          v-else
          title="还没有人评价这道菜"
          desc="你的第一条评价，能帮同学避雷"
          action-text="写第一条评价"
          @action="emit('write')"
        />
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
import type { Review, MyReview } from '@/types/review'

const props = defineProps<{
  reviews: Review[]
  /**
   * 标题行展示的评价条数 = **已加载条数**。
   * 分页壳只有 `records`（服务端不回传总数），故「总数」口径退化为当前已加载量；
   * 触底加载更多时该数字随之上浮，属契约既定形态。
   */
  count: number
  /** 评价首屏/刷新是否失败（失败 ≠ 零评价，渲染可重试失败态） */
  loadFailed?: boolean
  /** 「有图」筛选选中态（服务端过滤 `hasImage=1`；切换由页面重置分页并清空列表后重拉） */
  imageOnly: boolean
  /**
   * 重置式评价请求在途（首屏 / 只看有图切换 / 重试 / 提交后刷新）：
   * 为真时本区块空白静默，不渲染列表与空态（避免切换瞬间误闪「暂无带图评价 / 还没有人评价」）。
   */
  pending: boolean
}>()

/* 评价条数口径：**恒为已加载条数** —— 不再随筛选显示「有图 N」
   （随口径变形的数字会让用户以为「评价条数」在跳变）；数字经 `SectionTitle` 的 `count`
   与标题合并渲染为「评价 12」（同色 / 小半号 / 等宽）。 */

/* `delete` / `report` 两个转发事件已移除（UI 统一 Loop Round 17）：
   其唯一来源是 `ReviewItem` 的同名事件，而该事件在组件内从未触发 ⇒ 转发链整体为死代码；
   删除 / 举报现由页面 `ActionSheet`（经 `more` 事件）统一处理。 */
const emit = defineEmits<{
  (e: 'more', review: Review | MyReview): void
  /** 失败态点击重试：页面侧重拉评价列表（与进入页面同路径） */
  (e: 'retry'): void
  /** 零评价空态 → 写评价（页面侧走 requireAuth → ReviewComposer） */
  (e: 'write'): void
  /** 筛选口径变更（全部 ⇄ 有图）：页面侧重置分页 + 清空列表后按新口径重拉 */
  (e: 'toggle-image-only'): void
}>()

/**
 * 两段式筛选（Round 24）：`全部` → `hasImage=0`；`有图` → `hasImage=1`。
 *
 * **仅在筛选结果口径真的发生变化时上抛**（重复点已选中项 = 无操作）——
 * 否则会给页面送去一次无谓的「重拉第 1 页」，列表在途闪白且浪费一次请求。
 */
function onFilter(kind: 'all' | 'image') {
  if ((kind === 'image') === props.imageOnly) return
  emit('toggle-image-only')
}
</script>

<style scoped>
/* 纵向间距：块间距统管在外层（卡壳本身 `flush`，见模板）；同页两卡内距由此统一到 `--spacing-md`
   （UI 统一 Loop Round 19：原「综合评分」卡并入信息卡 ⇒ 同页三卡 → 两卡） */
.review-section { margin: var(--spacing-sm) var(--spacing-md) 0; }
/* 条目之间**纯留白**分隔（Round 24，用户口径：不加分割线）—— 间距由列表容器统一给，
   条目自身 `--flat` 无 padding / 无 border（见 ReviewItem） */
.review-list { display: flex; flex-direction: column; gap: var(--spacing-lg); }

/* ===== 标题行右位：两段式筛选胶囊（全部 / 有图）=====
   评价数已并入标题块（SectionTitle 的 `count`），右位只放筛选器。
   语义依据：这是**视图过滤**（tab），不是常驻偏好设置（switch）—— 切一下只影响当前列表，
   与用户「看完就切回」的心智一致；switch 会被读作「长期偏好」。 */
.seg {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  padding: var(--spacing-2xs);
  border-radius: var(--radius-pill);
  /* 槽底色须**明显浅于白卡**：选中项是「白底浮起」块，槽若近白则选中态不可辨
     （同 R14 开关轨道教训 —— --bg-placeholder 对白卡 ≈1.17:1）。取 --bg-soft（暖灰）保证层次。 */
  background: var(--bg-soft);
}
/* 单项：视觉紧凑（≈48rpx 高）；命中区经 ::after **仅纵向**扩至 ≥88rpx
   —— 横向不扩，避免左右两项热区互相窃取点击（a11y 44pt 下限同时满足）。 */
.seg-item {
  position: relative;
  padding: var(--spacing-2xs) var(--spacing-sm);
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
  transition: background var(--duration-fast) var(--ease-out);
}
.seg-item::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 88rpx;
  transform: translateY(-50%);
}
.seg-text { font-size: var(--font-small); font-weight: var(--weight-medium); color: var(--text-secondary); line-height: 1.2; white-space: nowrap; }
/* 选中项：白底 + 深色文字 + 极轻阴影（在槽内浮起）；未选中项：透明底 + 次级灰文字 */
.seg-item.on { background: var(--bg-card); box-shadow: var(--shadow-card); }
.seg-item.on .seg-text { color: var(--text-primary); }
.seg-item--pressed { opacity: 0.7; }
@media (prefers-reduced-motion: reduce) {
  .seg-item { transition: none; }
}

/* ===== 失败态：视觉由公共 RetryBlock 承担，此处仅补卡内上下呼吸 =====
   （选择器随 2B 收敛调整：卡壳已改 `CardSection`，`.review-card` 不复存在 ⇒ 改挂外层 `.review-section`） */
.review-section :deep(.retry-block) { margin: var(--spacing-sm) 0; }

/* 空态已上提为公共组件 components/EmptyState.vue（UI 统一 Loop Round 3），此处不再保留副本 */
</style>
