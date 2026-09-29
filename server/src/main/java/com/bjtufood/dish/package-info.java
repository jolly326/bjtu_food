/**
 * 菜品模块（package {@code com.bjtufood.dish}）。
 * <p>
 * <b>职责</b>：菜品展示、搜索、猜你喜欢、管理端录入、描述属性维度字典、删除级联。
 * <p>
 * <b>依赖方向</b>：common、canteen（档口读契约）、review（订阅评分事件）
 * <p>
 * <b>对外契约</b>：DishService（mapNameByIds / applyCorrection / recalcAvgRating）
 * <p>
 * <b>领域事件</b>：DishDeletedEvent（菜品删除，供 review 级联清理评价）
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在 {@code mvn test} 阶段强制校验：跨域只走
 * Service 契约或领域事件，禁止直连他域 Mapper / Entity / 实现类；域间依赖必须无环；
 * {@code common} 与 {@code wechat} 位于依赖图底部，不得反向依赖业务域。
 * 完整架构约定见 {@code docs/architecture.md}。
 */
package com.bjtufood.dish;
