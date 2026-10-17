/**
 * 由 `scripts/gen-icons.mjs` 生成 —— **请勿手改**。
 * 源资源与获取路径见 docs/ui/client/图标资源.md。
 */
import arrow_down__default from '@/assets/icon-variants/arrow-down--default.svg'
import arrow_down__primary from '@/assets/icon-variants/arrow-down--primary.svg'
import arrow_down__muted from '@/assets/icon-variants/arrow-down--muted.svg'
import book__default from '@/assets/icon-variants/book--default.svg'
import book__primary from '@/assets/icon-variants/book--primary.svg'
import book__muted from '@/assets/icon-variants/book--muted.svg'
import check__default from '@/assets/icon-variants/check--default.svg'
import check__primary from '@/assets/icon-variants/check--primary.svg'
import check__muted from '@/assets/icon-variants/check--muted.svg'
import clock__default from '@/assets/icon-variants/clock--default.svg'
import clock__primary from '@/assets/icon-variants/clock--primary.svg'
import clock__muted from '@/assets/icon-variants/clock--muted.svg'
import compass__default from '@/assets/icon-variants/compass--default.svg'
import compass__primary from '@/assets/icon-variants/compass--primary.svg'
import compass__muted from '@/assets/icon-variants/compass--muted.svg'
import dish__default from '@/assets/icon-variants/dish--default.svg'
import dish__primary from '@/assets/icon-variants/dish--primary.svg'
import dish__muted from '@/assets/icon-variants/dish--muted.svg'
import empty__default from '@/assets/icon-variants/empty--default.svg'
import empty__primary from '@/assets/icon-variants/empty--primary.svg'
import empty__muted from '@/assets/icon-variants/empty--muted.svg'
import frown__default from '@/assets/icon-variants/frown--default.svg'
import frown__primary from '@/assets/icon-variants/frown--primary.svg'
import frown__muted from '@/assets/icon-variants/frown--muted.svg'
import image_broken__default from '@/assets/icon-variants/image-broken--default.svg'
import image_broken__primary from '@/assets/icon-variants/image-broken--primary.svg'
import image_broken__muted from '@/assets/icon-variants/image-broken--muted.svg'
import meh__default from '@/assets/icon-variants/meh--default.svg'
import meh__primary from '@/assets/icon-variants/meh--primary.svg'
import meh__muted from '@/assets/icon-variants/meh--muted.svg'
import refresh_cw__default from '@/assets/icon-variants/refresh-cw--default.svg'
import refresh_cw__primary from '@/assets/icon-variants/refresh-cw--primary.svg'
import refresh_cw__muted from '@/assets/icon-variants/refresh-cw--muted.svg'
import report__default from '@/assets/icon-variants/report--default.svg'
import report__primary from '@/assets/icon-variants/report--primary.svg'
import report__muted from '@/assets/icon-variants/report--muted.svg'
import shuffle__default from '@/assets/icon-variants/shuffle--default.svg'
import shuffle__primary from '@/assets/icon-variants/shuffle--primary.svg'
import shuffle__muted from '@/assets/icon-variants/shuffle--muted.svg'
import smile__default from '@/assets/icon-variants/smile--default.svg'
import smile__primary from '@/assets/icon-variants/smile--primary.svg'
import smile__muted from '@/assets/icon-variants/smile--muted.svg'

export const ICON_TONES = {
  "default": "#7F6A55",
  "primary": "#B4531A",
  "muted": "#B5A594"
} as const

export type IconTone = keyof typeof ICON_TONES

/** 着色变体 -> 打包器资产地址（dev / build 均由模块图产出） */
export const ICON_SRC: Record<string, string> = {
  "arrow-down--default": arrow_down__default,
  "arrow-down--primary": arrow_down__primary,
  "arrow-down--muted": arrow_down__muted,
  "book--default": book__default,
  "book--primary": book__primary,
  "book--muted": book__muted,
  "check--default": check__default,
  "check--primary": check__primary,
  "check--muted": check__muted,
  "clock--default": clock__default,
  "clock--primary": clock__primary,
  "clock--muted": clock__muted,
  "compass--default": compass__default,
  "compass--primary": compass__primary,
  "compass--muted": compass__muted,
  "dish--default": dish__default,
  "dish--primary": dish__primary,
  "dish--muted": dish__muted,
  "empty--default": empty__default,
  "empty--primary": empty__primary,
  "empty--muted": empty__muted,
  "frown--default": frown__default,
  "frown--primary": frown__primary,
  "frown--muted": frown__muted,
  "image-broken--default": image_broken__default,
  "image-broken--primary": image_broken__primary,
  "image-broken--muted": image_broken__muted,
  "meh--default": meh__default,
  "meh--primary": meh__primary,
  "meh--muted": meh__muted,
  "refresh-cw--default": refresh_cw__default,
  "refresh-cw--primary": refresh_cw__primary,
  "refresh-cw--muted": refresh_cw__muted,
  "report--default": report__default,
  "report--primary": report__primary,
  "report--muted": report__muted,
  "shuffle--default": shuffle__default,
  "shuffle--primary": shuffle__primary,
  "shuffle--muted": shuffle__muted,
  "smile--default": smile__default,
  "smile--primary": smile__primary,
  "smile--muted": smile__muted,
}

/** 本次被源码引用并生成着色变体的图标键（+ empty 兜底） */
export const ICON_KEYS = [
  "arrow-down",
  "book",
  "check",
  "clock",
  "compass",
  "dish",
  "empty",
  "frown",
  "image-broken",
  "meh",
  "refresh-cw",
  "report",
  "shuffle",
  "smile",
] as const

export type IconKey = (typeof ICON_KEYS)[number]

/** 取图标资产地址；未知键回退 empty */
export function iconSrc(key: string, tone: IconTone): string {
  const safe = (ICON_KEYS as readonly string[]).includes(key) ? key : 'empty'
  return ICON_SRC[`${safe}--${tone}`]
}
