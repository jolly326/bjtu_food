<script setup lang="ts">
/**
 * 反馈表单（产品 / 菜品两类反馈共用，docs/ui/client/菜品首页.md `FeedbackForm`）。
 *
 * 容器即表单本体：标题、关闭、输入、提交全部同级，不设额外包裹层。
 * `type` 决定提交负载与标题；`variant` 只决定挂载位置（卡片内 / 页头下缘）。
 */
import { computed, ref } from 'vue'

import { feedbackApi } from '@/api'
import { FEEDBACK } from '@/constants'

defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    type: 'product' | 'dish'
    dishId?: number
    variant?: 'inline' | 'dropdown'
  }>(),
  { variant: 'inline' },
)

const emit = defineEmits<{
  (e: 'close'): void
}>()

const content = ref('')
const submitting = ref(false)
const title = computed(() => (props.type === 'product' ? '产品反馈' : '对这道菜的反馈'))
/** 占位文案按类型引导：产品反馈问使用问题，菜品反馈问信息错误 */
const placeholder = computed(() =>
  props.type === 'product'
    ? '发现 bug 或信息有误？说说具体遇到了什么'
    : '这道菜的图片 / 价格 / 位置有误？说说哪里不对',
)

function onInput(event: Event): void {
  const detail = (event as unknown as { detail?: { value?: string } }).detail
  content.value = detail?.value ?? ''
}

async function submit(): Promise<void> {
  const text = content.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写反馈内容', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await feedbackApi.submit({
      type: props.type,
      dishId: props.type === 'dish' ? props.dishId : undefined,
      content: text,
    })
    uni.showToast({ title: '反馈已收到', icon: 'none' })
    content.value = ''
    emit('close')
  } catch (error) {
    uni.showToast({ title: (error as Error).message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="feedback-form" :class="`feedback-form--${variant}`">
    <text class="feedback-form__title">{{ title }}</text>
    <view
      class="feedback-form__close"
      hover-class="feedback-form__close--press"
      aria-role="button"
      aria-label="关闭反馈"
      @tap="emit('close')"
    >关闭</view>
    <textarea
      class="feedback-form__input"
      :value="content"
      :maxlength="FEEDBACK.MAX_LEN"
      :placeholder="placeholder"
      placeholder-class="feedback-form__ph"
      @input="onInput"
    />
    <view
      class="feedback-form__submit"
      hover-class="feedback-form__submit--press"
      :class="{ 'feedback-form__submit--busy': submitting }"
      aria-role="button"
      aria-label="提交反馈"
      @tap="submit"
    >{{ submitting ? '提交中' : '提交' }}</view>
  </view>
</template>

<style lang="scss" scoped>
/* 单一容器：标题 / 关闭 / 输入 / 提交同级，靠 flex-wrap 排成三行。
   主体规格两类一致；容器形态按宿主适配，卡片内不形成第二层卡片。 */
.feedback-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-sm);
  box-sizing: border-box;
  background: var(--bg-card);
  animation: motion-rise var(--duration-base) var(--ease-out) both;
}

/* 卡片内：只留上分隔线，与信息区左右对齐 */
.feedback-form--inline {
  margin: 0 var(--spacing-sm) var(--spacing-sm);
  padding: var(--spacing-md) 0 0;
  border-top: var(--border-width) solid var(--border-color);
}

/* 表单即面板：以 .header 为定位上下文，页头下缘水平居中 */
.feedback-form--dropdown {
  position: absolute;
  top: 100%;
  right: var(--page-gutter);
  left: var(--page-gutter);
  z-index: var(--z-header);
  padding: var(--spacing-lg);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-card);
}

.feedback-form__title {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-title);
}

.feedback-form__close {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-width: var(--tap-target-size);
  min-height: var(--tap-target-size);
  font-size: var(--font-caption);
  color: var(--text-body);
}

.feedback-form__close--press {
  opacity: 0.6;
}

.feedback-form__input {
  flex: 0 0 100%;
  box-sizing: border-box;
  height: var(--feedback-input-height);
  padding: var(--spacing-sm);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-card);
  font-size: var(--font-body);
  color: var(--text-title);
}

.feedback-form__ph {
  color: var(--text-disabled);
}

/* 提交为幽灵按钮：全页唯一实底主色留给搜索按钮 */
.feedback-form__submit {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: var(--control-height);
  margin-left: auto;
  padding: 0 var(--spacing-xl);
  border: var(--border-width) solid var(--brand);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  font-size: var(--font-body);
  color: var(--brand);
}

.feedback-form__submit--press {
  opacity: 0.7;
}

.feedback-form__submit--busy {
  opacity: 0.6;
}
</style>
