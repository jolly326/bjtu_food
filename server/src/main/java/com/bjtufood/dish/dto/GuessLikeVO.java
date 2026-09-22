package com.bjtufood.dish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 猜你喜欢词视图对象（VO）——原 {@code HotSearchVO}，2026-09-22 change search-page-refresh 改名。
 * <p>
 * 语义：为搜索页「猜你喜欢」区块提供可点击的搜索词条。当前实现为**随机抽取在售菜品名**
 * （不看热度、不排序、不做个性化推荐算法），故热度分不再出参（原 {@code heat} 已于 2026-09-22 删除）。
 * <p>
 * **契约留扩展位**：将来把取数逻辑升级为个性化 / 推荐算法时，出参形态保持 {@code keyword} 列表不变
 * （或仅做向后兼容的字段追加），端上无需同步改造；同时不得预埋端上零消费的字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "猜你喜欢词条")
public class GuessLikeVO {

    @Schema(description = "猜你喜欢词（在售菜品名）", example = "牛肉拉面")
    private String keyword;
}
