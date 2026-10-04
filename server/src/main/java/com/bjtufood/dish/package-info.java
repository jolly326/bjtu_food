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
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验，详见 {@code docs/architecture.md}。
 */
package com.bjtufood.dish;
