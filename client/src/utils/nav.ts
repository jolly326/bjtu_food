/** 二级页统一返回首页（reLaunch 重置栈，避免返回死路） */
export function backToHome() {
  uni.reLaunch({ url: '/pages/home/index' })
}
