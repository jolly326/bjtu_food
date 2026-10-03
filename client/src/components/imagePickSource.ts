/**
 * 配图来源的共享契约（ImagePicker 与三个宿主页面的唯一真源）。
 *
 * <p><b>为何独立成模块</b>：配图来源弹层必须挂在<b>页面根级</b>（ActionSheet → BaseSheet 内部
 * 是 {@code position: fixed}，而 ImagePicker 在「意见反馈」「菜品纠错」两处位于
 * {@code <scroll-view>} 之内，fixed 层级会被压扁/裁剪）。于是「选来源」与「用来源」
 * 必然分处两个文件，若各自硬编码 key / 图标 / 文案，三处极易漂移
 * （改了一处文案另外两处还留旧字）。此处收敛为单一真源，各处只 import。
 *
 * <p><b>为何不用数组形式</b>：与 {@code wx.chooseMedia} 的 {@code sourceType} 一一对应，
 * 但刻意收敛为<b>单值</b>——来源选定后才传入，避免「同时给两个来源」又让微信弹原生
 * 选择面板，那样就退回成用户看到的单入口，等于没做。
 */

/** 配图来源：拍照 / 相册 */
export type PickSource = 'camera' | 'album'

/** ActionSheet 动作项（结构与 `components/ActionSheet.vue` 的 `ActionSheetItem` 对齐） */
export interface ImagePickAction {
  key: PickSource
  label: string
  icon: string
}

/**
 * 来源弹层的动作项（顺序即展示顺序：拍照在前，符合「现拍」优先的直觉）。
 *
 * <p>文案口径：<b>「拍照」/「从相册选择」</b>——均为微信官方用语，避免「文件夹」
 * 这类易与「聊天/文件」混淆的说法（小程序端相册由系统相册面板承载，非文件系统目录）。
 */
export const IMAGE_PICK_ACTIONS: ImagePickAction[] = [
  { key: 'camera', label: '拍照', icon: 'camera' },
  { key: 'album', label: '从相册选择', icon: 'image' },
]

/** key 是否为受支持的来源（ActionSheet 回抛 key 是 string，需收窄后才敢进 startPick） */
export function isPickSource(key: string): key is PickSource {
  return key === 'camera' || key === 'album'
}