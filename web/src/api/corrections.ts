import { getAdminPage, get, post, put } from './http'
import type {
  AdminPage,
  CorrectionAdminVO,
  CorrectionDetailVO,
  CorrectionListParams,
  CorrectionAdoptReq,
  ModerationHandleReq,
  StallConfirmVO,
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
 * - `field`：含 `differences[]` 差异对照，供采纳抽屉逐项勾选
 * - `gone`：`differences` 恒空，只有 `note` + `images` + `goneUserCount`，处置**仅「下架」**
 */
export function getCorrection(id: number): Promise<CorrectionDetailVO> {
  return get<CorrectionDetailVO>(`/admin/corrections/${id}`)
}

/**
 * B4 采纳 —— **按 `type` 分派**：
 * - `field`：**逐项**采纳 + 两段式档口确认（`acceptedFields` / `stallId` / `createIfMissing`）；
 *   响应二义性：`data = null` 采纳完成；`data = StallConfirmVO`（`needStallConfirm`）需先确认档口再调用。
 * - `gone`：**忽略差异项**，请求体只带可选附注 `replyContent`，服务端直接下架菜品
 *   （`status=off`，可逆、评价完整保留）。
 *   🔴 本流程**绝不删除**（删除仅在「菜品管理」中由管理员主动执行）。
 */
export function adoptCorrection(
  id: number,
  req?: CorrectionAdoptReq,
): Promise<StallConfirmVO | null> {
  return post<StallConfirmVO | null>(`/admin/corrections/${id}/adopt`, req ?? {})
}

/**
 * B4 驳回（不采纳 / 驳回下架反馈）：`ModerationHandleReq` 且 `outcome` 固定 `rejected`；
 * `replyContent` 必填（≤600 字，纯空白 → 400「请填写不采纳原因」）；`replyTitle` 可选（≤40 字）。
 * `gone` 型驳回语义为「经核实仍在售」，原因须写明。
 */
export function rejectCorrection(id: number, req: ModerationHandleReq): Promise<null> {
  return put<null>(`/admin/corrections/${id}`, req)
}
