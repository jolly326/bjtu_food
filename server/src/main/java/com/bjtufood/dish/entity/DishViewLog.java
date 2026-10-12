package com.bjtufood.dish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜品浏览明细日志（表 {@code dish_view_log}）。
 *
 * <p><b>一行 = 一次浏览</b>（PV 口径，不去重）。记录<b>精确到秒</b>的时间戳而非日期，
 * 以支撑任意时间窗聚合（近 7 天 / 近 30 天 / 自定义区间）—— 日聚合表会把精度锁死在「天」。
 *
 * <p><b>🔴 本表是浏览量的唯一载体</b>：一行 = 一次浏览 ⇒ 任意时间窗的聚合都由它算出，
 * 展示层拿到的「近 30 天浏览量」是**有窗口、会随时间回落**的计数，菜品表上不维护累计列。
 * 语义与窗口界定详见 {@code docs/schema/dish_view_log.md} §2.4。
 *
 * @see com.bjtufood.dish.mapper.DishViewLogMapper
 * @see com.bjtufood.dish.task.DishViewLogCleanupTask
 */
@Data
@TableName("dish_view_log")
public class DishViewLog {

    /** 主键（**无业务含义**，仅供分页 / 定位） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 菜品 ID（应用层保证引用有效，**不建外键** —— 与 {@code review.dish_id} 同款策略） */
    private Long dishId;

    /** 浏览时间（精确到秒；写入时取应用侧当前时间，不取 DB 时钟以避免多节点时钟漂移） */
    private LocalDateTime viewedAt;

    public DishViewLog() {
    }

    /** 便捷构造（供写入侧使用） */
    public DishViewLog(Long dishId, LocalDateTime viewedAt) {
        this.dishId = dishId;
        this.viewedAt = viewedAt;
    }
}