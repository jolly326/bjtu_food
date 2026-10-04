/**
 * 食堂与档口模块（package {@code com.bjtufood.canteen}）。
 * <p>
 * <b>职责</b>：食堂与档口档案，以及管理端维护用的只读候选列表（公开侧无字典端点）；
 * 并承载菜品纠错采纳时的档口楼层写回（{@code StallService#updateFloor}）。
 * <p>
 * <b>依赖方向</b>：common（<b>业务层零业务域依赖</b>）；仅 controller 编排层可读 review 的
 * 评分只读契约 {@code ReviewQueryService}——环偿还前的旧形态是
 * {@code canteen -> review -> dish -> canteen}，现档口均分改由
 * {@code CanteenAdminController#fillAvgRatings} 编排，canteen 业务层不再反向依赖评价域。
 * <p>
 * <b>对外契约</b>：StallService（listBriefCandidates）、StallBriefVO
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验。
 */
package com.bjtufood.canteen;
