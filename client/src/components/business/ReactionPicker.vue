<script setup lang="ts">
/**
 * 三态评分控件（规格见 docs/ui/client/公共组件与形态基线.md §2.2 ReactionPicker）。
 *
 * 单个圆形表情图标按钮（Julienne 卡片角标形态）：
 * - 显示当前档位 —— 未评分 = `smile` 弱化态；已评分 = 主色描边 + 主色图标；
 * - tap 按 **喜欢 → 一般 → 不喜欢 → 喜欢** 循环切换并派发 `pick(下一档)`；
 * - 取值：喜欢 = 5 ｜ 一般 = 3 ｜ 不喜欢 = 1（口径见 func/client/B2）；
 * - `locked`（写入口限频退避中）时挂禁用态。
 * - 记忆卡三图标中视觉优先级最高：40rpx 大图标（气泡 32 / 垃圾桶 28）。
 */
import { computed } from 'vue'
import AppIcon from '../base/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    /** 限频退避中：不可点 */
    locked?: boolean
  }>(),
  { modelValue: null, locked: false },
)

const emit = defineEmits<{ (e: 'pick', value: number): void }>()

/** 循环顺序：喜欢 → 一般 → 不喜欢 → 喜欢 */
const CYCLE = [5, 3, 1] as const

/** 已评分（选中态） */
const rated = computed(() => props.modelValue != null)

/** 下一档（循环）；未评分从第一档（喜欢）起 */
function next(): number {
  const idx = props.modelValue == null ? -1 : CYCLE.indexOf(props.modelValue as 5 | 3 | 1)
  return CYCLE[(idx + 1) % CYCLE.length]
}

function onTap() {
  if (props.locked) return
  emit('pick', next())
}
</script>

<template>
  <view
    class="picker"
    :class="{ 'picker--on': rated, 'is-disabled': locked }"
    hover-class="picker--pressed"
    @tap="onTap"
  >
    <view class="picker__circle">
      <AppIcon v-if="modelValue === 3" name="meh" :size="40" :tone="rated ? 'primary' : 'default'" />
      <AppIcon
        v-else-if="modelValue === 1"
        name="frown"
        :size="40"
        :tone="rated ? 'primary' : 'default'"
      />
      <AppIcon v-else name="smile" :size="40" :tone="rated ? 'primary' : 'default'" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 命中区 ≥ --tap-target-size；视觉圆小于命中区（触达下限口径见基线 §1.6） */
.picker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--tap-target-size);
  height: var(--tap-target-size);
}

/* 视觉圆：细描边圆环，Julienne 角标形态 */
.picker__circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: calc(var(--tap-target-size) - var(--spacing-md));
  height: calc(var(--tap-target-size) - var(--spacing-md));
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-circle);
  transition: border-color var(--duration-fast) var(--ease-out);
}

/* 已评分：主色描边（不实底填充） */
.picker--on .picker__circle {
  border-color: var(--color-primary);
}

/* 禁用（限频退避）：非主色可点件 ⇒ opacity .5，禁点由全局 .is-disabled 承担 */
.picker.is-disabled {
  opacity: 0.5;
}

.picker--pressed {
  opacity: 0.6;
}
</style>
