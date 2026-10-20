<script setup lang="ts">
/**
 * 记忆卡（规格 docs/ui/client/记忆.md）。竖版 480×540，两互斥状态：
 * - 浏览态 A：菜名 / 圆形图 / 左下「食堂·档口·价格」/ 右下探出状态角标（已评对勾、未评圆点）。
 * - 评价态 B：卡片底色面板原位覆盖，三档线性表情；点表情提交并退出，点空白不保存。
 * 入口：点角标 或 长按菜单「评价」（互斥由 useMemoryUI 保证）。
 */
import { computed } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import { useMemoryUI } from './useMemoryUI'
import { recordMetaLine } from '@/utils/recordMeta'
import type { RecordOut } from '@/api'

/** 宿主节点虚拟化：组件根直接参与卡片行布局（flex 子节点即 .record） */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    record?: RecordOut
    /** 辅助行文案（父级一次算好；缺省时组件按同一实现兜底） */
    meta?: string
  }>(),
  { meta: '' },
)

const emit = defineEmits<{ (e: 'react', value: number): void }>()

const { evaluatingId, openEval, closeEval, openMenu } = useMemoryUI()

const isEvaluating = computed(() => evaluatingId.value === props.record?.id)
const isRated = computed(() => props.record?.reaction != null)

/** 右下角状态图标：已评 = 圆圈对勾，未评 = 圆圈点（同一形态、同一弱装饰样式） */
const badgeIcon = computed(() => (isRated.value ? 'circle-check' : 'circle-dot'))

/** 辅助行：优先用 prop meta，缺省时按同一实现从 record 兜底（`食堂 · 档口 · N元`） */
const renderMeta = computed<string>(() =>
  props.meta || (props.record ? recordMetaLine(props.record) : ''),
)

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

    <!-- 浏览态 A：菜名(顶) / 圆形菜品图(居中) / 位置·价格(底部右对齐) -->
    <view class="record__content">
      <text class="record__name wrap-anywhere">{{ record?.name }}</text>
      <view class="record__photoWrap">
        <view class="record__photo">
          <image
            v-if="record?.imageUrl"
            :src="record?.imageUrl"
            class="record__img"
            mode="aspectFill"
          />
          <ImagePlaceholder v-else :size="64" />
        </view>
      </view>
      <text
        v-if="renderMeta"
        class="record__meta"
      >
        {{ renderMeta }}
      </text>
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
  /* 卡片内距：上下 40rpx / 左右 32rpx —— 文字与图片四周留足呼吸位，不贴卡缘 */
  padding: var(--spacing-xl) var(--page-gutter) var(--spacing-xl);
}

.record__name {
  flex: none;
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

.record__photoWrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  margin-top: var(--spacing-lg);
}

.record__photo {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 固定正方形（不做百分比高度 / 1:1 派生 —— 小程序容器高度不确定，两者都会塌成 0 或被裁）；
     尺寸封顶须保证「菜名折两行 + 位置·价格行」仍能放进 540rpx 卡高 */
  width: var(--record-photo-size);
  height: var(--record-photo-size);
  overflow: hidden;
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

.record__img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-circle);
}

/* 位置 · 价格：卡片左下角、单行、无衬线小字浅灰（对标参考图「30m cook time」的弱化辅助行） */
.record__meta {
  align-self: flex-start;
  font-family: var(--font-sans);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  letter-spacing: 0.02em;
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

