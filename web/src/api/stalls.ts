import { get, post, put, del } from './http'
import type { StallAdminVO, StallSaveReq } from '@/types/common'

/** A2 档口列表（可选 `canteenId` 筛选；按「食堂 → 档口名」升序；**不分页**） */
export function listStalls(canteenId?: number): Promise<StallAdminVO[]> {
  return get<StallAdminVO[]>('/admin/stalls', { canteenId })
}

/** A2 新增（`canteenId` 必填且须存在；同食堂下重名 / 楼层不在字典 → 400） */
export function createStall(req: StallSaveReq): Promise<StallAdminVO> {
  return post<StallAdminVO>('/admin/stalls', req)
}

/**
 * A2 修改。
 *
 * <p>`canteenId` / `name` 必填整体替换；`floor` / `windowNo` / `location` / `description` /
 * `images` / `sortOrder` 缺省 = 保持原值（空串 / 空数组 = 清空）。
 */
export function updateStall(id: number, req: StallSaveReq): Promise<null> {
  return put<null>(`/admin/stalls/${id}`, req)
}

/** A2 删除（**其下仍有菜品 → 400**） */
export function deleteStall(id: number): Promise<null> {
  return del<null>(`/admin/stalls/${id}`)
}
