package com.bjtufood;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界架构测试。
 * <p>
 * 本类把「模块边界」从约定升级为<b>可执行约束</b>：任何一次跨域直连 Mapper / 实体的提交都会在
 * {@code mvn test} 阶段失败，而不是等到代码评审或线上事故才被发现。
 * <p>
 * 分析范围：仅 {@code src/main/java} 的生产类（{@link ImportOption.DoNotIncludeTests} 排除测试类，
 * 测试为造桩而直接引用实体/Mapper 属正常手法，不属实现违规）。
 * <p>
 * 约束清单（逐条对应本次收口的问题编号）：
 * <ol>
 *   <li><b>P0-1 跨域读写解耦</b>：某域的 {@code mapper} / {@code entity} 仅允许本域访问——
 *       跨域读经对方 Service 只读契约（如 {@code UserService.mapBriefByIds}、{@code DishService.mapNameByIds}、
 *       {@code StallService.listBriefCandidates}），跨域写经对方 Service 写契约
 *       （如 {@code DishService.applyCorrection}、{@code FeedbackService.migrateOwnership}）或领域事件
 *       （{@code UserOwnershipMigratedEvent} / {@code UserAccountClosedEvent} / {@code DishDeletedEvent}）；</li>
 *   <li><b>P0-B 跨域只依赖接口契约</b>：任一域的 {@code service.impl} 仅允许本域访问——
 *       跨域必须依赖 Service <b>接口</b>。本条是 P0-B 收口的回归护栏：
 *       收口前 {@code UploadServiceImpl} 曾 {@code import ...ContentSecurityServiceImpl}
 *       只为读一个常量（1MB 图片上限），属典型依赖倒置破坏；</li>
 *   <li><b>P0-2 common 零业务依赖</b>：{@code com.bjtufood.common} 不得依赖任何业务域
 *       （业务常量归位各域 {@code constant} 包、认证态判定归位 {@code auth.support}、
 *       微信凭据归位 {@code wechat} 域）；</li>
 *   <li><b>分层</b>：{@code controller} 不得直接访问 {@code mapper}（只经 Service）。</li>
 * </ol>
 *
 * <h3>刻意不设规则的一处例外：{@code auth.support}</h3>
 * {@code auth.support}（{@code SecurityUtil} / {@code JwtUtil} / {@code AuthStateUtil}）<b>允许</b>
 * 被 {@code correction} / {@code feedback} / {@code notification} / {@code review} 直接引用，这是<b>有意为之</b>：
 * <ul>
 *   <li>三者都是<b>认证域的只读静态门面</b>（当前登录人 / 令牌解析 / 认证态判据），无状态、不碰数据库、
 *       不含业务编排，经 Service 转发纯属为一行 getter 制造无谓间接层；</li>
 *   <li>与之相对，auth 的<b>有状态能力</b>（用户查询、归属迁移、注销）已全部要求经
 *       {@code UserService} 契约访问，并受上列 mapper/entity 规则保护——这才是「跨域只走契约」的实质。</li>
 * </ul>
 * 因此本类<b>不</b>对 {@code support} 设「他域禁访」规则：若强行加这条，会诱导后人把
 * {@code SecurityUtil.getCurrentUserId()} 包一层 {@code UserService} 转发，
 * 那是纯粹的样板代码，不改善任何耦合。若日后 {@code support} 里长出了
 * <b>有状态或需 mock 的 Bean</b>，则应改用接口 + Service 契约，并同步为 {@code support} 加规则。
 */
