<template>
  <!-- 首页菜品卡：**四段固定排版**（2026-09-21 §7.34 / home-page-presentation）：
       ① 图（约卡片高度 52%）→ ② 菜名（黑色加粗、卡片最大字号）
       → ③ `食堂名称 | 档口名称`（浅灰纯文字）→ ④ 星+评分（左）/ 价格（右，橙色）。
       ⚠️ 不出现：彩色标签块（原「必吃推荐 / 招牌菜」随 dish.tags 删除）、距离文案、销量（月售）文案。 -->
  <view
    class="dish-card"
    :aria-label="`${dish.name}，${formatPrice(dish.price)}元`"
    @tap="handleClick"
    role="button"
    tabindex="0"
    hover-class="dish-card-pressed"
  >
    <!-- 段1：菜品实拍图（16:10；比例容器固定，占位与实图等高 → 瀑布流不重排） -->
    <view class="card-image">
      <image
        v-if="imgSrc && imgOk"
        :src="imgSrc"
        mode="aspectFill"
        class="card-img"
        :class="{ loaded: imgLoaded }"
        lazy-load
        @load="imgLoaded = true"
        @error="imgOk = false"
      />
      <view v-else class="image-placeholder">
        <IconSvg name="dish" :size="56" :color="COLOR_MAP['text-tertiary']" class="placeholder-icon" />
      </view>
    </view>

    <view class="card-info">
      <!-- 段2：菜名（一级信息，卡片内最大字号 + 加粗） -->
      <text class="card-name">{{ dish.name }}</text>
      <!-- 段3：食堂 | 档口（浅灰纯文字，分隔符为竖线；无彩色标签、无距离） -->
      <text class="card-loc">{{ locationText }}</text>
      <!-- 段4：左 = 实心星 + 数字评分；右 = 橙色价格 -->
      <view class="card-foot">
        <view class="card-rating">
          <!-- 星标为纯装饰：评分数值紧随其后，已由文本表达（aria-hidden 避免重复朗读） -->
          <IconSvg name="star-filled" :size="24" :color="COLOR_MAP['star']" class="star-icon" aria-hidden="true" />
          <text class="rating-text">{{ fmtRating(dish.rating) }}</text>
        </view>
        <text class="card-price">¥{{ formatPrice(dish.price) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Dish } from '@/types/dish'
import { getImageUrl, getThumbUrl } from '@/utils/image'
import { formatPrice } from '@/utils/money'
import IconSvg from '@/components/IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'

const props = defineProps<{
  dish: Dish
}>()

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件按原生事件名监听会被编译成 bindclick，emit 参数丢失。
const emit = defineEmits<{
  select: [dish: Dish]
}>()

/** 图片 URL：通过 getImageUrl 处理（兼容相对路径与完整 URL） */
// C14 列表缩略图走 _thumb（仅详情大图用原图），弱网下流量/时延明显下降
const imgSrc = computed(() => getImageUrl(getThumbUrl(props.dish.images?.[0] || '')))

/** 图片加载状态：加载失败则回退到占位，禁止裂图 */
const imgOk = ref(true)
/** 图片淡入：load 事件触发后置 true，配合 .card-img.loaded 做 opacity 过渡（B.5 降低 CLS） */
const imgLoaded = ref(false)

/** 段3 文案：`食堂名称 | 档口名称`（任一侧缺失时不产生悬空分隔符） */
const locationText = computed(() => {
  const parts = [props.dish.canteen, props.dish.stallName].filter(Boolean)
  return parts.join(' | ')
})

/** 评分统一保留一位小数（与详情页 toFixed(1) 一致，避免 4 / 4.5 显示不一致） */
function fmtRating(r: number): string {
  return Number(r || 0).toFixed(1)
}

function handleClick() {
  emit('select', props.dish)
}
</script>

<style scoped>
.dish-card {
  width: 100%;
  min-width: 0;
  /* 表面：白卡 + 品牌淡色柔和投影（tab-pages-visual-unify）——
     页面层级由「浅米灰底 — 白卡 — 内容 — 强调」四层结构承担。
     overflow:hidden 保留：顶部图片贴齐卡片上缘，需裁进圆角；
     同时规避微信 WXSS「border-radius + background」圆角外侧背景方角残留。 */
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  -webkit-tap-highlight-color: transparent;
}
/* 按压反馈：小程序端统一「透明度微降」（§4.9：废止 transform: scale 按压） */
.dish-card-pressed { opacity: 0.85; }
/* 固定 16:10 比例容器 = 经典 padding-bottom 等比盒（**不用 aspect-ratio**：
   小程序对 aspect-ratio 支持不稳，真机不认时容器高会塌成 0 → 图片整块消失（D2）；
   与 ReviewItem 的 .review-image-box / ImagePicker 的等比盒同法）。
   62.5% = 10 / 16；图约占卡片高度 52%（文字段越短占比越高、越长越低，均值≈52%）；
   未加载（占位）与加载后（图片）高度一致，消除瀑布流滚动重排卡顿（CLS=0） */
.card-image {
  position: relative;
  width: 100%;
  height: 0;
  padding-bottom: 62.5%;
  background: var(--bg-soft);
  overflow: hidden;
}
.card-img {
  /* 绝对定位贴合等比盒（宽高 100%）：容器高由 padding-bottom 撑出，图片不再参与撑高 */
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  /* B.5 图片加载淡入：默认透明，load 完成后淡入，避免硬切/跳变（CLS<0.1） */
  opacity: 0;
  transition: opacity var(--duration-slow) var(--ease-out);
}
.card-img.loaded { opacity: 1; }
/* 占位与图片同为绝对定位贴合等比盒：容器内容高为 0（高由 padding-bottom 撑出），
   占位块若留在常规流中 height:100% 会解析为 0 → 占位图标不可见 */
.image-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.placeholder-icon {
  font-size: var(--icon-2xl);
  line-height: 1;
}
.card-info {
  padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md);
  min-width: 0;
}
/* 段2 菜名：黑色（--text-primary）+ 加粗 + 卡片内最大字号（--font-h3，高于价格与位置行） */
.card-name {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: var(--font-h3);
  font-weight: var(--weight-bold);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  color: var(--text-primary);
}
/* 段3 食堂 | 档口：浅灰纯文字（--text-tertiary），字号小于菜名；单行省略 */
.card-loc {
  display: block;
  margin-top: var(--spacing-2xs);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 段4：评分居左 / 价格居右，同一行 */
.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}
.card-rating {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  min-width: 0;
}
.star-icon { flex-shrink: 0; line-height: 1; }
.rating-text {
  font-size: var(--font-small);
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
/* 价格：橙色（--color-price = 主色文字档）+ 加粗，字号/字重均高于食堂档口 */
.card-price {
  flex-shrink: 0;
  font-size: var(--font-subtitle);
  color: var(--color-price);
  font-weight: var(--weight-bold);
  font-variant-numeric: tabular-nums;
}

@media (prefers-reduced-motion: reduce) {
  .card-img { transition: none; opacity: 1; }
}
</style>
