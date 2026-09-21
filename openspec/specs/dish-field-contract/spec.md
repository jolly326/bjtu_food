# Dish Field Contract Specification

## Purpose

定义菜品信息对学生端公开的字段集与语义（价格、描述维度、位置表达、标签与浏览计数），作为列表页与详情页共同消费的稳定契约，并明确被删除字段不再以任何形式回流。

## Requirements

### Requirement: 公开菜品字段集

对小程序公开的菜品对象 SHALL 恰为 15 个字段：`id`、`name`、`price`、`originalPrice`、`description`、`images`、`stallName`、`canteenName`、`floor`、`avgRating`、`ratingCount`、`dietType`、`ingredients`、`flavorTags`、`serveTemp`。列表与详情 SHALL 返回同一字段集（详情额外附评分分布）。以下字段 SHALL NOT 再出现于公开响应：`status`（公开接口恒只返回在售）、`createdAt`、`canteenId`、`stallId`、`viewCount`、`promoPrice`、`tags`、`spiceLevel`、`region`、`windowNo`、`updatedAt`、`latitude`、`longitude`。

#### Scenario: 详情与列表字段一致

- **WHEN** 分别请求菜品列表与菜品详情
- **THEN** 行对象字段集相同（详情仅多出评分分布），共 15 个字段

#### Scenario: 被删字段不出参

- **WHEN** 检查任一公开菜品响应
- **THEN** 响应中不存在 `status`、`createdAt`、`canteenId`、`stallId`、`viewCount`、`promoPrice`、`tags`、`spiceLevel`、`region`、`windowNo`、`updatedAt`、`latitude`、`longitude` 任一字段

### Requirement: 价格语义与展示口径

`price` SHALL 表示**现价**（当前实际售价，已含折扣）；`originalPrice` SHALL 表示**原价**（可空）。「有折扣」的唯一判据 SHALL 为 `originalPrice` 有值且大于 `price`，此时界面 SHALL 在原价上呈现删除线。价格展示的唯一数据源 SHALL 为 `price`：界面 SHALL 常显 `price` 作为现价，SHALL NOT 出现「存在某第二个价格字段时改显该字段、不存在时改显 `price`」的双源切换。系统 SHALL NOT 提供第三个价格字段。

#### Scenario: 有折扣的展示

- **WHEN** 某菜 `originalPrice` 大于 `price`
- **THEN** 界面常显 `price` 作为现价，并并列展示带删除线的 `originalPrice`

#### Scenario: 无折扣的展示

- **WHEN** 某菜 `originalPrice` 为空或不大于 `price`
- **THEN** 界面只展示 `price`，无删除线价格

#### Scenario: 不存在折扣价字段

- **WHEN** 检查公开菜品响应与端上数据模型
- **THEN** 不存在除 `price` / `originalPrice` 之外的任何价格字段，且展示逻辑不依赖任何历史折扣价字段

### Requirement: 描述四维

菜品描述维度 SHALL 为**荤素 / 主料 / 口味 / 冷热**四维，分别由 `dietType`（荤 / 半荤 / 素 / 清真）、`ingredients`（主料）、`flavorTags`（口味）、`serveTemp`（热食 / 常温 / 冰）承载。原「辣度」与「风味 / 菜系」维度 SHALL 不再作为独立字段存在（辣度语义并入口味）；菜品列表查询 SHALL NOT 接受辣度筛选参数，学生端 SHALL NOT 提供辣度筛选入口。

#### Scenario: 四维随菜品返回

- **WHEN** 请求任一菜品
- **THEN** 响应包含 `dietType`、`ingredients`、`flavorTags`、`serveTemp` 四个字段，且不含辣度 / 风味字段

#### Scenario: 辣度筛选不再存在

- **WHEN** 用户在首页筛选区查看筛选维度，或调用菜品列表接口
- **THEN** 不存在辣度筛选入口，接口不接受辣度参数；传入该参数不生效

### Requirement: 位置表达（无坐标与距离）

菜品的位置表达 SHALL 仅为**食堂名称 + 楼层 + 档口名**。公开菜品响应 SHALL NOT 含任何坐标字段；学生端 SHALL NOT 计算或展示距离，SHALL NOT 申请定位权限，SHALL NOT 以任何兜底坐标生成「距离」文案。服务端 SHALL NOT 计算或下发距离。食堂坐标列 SHALL 从库中移除（幂等迁移），且系统 SHALL NOT 在地图上呈现「距你 X 米」之类的派生信息。

#### Scenario: 响应无坐标

- **WHEN** 请求菜品列表或详情、或食堂字典
- **THEN** 响应中不含纬度 / 经度等坐标字段

#### Scenario: 无定位也能完整浏览

- **WHEN** 用户拒绝或未授予定位权限（或所在端无定位能力）
- **THEN** 菜品卡片与详情页正常展示位置（食堂 · 楼层 · 档口名），且不出现任何距离文案或定位引导

#### Scenario: 端上不声明定位能力

- **WHEN** 检查小程序配置
- **THEN** 不存在定位权限声明与定位接口白名单条目

### Requirement: 标签字段下线

菜品标签字段 SHALL 全链不存在：公开响应、管理端响应与请求体、列表查询参数（标签筛选）与各端展示 SHALL 均不含标签。系统 SHALL NOT 保留仅服务标签的展示组件或枚举映射。

#### Scenario: 标签不出参也不可筛

- **WHEN** 请求菜品列表 / 详情，或在列表查询中传入标签参数
- **THEN** 响应不含标签字段，标签筛选参数不生效

#### Scenario: 界面无标签展示

- **WHEN** 查看首页卡片与详情页信息卡
- **THEN** 不出现标签 chips

### Requirement: 浏览计数的对外可见性

浏览量 SHALL 仅用于热度排序派生，SHALL NOT 作为公开响应字段出参；其统计口径 SHALL 保持「一直累计、不清零」，且同一用户对同一菜品每日只计一次。管理端 SHALL NOT 展示浏览量。

#### Scenario: 浏览量不出参

- **WHEN** 检查公开菜品响应与管理端菜品响应
- **THEN** 均不含浏览量字段，热度排序仍按既有权重口径生效

#### Scenario: 累计口径不变

- **WHEN** 同一用户当日重复浏览同一菜品
- **THEN** 浏览量只增加一次，且历史累计值不被清零
