<template>
  <Pressable
    class="app-btn"
    :class="[btnType, { disabled, loading }]"
    :style="btnStyle"
    :aria-label="text"
    @tap="handleTap"
  >
    <IconSvg v-if="icon" :name="icon" :size="30" color="var(--color-on-primary)" class="btn-icon" />
    <text class="btn-text">{{ text }}</text>
  </Pressable>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconSvg from './IconSvg.vue'
import Pressable from './Pressable.vue'

const props = withDefaults(defineProps<{
  text: string
  icon?: string
  type?: 'primary' | 'danger' | 'outline'
  disabled?: boolean
  loading?: boolean
  width?: string
  margin?: string
}>(), {
  type: 'primary',
  disabled: false,
  loading: false,
  width: '100%',
  margin: '0',
  icon: '',
})

// 自定义事件禁用原生事件名（tap/click）：否则 uni-app 编译 mp-weixin 时父组件
// 监听被当作原生 bindxxx，emit 参数会丢失（同 MomentCard/DishCard 坑，见其注释）。
const emit = defineEmits<{
  press: []
}>()

// icon 为 IconSvg 矢量图标名（通过 btnIcon slot 或文本渲染），全量禁 emoji（红线 §4.9③）。
const btnType = computed(() => `btn-${props.type}`)

const btnStyle = computed(() => ({
  width: props.width,
  margin: props.margin,
}))

function handleTap() {
  if (props.disabled || props.loading) return
  emit('press')
}
</script>

<style scoped>
.app-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  border-radius: var(--radius-btn);
  box-sizing: border-box;
  gap: var(--spacing-xs);
}
.btn-icon {
  flex-shrink: 0;
  margin-right: var(--spacing-xs);
}
.app-btn.disabled {
  opacity: 0.4;
}
.btn-text {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-medium);
  color: var(--color-on-primary);
}
.btn-primary {
  background: var(--color-primary);
}
.btn-danger {
  background: var(--color-error);
}
.btn-outline {
  background: transparent;
  border: 2rpx solid var(--color-primary);
}
.btn-outline .btn-text {
  color: var(--color-primary);
}
.app-btn.loading {
  opacity: 0.6;
  pointer-events: none;
}
</style>
