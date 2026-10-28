/**
 * 由 `scripts/gen-icons.mjs` 生成 —— **请勿手改**。
 * 源资源与获取路径见 docs/ui/client/图标资源.md。
 */
import arrows_clockwise__default from '@/assets/icon-variants/arrows-clockwise--default.svg'
import arrows_clockwise__primary from '@/assets/icon-variants/arrows-clockwise--primary.svg'
import arrows_clockwise__muted from '@/assets/icon-variants/arrows-clockwise--muted.svg'
import arrows_clockwise__default__dark from '@/assets/icon-variants/arrows-clockwise--default--dark.svg'
import arrows_clockwise__primary__dark from '@/assets/icon-variants/arrows-clockwise--primary--dark.svg'
import arrows_clockwise__muted__dark from '@/assets/icon-variants/arrows-clockwise--muted--dark.svg'
import flag__default from '@/assets/icon-variants/flag--default.svg'
import flag__primary from '@/assets/icon-variants/flag--primary.svg'
import flag__muted from '@/assets/icon-variants/flag--muted.svg'
import flag__default__dark from '@/assets/icon-variants/flag--default--dark.svg'
import flag__primary__dark from '@/assets/icon-variants/flag--primary--dark.svg'
import flag__muted__dark from '@/assets/icon-variants/flag--muted--dark.svg'
import image_broken__default from '@/assets/icon-variants/image-broken--default.svg'
import image_broken__primary from '@/assets/icon-variants/image-broken--primary.svg'
import image_broken__muted from '@/assets/icon-variants/image-broken--muted.svg'
import image_broken__default__dark from '@/assets/icon-variants/image-broken--default--dark.svg'
import image_broken__primary__dark from '@/assets/icon-variants/image-broken--primary--dark.svg'
import image_broken__muted__dark from '@/assets/icon-variants/image-broken--muted--dark.svg'
import question__default from '@/assets/icon-variants/question--default.svg'
import question__primary from '@/assets/icon-variants/question--primary.svg'
import question__muted from '@/assets/icon-variants/question--muted.svg'
import question__default__dark from '@/assets/icon-variants/question--default--dark.svg'
import question__primary__dark from '@/assets/icon-variants/question--primary--dark.svg'
import question__muted__dark from '@/assets/icon-variants/question--muted--dark.svg'
import warning_circle__default from '@/assets/icon-variants/warning-circle--default.svg'
import warning_circle__primary from '@/assets/icon-variants/warning-circle--primary.svg'
import warning_circle__muted from '@/assets/icon-variants/warning-circle--muted.svg'
import warning_circle__default__dark from '@/assets/icon-variants/warning-circle--default--dark.svg'
import warning_circle__primary__dark from '@/assets/icon-variants/warning-circle--primary--dark.svg'
import warning_circle__muted__dark from '@/assets/icon-variants/warning-circle--muted--dark.svg'

/** 浅色色调（默认主题） */
export const ICON_TONES = {
  "default": "#8A5A2B",
  "primary": "#FF8A4C",
  "muted": "#C9B49C"
} as const

/** 深色色调（data-theme="dark" 下生效） */
export const ICON_TONES_DARK = {
  "default": "#E8D9C4",
  "primary": "#FFA76B",
  "muted": "#A08D78"
} as const

export type IconTone = keyof typeof ICON_TONES

/** 浅色组：着色变体 -> 打包器资产地址（dev / build 均由模块图产出） */
export const ICON_SRC: Record<string, string> = {
  "arrows-clockwise--default": arrows_clockwise__default,
  "arrows-clockwise--primary": arrows_clockwise__primary,
  "arrows-clockwise--muted": arrows_clockwise__muted,
  "flag--default": flag__default,
  "flag--primary": flag__primary,
  "flag--muted": flag__muted,
  "image-broken--default": image_broken__default,
  "image-broken--primary": image_broken__primary,
  "image-broken--muted": image_broken__muted,
  "question--default": question__default,
  "question--primary": question__primary,
  "question--muted": question__muted,
  "warning-circle--default": warning_circle__default,
  "warning-circle--primary": warning_circle__primary,
  "warning-circle--muted": warning_circle__muted,
}

/** 深色组：着色变体 -> 打包器资产地址 */
export const ICON_SRC_DARK: Record<string, string> = {
  "arrows-clockwise--default--dark": arrows_clockwise__default__dark,
  "arrows-clockwise--primary--dark": arrows_clockwise__primary__dark,
  "arrows-clockwise--muted--dark": arrows_clockwise__muted__dark,
  "flag--default--dark": flag__default__dark,
  "flag--primary--dark": flag__primary__dark,
  "flag--muted--dark": flag__muted__dark,
  "image-broken--default--dark": image_broken__default__dark,
  "image-broken--primary--dark": image_broken__primary__dark,
  "image-broken--muted--dark": image_broken__muted__dark,
  "question--default--dark": question__default__dark,
  "question--primary--dark": question__primary__dark,
  "question--muted--dark": question__muted__dark,
  "warning-circle--default--dark": warning_circle__default__dark,
  "warning-circle--primary--dark": warning_circle__primary__dark,
  "warning-circle--muted--dark": warning_circle__muted__dark,
}

/** 本次被源码引用并生成着色变体的图标键（+ empty 兜底） */
export const ICON_KEYS = [
  "arrows-clockwise",
  "flag",
  "image-broken",
  "question",
  "warning-circle",
] as const

export type IconKey = (typeof ICON_KEYS)[number]

/** 取图标资产地址；未知键回退 empty；dark = 取深色色调组 */
export function iconSrc(key: string, tone: IconTone, dark = false): string {
  const safe = (ICON_KEYS as readonly string[]).includes(key) ? key : 'question'
  const table = dark ? ICON_SRC_DARK : ICON_SRC
  return table[`${safe}--${tone}${dark ? '--dark' : ''}`]
}
