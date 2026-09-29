/**
 * 菜品信息纠错模块（package {@code com.bjtufood.correction}）。
 * <p>
 * <b>职责</b>：学生提交纠错，管理端核实（采纳或拒绝），经 DishService 写契约落到菜品。
 * <p>
 * <b>依赖方向</b>：common、dish（写契约与 Cmd）、canteen（档口确认）、auth、notification、moderation
 * <p>
 * <b>对外契约</b>：CorrectionService
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在 {@code mvn test} 阶段强制校验：跨域只走
 * Service 契约或领域事件，禁止直连他域 Mapper / Entity / 实现类；域间依赖必须无环；
 * {@code common} 与 {@code wechat} 位于依赖图底部，不得反向依赖业务域。
 * 完整架构约定见 {@code docs/architecture.md}。
 */
package com.bjtufood.correction;
