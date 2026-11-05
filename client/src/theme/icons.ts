/**
 * 学生端图标资产映射（Streamline Plump · Solid，CC BY 4.0）。
 *
 * 资源为**中性灰实心 SVG**（`fill: #6B7280`、无描边）落盘，微信 `<image>` 直接渲染
 * （小程序 `<image>` 不支持 `currentColor`，故颜色写死）；深浅主题的着色差异由
 * `--icon-filter`（`theme/design-tokens.css`）统一承载，主色实底上由 `AppIcon` 的 `inverse` 反白。
 * 键名与 `src/assets/icons/<key>.svg` 一致；只引入学生端实际消费的键。
 * 署名：Icons by Streamline (https://streamlinehq.com), licensed under CC BY 4.0.
 */
import help from '@/assets/icons/help.svg'
import image from '@/assets/icons/image.svg'
import loading from '@/assets/icons/loading.svg'
import moon from '@/assets/icons/moon.svg'
import search from '@/assets/icons/search.svg'
import sun from '@/assets/icons/sun.svg'
import warning from '@/assets/icons/warning.svg'

/** 图标键 -> 打包器资产地址 */
export const ICONS = {
  help,
  image,
  loading,
  moon,
  search,
  sun,
  warning,
} as const

export type IconKey = keyof typeof ICONS

/** 未知图标键的回退目标 */
export const FALLBACK_ICON_KEY = 'help' as const

/** 取图标资产地址；未知键回退 help，避免空白 */
export function iconSrc(key: string): string {
  return ICONS[key as IconKey] ?? ICONS[FALLBACK_ICON_KEY]
}
