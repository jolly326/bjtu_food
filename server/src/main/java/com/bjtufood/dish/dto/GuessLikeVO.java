package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 猜你喜欢词视图对象（VO）。
 * <p>
 * 语义：为搜索页「猜你喜欢」区块提供可点击的搜索词条。实现为**随机抽取在售菜品名**
 * （不看热度、不排序、不做个性化推荐算法），故热度分不出参。
 * <p>
 * 出参恰 {@code name} 一项（随机在售菜品名）：端上唯一消费点即 chip 文案与点击起搜，
 * 其余字段零消费（「零消费即删」）；将来 chip 要展示图片 / 价格时再按真实消费扩字段。
 * <p>
 * **契约留扩展位**：将来把取数逻辑升级为个性化 / 推荐算法时，出参形态保持不变，端上无需同步改造。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "猜你喜欢词条")
public class GuessLikeVO {

    @Schema(description = "菜品名（随机抽取的在售菜品）", example = "牛肉拉面")
    private String name;
}
