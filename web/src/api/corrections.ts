import { getAdminPage, get, post, put } from './http'
import type {
  AdminPage,
  CorrectionAdminVO,
  CorrectionDetailVO,
  CorrectionListParams,
  DishCorrectionHandleReq,
  ModerationHandleReq,
} from '@/types/common'

/**
 * B4 菜品问题反馈列表（排序 `createdAt DESC`）
 *
 * 支持 `type` 筛选（`field` / `gone` / 不传=全部），管理端据此分 Tab 展示。
 */
export function listCorrections(
  params: CorrectionListParams,
): Promise<AdminPage<CorrectionAdminVO>> {
  return getAdminPage<CorrectionAdminVO>('/admin/corrections', params)
}

/**
 * B4 单条详情 —— **按 `type` 分派**：
 * - `field`：含 `differences[]` 提交项对照（`oldValue` 为实时回查的当前值、`newValue` 为用户提交值、
 *   `same` 标记是否已一致），**只读**、供管理员阅读判断；
 * - `gone`：`differences` 恒空，只有 `note` + `images` + `goneUserCount`。
 */
export function getCorrection(id: number): Promise<CorrectionDetailVO> {
  return get<CorrectionDetailVO>(`/admin/corrections/${id}`)
}

/**
 * B4 标记已处理（接受反馈）：`DishCorrectionHandleReq` 的 `replyTitle` / `replyContent` **均可选**
 * （≤40 / ≤600 字，留空由服务端下发缺省回执文案）。
 *
 * **只归档处置结论（`outcome='handled'`）+ 投递站内回执，不改写任何菜品数据** ——
 * 菜品资料修改 / `gone` 可逆下架由管理员在「菜品管理」手动执行，不在本端点。
 */
export function handleCorrection(id: number, req?: DishCorrectionHandleReq): Promise<null> {
  return post<null>(`/admin/corrections/${id}/handle`, req ?? {})
}

/**
 * B4 驳回（不采纳 / 经核实仍在售）：`ModerationHandleReq` 且 `outcome` 固定 `rejected`；
 * `replyContent` **必填**（≤600 字，纯空白 → 400「请填写不采纳原因」）；`replyTitle` 可选（≤40 字）。
 *
 * 只归档 `outcome='rejected'` + 投递含原因的站内回执，**不改写任何菜品数据**。
 */
export function rejectCorrection(id: number, req: ModerationHandleReq): Promise<null> {
  return put<null>(`/admin/corrections/${id}`, req)
}
