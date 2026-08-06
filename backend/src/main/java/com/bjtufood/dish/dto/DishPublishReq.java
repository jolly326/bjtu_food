package com.bjtufood.dish.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 学生发布 / 编辑菜品请求参数
 * <p>
 * 由学生端（/dishes POST、/dishes/{id} PUT）提交，后端强制写入
 * created_by=当前用户、audit_status=pending，状态与审核解耦，学生不可设置上下架。
 */
@Data
public class DishPublishReq {

    @NotNull(message = "档口ID不能为空")
    private Long stallId;

    @NotBlank(message = "菜品名称不能为空")
    private String name;

    @NotNull(message = "价格不能为空")
    private Integer price;

    private String description;

    private List<String> images;

    private String tags;
}
