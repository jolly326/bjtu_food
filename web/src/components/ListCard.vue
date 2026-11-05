<script setup lang="ts">
/**
 * 列表卡片（运营端唯一卡片形态）。
 * 结构只有两层：卡片本体 → 内容区；标题 / 状态 / 元信息 / 正文 / 操作按层级平铺。
 * 卡片不承载日期、编号等运营不需要的字段。
 */
import AppIcon from '@/components/AppIcon.vue'

type Props = {
  title: string
  /** 是否展示 16:9 封面区（反馈卡无封面） */
  cover?: boolean
  imageUrl?: string | null
  /** 无图占位图标，默认 image */
  coverIcon?: string
  /** 标题下方的单行元信息（价格 / 计数 / 归属等合并为一行） */
  meta?: string
  dim?: boolean
  /** 可点卡片：进编辑弹窗，支持键盘 Enter / 空格 */
  interactive?: boolean
  ariaLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  cover: false,
  imageUrl: '',
  coverIcon: 'image',
  meta: '',
  dim: false,
  interactive: false,
  ariaLabel: '',
})

const emit = defineEmits<{ (e: 'activate'): void }>()

function onActivate() {
  if (props.interactive) emit('activate')
}
</script>

<template>
  <div
    class="list-card"
    :class="{ 'list-card--dim': dim, 'list-card--interactive': interactive }"
    :role="interactive ? 'button' : undefined"
    :tabindex="interactive ? 0 : undefined"
    :aria-label="ariaLabel || undefined"
    @click="onActivate"
    @keydown.enter.prevent="onActivate"
    @keydown.space.prevent="onActivate"
  >
    <template v-if="cover">
      <img
        v-if="imageUrl"
        v-zoomimg
        :src="imageUrl"
        class="list-card__cover"
        :alt="title"
        loading="lazy"
      />
      <span v-else class="list-card__cover list-card__cover--ph" aria-hidden="true">
        <AppIcon :name="coverIcon" :size="28" />
      </span>
    </template>

    <div class="list-card__body">
      <div class="list-card__head">
        <h3 class="list-card__title">{{ title }}</h3>
        <slot name="badge" />
      </div>
      <p v-if="meta" class="list-card__meta">{{ meta }}</p>
      <slot />
      <slot name="actions" />
    </div>
  </div>
</template>
