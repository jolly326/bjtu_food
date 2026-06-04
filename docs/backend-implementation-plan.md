# 后端修复完善开发方�?
本文档用于指导后端开发者根据前�?`docs/api-docs.md` 完善 Spring Boot 后端，使接口、数据库、图片存储和前端请求保持一致�?
当前前端基地址为：

```ts
http://localhost:8080/api
```

后端当前配置�?
```yaml
server:
  port: 8080
  address: 0.0.0.0
  servlet:
    context-path: /api
```

因此后端 API 实际访问地址示例�?
```text
http://localhost:8080/api/dishes
http://localhost:8080/api/upload/image
```

## 1. 总体目标

后端需要完成以下目标：

1. 所有前端文档中的接口真实可用，不再返回占位字符串�?2. 数据库字段、后端实体、VO 响应结构与前端类型保持一致�?3. 图片保存在后端服务器本地文件夹中，由 Spring Boot 暴露�?HTTP 静态访问路径�?4. 数据库中只存图片 URL，不存图片二进制内容�?5. 收藏、评价、评分统计、用户统计等业务链路完整闭环�?
前端当前依赖的接口：

```text
POST /auth/login
PUT  /auth/profile
GET  /auth/stats

GET  /canteens/banners
GET  /canteens
GET  /canteens/images
GET  /canteens/stallDetail

GET  /dishes/hot
GET  /dishes
GET  /dishes/{id}

GET  /dishes/{dishId}/reviews
POST /reviews

GET  /favorites
POST /favorites/toggle

POST /upload/image
```

## 2. 当前主要问题

当前后端存在以下问题�?
1. 很多 Controller 方法仍然�?TODO，只返回 `"菜品列表"`、`"食堂列表"` 等占位字符串�?2. 前端需�?`images`、`subtitle`、`canteenName`、`stallName`、`ratingDistribution` 等响应字段，后端实体�?VO 尚未完全支持�?3. 当前用户获取逻辑未统一封装，很多接口需�?`userId` 但还没有接入 JWT 上下文�?4. 上传接口需要返�?`{ "url": "..." }`，而不是普通字符串�?5. 静态图片访问路径需要统一约定�?6. 复杂查询需要联表，单纯 MyBatis-Plus Wrapper 不够方便�?
## 3. 数据库前�?
你已经按要求给数据库新增�?`images` 等字段。后端开发时请确认数据库至少具备以下字段�?
### 3.1 canteen �?
建议字段�?
```sql
id
name
images
location
description
sort_order
status
created_at
updated_at
```

前端使用�?
```text
name
location
description
images
```

### 3.2 stall �?
建议字段�?
```sql
id
canteen_id
name
images
images
location
description
avg_rating
sort_order
status
created_at
updated_at
```

`images` 建议�?JSON 字符串：

```json
["http://localhost:8080/api/images/stall/a.jpg", "http://localhost:8080/api/images/stall/b.jpg"]
```

### 3.3 dish �?
建议字段�?
```sql
id
stall_id
name
images
images
price
tags
description
avg_rating
rating_count
favorite_count
view_count
status
created_at
updated_at
```

说明�?
```text
price 单位为分
tags 可存 "recommended,signature"
images 建议�?JSON 字符�?favorite_count 对应前端 favoriteCount / collectCount
```

### 3.4 banner �?
建议字段�?
```sql
id
title
subtitle
images
type
target_id
target_url
canteen_id
sort_order
status
created_at
updated_at
```

前端 `GET /canteens/banners` 需要：

```json
{
  "title": "...",
  "subtitle": "...",
  "images": ["..."]
}
```

### 3.5 review �?
建议字段�?
```sql
id
user_id
dish_id
rating
content
images
is_hidden
created_at
updated_at
```

`images` 建议�?JSON 字符串�?
### 3.6 favorite �?
建议字段�?
```sql
id
user_id
dish_id
created_at
```

