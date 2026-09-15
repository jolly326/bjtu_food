/** 食堂信息（最小 DTO：消费方仅读 id/name） */
export interface CanteenInfo {
  /** 食堂 ID（过滤菜品用；后端 /canteens 返回 id，此前被丢弃，现补齐） */
  id?: number
  name: string
}

/**
 * 食堂树中的档口节点（GET /canteens/all）。
 * 反馈页位置选择器只消费 `name`（两级联动：食堂 → 档口），故仅定型到实际读取的最小字段集；
 * 其余后端字段不透传、不建模（P2-11 / PR-12：跨端 DTO 须显式定型，禁止 `any` 逃逸）。
 */
export interface StallNode {
  id?: number
  name: string
}

/**
 * 食堂含档口树（GET /canteens/all）：食堂 → stalls[]。
 * 反馈页「推荐菜品」位置选择用它做食堂 / 档口两级联动，唯一消费方为
 * `pages/me/feedback/useFeedback.ts`。
 */
export interface CanteenWithStalls {
  id?: number
  name: string
  stalls?: StallNode[]
}
