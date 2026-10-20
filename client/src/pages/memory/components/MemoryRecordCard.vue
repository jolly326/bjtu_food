<script setup lang="ts">
/**
 * 记忆卡（规格 docs/ui/client/记忆.md）。竖版 480×540，两互斥状态：
 * - 浏览态 A：菜名行（菜名 + 价格） / 中部居中圆形大图 / 左下位置（食堂 档口，无衬线小字）/ 右下探出状态角标（已评对勾、未评圆点）。
 * - 评价态 B：卡片底色面板原位覆盖，三档线性表情；点表情提交并退出，点空白不保存。
 * 入口：点角标 或 长按菜单「评价」（互斥由 useMemoryUI 保证）。
 */
import { computed } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import { useMemoryUI } from './useMemoryUI'
import { recordPlaceLine, recordPriceText } from '@/utils/recordMeta'
import type { RecordOut } from '@/api'

/** 宿主节点虚拟化：组件根直接参与卡片行布局（flex 子节点即 .record） */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    record?: RecordOut
    /** 位置文案（父级一次算好；缺省时组件按同一实现兜底） */
    place?: string
  }>(),
  { place: '' },
)

const emit = defineEmits<{ (e: 'react', value: number): void }>()

const { evaluatingId, openEval, closeEval, openMenu } = useMemoryUI()

const isEvaluating = computed(() => evaluatingId.value === props.record?.id)
const isRated = computed(() => props.record?.reaction != null)

/** 右下角状态图标：已评 = 圆圈对勾，未评 = 圆圈点（同一形态、同一弱装饰样式） */
const badgeIcon = computed(() => (isRated.value ? 'circle-check' : 'circle-dot'))

/** 位置行：优先用 prop place，缺省时按同一实现从 record 兜底（`食堂 档口`） */
const renderPlace = computed<string>(() =>
  props.place || (props.record ? recordPlaceLine(props.record) : ''),
)

/** 价格：与菜名同行右侧（无有效价格为空串，不占位） */
const priceText = computed<string>(() => (props.record ? recordPriceText(props.record) : ''))

/** 点右下角标 → 进入原位评价态（长按菜单「评价」为同一入口的第二条路径） */
function onBadgeTap() {
  if (!props.record) return
  openEval(props.record.id)
}

function onCardLongPress() {
  if (isEvaluating.value || !props.record) return
  openMenu(props.record.id)
}

function submit(value: number) {
  emit('react', value)
  closeEval()
}

function onBlankTap() {
  closeEval()
}
</script>

<template>
  <view class="record" @longpress="onCardLongPress">
    <!-- 不透明卡片表面：遮挡挂角重叠部分，使挂角呈「探出头」效果 -->
    <view class="record__surface" />

    <!-- 浏览态 A：菜名行(顶) / 圆形菜品图(居中) / 位置行(底部左对齐)；
         右上角唯一例外：菜品已下架 ⇒ 中性灰 tag 提示（低刺激，不抢主体） -->
    <view class="record__content">
      <text v-if="record?.offShelf" class="record__offTag">已下架</text>
      <view class="record__title">
        <text class="record__name wrap-anywhere">{{ record?.name }}</text>
        <text v-if="priceText" class="record__price">{{ priceText }}</text>
      </view>
      <view class="record__photoWrap">
        <view class="record__photo">
          <image
            v-if="record?.imageUrl"
            :src="record?.imageUrl"
            class="record__img"
            mode="aspectFill"
            lazy-load
          />
          <ImagePlaceholder v-else :size="64" />
        </view>
      </view>
      <text v-if="renderPlace" class="record__placeText">{{ renderPlace }}</text>
    </view>

    <!-- 右下：探出式状态角标（已评 = 圆圈对勾 / 未评 = 圆圈点；低透明度弱装饰外观，
         点按进入原位评价态，评价选项在卡片内展开） -->
    <view
      v-show="!isEvaluating"
      class="record__badge"
      hover-class="record__badge--pressed"
      role="button"
      aria-label="对该菜品进行评价"
      @tap.stop="onBadgeTap"
    >
      <AppIcon :name="badgeIcon" :size="200" tone="muted" />
    </view>

    <!-- 评价激活态 B：原位面板（卡片底色，覆盖卡片，容器不变形） -->
    <transition name="eval-fade">
      <view v-if="isEvaluating" class="record__eval" @tap="onBlankTap">
        <view class="record__evalInner" @tap.stop>
          <text class="record__evalTip">为这道菜打分</text>
          <view class="record__evalOpts">
            <view
              class="record__evalOpt"
              :class="{ 'is-active': record?.reaction === 1 }"
              hover-class="record__evalOpt--pressed"
              role="button"
              aria-label="为这道菜品打分：一般"
              @tap.stop="submit(1)"
            >
              <AppIcon name="meh" :size="64" tone="default" />
              <text class="record__evalLabel">一般</text>
            </view>
            <view
              class="record__evalOpt"
              :class="{ 'is-active': record?.reaction === 3 }"
              hover-class="record__evalOpt--pressed"
              role="button"
              aria-label="为这道菜品打分：不错"
              @tap.stop="submit(3)"
            >
              <AppIcon name="smile" :size="64" tone="default" />
              <text class="record__evalLabel">不错</text>
            </view>
            <view
              class="record__evalOpt"
              :class="{ 'is-active': record?.reaction === 5 }"
              hover-class="record__evalOpt--pressed"
              role="button"
              aria-label="为这道菜品打分：很棒"
              @tap.stop="submit(5)"
            >
              <AppIcon name="laugh" :size="64" tone="default" />
              <text class="record__evalLabel">很棒</text>
            </view>
          </view>
        </view>
      </view>
    </transition>
  </view>
