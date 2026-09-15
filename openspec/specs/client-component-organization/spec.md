# client-component-organization Specification

## Purpose
约束 `client/src/components/` 与 `pages/<包>/` 之间组件的归属判定，以微信小程序分包边界为准，防止「单包组件滞留公共层」或「跨包组件被下沉到分包导致越界引用」。

## Requirements

### Requirement: 组件归属按跨包复用边界判定

前端组件 SHALL 按实际消费者归属：
- **A 类（跨多个页面包共用）** SHALL 位于 `client/src/components/`（该目录编入主包，微信分包可引用）；
- **B 类（仅单一页面包使用）** SHALL 下沉至该页面包的 `pages/<包>/` 目录，与页面 colocation；
- **C 类（仅被某一公共组件内部使用）** SHALL 跟随其宿主壳组件所在层存放，不得机械下沉到任一页面包。

判定依据 SHALL 是 import 引用全集（`components/` 平铺、显式 import，无 easycom 自动注册），不得凭名称或目测猜测。任何「单一页面包组件」不得留在 `components/` 公共层。

#### Scenario: 新增单包页面局部组件

- **WHEN** 新增一个只被 `pages/dish/` 页面使用的局部组件
- **THEN** 该组件文件放置于 `pages/dish/` 目录（或 `pages/dish/` 下的私有子目录），不从 `components/` 引用且不在 `components/` 建立该组件

#### Scenario: 新增跨分包公共组件

- **WHEN** 新增的组件将被两个及以上不同页面包引用
- **THEN** 该组件必须放置于 `client/src/components/`（主包），不能落入任一 `pages/<包>/` 分包目录

#### Scenario: 私有子件跟随宿主

- **WHEN** 一个组件仅被另一个公共弹层内部引用（如 `AuthForm` 之于 `AuthSheet`）
- **THEN** 该子件留在宿主所在层（`components/`）随宿主发布，不因「只被一处引用」而迁入 `pages/` 任一包

### Requirement: 单包组件迁移完成且引用一致

下列曾经滞留公共层的单包组件 SHALL 全部位于其唯一使用页面包，并同步所有引用路径：

- `ReviewItem`、`ReviewComposer`、`ImageSwiper` → `pages/detail/dish/`;
- `ImageFallback` → `pages/mine/`（随「我的」页目录由 `profile` 收敛）。

迁移后 `client/src/components/` SHALL 不存在上述文件；全仓 SHALL NOT 存在指向旧公共层路径的 import（如 `@/components/ReviewItem.vue`），亦 SHALL NOT 存在指向已删除页面目录的路径引用（`pages/publish-content`、`pages/publish-moment`、`pages/dynamic`、`pages/detail/moment`、`pages/me/publish-mine`、`pages/profile-edit`）；URL 或模块路径一律为收敛后的位置。凡被判定为 B 类（仅单一页面包使用）的组件 SHALL 下沉，不得滞留主包。

#### Scenario: 全仓引用已指向新路径

- **WHEN** 在 `client/src` 内检索旧路径（如 `@/components/ReviewItem.vue`、`components/ImageFallback.vue`、`pages/publish-moment/`、`pages/profile-edit/`）
- **THEN** 无任何命中；页面内对该组件的引用使用收敛后的相对路径与页面目录

#### Scenario: 已无消费者的公共组件被移除

- **WHEN** 审查 `client/src/components/` 下组件与其 import 引用全集
- **THEN** 不存在零消费者的组件；历史案例：UGC 配图组件曾随其唯一消费方消失而删除（2026-09），2026-09-13 随评价/反馈配图恢复后按本 capability 原则重建为「评价弹层 + 反馈表单」≥2 页复用的公用组件，不构成零消费者残留

#### Scenario: 分包构建通过

- **WHEN** 重新执行 `client` 的小程序类型检查与 `mp-weixin` 构建
- **THEN** 无跨分包 import 错误，下沉组件的引用方与其所在分包一致

### Requirement: 组件归属变更前先核对消费者

执行组件移动或从公共层下沉前，SHALL 先以 import 引用全集核对消费者归属（A/B/C），只有归属为 B 的单包组件才可下沉，归属为 A 或 C 的组件 SHALL NOT 下沉。

#### Scenario: 疑似单包组件实际被多包引用

- **WHEN** 想下沉某组件但检索到它同时被两个以上页面包 import
- **THEN** 不执行下沉，该组件留在 `client/src/components/`（主包）