需要唯一约束�?
```sql
UNIQUE KEY uk_favorite_user_dish (user_id, dish_id)
```

## 4. 图片本地存储�?HTTP 访问方案

### 4.1 推荐目录

建议不要把上传图片放�?`src/main/resources/static`，因为运行后上传文件不会自动进入 classpath�?
推荐使用项目根目录下的：

```text
backend/uploads/images
```

或者使用机器上的固定目录：

```text
D:/bjtu-food/uploads/images
```

开发阶段建议：

```yaml
upload:
  path: ./uploads/images
```

### 4.2 URL 约定

由于后端设置了：

```yaml
server.servlet.context-path: /api
```

如果使用 Spring MVC 资源映射�?
```java
registry.addResourceHandler("/images/**")
```

最终访问路径通常是：

```text
http://localhost:8080/api/images/xxx.jpg
```

因此建议数据库中存：

```text
http://localhost:8080/api/images/2026/05/xxx.jpg
```

局域网访问时存�?
```text
http://你的电脑IP:8080/api/images/2026/05/xxx.jpg
```

如果前端运行在手机或其他设备上，不要在数据库 URL 中写 `localhost`�?
### 4.3 application.yml 配置

建议增加�?
```yaml
app:
  public-base-url: http://localhost:8080/api

upload:
  path: ./uploads/images
  url-prefix: /images
```

局域网调试时改为：

```yaml
app:
  public-base-url: http://你的电脑IP:8080/api
```

更好的方式是使用环境变量�?
```yaml
app:
  public-base-url: ${APP_PUBLIC_BASE_URL:http://localhost:8080/api}

upload:
  path: ${UPLOAD_PATH:./uploads/images}
  url-prefix: ${UPLOAD_URL_PREFIX:/images}
```

### 4.4 WebMvcConfig 静态资源映�?
修改�?
```text
backend/src/main/java/com/bjtufood/common/config/WebMvcConfig.java
```

目标代码�?
```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.path:./uploads/images}")
    private String uploadPath;

    @Value("${upload.url-prefix:/images}")
    private String urlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = urlPrefix.endsWith("/**") ? urlPrefix : urlPrefix + "/**";
        Path path = Paths.get(uploadPath).toAbsolutePath().normalize();
        registry.addResourceHandler(pattern)
                .addResourceLocations(path.toUri().toString());
    }
}
```

注意�?
```text
upload.path = ./uploads/images
upload.url-prefix = /images
```

则文件：

```text
backend/uploads/images/2026/05/a.jpg
```

对应 URL�?
```text
http://localhost:8080/api/images/2026/05/a.jpg
```

### 4.5 UploadService 实现

上传接口必须�?
1. 校验文件非空�?2. 校验后缀�?Content-Type�?3. 按日期分目录保存�?4. 使用 UUID 生成文件名�?5. 返回完整可访�?URL�?
推荐返回�?
```json
{
  "url": "http://localhost:8080/api/images/2026/05/uuid.jpg"
}
```

`UploadServiceImpl` 核心逻辑�?
```java
@Service
public class UploadServiceImpl implements UploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    @Value("${upload.path:./uploads/images}")
    private String uploadPath;

    @Value("${upload.url-prefix:/images}")
    private String urlPrefix;

    @Value("${app.public-base-url:http://localhost:8080/api}")
    private String publicBaseUrl;

    @Override
    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("仅支�?jpg、jpeg、png、webp 图片");
        }

        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String filename = UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
        Path dir = Paths.get(uploadPath, datePath).toAbsolutePath().normalize();

        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
        } catch (IOException e) {
            throw new BusinessException("图片上传失败");
        }

        return trimEnd(publicBaseUrl, "/") + trimEnd(urlPrefix, "/") + "/" + datePath + "/" + filename;
    }

    private String trimEnd(String value, String suffix) {
        while (value.endsWith(suffix)) {
            value = value.substring(0, value.length() - suffix.length());
        }
        return value;
    }
}
```

### 4.6 UploadController 返回格式

