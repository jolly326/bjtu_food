/**
 * 图片上传模块（package {@code com.bjtufood.upload}）。
 * <p>
 * <b>职责</b>：multipart 直传与小程序云存储 fileID 转存两条链路，尺寸与魔数校验，审核后转存 COS。
 * <p>
 * <b>依赖方向</b>：common、moderation（送审）、wechat（云存储 token）
 * <p>
 * <b>对外契约</b>：UploadService、ImageDimensionChecker（静态工具）
 * <p>
 * <b>领域事件</b>：无
 * <p>
 * 模块边界由 {@code ArchTests}（ArchUnit）在测试阶段强制校验。
 */
package com.bjtufood.upload;
