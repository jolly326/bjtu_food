/**
 * 学生端图标资产映射（Streamline Plump · Flat，CC BY 4.0）。
 *
 * 资源为**黑色轮廓 SVG**（`fill: none` + `stroke: #000000`），微信 `<image>` 可直接渲染。
 * 键名与 `src/assets/icons/<key>.svg` 一致；只引入学生端实际消费的键。
 * 署名：Icons by Streamline (https://streamlinehq.com), licensed under CC BY 4.0.
 */
import heart from '@/assets/icons/heart.svg'
import heartOn from '@/assets/icons/heart-on.svg'
import help from '@/assets/icons/help.svg'
import image from '@/assets/icons/image.svg'
import loading from '@/assets/icons/loading.svg'
import warning from '@/assets/icons/warning.svg'

/** 图标键 -> 打包器资产地址 */
export const ICONS = {
  heart,
  'heart-on': heartOn,
  help,
  image,
  loading,
  warning,
} as const

export type IconKey = keyof typeof ICONS

/** 取图标资产地址；未知键回退 help，避免空白 */
export function iconSrc(key: string): string {
  return ICONS[key as IconKey] ?? ICONS.help
}
