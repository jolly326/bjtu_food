/** 食堂信息 */
export interface CanteenInfo {
  name: string
  location: string
  icon: string
  /** 食堂坐标（GCJ-02）；前端本地 Haversine 算「距你 Xm」用，服务器不再计算距离 */
  latitude?: number
  /** 食堂经度（GCJ-02） */
  longitude?: number
  /** 距用户距离（米）：由前端基于 locationStore 用户坐标 + Haversine 本地计算，未定位/无坐标时为 undefined */
  distance?: number
}