当前前端 `uploadFile()` 期望�?
```ts
Promise<{ url: string }>
```

所�?Controller 必须返回�?
```java
@PostMapping("/image")
public Result<?> uploadImage(@RequestParam("file") MultipartFile file) {
    String url = uploadService.uploadImage(file);
    return Result.success(Map.of("url", url));
}
```

不要返回�?
```java
Result.success("图片上传成功")
```

## 5. 后端实体修改

### 5.1 Canteen

补充字段�?
```java
private String images;
private String location;
private String status;
```

### 5.2 Stall

补充字段�?
```java
private String images;
private String images;
private String location;
private BigDecimal avgRating;
private String status;
```

### 5.3 Dish

补充字段�?
```java
private String images;
```

如果数据库仍使用 `favorite_count`，保留：

```java
@TableField("favorite_count")
private Integer collectCount;
```

### 5.4 Banner

新增实体�?
```java
@Data
@TableName("banner")
public class Banner {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String subtitle;
    private String images;
    private String type;
    private Long targetId;
    private String targetUrl;
    private Long canteenId;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

新增 Mapper�?
```java
public interface BannerMapper extends BaseMapper<Banner> {
}
```

## 6. VO �?DTO 设计

### 6.1 BannerVO

```java
@Data
public class BannerVO {
    private String title;
    private String subtitle;
    private String images;
}
```

### 6.2 CanteenInfoVO

```java
@Data
public class CanteenInfoVO {
    private Long id;
    private String name;
    private String location;
    private String description;
    private List<String> images;
}
```

映射规则�?
```text
images = parse(canteen.images)
location = canteen.location
```

### 6.3 StallDetailVO

```java
@Data
public class StallDetailVO {
    private Long id;
    private String name;
    private List<String> images;
    private String location;
    private String description;
}
```

### 6.4 DishVO

现有 `DishVO` 建议补充�?
```java
private List<String> images;
private String canteenName;
private String stallName;
private Integer collectCount;
```

前端会映射：

```text
collectCount -> favoriteCount
avgRating -> rating
```

### 6.5 DishDetailVO

建议继承或组�?`DishVO`�?
```java
@Data
public class DishDetailVO extends DishVO {
    private List<RatingDistributionVO> ratingDistribution;
}
```

### 6.6 RatingDistributionVO

```java
@Data
@AllArgsConstructor
public class RatingDistributionVO {
    private Integer star;
    private Long count;
}
```

### 6.7 UserStatsVO

```java
@Data
@AllArgsConstructor
public class UserStatsVO {
    private Long favoriteCount;
    private Long reviewCount;
}
```

### 6.8 ProfileUpdateReq

```java
@Data
public class ProfileUpdateReq {
    @Size(max = 20)
    private String nickname;

