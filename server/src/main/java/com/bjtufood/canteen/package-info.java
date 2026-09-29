/**
 * 食堂与档口模块（package {@code com.bjtufood.canteen}）。
 * <p>
 * <b>职责</b>：食堂与档口档案，以及管理端维护用的只读候选列表（公开侧无字典端点）。
 * <p>
 * <b>依赖方向</b>：common（<b>业务层零业务域依赖</b>）；仅 controller 编排层可读 review 的
 * 评分只读契约 {@code ReviewQueryService}——2026-09-28 环偿还前的旧形态是
 * {@code canteen -> review -> dish -> canteen}，现档口均分改由
 * {@code CanteenAdminController#fillAvgRatings} 编排，canteen 业务层不再反向依赖评价域。
 * <p>
 * <b>对外契约</b>：StallService（listBriefCandidates）、StallBriefVO
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在 {@code mvn test} 阶段强制校验：跨域只走
 * Service 契约或领域事件，禁止直连他域 Mapper / Entity / 实现类；域间依赖必须无环；
 * {@code common} 与 {@code wechat} 位于依赖图底部，不得反向依赖业务域。
 * 完整架构约定见 {@code docs/architecture.md}。
 */
package com.bjtufood.canteen;