</template>

<style lang="scss" scoped>
.record {
  position: relative;
  display: inline-flex;
  flex: none;
  z-index: 0;
  box-sizing: border-box;
  width: var(--record-card-width);
  height: var(--record-card-height);
  border-radius: var(--radius-card);
  border: 1rpx solid var(--border-color);
  overflow: hidden;
  box-shadow: var(--shadow-card-stack);
}

.record__surface {
  position: absolute;
  inset: 0;
  z-index: 0;
  /* 卡面无实线描边：靠极浅灰白底色 + 极弱阴影浮起 */
  background: var(--bg-card);
  border-radius: var(--radius-card);
}

.record__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  /* 卡片内距：上下左右均 40rpx —— 文字与图片四周留足呼吸位，不贴卡缘 */
  padding: var(--spacing-xl);
}

/* 右上角「已下架」tag：中性灰（占位底 + 弱化字），仅菜品下架时渲染；
   绝对定位于内容区右上（落在卡内距内，不贴卡缘） */
.record__offTag {
  position: absolute;
  top: var(--spacing-xl);
  right: var(--spacing-xl);
  padding: var(--spacing-2xs) var(--spacing-sm);
  background: var(--bg-placeholder);
  border-radius: var(--radius-xs);
  font-size: var(--font-small);
  color: var(--text-tertiary);
}

/* 菜名行：菜名与价格同行（价格紧跟菜名右侧，间距 --spacing-xs） */
.record__title {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: var(--spacing-xs);
  width: 100%;
}

.record__name {
  flex: 1;
  display: -webkit-box;
  overflow: hidden;
  font-family: var(--font-serif);
  font-size: var(--record-name-size);
  /* 标题「中等偏粗但不重」——衬线不用 bold，避免厚重压迫感 */
  font-weight: var(--weight-medium);
  line-height: 1.3;
  color: var(--text-title);
  text-align: left;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

/* 价格：紧随菜名右侧同行，字号与字重低于菜名一档（弱化的数据文本） */
.record__price {
  flex: none;
  font-family: var(--font-sans);
  font-size: var(--font-h3);
  font-weight: var(--weight-medium);
  line-height: 1.3;
  color: var(--text-tertiary);
}

/* 图片槽：吃掉菜名行以下、位置行以上的剩余空间，圆图在其中水平 + 竖直居中（与菜名块之间不留间距） */
.record__photoWrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
}

/* 圆形菜品图：**取剩余高度、封顶 --record-photo-size**（空间越足图越大，菜名折两行时等比收缩，
   永不挤压变形）；宽度由 1:1 派生，卡内更窄时由 100% 让位 */
.record__photo {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  max-height: var(--record-photo-size);
  max-width: 100%;
  overflow: hidden;
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
  aspect-ratio: 1;
}

.record__img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-circle);
}

/* 位置文字：无衬线正文级字号弱化行（左下角，低于菜名一档） */
.record__placeText {
  flex: none;
  align-self: flex-start;
  max-width: 100%;
  font-family: var(--font-sans);
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  line-height: 1.3;
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 右下角探头 = 状态角标：单图标、低透明度弱装饰外观、**正立角度**；卡片 overflow:hidden 裁切
   ⇒ 偏移 -56rpx、仅右下角局部探入可见；露出部分即可点（进入原位评价态） */
.record__badge {
  position: absolute;
  right: -56rpx;
  bottom: -56rpx;
  z-index: 2; /* 高于卡片表面与内容，低于评价面板（3） */
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--record-badge-size);
  height: var(--record-badge-size);
  opacity: 0.25;
  pointer-events: auto;
}

.record__badge--pressed {
  opacity: 0.55;
}

/* 评价激活态 B：覆盖卡片的面板（卡片底色，非白底弹窗）—— 必须绝对定位覆盖，
   否则会被卡片 overflow:hidden 裁在可视区外（点了评价看不到选项） */
.record__eval {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl) var(--spacing-xl) var(--spacing-xl);
  background: var(--bg-card);
  border-radius: inherit;
}

.record__evalInner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-lg);
  width: 100%;
}

.record__evalTip {
  font-family: var(--font-serif);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
}

.record__evalOpts {
  display: flex;
  justify-content: space-around;
  width: 100%;
}

.record__evalOpt {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2xs);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-card);
  transition: background-color var(--duration-base) var(--ease-out);
}

.record__evalOpt.is-active {
  background: var(--eval-active-bg);
}

.record__evalOpt--pressed {
  opacity: 0.7;
}

.record__evalLabel {
  font-size: var(--font-small);
  color: var(--text-body);
}

/* 进入 / 退出：面板淡入淡出（--duration-base） */
.eval-fade-enter-active,
.eval-fade-leave-active {
  transition: opacity var(--duration-base) var(--ease-out);
}
.eval-fade-enter-from,
.eval-fade-leave-to {
  opacity: 0;
}

</style>

