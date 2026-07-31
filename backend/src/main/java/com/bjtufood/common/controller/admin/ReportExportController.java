package com.bjtufood.common.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.common.result.Result;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import com.bjtufood.moment.entity.Moment;
import com.bjtufood.moment.mapper.MomentMapper;
import com.bjtufood.review.entity.Review;
import com.bjtufood.review.mapper.ReviewMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据报表导出接口（Web 后台，ADM，W3）
 * <p>
 * 返回 CSV 文件流（按 ARCH_D6 降级方案，避免引入重依赖 POI）。
 * 数据复用各表查询（dashboard 同源），不重复计算。
 */
@Tag(name = "18. 后台报表导出", description = "菜品/评价/用户/动态报表 CSV 导出。需要管理员 token。")
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReportExportController {

    private final DishMapper dishMapper;
    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final MomentMapper momentMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Operation(summary = "菜品报表导出", description = "ADM。CSV 文件流，支持时间范围。")
    @GetMapping("/dishes/export")
    public void exportDishes(HttpServletResponse response,
                             @Parameter(description = "起始时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String startAt,
                             @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String endAt) throws IOException {
        var list = dishMapper.selectList(range(Dish::getCreatedAt, startAt, endAt));
        String[] header = {"id", "name", "price(分)", "stallId", "auditStatus", "status", "viewCount", "favoriteCount", "avgRating", "ratingCount", "createdAt"};
        writeCsv(response, "dishes", header, list, d -> new String[]{
                str(d.getId()), esc(d.getName()), str(d.getPrice()), str(d.getStallId()),
                nz(d.getAuditStatus()), nz(d.getStatus()), str(d.getViewCount()),
                str(d.getFavoriteCount()), str(d.getAvgRating()), str(d.getRatingCount()), fmt(d.getCreatedAt())
        });
    }

    @Operation(summary = "评价报表导出", description = "ADM。CSV 文件流，支持时间范围。")
    @GetMapping("/reviews/export")
    public void exportReviews(HttpServletResponse response,
                              @Parameter(description = "起始时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String startAt,
                              @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String endAt) throws IOException {
        var list = reviewMapper.selectList(range(Review::getCreatedAt, startAt, endAt));
        String[] header = {"id", "userId", "dishId", "rating", "content", "isHidden", "usefulCount", "createdAt"};
        writeCsv(response, "reviews", header, list, r -> new String[]{
                str(r.getId()), str(r.getUserId()), str(r.getDishId()), str(r.getRating()),
                esc(r.getContent()), str(r.getIsHidden()), str(r.getUsefulCount()), fmt(r.getCreatedAt())
        });
    }

    @Operation(summary = "用户报表导出", description = "ADM。CSV 文件流，支持时间范围。")
    @GetMapping("/users/export")
    public void exportUsers(HttpServletResponse response,
                            @Parameter(description = "起始时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String startAt,
                            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String endAt) throws IOException {
        var list = userMapper.selectList(range(User::getCreatedAt, startAt, endAt));
        String[] header = {"id", "username", "email", "nickname", "role", "status", "createdAt"};
        writeCsv(response, "users", header, list, u -> new String[]{
                str(u.getId()), esc(u.getUsername()), esc(u.getEmail()), esc(u.getNickname()),
                nz(u.getRole()), nz(u.getStatus()), fmt(u.getCreatedAt())
        });
    }

    @Operation(summary = "动态报表导出", description = "ADM。CSV 文件流，支持时间范围。")
    @GetMapping("/moments/export")
    public void exportMoments(HttpServletResponse response,
                              @Parameter(description = "起始时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String startAt,
                              @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String endAt) throws IOException {
        var list = momentMapper.selectList(range(Moment::getCreatedAt, startAt, endAt));
        String[] header = {"id", "userId", "content", "relatedType", "relatedId", "auditStatus", "rejectReason", "usefulCount", "commentCount", "status", "createdAt"};
        writeCsv(response, "moments", header, list, m -> new String[]{
                str(m.getId()), str(m.getUserId()), esc(m.getContent()), nz(m.getRelatedType()),
                str(m.getRelatedId()), nz(m.getAuditStatus()), esc(m.getRejectReason()),
                str(m.getUsefulCount()), str(m.getCommentCount()), str(m.getStatus()), fmt(m.getCreatedAt())
        });
    }

    private <T> LambdaQueryWrapper<T> range(SFunction<T, LocalDateTime> getter, String startAt, String endAt) {
        LambdaQueryWrapper<T> w = new LambdaQueryWrapper<>();
        if (startAt != null && !startAt.isBlank()) w.ge(getter, startAt);
        if (endAt != null && !endAt.isBlank()) w.le(getter, endAt);
        w.orderByDesc(getter);
        return w;
    }

    private <T> void writeCsv(HttpServletResponse response, String name, String[] header,
                              java.util.List<T> list, java.util.function.Function<T, String[]> rowFn) throws IOException {
        response.setContentType("text/csv;charset=utf-8");
        String fileName = name + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        try (PrintWriter pw = response.getWriter()) {
            pw.println("\uFEFF" + String.join(",", header));
            for (T item : list) {
                pw.println(String.join(",", rowFn.apply(item)));
            }
        }
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String esc(String s) {
        if (s == null) return "";
        String t = s.replace("\"", "\"\"");
        if (t.contains(",") || t.contains("\"") || t.contains("\n")) {
            t = "\"" + t + "\"";
        }
        return t;
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(FMT);
    }
}
