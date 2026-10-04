/**
 * 楼层受控字典（**值即汉字**，全链零映射）。
 *
 * <p>真源：[schema/stall.md](../../../docs/schema/stall.md) —— 值域 `负一层` / `一层` / `二层` / `三层` / `四层`；
 * 管理端 A2 与小程序纠错页**同用该字典**。
 *
 * <p><b>变更规则</b>：新增楼层 = 两端各加一项（免迁移）；删除 / 改名 = **先迁移存量档口**再改字典
 * （故不会出现字典外的值）。后端 `FloorDict`（`server/.../canteen/constant/FloorDict.java`）与本表逐项一致。
 */
export const FLOOR_OPTIONS = ['负一层', '一层', '二层', '三层', '四层'] as const
