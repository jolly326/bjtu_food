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
 * 模块边界由 {@code ArchTests}（ArchUnit）在 {@code mvn test} 阶段强制校验：跨域只走
 * Service 契约或领域事件，禁止直连他域 Mapper / Entity / 实现类；域间依赖必须无环；
 * {@code common} 与 {@code wechat} 位于依赖图底部，不得反向依赖业务域。
 * 完整架构约定见 {@code docs/architecture.md}。
 */
package com.bjtufood.upload;
