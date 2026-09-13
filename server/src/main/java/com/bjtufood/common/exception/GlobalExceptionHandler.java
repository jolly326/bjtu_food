package com.bjtufood.common.exception;

import com.bjtufood.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 * <p>
 * 使用 @RestControllerAdvice 统一拦截所有 Controller 抛出的异常，
 * 转换为统一格式的 Result 响应返回前端，避免将异常堆栈暴露给客户端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常（自定义异常）
     * 调用方式：throw new BusinessException(400, "参数错误")
     * <p>
     * code=403 的认证/权限类业务异常映射为 HTTP 403（供前端识别「未完成学号邮箱认证」），
     * 其余 4xx 业务码沿用 200 承载统一 code/message 结构，避免破坏既有前端契约。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = (e.getCode() == 403) ? HttpStatus.FORBIDDEN : HttpStatus.OK;
        return ResponseEntity.status(status).body(Result.of(e.getCode(), e.getMessage(), null));
    }

    /**
     * 处理未登录异常
     * 调用方式：throw new UnauthorizedException("请先登录")
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleUnauthorizedException(UnauthorizedException e) {
        log.warn("未登录: {}", e.getMessage());
        return Result.unauthorized(e.getMessage());
    }

    /**
     * 处理 @Valid 参数校验失败
     * 自动捕获实体类上的校验注解错误，提取第一个失败信息返回
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败: {}", message);
        return Result.badRequest(message);
    }

    /**
     * 处理单个参数校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("参数校验失败: {}", e.getMessage());
        return Result.badRequest(e.getMessage());
    }

    /**
     * 处理路径变量 / 查询参数类型不匹配（如 /dishes/{id} 传 abc），
     * 返回 400 而非落到兜底 500（同 BE-110：非业务异常不返回 500）。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: name={}, value={}, requiredType={}",
                e.getName(), e.getValue(), e.getRequiredType());
        return Result.badRequest("参数格式错误：" + e.getName());
    }

    /**
     * 处理缺少必填查询参数（@RequestParam required=true 未携带），
     * 返回 400 而非落到兜底 500。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParameterException(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: name={}, type={}", e.getParameterName(), e.getParameterType());
        return Result.badRequest("缺少必填参数：" + e.getParameterName());
    }

    /**
     * 处理请求体反序列化失败（JSON 格式错误 / 字段类型不匹配），
     * 返回 400 而非落到兜底 500；cause 中可能含敏感细节，不向客户端透出。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.badRequest("请求体格式错误");
    }

    /**
     * 处理 IllegalArgumentException（非法参数）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.badRequest(e.getMessage());
    }

    /**
     * 处理方法级权限校验失败（@PreAuthorize），返回 403 而非误报 500
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.of(403, "无权限访问", null);
    }

    /**
     * 处理数据库数据完整性冲突（唯一键/外键/非空等）
     * <p>
     * 业务层未显式捕获的唯一键并发竞态等兜底，返回 400 而非误报 500，
     * 避免把底层 SQL 异常堆栈暴露给客户端。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("数据冲突: {}", e.getMessage());
        return Result.badRequest("数据冲突，请重试");
    }

    /**
     * 处理静态资源 / 未匹配路径 404（Spring 6.1 起未匹配请求统一抛 NoResourceFoundException），
     * 属客户端请求错误而非服务端故障，返回 400 而非落到兜底 500 刷堆栈
     * （同 BE-110：图片路径失效、错误 API 路径等场景不再误报 500）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.badRequest("资源不存在");
    }

    /**
     * 兜底异常处理（所有未捕获的异常）
     * 生产环境应隐藏详细错误信息
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return Result.error("服务器繁忙，请稍后再试");
    }
}
