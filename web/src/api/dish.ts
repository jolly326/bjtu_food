import type { Dish } from '@/types'
import { del, get, post, put } from './http'
import { dishToApi, dishToLegacy, pageRecords } from './adapter'
import type { PageEnvelope, RawDish } from './adapter'

/**
 * 菜品全量采集（聚合页 / 详情页联查等需要完整集合的场景）。
 * 后端 GET /admin/dishes 为分页 IPage（{records,total,...}，page/pageSize 透传，
 * PageUtil 单页上限 100），按 page 循环拉取直到取完，避免只取首页导致列表静默截断（WEB-104）。
 */
export async function getAll(): Promise<Dish[]> {
  const all: Dish[] = []
  let page = 1
  const pageSize = 100
  for (let guard = 0; guard < 1000; guard++) {
    const data = await get<PageEnvelope<RawDish>>('/admin/dishes', { page, pageSize })
    const records = pageRecords(data).map(dishToLegacy)
    if (!records.length) break
    all.push(...records)
    if (records.length < pageSize) break
    page++
  }
  return all
}

/**
 * 菜品保存 payload（DishAdminReq 契约，§7.23 第 1 条）：
 *  - `stallId`：既有档口直接选中；
 *  - `stallName`：按名 upsert 档口（存在复用 / 不存在自动建档），有效时优先于 stallId；
 *  - `canteenName`：仅在 stallName 触发新建档口时被后端消费（按名 upsert 所属食堂）。
 * 价格等其余字段沿用 Dish（api 层 dishToApi 元→分）。
 */
export type DishSavePayload = Omit<Dish, 'id' | 'created_at' | 'updated_at'> & {
  canteenName?: string | null
  stallName?: string | null
}

export async function create(data: DishSavePayload) {
  await post<void>('/admin/dishes', dishToApi(data))
}

export async function updateById(id: number, data: Partial<Dish>) {
  await put<void>(`/admin/dishes/${id}`, dishToApi(data))
}

export async function deleteById(id: number) {
  await del<void>(`/admin/dishes/${id}`)
}

/** 菜品大类字典项（公开只读端点 `GET /dishes/views` 出参，学生端与管理端共用）。 */
export interface MealTypeDictItem {
  /** 大类枚举值（写入 `DishAdminReq.mealType` 用的值）——来自后端视图键 `key` */
  value: string
  /** 中文标签（端上直接渲染，**端上不得另行维护任何 值 → 中文 映射**） */
  label: string
  /** 展示顺序（后端已按升序下发） */
  order: number
}

/**
 * 菜品大类字典（**公开只读端点** `GET /dishes/views`，学生端与管理端共用）。
 *
 * - 公开只读端点（`permitAll`、数据非敏感），两端复用符合业界惯例。
 *   管理端的**写操作**仍全部走 `/admin/**`（口令保护），隔离边界在那里。
 *   本端点只读，web 调用它不产生任何越权面。
 * - 标签文案 / 顺序 / 集合的**唯一真源在后端**（`DishViewConst`）→ Web 端零硬编码中文。
 * - ⚠️ **临时适配（web 待重构）**：端点由 `/dishes/meal-types` 改为 `/dishes/views`，出参由
 *   `{value,label}` 改为 `{key,label}`。此处把 `key` 映射回既有 `value`，并剔除默认视图
 *   （`recommend`，非大类），使既有管理端下拉语义不变；待 web 重构时统一改为视图模型。
 * - 出参字段本身即 camelCase，故此处只做形状与空值归一，不做下划线→驼峰映射。
 */
/** 视图字典的原始行（服务端出参形状：key/label） */
interface RawMealType {
  key?: string | number | null
  value?: string | number | null
  label?: string
  order?: number
}

