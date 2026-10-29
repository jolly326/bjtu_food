/**
 * 学生端图标资产映射（Streamline Plump · Flat，CC BY 4.0）。
 *
 * 资源为**单色棕描边 SVG**（显式 `stroke`），微信 `<image>` 可直接渲染，无需按色调烘焙。
 * 键名与 `src/assets/icons/<key>.svg` 一致；只引入学生端实际消费的键，避免把运营端图标打进主包。
 * 署名：Icons by Streamline (https://streamlinehq.com), licensed under CC BY 4.0.
 */
import arrowsClockwise from '@/assets/icons/arrows-clockwise.svg'
import heart from '@/assets/icons/heart.svg'
import heartFill from '@/assets/icons/heart-fill.svg'
import imageBroken from '@/assets/icons/image-broken.svg'
import question from '@/assets/icons/question.svg'
import warningCircle from '@/assets/icons/warning-circle.svg'

/** 图标键 -> 打包器资产地址 */
export const ICONS = {
  'arrows-clockwise': arrowsClockwise,
  heart,
  'heart-fill': heartFill,
  'image-broken': imageBroken,
  question,
  'warning-circle': warningCircle,
} as const

export type IconKey = keyof typeof ICONS

/** 取图标资产地址；未知键回退 question，避免空白 */
export function iconSrc(key: string): string {
  return ICONS[key as IconKey] ?? ICONS.question
}
