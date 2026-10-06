package com.bjtufood.canteen.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 档口简要项（跨域只读契约：id + 名称）。
 * <p>
 * 纠错采纳的「档口确认候选列表」由 {@code StallService} 以本投影下发（查询与排序口径留在 canteen 模块）——
 * 调用方**不触达** canteen 实体与 Mapper，按自己的 VO 组装。
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
