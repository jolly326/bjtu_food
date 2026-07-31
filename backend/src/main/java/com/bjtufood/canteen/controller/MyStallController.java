package com.bjtufood.canteen.controller;

import com.bjtufood.canteen.dto.MyPublishStallVO;
import com.bjtufood.canteen.dto.StallUgcSubmitReq;
import com.bjtufood.canteen.service.StallService;
import com.bjtufood.common.constant.RoleConst;
import com.bjtufood.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * 学生个人中心：提交档口/食堂 UGC（需登录）
 * <p>
 * 提交后写入 audit_status=pending，等待后台审核；小程序端仅展示 approved 且 status=open 的数据。
 */
@Tag(name = "学生 UGC 提交", description = "学生提交档口/食堂，需登录。")
@RestController
@RequestMapping("/my/stalls")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MyStallController {

    private final StallService stallService;

    @Operation(
            summary = "提交档口/食堂",
            description = "用途：学生提交新档口或新食堂。type=stall 需传 canteenId；type=canteen 无需关联。"
                    + "提交后状态为待审核（pending），由后台审核通过后展示。"
    )
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody StallUgcSubmitReq req) {
        return Result.success(stallService.submitUgc(req));
    }

    @Operation(
            summary = "我的发布-档口/食堂列表",
            description = "用途：学生查看自己提交的档口/食堂列表（含审核状态与退回原因）。"
                    + "分别按 created_by=当前用户查 stall 表与 canteen 表，合并返回；"
                    + "前端依据 canteenId 是否为 null 推断 type=stall/canteen。"
    )
    @GetMapping
    @PreAuthorize("hasRole('" + RoleConst.STUDENT + "')")
    public Result<List<MyPublishStallVO>> listMySubmissions() {
        return Result.success(stallService.listMySubmissions());
    }
}
