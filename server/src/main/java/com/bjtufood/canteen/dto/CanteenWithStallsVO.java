package com.bjtufood.canteen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "食堂及其档口展示信息")
public class CanteenWithStallsVO extends CanteenInfoVO {

    @Schema(description = "食堂下属档口列表")
    private List<StallDetailVO> stalls;
}
