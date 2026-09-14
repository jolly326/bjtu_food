package com.bjtufood.auth.config;

import com.bjtufood.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理端（Web 后台）口令校验过滤器（2026-09-13 定型：后台为本地数据操作工具、无登录体系）。
 * <p>
 * 背景：小程序端已无管理员登录，Web 后台不再做账号登录（登录即用 / 无感），但后端部署在公网，
 * 因此管理端接口改由**环境变量口令**保护：请求头 {@code X-Admin-Token} 必须等于环境变量
 * {@code ADMIN_TOKEN}（{@code admin.token}）。Web 侧在本地 .env 配同一个口令，启动时自动携带，用户无感。
 * <p>
 * 安全口径：
 * <ul>
 *   <li>未配置 {@code ADMIN_TOKEN} → **fail-closed 拒绝全部 /admin 请求**（403），避免遗忘配置导致管理端裸奔；</li>
 *   <li>口令比对使用等时比较（MessageDigest.isEqual），降低时序侧信道风险；</li>
 *   <li>仅作用 {@code /admin} 路径，小程序端接口不受任何影响。</li>
 * </ul>
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    /** 管理端口令请求头 */
    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${admin.token:}")
    private String adminToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // context-path 可能为 /api，统一用包含判断，避免前后缀差异导致漏检/误检
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        // 1) /admin/** 全量受口令保护；
        // 2) /upload/image 是**管理后台**的图片上传入口（web 端只带 X-Admin-Token、没有学生 JWT），
        //    若不在本过滤器范围内会落到 anyRequest().authenticated() 而返回 401，导致后台上传必然失败。
        //    注意必须用 endsWith 精确匹配："/upload/image" 是学生端 "/upload/images" 的子串，
        //    用 contains 会把小程序链路一并拖进口令校验（小程序走 JWT，会 403）。
        boolean isAdminUpload = uri.endsWith("/upload/image");
        return !uri.contains("/admin/") && !isAdminUpload;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!StringUtils.hasText(adminToken)) {
            // fail-closed：未配置口令即拒绝，防止公网环境下的管理端裸奔
            writeJson(response, HttpStatus.FORBIDDEN.value(),
                    Result.forbidden("管理端未配置 ADMIN_TOKEN，已拒绝访问（fail-closed）"));
            return;
        }
        String provided = request.getHeader(ADMIN_TOKEN_HEADER);
        if (!constantTimeEquals(adminToken, provided)) {
            writeJson(response, HttpStatus.FORBIDDEN.value(), Result.forbidden("管理端口令无效"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 等时比较，避免通过响应时间差逐字符猜测口令 */
    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeJson(HttpServletResponse response, int status, Result<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
