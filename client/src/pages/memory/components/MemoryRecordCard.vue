<script setup lang="ts">
/**
 * 记忆卡片（设计稿 docs/ui/client/记忆.md；交互状态机见记忆.md·全局互斥铁则）。
 *
 * 竖版卡片（高 > 宽，600×750）。两互斥状态：
 *  - 浏览态 A：菜名(左上) / 圆形菜品图(居中) / 位置·价格(左下) / 右下「探出式」笑脸挂角（卡片 overflow:hidden 裁切，本体大半落卡外被裁掉、仅角部露出含笑脸）；
 *  - 评价激活态 B：原位面板（卡片底色，容器不变形），「为这道菜打分」+ 三档线性表情（一般/不错/很棒）。
 *
 * 笑脸挂角 ≠ 普通按钮：卡片 overflow:hidden 裁切，本体大半落卡外被裁掉、仅右下角局部探入卡片可见（含笑脸中心），像挂在角上的小吉祥物；
 * 交互命中区即露出部分。点挂角 → 进入 B；B 内点表情提交并退出 B、挂角标记已评；B 内点空白退出 B 不保存。
 * 卡片长按（仅 A）→ 唤起长按菜单。B 激活时长按无效。
 * 评分映射（仅换皮，后端 B2 不变）：很棒=laugh(5) / 不错=smile(3) / 一般=meh(1)（原「不喜欢(1)」界面改名为「一般」）。
 */
import { computed } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import { useMemoryUI } from './useMemoryUI'
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

    <!-- 浏览态 A：菜名(顶) / 圆形菜品图(居中) / 位置·价格(底) -->
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
          <ImagePlaceholder v-else :size="40" bare />
        </view>
      </view>
      <text v-if="meta" class="record__meta">{{ meta }}</text>
    </view>

    <!-- 右下：探出式笑脸挂角（仅 icon 本体、放大 ~4×、rotate(-45deg)；卡片 overflow:hidden 裁切，
         大半圆体落卡外被裁、仅角部探入可见；不论是否评价皆 muted 灰、点按可二次评价；评价态隐藏） -->
    <view
      v-show="!isEvaluating"
      class="record__badge"
      hover-class="record__badge--pressed"
      role="button"
      aria-label="对该菜品进行评价"
      @tap.stop="onBadgeTap"
    >
      <AppIcon name="face" :size="176" tone="muted" />
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
              <AppIcon name="meh" :size="52" tone="muted" />
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
  z-index: 0; /* 建立层叠上下文：挂角 / 评价面板层级收纳于此 */
  box-sizing: border-box;
  width: var(--record-card-width);
  height: var(--record-card-height);
  border-radius: var(--radius-card);
  /* 溢出隐藏：卡片裁切挂角——本体 130rpx 锚定右下角外，大半落卡外被裁掉、
     仅右下角局部（含笑脸中心）探入卡片可见，且不受相邻卡遮挡、始终一致 */
  overflow: hidden;
  box-shadow: var(--shadow-card-stack);
}

/* 不透明卡片表面：遮挡挂角重叠区，制造「卡片压住笑脸」的探出效果 */
.record__surface {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: var(--bg-page);
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
  padding: var(--spacing-2xl) var(--spacing-xl) var(--spacing-xl);
}

.record__name {
  flex: none;
  display: -webkit-box;
  overflow: hidden;
  font-family: var(--font-serif);
  font-size: var(--font-h3);
  font-weight: var(--weight-bold);
  line-height: 1.3;
  color: var(--text-title);
  text-align: left;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

/* 中部：圆形菜品图（视觉主体），上下留白均等、水平居中 */
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
  flex: none;
  margin-top: var(--spacing-lg);
  font-family: var(--font-serif);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  white-space: nowrap;
}

/* 探出式笑脸挂角：大半在卡片之后（z-index:-1，被表面遮挡），仅右下角头部露出 */
.record__badge {
  position: absolute;
  right: -56rpx;
  bottom: -56rpx;
  z-index: 2; /* 高于卡片表面与内容，低于评价面板（3） */
  display: flex;
  align-items: center;
  justify-content: center;
  width: 176rpx;
  height: 176rpx;
  transform: rotate(-45deg);
  pointer-events: auto;
}

.record__badge--pressed {
  opacity: 0.85;
}

/* 骨架态：挂角置于表面之上以可见 */
.record__badge--ph {
  right: -48rpx;
  bottom: -48rpx;
  width: 96rpx;
  height: 96rpx;
  background: var(--bg-placeholder);
  border-radius: 50%;
}

/* 评价激活态 B：覆盖卡片的面板（卡片底色，非白底弹窗） */
.record__eval {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl) var(--spacing-xl) var(--spacing-xl);
  background: var(--bg-page);
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
  background: rgba(180, 83, 26, 0.12);
}

.record__evalOpt--pressed {
  opacity: 0.7;
}

.record__evalLabel {
  font-size: var(--font-small);
  color: var(--text-body);
}

/* 进入 / 退出：面板淡入淡出 200ms ease-out */
.eval-fade-enter-active,
.eval-fade-leave-active {
  transition: opacity 0.2s var(--ease-out);
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
  height: var(--font-h3);
}
.record__phLine--meta {
  width: 40%;
  height: var(--font-small);
}
</style>