    private String avatar;
}
```

## 7. JSON 图片字段处理

数据库中�?`images` 字段建议统一�?JSON 字符串�?
示例�?
```json
["http://localhost:8080/api/images/2026/05/a.jpg"]
```

建议新增工具类：

```text
backend/src/main/java/com/bjtufood/common/utils/JsonListUtil.java
```

功能�?
```java
public static List<String> parseStringList(String value)
public static String toJson(List<String> list)
```

解析规则�?
1. `null` 或空字符串返回空数组�?2. 如果�?JSON 数组，按 JSON 解析�?3. 如果不是 JSON，可兼容逗号分隔字符串�?
这样可以兼容旧数据�?
## 8. 认证上下文方�?
很多接口需要当前用�?ID�?
建议新增�?
```text
backend/src/main/java/com/bjtufood/common/utils/SecurityUtil.java
```

目标�?
```java
public class SecurityUtil {
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("请先登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        if (principal instanceof String text && text.matches("\\d+")) {
            return Long.valueOf(text);
        }
        throw new UnauthorizedException("登录信息无效");
    }
}
```

同时检�?`JwtAuthFilter`，确保认证成功后�?`userId` 放入 `Authentication`�?
## 9. Controller 修复清单

所�?Controller 都必须返回真�?Service 数据�?
### 9.1 AuthController

#### POST /auth/login

前端文档要求首次登录自动注册�?
建议逻辑�?
```text
用户存在：校验密�?用户不存在：自动创建 student 用户
返回 token + userInfo
```

#### PUT /auth/profile

逻辑�?
```text
�?JWT 获取 userId
更新 nickname/avatar
返回更新后的用户信息
```

#### GET /auth/stats

逻辑�?
```text
favoriteCount = count favorite by userId
reviewCount = count review by userId
```

### 9.2 CanteenController

需要实现：

```text
GET /canteens/banners
GET /canteens
GET /canteens/images
GET /canteens/stallDetail
```

### 9.3 DishController

需要实现：

```text
GET /dishes/hot
GET /dishes
GET /dishes/{id}
```

注意�?
```text
GET /dishes keyword 要同时匹�?dish.name �?stall.name
```

### 9.4 ReviewController

需要实现：

```text
GET /dishes/{dishId}/reviews
POST /reviews
```

### 9.5 FavoriteController

需要实现：

```text
GET /favorites
POST /favorites/toggle
```

`GET /favorites` 必须返回完整 Dish 数组或分�?Dish 对象，不要只返回 favorite 关系�?
### 9.6 UploadController

需要实现：

```text
POST /upload/image
```

返回�?
```json
{
  "url": "http://localhost:8080/api/images/2026/05/xxx.jpg"
}
```

## 10. Service 实现重点

### 10.1 CanteenService

新增方法�?
```java
List<BannerVO> listBanners();
List<CanteenInfoVO> listCanteens();
Map<String, String> listCanteenImages();
StallDetailVO getStallDetail(String canteen, String stallName);
```

查询逻辑�?
```text
banner: status='enabled', order by sort_order
canteen: status='open', order by sort_order
stallDetail: canteen.name + stall.name 联表查询
```

### 10.2 DishService

新增或完善：

```java
List<DishVO> getHotDishes();
IPage<DishVO> listDishes(DishQueryReq req);
DishDetailVO getDishDetail(Long id, Long userId);
```

详情逻辑�?
```text
�?dish + stall + canteen
解析 images
统计 ratingDistribution
可选返�?isFavorited / hasReviewed
```

### 10.3 ReviewService

新增或完善：

```java
IPage<ReviewVO> listByDishId(Long dishId, int page, int pageSize);
Long submitReview(Long userId, ReviewReq req);
```

提交评价后：

```text
保存 review
重算 dish.avg_rating
重算 dish.rating_count
```

### 10.4 FavoriteService

新增或完善：

```java
IPage<DishVO> listFavoriteDishes(Long userId, int page, int pageSize);
boolean toggle(Long userId, Long dishId);
```

toggle 后：

```text
重算 dish.favorite_count
```

### 10.5 UploadService

必须返回完整 URL�?
## 11. Mapper / SQL 设计

简�?CRUD 可以�?MyBatis-Plus�?
复杂联表建议�?XML�?
### 11.1 DishMapper

建议方法�?
```java
IPage<DishVO> selectDishPage(Page<?> page, @Param("req") DishQueryReq req);
List<DishVO> selectHotDishes();
DishDetailVO selectDishDetail(@Param("id") Long id);
List<RatingDistributionVO> selectRatingDistribution(@Param("dishId") Long dishId);
```

关键 SQL�?
```sql
SELECT
  d.id,
  d.name,
  d.price,  d.images,
  d.tags,
  d.description,
  d.avg_rating,
  d.rating_count,
  d.favorite_count AS collect_count,
  d.view_count,
  d.status,
  d.created_at,
  s.id AS stall_id,
  s.name AS stall_name,
  c.id AS canteen_id,
  c.name AS canteen_name
