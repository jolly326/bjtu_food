<script setup lang="ts">
/**
 * 运营端图标：与学生端同源的 **Streamline Plump（Flat）单色棕描边 SVG**（`assets/icons`，13 枚，CC BY 4.0；
 * 🔴「零消费即删」—— 新增前先全仓查引用，无消费的不入库，见 docs/ui/web/设计变量.md §2.4）。
 * SVG 使用显式 `stroke` ⇒ 直接注入渲染，无需运行时换色。
 * 图标与尺寸口径见 docs/ui/web/公共组件与形态基线.md。
 */
import { computed, ref, watchEffect } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 图标键（对应 `assets/icons/<name>.svg`，如 plus / sign-out / image-broken） */
    name: string
    /** 边长（px） */
    size?: number
  }>(),
  { size: 16 },
)

/** 按需加载：每个图标独立 chunk，首次使用时才拉取；加载结果缓存 */
const loaders = import.meta.glob('../assets/icons/*.svg', {
  query: '?raw',
  import: 'default',
}) as Record<string, () => Promise<string>>

const cache = new Map<string, string>()
const svg = ref('')
const FALLBACK_KEY = '../assets/icons/question.svg'

async function loadSvg(key: string): Promise<string> {
  const load = loaders[key]
  if (!load) return ''
  try {
    return await load()
  } catch {
    return ''
  }
}

watchEffect(() => {
  const key = `../assets/icons/${props.name}.svg`
  const cached = cache.get(key)
  if (cached !== undefined) {
    svg.value = cached
    return
  }
  svg.value = ''
  void (async () => {
    let text = await loadSvg(key)
    // 加载失败回退到兜底图标，避免导航出现空白
    if (!text) text = await loadSvg(FALLBACK_KEY)
    cache.set(key, text)
    // 加载期间 name 可能已变，仅当仍是当前图标时写入
    if (key === `../assets/icons/${props.name}.svg`) svg.value = text
  })()
})

const box = computed(() => ({ width: `${props.size}px`, height: `${props.size}px` }))
</script>

<template>
  <span class="app-icon" :style="box" aria-hidden="true" v-html="svg" />
</template>

<style scoped>
.app-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  line-height: 0;
}

.app-icon :deep(svg) {
  width: 100%;
  height: 100%;
}
</style>
