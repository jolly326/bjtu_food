/**
 * 二级页统一返回：优先 navigateBack 保留来源栈（搜索→详情→返回搜索、社区→详情→返回社区），
 * 仅当无上一页（分享直达/扫码直达等冷启动场景）时 reLaunch 首页兜底，避免返回死路。
 */
export function backToHome() {
  if (getCurrentPages().length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: '/pages/home/index' })
  }
}
