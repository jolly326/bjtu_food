/**
 * 学生端图标资产映射（Streamline Plump · Solid，CC BY 4.0）。
 *
 * 资源为**中性棕实心 SVG**（`fill: #8C7B6B`、无描边）落盘，微信 `<image>` 直接渲染（小程序 `<image>` 不支持 `currentColor`，故颜色写死）；唯 `heart-on` 为**珊瑚红 `#e8604c` 实心**（点赞激活态，与运营端 `--el-color-danger` 同源）—— 两态形状一致、靠颜色区分。
 * 键名与 `src/assets/icons/<key>.svg` 一致；只引入学生端实际消费的键。
 * 署名：Icons by Streamline (https://streamlinehq.com), licensed under CC BY 4.0.
 */
import check from '@/assets/icons/check.svg'
import chevron from '@/assets/icons/chevron.svg'
import heart from '@/assets/icons/heart.svg'
import heartOn from '@/assets/icons/heart-on.svg'
import help from '@/assets/icons/help.svg'
import image from '@/assets/icons/image.svg'
import loading from '@/assets/icons/loading.svg'
import warning from '@/assets/icons/warning.svg'

/** 图标键 -> 打包器资产地址 */
export const ICONS = {
  check,
  chevron,
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
