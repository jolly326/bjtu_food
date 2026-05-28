/** 首页横幅轮播项 */
export interface BannerItem {
  title: string
  subtitle: string
  image: string
}

/** 食堂信息 */
export interface CanteenInfo {
  name: string
  location: string
  icon: string
}

/** 档口详情 */
export interface StallDetail {
  name: string
  images: string[]
  location: string
  description: string
}
