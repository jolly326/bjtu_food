/** 食堂信息 */
export interface CanteenInfo {
  name: string
  location: string
  icon: string
  /** 与用户位置的距离（米，按定位排序时后端返回）；未定位为 undefined */
  distance?: number
}


