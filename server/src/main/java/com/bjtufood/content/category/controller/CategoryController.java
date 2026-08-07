package com.bjtufood.content.category.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.common.result.Result;
import com.bjtufood.content.category.dto.CategoryVO;
import com.bjtufood.content.category.entity.Category;
import com.bjtufood.content.category.mapper.CategoryMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜品分类（task-14 W6/W2，CONTRACT_IMPACT A.17）
 * <p>
 * 公开接口，无需登录；find 页分类宫格数据来源。
 */
@Tag(name = "19. 菜品分类", description = "find 页分类宫格数据，公开接口。")
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;

    @Operation(
            summary = "菜品分类列表",
            description = """
                    用途：find 页分类宫格（8 个网格）数据来源。
                    返回 enabled 分类，按 sort_order 升序；前端按 §0.5 映射表匹配矢量图标。
                    无数据时返回空数组。
                    """
    )
    @GetMapping("/categories")
    public Result<List<CategoryVO>> listCategories() {
        List<Category> list = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getStatus, "enabled")
                        .orderByAsc(Category::getSortOrder));
        return Result.success(list.stream().map(this::toVO).toList());
    }

    private CategoryVO toVO(Category c) {
        CategoryVO vo = new CategoryVO();
        vo.setId(c.getId());
        vo.setName(c.getName());
        vo.setSortOrder(c.getSortOrder());
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }
}
