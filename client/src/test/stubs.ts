/**
 * 组件测试的共享桩（**测试专用，不进运行时**）。
 *
 * <p><b>为何需要</b>：小程序组件的依赖链会摸到 uni 运行时（`BaseSheet` 监听
 * `uni.onKeyboardHeightChange`、`toastInfo` 走 `uni.showToast`、`ImagePicker` 取 `getWxApi`）。
 * happy-dom 里没有 `uni` 全局，直接挂载会抛 `uni is not defined`。
 *
 * <p><b>只桩被真实调用到的 API</b>：不追求「完整模拟小程序」。桩的行为按**契约**写
 * （如 `showToast` 记录调用），使测试能断言「点了提交是否弹了提示」这类交互。
 */

/** `showToast` 等的调用记录，供断言交互副作用 */
export const uniCalls = {
  showToast: [] as Array<{ title: string; icon?: string }>,
  showModal: [] as Array<Record<string, unknown>>,
  previewImage: [] as Array<Record<string, unknown>>,
  chooseImage: [] as unknown[],
  getImageInfo: [] as unknown[],
}

function reset() {
  uniCalls.showToast.length = 0
  uniCalls.showModal.length = 0
  uniCalls.previewImage.length = 0
  uniCalls.chooseImage.length = 0
  uniCalls.getImageInfo.length = 0
}

/** 安装最小 `uni` 全局桩；每个用例前调用 `installUni()` 保证干净状态 */
export function installUni() {
  reset()
  const g = globalThis as Record<string, unknown>
  g.uni = {
    showToast: (o: { title?: string; icon?: string }) => {
      uniCalls.showToast.push({ title: o?.title ?? '', icon: o?.icon })
    },
    hideToast: () => {},
    showModal: (o: Record<string, unknown>) => {
      uniCalls.showModal.push(o)
      // 默认走「确认」分支；需测取消时用 uniCalls.showModal 自行触发
      ;(o?.success as ((r: { confirm: boolean }) => void) | undefined)?.({ confirm: true })
    },
    previewImage: (o: Record<string, unknown>) => {
      uniCalls.previewImage.push(o)
    },
    // BaseSheet 键盘监听（未开启 keyboardAvoid 时不会真正用到，但要存在以免 undefined 报错）
    onKeyboardHeightChange: () => {},
    offKeyboardHeightChange: () => {},
    getSystemInfoSync: () => ({
      statusBarHeight: 20,
      windowWidth: 375,
      windowHeight: 667,
      safeArea: { top: 20, bottom: 667, left: 0, right: 375 },
    }),
    navigateTo: () => {},
    navigateBack: () => {},
    reLaunch: () => {},
    setStorageSync: () => {},
    getStorageSync: () => '',
  }
}

/**
 * 触发小程序 `input` 事件。
 *
 * <p>⚠️ uni 的 `<input>` 事件对象是 `{ detail: { value } }`，**不是**原生 DOM 的
 * `event.target.value`。组件里正是按 `e.detail.value` 取值，故这里必须构造该形状。
 */
export function inputEvent(value: string) {
  return { detail: { value } }
}