/**
 * 图片地址组合式函数（docs/ui/client/公共组件与形态基线.md §1.9）。
 *
 * 加载顺序：万象缩略图 → 原图 → 占位（`ImagePlaceholder`）。
 * 缩略图处理参数不可用（或未开启缩略图）时直接回落原图，原图仍失败才交占位。
 */
import { computed, ref, watch } from 'vue'

import { thumbUrl } from '@/utils/image'

type SourceStage = 'thumb' | 'origin' | 'failed'

export function useImageSource(url: () => string | null | undefined, width: number) {
  const stage = ref<SourceStage>('thumb')

  watch(url, () => {
    stage.value = 'thumb'
  })

  const src = computed(() => {
    const raw = url()
    if (!raw || stage.value === 'failed') return ''
    return stage.value === 'thumb' ? thumbUrl(raw, width) : raw
  })

  function onError(): void {
    const raw = url() ?? ''
    stage.value = stage.value === 'thumb' && thumbUrl(raw, width) !== raw ? 'origin' : 'failed'
  }

  return { src, onError }
}
