/** 食堂信息 */
export interface CanteenInfo {
  name: string
  location: string
  icon: string
}

/** 档口详情 */
export interface StallDetail {
  id?: number
  name: string
  images: string[]
  location: string
  description: string
  /** 平均星级（后端 StallDetailVO.avgRating，BigDecimal，无则 0.00） */
  avgRating?: number
}

/** 档口简讯（GET /stalls 列表项） */
export interface StallInfo {
  id: number
  canteenId?: number
  name: string
  images?: string[]
  location?: string
  description?: string
  avgRating?: number
  status?: 'open' | 'closed'
}

/** 我的档口·食堂 UGC 提交（GET /my/stalls，对齐 StallAdminVO：auditStatus / rejectReason） */
export interface MyPublishStall {
  id: number
  /** 提交类型：stall=档口，canteen=食堂 */
  type: 'stall' | 'canteen'
  name: string
  location?: string
  description?: string
  images?: string[]
  auditStatus: 'pending' | 'approved' | 'rejected'
  rejectReason?: string
  createTime?: string
}
