package com.bjtufood.dish.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 纠错采纳写回指令（跨域写契约：correction → dish）。
 * <p>
 * 纠错采纳由 {@code DishService.applyCorrection(DishCorrectionCmd)} 承接 ——
 * correction 模块**不直接写** dish 表；写库动作与其字段落库形态
 * （images 的 JSON 序列化、null 字段跳过策略）回归 dish 模块唯一真源。
 * <p>
 * 可空语义保持不变：{@code attributes}/{@code images} 为 null 表示
 * 「本次纠错未提供，不覆盖 dish 既有值」（空集合同样按未提供处理）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DishCorrectionCmd {

    /** 目标菜品ID（dish.id） */
    private Long dishId;

    /** 菜品名称（纠错快照，必填） */
    private String name;

    /** 价格（分，必填） */
    private Integer price;

    /** 采纳后挂靠的档口ID（stall.id，由 correction 侧解析得出） */
    private Long stallId;

    /** 描述属性（键=维度 ID 字符串，值=中文文本/数组；null 或空 Map=不覆盖） */
    private Map<String, Object> attributes;

    /** 配图相对路径（null 或空列表=不覆盖；实现侧负责 JSON 序列化） */
    private List<String> images;
}
