/**
 * 配图来源的共享契约（ImagePicker 与各宿主页面的唯一真源）。
 *
 * 配图来源弹层必须挂在**页面根级**（BaseSheet 内部是 `position: fixed`，而 ImagePicker 在
 * `<scroll-view>` 之内时 fixed 层级会被压扁/裁剪），于是「选来源」与「用来源」必然分处两个文件
 * —— 收敛为单一真源，避免三处各自硬编码后文案漂移。
 *
 * ⚠️ 与 `wx.chooseMedia` 的 `sourceType` 一一对应，但刻意收敛为**单值**：来源选定后才传入，
 * 否则微信会弹原生选择面板、退回成用户看到的单入口，等于没做。
 */

/** 配图来源：拍照 / 相册 */
export type PickSource = 'camera' | 'album'

/** ActionSheet 动作项（结构与 `components/ActionSheet.vue` 的 `ActionSheetItem` 对齐） */
interface ImagePickAction {
  key: PickSource
  label: string
  icon: string
}

/**
 * 来源弹层的动作项（顺序即展示顺序：拍照在前，符合「现拍」优先的直觉）。
 *
 * 文案沿用微信官方用语「拍照」/「从相册选择」，避免「文件夹」这类易与聊天/文件混淆的说法。
 */
export const IMAGE_PICK_ACTIONS: ImagePickAction[] = [
  { key: 'camera', label: '拍照', icon: 'camera' },
  { key: 'album', label: '从相册选择', icon: 'image' },
]

/** key 是否为受支持的来源（ActionSheet 回抛 key 是 string，需收窄后才敢进 startPick） */
export function isPickSource(key: string): key is PickSource {
  return key === 'camera' || key === 'album'
}