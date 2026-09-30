package com.bjtufood.canteen.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 档口简要项（跨域只读契约：id + 名称）。
 * <p>
 * 架构收口 P0-1：纠错采纳的「档口确认候选列表」此前由 correction 模块直接
 * import {@code canteen.entity.Stall}/{@code Canteen} + {@code StallMapper}/{@code CanteenMapper}
 * 自查拼装。现由 {@code StallService} 以本投影下发（查询与排序口径留在 canteen 模块），
 * 调用方按自己的 VO 组装，不再触达 canteen 实体与 Mapper。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StallBriefVO {

    /** 档口ID（stall.id） */
    private Long id;

    /** 档口名称 */
    private String name;
}
