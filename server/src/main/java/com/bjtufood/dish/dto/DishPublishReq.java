package com.bjtufood.dish.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 学生发布 / 编辑菜品请求参数
 * <p>
 * 由学生端（/dishes POST、/dishes/{id} PUT）提交，后端强制写入
 * created_by=当前用户、audit_status=pending，状态与审核解耦，学生不可设置上下架。
 * <p>
 * 校验对齐前端约束（前端 price 上限 9999 元）：price 以「分」存储，范围 0~999900 分，
 * 防止前端限制被绕过导致超大/负价格入库（此前仅 @NotNull，存在后端校验缺口）。
 */
@Data
public class DishPublishReq {

    @NotNull(message = "档口ID不能为空")
    private Long stallId;

    @NotBlank(message = "菜品名称不能为空")
    @Size(max = 64, message = "菜品名称不能超过64字")
    private String name;

    @NotNull(message = "价格不能为空")
    @Min(value = 0, message = "价格不能为负")
    @Max(value = 999900, message = "价格不能超过9999元")
    private Integer price;

    @Size(max = 512, message = "菜品描述不能超过512字")
    private String description;

    private List<String> images;

    @Size(max = 128, message = "标签过长")
    private String tags;
}
