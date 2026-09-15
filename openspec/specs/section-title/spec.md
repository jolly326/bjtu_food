# Section Title Specification

## Purpose

统一全站分区/模块标题（SectionTitle）的视觉呈现：移除标题左侧红竖条装饰、以纯文本标题表达层级，并对标题右侧附加说明（选填、计数等）给出弱化规范，作为共享标题组件的视觉契约。

## Requirements

### Requirement: 分区标题无竖线纯文本

全站经由共享组件 `SectionTitle` 渲染的分区/模块标题 SHALL 为**无竖线纯文本标题**：标题左侧 SHALL NOT 渲染任何竖条 / accent 装饰线（`bar` 装饰能力整体移除）；标题层级 SHALL 由字号、字重与间距表达，遵循 `client-visual-language` 的标题档位令牌。`SectionTitle` 组件 SHALL NOT 再提供竖线开关属性，全站既有调用点 SHALL 同步收敛为无竖线样式。

#### Scenario: 卡片式模块标题无竖线

- **WHEN** 查看菜品详情、意见反馈（含配图区块）等卡片式模块的分区标题
- **THEN** 标题左侧无任何红竖线/竖条装饰，为纯文本标题，层级由字号字重呈现

#### Scenario: 移除竖线开关能力

- **WHEN** 在代码中检索 `SectionTitle` 的 `bar` 属性用法
- **THEN** 组件不再存在该属性；以 `:bar`/`bar=` 传参的调用点已清除或与无竖线默认一致

### Requirement: 标题右侧附加说明弱化层级

分区/模块标题右侧的附加说明（如「选填」「最多 3 张」「x/3」）SHALL 以更小字号 + 浅灰（`--text-tertiary`）呈现，并与标题保持间距；其视觉权重 SHALL 显著低于标题，不得与标题同字号同字色造成并列层级。

#### Scenario: 附加说明弱于标题

- **WHEN** 查看标题旁带有「选填」「最多 3 张」等附加说明的模块
- **THEN** 附加说明为小号浅灰、与标题明显拉开层级与间距
