import { put } from './http'
import type { SortItemsReq } from '@/types/common'

/**
 * 拖拽排序提交（`PUT .../sort`，**整体提交全量行**）。
 *
 * A4 维度 / A4 取值 / A5 Banner / A6 视图 / A6 分类值 / A7 举报原因**六个端点形状完全一致**
 * （后端亦由统一的 `SortReorderUtil` 校验），故收敛为单一真源，各api 模块只给路径。
 */
export function submitSort(path: string, req: SortItemsReq): Promise<null> {
  return put<null>(path, req)
}