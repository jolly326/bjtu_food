/**
 * 统一矢量图标引用（规范：web/src/assets/icons/*.svg）。
 *
 * - 所有功能性图标以 snake-case 命名落地到 web/src/assets/icons/（线性 24×24、currentColor）。
 * - 通过 Vite import.meta.glob 一次性收集，组件用 <img :src="icon.xxx"> 引用。
 * - 图标颜色跟随当前上下文文字色（currentColor），尺寸由 CSS 控制，符合 ui-styling token 规范。
 *
 * 用法：
 *   import { icon } from '@/utils/icon'
 *   <img :src="icon.plus" class="my-icon" alt="" />
 */
const modules = import.meta.glob('@/assets/icons/*.svg', {
  eager: true,
  import: 'default',
}) as Record<string, string>

function buildMap(): Record<string, string> {
  const map: Record<string, string> = {}
  for (const path in modules) {
    const name = path.split('/').pop()!.replace(/\.svg$/, '')
    const url = modules[path]
    if (typeof url === 'string') map[name] = url
  }
  return map
}

/** 统一图标映射：icon.plus / icon.search / icon.delete ... */
export const icon = buildMap()

/** 取指定名称的图标 URL（不存在回退空字符串，便于调用方降级） */
export function getIcon(name: string): string {
  return icon[name] || ''
}
