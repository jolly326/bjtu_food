<script setup lang="ts">
/**
 * 三态评分控件（规格见 docs/ui/client/公共组件与形态基线.md §2.2 ReactionPicker）。
 *
 * 记忆卡右下角的**纯线性表情图标** —— 🔴 无圆形底、无描边外框、无填充色块；
 * **只改线条颜色与人脸轮廓**，不加任何外框/底色：
 * - 未评价 ⇒ `meh` 平脸 + `tone="muted"`（浅灰）
 * - 喜欢(5) ⇒ `smile` 笑脸 + `tone="primary"`（暖棕）
 * - 一般(3) ⇒ `meh` 平脸 + `tone="default"`（浅棕）
 * - 不喜欢(1) ⇒ `frown` 低眉脸 + `tone="muted"`（浅灰）
 * - **tap 循环**：未评价 → 喜欢 → 一般 → 不喜欢 → 未评价（`pick(null)` = 清除评价）
 * - 取值：喜欢 = 5 ｜ 一般 = 3 ｜ 不喜欢 = 1 ｜ null = 未评价（口径见 func/client/B2）
 * - `locked`（写入口限频退避中）时挂禁用态
 */
import { computed } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'

/** 宿主节点虚拟化：组件根直接参与记忆卡布局 */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    /** 当前评分档位：5 喜欢 ｜ 3 一般 ｜ 1 不喜欢 ｜ null 未评价 */
    modelValue?: number | null
    /** 写入口限频退避中：禁用（docs/secur/S-03） */
    locked?: boolean
  }>(),
  { modelValue: null, locked: false },
)

const emit = defineEmits<{ (e: 'pick', value: number | null): void }>()

/** 四步循环：未评价 → 喜欢 → 一般 → 不喜欢 → 未评价 */
const nextValue = computed<number | null>(() => {
  if (props.modelValue === 5) return 3
  if (props.modelValue === 3) return 1
  if (props.modelValue === 1) return null
  return 5
})

/** 三态图标表（字符串字面量 —— `scripts/gen-icons.mjs` 按含 `icon` 的行静态识别并烘焙变体） */
const REACTION_ICON = { like: 'smile', neutral: 'meh', bad: 'frown' } as const

/** 表情轮廓：喜欢笑脸 / 不喜欢低眉脸 / 未评价与一般同为平脸 */
const iconName = computed(() => {
  if (props.modelValue === 5) return REACTION_ICON.like
  if (props.modelValue === 1) return REACTION_ICON.bad
  return REACTION_ICON.neutral
})

/** 色调：喜欢暖棕；一般浅棕（default）；未评价与不喜欢浅灰（muted） */
const iconTone = computed(() => {
  if (props.modelValue === 5) return 'primary'
  if (props.modelValue === 3) return 'default'
  return 'muted'
})

function onTap() {
  if (props.locked) return
  emit('pick', nextValue.value)
}
</script>

<template>
  <view
    class="reaction"
    :class="{ 'is-disabled': locked }"
    hover-class="reaction--pressed"
    @tap="onTap"
  >
    <AppIcon :name="iconName" :size="40" :tone="iconTone" />
  </view>
</template>

<style lang="scss" scoped>
/* 纯线性图标按钮：无圆形底、无描边、无填充（命中区仍满足触达下限） */
.reaction {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--tap-target-size);
  height: var(--tap-target-size);
}

.reaction--pressed {
  opacity: 0.6;
}
</style>