@AnalyzeClasses(packages = "com.bjtufood", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchTests {

    /**
     * {@code com.bjtufood} 下的一级包（共享层 common + 各业务域 + 横切能力域）。
     * <p>
     * 变更：{@code content}（3 文件的横切能力，易与「内容管理」混淆）更名为
     * {@code moderation}（UGC 内容审核）；新增 {@code wechat}（微信平台集成：凭据获取），
     * 承接自 ContentSecurityService 剥离的 token 能力。
     */
    private static final List<String> DOMAINS = List.of(
            "auth", "banner", "canteen", "moderation", "correction",
            "dish", "feedback", "notification", "review", "upload", "wechat");

    /** P0-2：common 不得依赖业务域（业务常量与领域知识不得回流 common）。 */
    @ArchTest
    static final ArchRule common_mustNotDependOnBusinessDomains = noClasses()
            .that().resideInAPackage("com.bjtufood.common..")
            .should().dependOnClassesThat().resideInAnyPackage(domainPackages())
            .because("common 为跨模块通用件（无业务依赖）；业务常量/领域判定一律留在属主域，否则引 common 即拖入业务实现");

    /** 分层：控制器只允许经 Service 取数，不得直连 Mapper。 */
    @ArchTest
    static final ArchRule controllers_mustNotAccessMappers = noClasses()
            .that().resideInAPackage("..controller..")
            .should().accessClassesThat().resideInAPackage("..mapper..")
            .because("直连 Mapper 会绕过 Service 的业务口径与事务边界（P0-1 分层要求）");

    /**
     * P0-1：任一域的 {@code mapper} 包只允许被本域访问。
     * <p>
     * 逐域断言而非单条规则，便于违规时直接由用例名定位「哪个域的他域 Mapper 被引用」。
     */
    @ArchTest
    static void mapper_onlyAccessibleWithinOwningDomain(JavaClasses classes) {
        for (String domain : DOMAINS) {
            noClasses().that().resideOutsideOfPackage("com.bjtufood." + domain + "..")
                    .should().accessClassesThat().resideInAPackage("com.bjtufood." + domain + ".mapper..")
                    .because("跨域直连他域 Mapper 属 P0-1 违规：跨域读经对方 Service 契约，跨域写经 Service 契约或领域事件")
                    .check(classes);
        }
    }

    /**
     * P0-1：任一域的 {@code entity} 包只允许被本域访问（表结构不外泄）。
     * <p>
     * 跨域只传 DTO / 只读投影（如 {@code UserBriefVO}、{@code UserAuthContextVO}、{@code StallBriefVO}）
     * 或领域事件，避免他域依赖实体字段与 MyBatis-Plus 映射细节。
     */
    @ArchTest
    static void entity_onlyAccessibleWithinOwningDomain(JavaClasses classes) {
        for (String domain : DOMAINS) {
            noClasses().that().resideOutsideOfPackage("com.bjtufood." + domain + "..")
                    .should().accessClassesThat().resideInAPackage("com.bjtufood." + domain + ".entity..")
                    .because("他域实体（表结构）不外泄：跨域一律传 DTO/投影或事件")
                    .check(classes);
        }
    }

    /**
     * P0-B：任一域的 {@code service.impl} 包只允许被本域访问（跨域必须依赖接口契约）。
     * <p>
     * 本条为 收口新增的回归护栏。收口前的真实违规：
     * {@code UploadServiceImpl} 直接 {@code import com.bjtufood.content.security.impl.ContentSecurityServiceImpl}，
     * 仅为读取 {@code MAX_IMAGE_BYTES} 常量——为一个常量跨越域边界依赖实现类，
     * 既破坏依赖倒置，也让该常量的真实归属（微信平台限制）被掩盖。
     * 修正后常量归位 {@code wechat.constant.WechatApiConst}，调用方只依赖接口。
     */
    @ArchTest
    static void serviceImpl_onlyAccessibleWithinOwningDomain(JavaClasses classes) {
        for (String domain : DOMAINS) {
            noClasses().that().resideOutsideOfPackage("com.bjtufood." + domain + "..")
                    .should().accessClassesThat().resideInAPackage("com.bjtufood." + domain + ".service.impl..")
                    .because("跨域必须依赖 Service 接口契约，不得直连实现类（依赖倒置）")
                    .check(classes);
        }
    }

    /**
     * {@code wechat} 必须是<b>平台集成叶子域</b>：只依赖 {@code common}，不得反向依赖任何业务域。
     * <p>
     * 收口：{@code wechat} 域聚合了全部微信开放平台 API 客户端
     * （{@code WechatService} jscode2Session + {@code WechatAccessTokenProvider} stable_token），
     * 被 {@code auth}（登录）、{@code moderation}（内容审核）、{@code upload}（云存储）共同依赖。
     * 正因它是三方共用的最底层，<b>必须保持叶子</b>——一旦某天它反过来引用了
     * {@code auth.entity.User} 之类，就会形成 auth ↔ wechat 的包级循环依赖，
     * 而这种环在编译期不报错、只在运行时以诡异方式爆炸。
     * <p>
     * 注：本条与 {@code common} 零业务依赖规则形如镜像，二者共同保证
     * 「依赖图底部（common / wechat）永不向上依赖业务域」。
     */
    @ArchTest
    static final ArchRule wechat_mustNotDependOnBusinessDomains = noClasses()
            .that().resideInAPackage("com.bjtufood.wechat..")
            .should().dependOnClassesThat().resideInAnyPackage(otherDomainPackages())
            .because("wechat 为微信平台集成叶子域（被 auth/moderation/upload 依赖），不得反向依赖业务域，"
                    + "否则形成包级循环依赖");

    /**
     * 【已撤销的规则 · 留档】域间无环检测（ArchUnit {@code SlicesRuleDefinition#beFreeOfCycles}）。
     * <p>
     * <b>为何撤销而非保留</b>：本规则 上线即检出 <b>2 个真实包级环</b>：
     * <ol>
     *   <li>{@code dish -> review -> dish}：{@code RatingUpdateListener} 订阅
     *       {@code ReviewSubmittedEvent} 重算评分。语义单向（review 对该订阅毫不知情），
     *       包级成环仅因监听器签名引用了 review 的事件类型，属可接受的发布/订阅形态。</li>
     *   <li>{@code canteen -> review -> dish -> canteen}：管理端档口列表要展示档口平均评分，
     *       而 dish 又需 canteen 的档口名——<b>真实技术债</b>。</li>
     * </ol>
     * ArchUnit 1.3 的 {@code SliceRule#ignoreDependency(String, String)} 虽可链式调用，
     * 但实测<b>不影响 {@code beFreeOfCycles()} 的环计算</b>（加豁免后规则仍报同样 2 处环）。
     * 与其留一条靠失效豁免「变绿」的假护栏，不如<b>撤下规则并如实登记待办</b>——
     * 假绿的护栏比没有护栏更危险。
     * <p>
     * ：第 2 条（真实技术债）<b>已偿还</b>——档口均分原本由
     * {@code StallServiceImpl} 注入 {@code ReviewQueryService} 拉取，现上移至
     * {@code CanteenAdminController#fillAvgRatings} 编排（均分是 review 按 dish 聚合的派生展示值，
     * 不属 canteen 自有知识；controller 在依赖图顶端，不产生新包级边），出参与口径不变。
     * 第 1 条（dish ↔ review 发布/订阅）仍为环，规则继续留档。
     * <p>
     * <b>剩余阻塞</b>：ArchUnit 1.3 无法对 {@code beFreeOfCycles()} 做有效豁免，
     * 故在 dish ↔ review 这一条<b>可接受的发布/订阅边</b>被消化前，规则无法重新启用。
     */

    /**
     * 环偿还回归护栏：{@code canteen} 的<b>业务层</b>不得依赖 review 域。
     * <p>
     * 本条是对已偿还债务的<b>定向锁死</b>，而非全图环检测：全图 {@code beFreeOfCycles()}
     * 因 dish ↔ review 的发布/订阅边无法豁免而不可用，但这一条<b>曾经真实发生过</b>
     * （{@code StallServiceImpl} 注入 {@code ReviewQueryService}），
     * 必须留下回归护栏，否则后人「顺手在 Service 里拉一下均分」就会静默把环接回来。
     * <p>
     * <b>为何放行 {@code canteen.controller}</b>：均分回填现由
     * {@code CanteenAdminController#fillAvgRatings} 在<b>编排层</b>完成——controller 位于依赖图顶端，
     * 跨域只经 Service 契约，不构成包级环。这是该场景的<b>唯一正确位置</b>：
     * 放回 Service 就是把刚断开的边接回来。因此规则精确限定在
     * {@code service / mapper / entity}——即<b>持有业务逻辑与数据访问</b>的层。
     */
    @ArchTest
    static final ArchRule canteenBusinessLayers_mustNotDependOnReview = noClasses()
            .that().resideInAnyPackage("com.bjtufood.canteen.service..",
                                       "com.bjtufood.canteen.mapper..",
                                       "com.bjtufood.canteen.entity..")
            .should().dependOnClassesThat().resideInAPackage("com.bjtufood.review..")
            .because("canteen -> review -> dish -> canteen 曾构成包级环（2026-09-28 已偿还：档口均分上移至 "
                    + "CanteenAdminController 编排）。canteen 是菜品属性字典，其业务层不得反向依赖评价域；"
                    + "跨域只读展示值只能由 controller 编排层经 Service 契约获取");

    /**
     * 一级业务包的通配表达式（{@code com.bjtufood.<domain>..}）。
     */
    private static String[] domainPackages() {
        return DOMAINS.stream().map(domain -> "com.bjtufood." + domain + "..").toArray(String[]::new);
    }

    /**
     * 除 {@code wechat} 外的一级业务包（供叶子域规则使用——自身不能出现在排除列表里）。
     */
    private static String[] otherDomainPackages() {
        return DOMAINS.stream()
                .filter(domain -> !"wechat".equals(domain))
                .map(domain -> "com.bjtufood." + domain + "..")
                .toArray(String[]::new);
    }
}
