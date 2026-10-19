<script setup lang="ts">
/** 记忆卡片（设计稿 docs/ui/client/记忆.md；交互状态机见记忆.md·全局互斥铁则）。
 *
 * 竖版卡片（高 > 宽，480×540）。两互斥状态：
 *  - 浏览态 A：菜名(左上) / 圆形菜品图(居中) / 位置·价格(底部右对齐)；
 *  - 评价激活态 B：原位面板（卡片底色，容器不变形），「为这道菜打分」+ 三档线性表情。
 *
 * 星芒挂角 = 纯装饰（低透明度、不可交互）：评价入口走长按菜单（见 useMemoryUI / MemoryActionsSheet）。
 * B 由长按菜单「评价」进入；B 内点表情提交并退出 B；B 内点空白退出 B 不保存。
 * 评分映射（仅换皮，后端 B2 不变）：很棒=laugh(5) / 不错=smile(3) / 一般=meh(1)。
 */
import { computed } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import { useMemoryUI } from './useMemoryUI'
import { fenToYuan } from '@/utils/price'
import type { RecordOut } from '@/api'

/** 宿主节点虚拟化：组件根直接参与卡片行布局（flex 子节点即 .record） */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    record?: RecordOut
    meta?: string
    loading?: boolean
  }>(),
  { meta: '', loading: false },
)

const emit = defineEmits<{ (e: 'react', value: number): void }>()

const { evaluatingId, openEval, closeEval, openMenu } = useMemoryUI()

const isEvaluating = computed(() => evaluatingId.value === props.record?.id)

/** 计算显示的元信息：优先用 prop meta，其次用 record 兜底（档口 / 食堂 · 价格） */
const renderMeta = computed<string>(() => {
  if (props.meta) return props.meta
  const { stallName, canteenName, price } = props.record ?? {}
  const place = stallName || canteenName || ''
  const yuan = price && price > 0 ? `${Number(fenToYuan(price))}元` : ''
  if (place && yuan) return `${place} · ${yuan}`
  return place || yuan
})

/** 点右下星芒挂角 → 进入原位评价态（长按菜单「评价」为同一入口的第二条路径） */
function onBadgeTap() {
  if (props.loading || !props.record) return
  openEval(props.record.id)
}

function onCardLongPress() {
  if (props.loading || isEvaluating.value || !props.record) return
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
  <view v-if="loading" class="record record--ph">
    <view class="record__surface" />
    <view class="record__content">
      <view class="record__phLine record__phLine--name" />
      <view class="record__photo" />
      <view class="record__phLine record__phLine--meta" />
    </view>
    <view class="record__badge record__badge--ph" />
  </view>

  <view v-else class="record" @longpress="onCardLongPress">
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

    <!-- 右下：探出式星芒挂角（低透明度弱装饰外观；点按进入原位评价态，评价选项在卡片内展开） -->
    <view
      v-show="!isEvaluating"
      class="record__badge"
      hover-class="record__badge--pressed"
      role="button"
      aria-label="对该菜品进行评价"
      @tap.stop="onBadgeTap"
    >
      <AppIcon name="sparkles" :size="200" tone="muted" />
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
              <AppIcon name="meh" :size="52" tone="default" />
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
              <AppIcon name="smile" :size="52" tone="default" />
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
              <AppIcon name="laugh" :size="52" tone="primary" />
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
  background: var(--bg-card);
  border: 1rpx solid var(--border-color);
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
  padding: var(--spacing-lg) var(--page-gutter) calc(var(--spacing-lg) + var(--spacing-md));
}

.record__name {
  flex: none;
  display: -webkit-box;
  overflow: hidden;
  font-family: var(--font-serif);
  font-size: var(--record-name-size);
  font-weight: var(--weight-bold);
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

.record__meta {
  align-self: flex-end;
  font-family: var(--font-serif);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 右下角探头 = 星芒挂角：单图标、低透明度弱装饰外观；卡片 overflow:hidden 裁切 ⇒ 偏移 -56rpx、
   大半落卡外、仅右下角局部探入可见；露出部分即可点（进入原位评价态） */
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
  transform: rotate(-45deg);
  pointer-events: auto;
}

.record__badge--pressed {
  opacity: 0.55;
}

/* 骨架态：挂角占位 */
.record__badge--ph {
  right: -48rpx;
  bottom: -48rpx;
  width: var(--record-badge-ph-size);
  height: var(--record-badge-ph-size);
  background: var(--bg-placeholder);
  border-radius: 50%;
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

/* 加载骨架 */
.record--ph {
  pointer-events: none;
}
.record__phLine {
  background: var(--bg-placeholder);
  border-radius: var(--radius-xs);
}
.record__phLine--name {
  width: 55%;
  height: var(--record-name-size);
}
.record__phLine--meta {
  width: 40%;
  height: var(--font-small);
}
</style>

