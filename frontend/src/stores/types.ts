/**
 * 数据接口定义 —— 集中管理所有 API 相关类型
 *
 * 注意：核心业务类型（Dish, Review, UserInfo 等）仍在 @/types/ 下
 * 此文件仅存放从 api/ 文件中迁移过来的接口类型
 */

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
