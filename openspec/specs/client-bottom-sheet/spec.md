# client-bottom-sheet Specification

## Purpose
统一小程序端全部「从底部弹出的抽屉/动作面板/选择器」的实现基座：骨架唯一真源、动作菜单与列表选择器通用化，禁止同构弹层跨页面各写一份手抄副本。

## Requirements

### Requirement: 底部弹层骨架唯一真源

所有底部弹出式弹层（认证表单、评价表单、动作菜单、列表/搜索选择器及未来新增的底部弹层）SHALL 复用共享骨架组件 `client/src/components/BaseSheet.vue` 承载如下能力：半透明遮罩（点击关闭）、顶部 grabber 横条、translateY 上滑开合动画、下拉关闭手势、`env(safe-area-inset-bottom)` 底部安全区、可选头部与内容区滚动。任何新增底部弹层 SHALL NOT 手抄上述骨架的遮罩/手势/CSS。

#### Scenario: 新增底部弹层

- **WHEN** 需要一个新底部弹层
- **THEN** 通过 BaseSheet 组合其专属内容实现，源码中不出现第二套 `.sheet-mask`/拖拽关闭函数/`.sheet-grabber` 手抄副本

#### Scenario: 既有弹层全部迁移到骨架

- **WHEN** 检查评价举报、评价动作、写评价、认证、关联选择、反馈位置选择六类既有弹层
- **THEN** 全部复用 BaseSheet，未迁移前的手抄骨架与重复 CSS 已删除

### Requirement: 动作菜单统一为通用 ActionSheet

「竖排图标+文字动作项」型菜单 SHALL 由公共组件 `client/src/components/ActionSheet.vue` 承载（以选项数据或插槽驱动，支持危险项样式、`isOwn` 权限分支与分享插槽），并 SHALL 取代原 `ReviewActionSheet`（评价）各自实现。评价入口的行为语义保持不变：本人删除 / 他人举报。

#### Scenario: 评价动作使用通用动作菜单

- **WHEN** 在菜品评价操作弹出动作面板
- **THEN** 渲染自 `ActionSheet`，本人显示删除、他人显示举报，危险项样式保持警示色

#### Scenario: 动态三点使用通用动作菜单

- **WHEN** 检查社区板块（动态列表 / 动态详情 / 我的发布）相关入口的动作面板
- **THEN** 该入口已随社区板块下线整体移除，动作菜单现仅由评价三点消费且仍渲染自 `ActionSheet`

#### Scenario: 原独立动作菜单组件已移除

- **WHEN** 检索 `ReviewActionSheet` 组件文件
- **THEN** 其不再作为独立文件存在，职责由 `ActionSheet` 承担

### Requirement: 列表/搜索选择器统一为通用 ListPickerSheet

「标题 + 可搜索列表 + 项选择（+确认）」型底部选择器 SHALL 由公共组件 `client/src/components/ListPickerSheet.vue` 承载，并 SHALL 取代 feedback 页内三份 `.loc-sheet` 副本。替换后各入口的选择交互与选中回传语义不变。

#### Scenario: 反馈页多级位置选择使用通用选择器

- **WHEN** 意见反馈页选择位置/食堂/档口/菜品（原有三处内联 `.loc-sheet`）
- **THEN** 全部渲染自 `ListPickerSheet`，页面内不再存在重复的 sheet 骨架副本

#### Scenario: 发布页关联选择使用通用选择器

- **WHEN** 检查发表内容页的「关联对象」选择
- **THEN** 发布动态页已随社区板块下线移除，该入口不存在；`ListPickerSheet` 现由反馈页多级位置选择消费

#### Scenario: 新增同类选择器必须复用

- **WHEN** 未来某页面需要「列表搜索选择」类弹层
- **THEN** 复用 `ListPickerSheet`，不复制一份页面内选择器

### Requirement: 表单型弹层保持独立语义但复用骨架

认证表单（AuthSheet/AuthForm）与写评价表单（ReviewComposer）属不同业务语义，SHALL NOT 并入动作菜单或列表选择器；它们 SHALL 只复用 BaseSheet 骨架并各自承载表单逻辑、键盘与提交状态。

#### Scenario: 认证弹层保留独立表单

- **WHEN** 页面触发学号邮箱认证
- **THEN** 弹出基于 BaseSheet 的 AuthSheet（含 AuthForm），不渲染为动作菜单或选择器

### Requirement: 列表选择器单选选中态以圆形单选呈现

`ListPickerSheet` 的列表行项 SHALL 以**单选**语义呈现选中态，并遵循以下规则（`selectedKey` 为 `null` 时 SHALL 无任何项呈选中态，「不关联」一类语义由消费方页面自行表达，不在列表内置固定项）：

- 每行右侧选中指示 SHALL 为「圆形单选」：未选中 SHALL 显示浅灰空心圆，选中 SHALL 显示主色圆点（或主色实心圆 + 内白点）；SHALL NOT 使用方形复选框、方形勾选块来标记选中。
- 行内无图标/无图片的占位区 SHALL NOT 渲染成可勾选的方框样式，避免用户误读为多选；无内容项不渲染疑似输入件的空块。
- 每行**整行** SHALL 为选中热区：点击行内任意区域（图标/图片/文字/右侧指示均含）SHALL 选中该项。
- 列表项之间 SHALL 以轻微间距区隔（不拥挤），选中行有清晰的浅色反馈底。

#### Scenario: 未选中项显示空心圆

- **WHEN** 查看选择弹窗中某项未选中行
- **THEN** 该行右侧呈现浅灰空心圆（非方框/非空位占位），点击行任意位置即选中该项

#### Scenario: 选中项显示主色圆点

- **WHEN** 某行处于选中态
- **THEN** 该行右侧圆形指示呈选中态（主色圆点/实心圆），行有浅色选中反馈且整行可再次点击切换

#### Scenario: 整行点击热区

- **WHEN** 用户点击行内文字、图片或空白区域（不限于右侧圆圈）
- **THEN** 该行被选中，单选语义下仅一个选项保持选中

### Requirement: 底部弹层拖拽关闭阻断背景滚动穿透

基于 `BaseSheet` 骨架的底部弹层（含 AuthSheet / ListPickerSheet / ActionSheet / 评价弹层等）打开期间，用户在弹层可见的非滚动区域（grabber、头部、页脚、普通正文）发起下拉手势时，SHALL NOT 引发遮罩背后页面/滚动区的滚动或拖动——弹层关闭手势 SHALL 仅由弹层自身 translateY 跟随，背景不得被带动。遮罩区的既有 touch 阻断保持不变。弹层**内部**的滚动容器（`scrollBody` 的 scroll-view、ListPickerSheet 的列表区等）SHALL 在弹层打开时继续提供自身的纵向滚动，不受该阻断影响。

#### Scenario: 拖 grabber 关闭时背景不动

- **WHEN** 用户在弹层 grabber（或非滚动区）处向下拖动以关闭弹层
- **THEN** 弹层跟随下移；其背后页面/滚动区不随拖动滚动或位移

#### Scenario: 弹层内部列表仍可滚动

- **WHEN** 弹层内容超高，用户在内部滚动区（如 ListPickerSheet 列表 / scrollBody）上下滑动
- **THEN** 内部正常滚动；同时遮罩后背景不发生穿透滚动

#### Scenario: 遮罩点击关闭与内部滚动互不干扰

- **WHEN** 用户在遮罩区点击、或在弹层内部滚动区滑动
- **THEN** 点击关闭语义与内部滚动各自生效，背景滚动均不穿透