export async function listMealTypes(): Promise<MealTypeDictItem[]> {
  const data = await get<RawMealType[]>('/dishes/views')
  const rows = Array.isArray(data) ? data : []
  return rows
    .map(raw => ({
      value: String(raw.key ?? raw.value ?? '').trim(),
      label: String(raw.label ?? ''),
      order: Number(raw.order ?? 0),
    }))
    // 剔除默认视图（recommend）：管理端大类下拉只接受 meal_type 值域，不接受聚合视角
    .filter(item => item.value && item.value !== 'recommend' && item.label)
    .sort((a, b) => a.order - b.order)
}

/** 菜品描述属性维度定义项（`GET /dishes/attributes` 出参，学生端与管理端共用）。 */
export interface DishAttributeDef {
  /** 维度定义ID */
  id: number
  /** 维度键（**恒等于菜品 attributes 的键**：dietType / ingredients / flavorTags / serveTemp，R13） */
  fieldKey: string
  /** 维度中文名（饮食属性 / 口味 / 食材 / 冷热） */
  name: string
  /** 取值类型：single=单值 / multi=多值 */
  valueType: 'single' | 'multi'
  /** 维度展示顺序（升序） */
  order: number
  /** 参考候选值（该维度全库已用值去重、按频次倒序）；**仅为参考、不构成约束**，空数组 = 暂无参考值 */
  options: string[]
  /** 是否有参考候选（仅供 UI 提示；**无论真假，录入始终允许自由输入**） */
  isOpenText: boolean
}


/**
 * 菜品描述属性维度字典（**公开只读端点** `GET /dishes/attributes`，学生端与管理端共用）。
 *
 * - 四维（荤素 / 主料 / 口味 / 冷热）的**维度定义与中文标签唯一真源在后端**
 *   （`dish_attribute_dimension` 表，数据驱动、免发版增维度）→ Web 端**零硬编码映射表**。
 * - **2026-09-29 修复**：此前调用的 `/dishes/attributes` 后端**从未存在**（本域只有按单菜的
 *   `/dishes/{id}/attributes`），故本函数的字典长期 404。现端点已补齐。
 * - 与 `listMealTypes` 的差异：本字典下发**全部维度**（管理端录入表单需要完整维度集）；
 *   `options` 是**参考候选**（该维度全库已用值去重，按频次倒序），
 *   **仅为参考、不构成约束**——空数组表示暂无参考值，端上仍应允许自由输入。
 * - 出参字段本身即 camelCase，故此处只做形状与空值归一。
 */
interface RawDishAttributeDef {
  id?: number
  fieldKey?: string
  name?: string
  valueType?: string
  order?: number
  /** 参考候选值：字符串数组（值即中文） */
  options?: string[]
}

export async function listDishAttributes(): Promise<DishAttributeDef[]> {
  const data = await get<RawDishAttributeDef[]>('/dishes/attributes')
  const rows = Array.isArray(data) ? data : []
  return rows
    .filter(r => r && r.fieldKey)
    .map(r => {
      const options = Array.isArray(r.options)
        ? r.options.map(o => String(o).trim()).filter(Boolean)
        : []
      return {
        id: Number(r.id ?? 0),
        fieldKey: String(r.fieldKey),
        name: String(r.name ?? ''),
        valueType: (r.valueType === 'multi' ? 'multi' : 'single') as 'single' | 'multi',
        order: Number(r.order ?? 0),
        options,
        // 「无参考候选」不等于「禁止自由输入」——仅作 UI 提示，录入始终允许自填
        isOpenText: options.length === 0,
      }
    })
    .sort((a, b) => a.order - b.order)
}

/**
 * 原 `getById(id)` 封装（公开端点 GET /dishes/{id}）已于 DEV-04 收口移除：
 * Web 后台只经 /admin/**，且该公开端点只返回在售菜品 → 已下架菜品取不到名。
 * 反馈关联菜品名改由 FeedbackAdminVO.relatedDishName 提供（见 api/feedback.ts）。
 * 如需菜品详情，请从 /admin/dishes 聚合（getAll）或列表数据中取，勿再直连公开端点。
 */
