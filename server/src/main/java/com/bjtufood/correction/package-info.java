/**
 * 菜品问题反馈模块（package {@code com.bjtufood.correction}）。
 * <p>
 * <b>职责</b>：学生提交菜品问题反馈（信息有误 / 已经下架），管理端核实并处置
 * （采纳后经 DishService 写契约落到菜品；楼层例外——楼层归属档口，采纳时经 canteen 域写契约
 * {@code StallService#updateFloor} 写回目标档口 {@code stall.floor}，同档口其他菜品一并生效）。
 * <p>
 * 提交行（`correction`）append-only；「是否已处置 + 处置结论 + 处置文案」外包给
 * {@code moderation} 域的处置任务表（`moderation_task`），本域只经其服务契约读写。
 * <p>
 * <b>依赖方向</b>：common、dish（写契约与 Cmd）、canteen（档口确认与楼层）、auth、
 * moderation（内容安全检测 + 处置任务）、upload（配图校验工具）
 * <p>
 * <b>对外契约</b>：CorrectionService
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验。
 */
package com.bjtufood.correction;
