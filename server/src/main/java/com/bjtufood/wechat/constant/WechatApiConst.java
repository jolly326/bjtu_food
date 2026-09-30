package com.bjtufood.wechat.constant;

/**
 * 微信开放平台 API 常量（端点与平台硬限制，单一真源）。
 * <p>
 * 架构收口 P0-B：原先这些常量散落在
 * {@code content.security.impl.ContentSecurityServiceImpl} 内，其中
 * {@code MAX_IMAGE_BYTES} 被 {@code UploadServiceImpl} 跨域引用
 * （为读常量而 import 实现类，违反依赖倒置）。
 * 现归位到微信平台集成域，语义回到其本来的归属——它们是<b>微信 API 的限制</b>，
 * 不是「内容安全服务」的实现细节。
 * <p>
 * 采用 {@code interface} 承载常量，与各域 {@code XxxConst} 的既有风格一致
 * （如 {@code feedback.constant.FeedbackConst}）。
 */
public interface WechatApiConst {

    /** 获取稳定版接口调用凭据（access_token） */
    String STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";

    /** 小程序登录凭证校验（code2Session，GET） */
    String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    /** 文本内容安全检测（msgSecCheck v2） */
    String MSG_SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check";

    /** 图片内容安全检测（imgSecCheck） */
    String IMG_SEC_CHECK_URL = "https://api.weixin.qq.com/wxa/img_sec_check";

    /** 小程序云存储文件批量下载（batchdownloadfile） */
    String BATCH_DOWNLOAD_URL = "https://api.weixin.qq.com/tcb/batchdownloadfile";

    /**
     * imgSecCheck 图片大小硬上限（字节，1MB）——<b>微信平台限制</b>。
     * <p>
     * 消费方：{@code moderation}（送检前大小兜底）与 {@code upload}
     * （从微信云存储下载图片时限制读取上限，避免无界流式读入撑爆内存）。
     * 二者是同一平台约束，故共用本常量。
     */
    long MAX_IMAGE_SEC_CHECK_BYTES = 1024L * 1024;
}
