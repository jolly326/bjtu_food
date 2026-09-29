/**
 * 评价模块（package {@code com.bjtufood.review}）。
 * <p>
 * <b>职责</b>：评价提交、重新评价、删除、隐藏、审核，以及只读查询契约。
 * <p>
 * <b>依赖方向</b>：common、auth（用户读契约与事件）、dish（菜品读契约与删除事件）、moderation、notification（事件）
 * <p>
 * <b>对外契约</b>：ReviewService（写）、ReviewQueryService（只读，零业务依赖可安全注入）、StallAvgRatingVO
 * <p>
 * <b>领域事件</b>：ReviewSubmittedEvent（评分重算，由 dish 异步订阅）
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在 {@code mvn test} 阶段强制校验：跨域只走
 * Service 契约或领域事件，禁止直连他域 Mapper / Entity / 实现类；域间依赖必须无环；
 * {@code common} 与 {@code wechat} 位于依赖图底部，不得反向依赖业务域。
 * 完整架构约定见 {@code docs/architecture.md}。
 */
package com.bjtufood.review;