FROM dish d
JOIN stall s ON d.stall_id = s.id
JOIN canteen c ON s.canteen_id = c.id
WHERE d.status = 'on'
  AND (#{req.keyword} IS NULL
       OR d.name LIKE CONCAT('%', #{req.keyword}, '%')
       OR s.name LIKE CONCAT('%', #{req.keyword}, '%'))
```

排序�?
```text
sortBy=rating -> d.avg_rating DESC
sortBy=price  -> d.price ASC
默认 -> d.favorite_count DESC, d.avg_rating DESC
```

评分分布�?
```sql
SELECT rating AS star, COUNT(*) AS count
FROM review
WHERE dish_id = #{dishId}
  AND is_hidden = 0
GROUP BY rating
```

Service 层补�?1 �?5 星�?
### 11.2 ReviewMapper

建议方法�?
```java
IPage<ReviewVO> selectReviewPageByDishId(Page<?> page, @Param("dishId") Long dishId);
```

SQL�?
```sql
SELECT
  r.id,
  r.user_id,
  u.nickname AS user_nickname,
  u.avatar AS user_avatar,
  r.dish_id,
  r.rating,
  r.content,
  r.images,
  r.created_at
FROM review r
JOIN user u ON r.user_id = u.id
WHERE r.dish_id = #{dishId}
  AND r.is_hidden = 0
ORDER BY r.created_at DESC
```

### 11.3 FavoriteMapper

建议方法�?
```java
IPage<DishVO> selectFavoriteDishes(Page<?> page, @Param("userId") Long userId);
```

SQL�?
```sql
SELECT
  d.id,
  d.name,
  d.price,  d.images,
  d.tags,
  d.description,
  d.avg_rating,
  d.rating_count,
  d.favorite_count AS collect_count,
  d.view_count,
  s.name AS stall_name,
  c.name AS canteen_name
FROM favorite f
JOIN dish d ON f.dish_id = d.id
JOIN stall s ON d.stall_id = s.id
JOIN canteen c ON s.canteen_id = c.id
WHERE f.user_id = #{userId}
ORDER BY f.created_at DESC
```

### 11.4 Canteen / Stall Mapper

`/canteens/stallDetail` 可用�?
```sql
SELECT
  s.id,
  s.name,  s.images,
  s.location,
  s.description
FROM stall s
JOIN canteen c ON s.canteen_id = c.id
WHERE c.name = #{canteen}
  AND s.name = #{stallName}
LIMIT 1
```

## 12. 分页配置

如果项目没有 MyBatis-Plus 分页插件，需要新增配置：

```java
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

## 13. 前后端响应格式约�?
前端 `http.ts` 只接受：

```json
{
  "code": 200,
  "message": "...",
  "data": {}
}
```

因此后端统一使用�?
```java
return Result.success(data);
```

不要用占位：

```java
return Result.success("菜品列表");
```

## 14. 价格和标签约�?
### 14.1 价格

数据库和后端 API 均使用“分”：

```text
1500 = 15 �?```

前端当前会执行：

```ts
Math.round(raw.price / 100)
```

所以后端不要返回元�?
### 14.2 标签

数据库可存：

```text
recommended,signature
```

后端可返回字符串或数组。前端当前两种都兼容�?
推荐后端返回数组�?
```json
["recommended", "signature"]
```

如果短期不想�?VO，也可以继续返回字符串�?
## 15. 业务规则

### 15.1 登录

前端文档要求首次登录自动注册�?
建议�?
```text
POST /auth/login
如果 username 不存在，则自动创�?student 用户
```

如果后端不接受自动注册，必须同步修改前端文档和前端逻辑�?
### 15.2 评价

规则�?
```text
同一用户同一菜品只能评价一�?rating 范围 1-5
content 必填，最�?500 �?images 最�?3 �?```

### 15.3 收藏

规则�?
```text
POST /favorites/toggle 是幂等切�?已收藏则取消
未收藏则新增
操作后同�?dish.favorite_count
```

### 15.4 图片上传

规则�?
```text
只允�?jpg/jpeg/png/webp
单文件最�?5MB
�?yyyy/MM 分目�?数据库只�?URL
```

## 16. 验收步骤

### 16.1 编译

```bash
cd backend
mvn compile
```

必须成功�?
### 16.2 启动

```bash
mvn spring-boot:run
```

访问�?
```text
http://localhost:8080/api/doc.html
```

### 16.3 测试上传

调用�?
```text
POST http://localhost:8080/api/upload/image
```

期望�?
```json
{
  "code": 200,
  "data": {
    "url": "http://localhost:8080/api/images/2026/05/xxx.jpg"
  }
}
```

然后浏览器直接访问返回的 URL，图片必须能打开�?
### 16.4 测试首页

```text
GET /api/canteens/banners
GET /api/canteens
GET /api/canteens/images
GET /api/dishes/hot
```

都必须返回数组或对象，不允许返回占位字符串�?
### 16.5 测试菜品搜索

```text
GET /api/dishes?keyword=�?page=1&pageSize=20
```

期望返回分页对象或数组。建议统一分页对象�?
```json
{
  "records": [],
  "total": 0,
  "page": 1,
  "pageSize": 20
}
```

### 16.6 测试菜品详情

```text
GET /api/dishes/1
```

必须包含�?
```text
id
name
price
images
images
avgRating
ratingCount
collectCount
tags
description
canteenName
stallName
ratingDistribution
```

### 16.7 测试登录

```text
POST /api/auth/login
```

请求�?
```json
{
  "username": "20240001",
  "password": "123456"
}
```

期望返回 token�?
### 16.8 测试收藏

�?token�?
```text
POST /api/favorites/toggle
GET  /api/favorites
```

`GET /favorites` 必须返回菜品数据�?
### 16.9 测试评价

�?token�?
```text
POST /api/reviews
GET  /api/dishes/1/reviews
GET  /api/dishes/1
```

提交评价后，菜品详情的：

```text
avgRating
ratingCount
ratingDistribution
```

应该变化�?
## 17. 推荐开发任务拆�?
### 任务 A：图片存储和上传

1. 修改 `application.yml`�?2. 修改 `WebMvcConfig`�?3. 修改 `UploadServiceImpl`�?4. 修改 `UploadController`�?5. 验证返回 URL 可访问�?
### 任务 B：实体和 VO

1. �?`Canteen/Stall/Dish` 字段�?2. 新增 `Banner` 实体�?Mapper�?3. 新增 `BannerVO/CanteenInfoVO/StallDetailVO/DishDetailVO/UserStatsVO`�?4. 新增 JSON 图片字段解析工具�?
### 任务 C：食堂接�?
1. `/canteens/banners`
2. `/canteens`
3. `/canteens/images`
4. `/canteens/stallDetail`

### 任务 D：菜品接�?
1. `/dishes/hot`
2. `/dishes`
3. `/dishes/{id}`
4. 评分分布聚合�?
### 任务 E：认证和用户

1. 自动注册登录�?2. 当前用户工具类�?3. `/auth/profile`
4. `/auth/stats`

### 任务 F：收藏和评价

1. `/favorites`
2. `/favorites/toggle`
3. `/reviews`
4. `/dishes/{dishId}/reviews`
5. 同步菜品收藏数和评分�?
### 任务 G：测试和清理

1. `mvn compile`
2. `mvn test`
3. Knife4j 接口测试
4. 前端关闭 mock fallback 后联�?
## 18. 最终交付标�?
开发完成后应满足：

1. 后端可正常启动�?2. 所有前�?API 文档中的接口均可访问�?3. 所有接口返回统一 `Result` 格式�?4. 上传图片后，返回 URL 可直接在浏览器打开�?5. 数据库只保存图片 URL，不保存图片文件本身�?6. 首页、菜品列表、菜品详情、收藏、评价、个人页都能从真实后端获取数据�?7. 前端不再依赖 mock fallback 也能正常运行�?
