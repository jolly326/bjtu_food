/**
 * 菜品信息纠错模块（package {@code com.bjtufood.correction}）。
 * <p>
 * <b>职责</b>：学生提交纠错，管理端核实（采纳或拒绝），经 DishService 写契约落到菜品；
 * 楼层（floor）例外——楼层归属档口，采纳时经 canteen 域写契约 {@code StallService#updateFloor}
 * 写回目标档口 {@code stall.floor}（同档口其他菜品一并生效）。
 * <p>
 * <b>依赖方向</b>：common、dish（写契约与 Cmd）、canteen（档口确认）、auth、notification、moderation
 * <p>
 * <b>对外契约</b>：CorrectionService
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验。
 */
package com.bjtufood.correction;
