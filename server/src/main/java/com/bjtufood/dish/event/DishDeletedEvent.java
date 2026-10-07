package com.bjtufood.dish.event;

/**
 * 菜品删除事件（P0-1 跨域写侧解耦）。
 * <p>
 * 发布方：{@code DishServiceImpl.deleteDish}（ dish 域，事务内发布）。
 * 订阅方：{@code review.event.ReviewDishCascadeListener}（删除该菜品下的全部评价）。
 * <p>
 * <b>为何用事件而非直接调用 ReviewService</b>：ReviewServiceImpl 依赖 DishService
 * （管理端列表补全菜品名），若 DishServiceImpl 反向注入 ReviewService 即成构造期循环依赖
 * （Spring Boot 3 默认禁止循环引用，直接启动失败）。写侧跨域动作一律以领域事件解耦，
 * 与既有 {@code ReviewSubmittedEvent → RatingUpdateListener}（review → dish 反向补分）同构。
 * <p>
 * <b>事务语义</b>：无 {@code @TransactionalEventListener} → 监听器在发布者事务内同步执行，
 * 级联删除失败仍整体回滚（「删除菜品 + 清评价」保持原子性）。
 */
public record DishDeletedEvent(Long dishId) {
}
