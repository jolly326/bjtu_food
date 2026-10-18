<script setup lang="ts">
/**
 * 三态评分控件（规格见 docs/ui/client/公共组件与形态基线.md §2.2 ReactionPicker）。
 *
 * 记忆卡右下角的**纯线性表情图标** —— 🔴 无圆形底、无描边外框、无填充色块；
 * **仅线条颜色随状态变化**：
 * - 未评价 ⇒ `meh` 平脸 + `tone="muted"`（浅灰）
 * - 喜欢(5) ⇒ `smile` 笑脸 + `tone="primary"`（暖棕）
 * - 一般(3) ⇒ `meh` 平脸 + `tone="default"`（浅棕）
 * - 不喜欢(1) ⇒ `frown` 皱眉 + `tone="muted"`（浅灰）
 * - tap 按 **喜欢 → 一般 → 不喜欢 → 喜欢** 循环切换并派发 `pick(下一档)`
 * - 取值：喜欢 = 5 ｜ 一般 = 3 ｜ 不喜欢 = 1（口径见 func/client/B2）
 * - `locked`（写入口限频退避中）时挂禁用态
 */
import { computed } from 'vue'
import AppIcon from '../base/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    /** 当前评分档位：5 喜欢 ｜ 3 一般 ｜ 1 不喜欢 ｜ null 未评价 */
    modelValue?: number | null
    /** 写入口限频退避中：禁用（docs/secur/S-03） */
    locked?: boolean
  }>(),
  { modelValue: null, locked: false },
)

const emit = defineEmits<{ (e: 'pick', value: number): void }>()

/** 三档循环：未评价 → 喜欢 → 一般 → 不喜欢 → 喜欢… */
const nextValue = computed(() => {
  if (props.modelValue === 5) return 3
  if (props.modelValue === 3) return 1
  return 5
})

/** 表情：喜欢笑脸 / 不喜欢皱眉 / 未评价与一般同为平脸 */
const iconName = computed(() => {
  if (props.modelValue === 5) return 'smile'
  if (props.modelValue === 1) return 'frown'
  return 'meh'
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
