# client-filter-bar Specification

## Purpose

小程序共享的筛选栏单一组件：合并原「食堂胶囊 + 食堂下拉 + 价格弹层」三件套为 `FilterBar`，统一首页与发现页的「食堂 + 价格」筛选交互，作为筛选栏唯一真相源，消除跨页重复编排。

## Requirements

### Requirement: 共享筛选栏单一组件
`FilterBar` SHALL 以**单一组件文件**渲染食堂胶囊、价格胶囊、右侧常驻筛选 icon、食堂下拉面板与价格弹层（不再拆分为多个组件文件），并**自持**两表单的开关显隐状态（父级无需管理任何显隐开关；内部以单一 `activePanel` 表达，见 design Decision 2）。

#### Scenario: 渲染完整筛选栏
- **WHEN** 页面挂载 `<FilterBar>` 且传入 `canteens`
- **THEN** 展示食堂胶囊与价格胶囊；食堂下拉与价格弹层默认收起

#### Scenario: 单击胶囊展开对应面板
- **WHEN** 用户单击处于收起态的食堂胶囊
- **THEN** `FilterBar` 内部将食堂下拉设为展开；再次单击该胶囊则收起（见「两颗胶囊交互一致且表单互斥单开」）

### Requirement: 筛选栏单行布局与常驻筛选 icon
`FilterBar` SHALL 以单行呈现筛选栏，且该行 SHALL 撑满其宿主容器宽度；行内两颗按钮组（「全部食堂」+「全部价格」）与筛选 icon SHALL 呈**两端对齐**分布：按钮组占满 icon 左侧的全部剩余空间、两颗按钮在其中**平均分配**（各占 `flex:1` 等宽），筛选 icon 恒定贴该行最右侧。

#### Scenario: 布局与 icon 位置
- **WHEN** 筛选栏渲染完成
- **THEN** 筛选 icon 位于行最右侧且不随胶囊宽度变化；「全部食堂」与「全部价格」两按钮等分其左侧空间

#### Scenario: 长文案下 icon 仍贴最右
- **WHEN** 食堂名或价格文案超长
- **THEN** 文案仅在按钮内部省略，筛选 icon 位置不变、仍贴行最右侧且不被挤出屏幕

#### Scenario: 筛选 icon 不触发导航
- **WHEN** 用户点击最右侧筛选 icon
- **THEN** 不触发页面导航，且不影响任一表单的展开态

### Requirement: 展开态上下箭头翻转
每颗按钮 SHALL 携带上下箭头 icon：**表单收起时箭头朝下（▼），表单展开（按钮激活、红色）时箭头朝上（▲）**；箭头方向 SHALL 与对应表单显隐状态同步。

#### Scenario: 展开时箭头翻转
- **WHEN** 用户单击食堂胶囊展开食堂下拉
- **THEN** 食堂胶囊箭头由 ▼ 翻转为 ▲，按钮呈激活态（红）

#### Scenario: 收起时箭头复原
- **WHEN** 食堂下拉经再次单击胶囊 / 切换至价格胶囊 / 点击遮罩 / 选中某项而收起
- **THEN** 食堂胶囊箭头由 ▲ 复原为 ▼，按钮恢复非激活态

### Requirement: 筛选选中态两极色值与顶部按钮统一
`FilterBar` 顶部筛选按钮（食堂胶囊、价格胶囊、常驻筛选 icon）SHALL 采用统一的两极选中语言：**选中态** SHALL 为纯红底（`--color-primary`）+ 反白图标/文字/下拉箭头（`--color-on-primary`）；**未选中态** SHALL 为纯白底（`--bg-card`）+ 深灰图标/文字（`--text-secondary`）。两态之间 SHALL 仅切换底色与前景色，**几何属性（高度、圆角、左右内边距、图标大小、线宽、图标与文字位置）完全不变**，保证切换时只变色、不晃眼；展开态（按钮激活）的下拉箭头 SHALL 180° 上翻。

#### Scenario: 选中态仅变色不变形
- **WHEN** 食堂胶囊由未选中切换为选中（展开下拉）
- **THEN** 仅底色由纯白变为纯红、图标/文字/箭头由深灰变为反白，按钮高度/圆角/内边距/图标尺寸与位置均不变

#### Scenario: 未选中态中性纯白
- **WHEN** 价格胶囊处于收起态
- **THEN** 价格胶囊为纯白底 + 深灰图标与文字，视觉重量平稳、专门衬托红色选中按钮

#### Scenario: 展开价格弹层时箭头翻转
- **WHEN** 用户单击价格胶囊展开价格弹层
- **THEN** 价格胶囊箭头 180° 上翻为 ▲，且胶囊呈纯红选中态

### Requirement: 食堂名与价格文案内化计算
`FilterBar` SHALL 依据受控属性 `selectedCanteenId` 在 `canteens` 列表中查找食堂名用于胶囊回显；价格胶囊文案 SHALL 依据受控属性 `priceRange`（**单位：元**）直接以元表达（如 `{ min: 10, max: 20 }` → 胶囊显示「10-20 元」），**禁止对金额做裸算 `/100`，亦禁止将已是元的区间再次经 `yuanToFen`/`fenToYuan` 换算**。价格筛选全链路 SHALL 以「元」为唯一对外口径：`FilterBar` 的价格预设、自定义输入、`priceRange` prop、`price-select` emit、页面/store 持有的区间、提交给 `searchDishesPage` 的 `minPrice`/`maxPrice` 均表达为元；「元→分」仅在 api 层提交时发生一次。

#### Scenario: 未选食堂时文案
- **WHEN** `selectedCanteenId` 为 `null` 或不在 `canteens` 中
- **THEN** 食堂胶囊显示「全部食堂」

#### Scenario: 价格区间回显
- **WHEN** `priceRange` 为 `{ min: 10, max: 20 }`
- **THEN** 价格胶囊显示「10-20 元」（以元直显，非「1000-2000」）

#### Scenario: 价格单位不再二次换算
- **WHEN** 用户确认某预设区间（如 10–20 元）且该区间以元形式经 `price-select` 交由父级透传
- **THEN** 提交链路中该区间不被当作「分」再换算一次：到达 `searchDishesPage` 的值为元，实际向后端请求的 `minPrice`/`maxPrice`（分）恰为所选元值的 100 倍

### Requirement: 受控选中态与选择事件
`FilterBar` SHALL 通过 props 接收当前 `selectedCanteenId` 与 `priceRange`，并在用户确认选择时 emit `canteen-select(id: number | null)` 与 `price-select(range: { min?: number; max?: number })`；选中后 SHALL 自行关闭对应下拉。

#### Scenario: 选择具体食堂
- **WHEN** 用户在食堂下拉中选择食堂 X
- **THEN** `FilterBar` emit `canteen-select(X.id)` 并关闭食堂下拉，父级据此更新过滤

#### Scenario: 选择「全部」食堂
- **WHEN** 用户在食堂下拉中选择「全部」
- **THEN** `FilterBar` emit `canteen-select(null)` 并关闭下拉

#### Scenario: 确认价格区间
- **WHEN** 用户在价格弹层选择预设或自定义区间并确认
- **THEN** `FilterBar` emit `price-select(range)` 并关闭价格弹层

### Requirement: 两颗胶囊交互一致且表单互斥单开
食堂胶囊与价格胶囊 SHALL 交互一致：**单击**切换（toggle）其对应下拉/弹层——收起态时单击展开，已展开时再次单击同一胶囊收起；两颗胶囊对应的表单 SHALL **互斥**，任意时刻**最多一个表单展开、最多一个按钮处于激活态（红）**；从一颗已激活胶囊切换到另一颗时，SHALL **先收起当前表单、再展开目标表单**，两表单不重叠覆盖。筛选栏 SHALL NOT 依赖双击（double tap）手势来收起表单。

#### Scenario: 单击展开收起态面板
- **WHEN** 用户单击处于收起态的食堂胶囊
- **THEN** 食堂下拉展开，食堂胶囊激活为红

#### Scenario: 单击关闭已展开面板
- **WHEN** 用户单击已展开的价格胶囊
- **THEN** 价格弹层收起，价格胶囊恢复非激活态（与食堂胶囊同款行为）

#### Scenario: 双击关闭已展开面板
- **WHEN** 用户在 300ms 内连续两次单击（即双击）同一颗已收起的价格胶囊
- **THEN** 两次单击各自独立生效（先展开、再收起），最终价格弹层为收起态、价格胶囊为非激活态；该结果由单击 toggle 自然得出，不依赖任何双击手势识别

#### Scenario: 切换胶囊时前一个表单先收起
- **WHEN** 食堂下拉已展开，用户单击价格胶囊
- **THEN** 食堂下拉先收起（食堂胶囊恢复非激活态），随后价格弹层展开（价格胶囊激活为红），两表单不重叠覆盖

#### Scenario: 最多一个表单展开
- **WHEN** 任意交互序列结束后检查筛选栏状态
- **THEN** 同一时刻至多一个面板处于展开态，且仅其对应按钮为激活态（红）

### Requirement: 视觉与无障碍一致性
`FilterBar` 渲染的下拉面板（食堂）与价格弹层 SHALL 与顶部筛选按钮的选中语言**完全统一**，且面板容器 SHALL 与筛选行**视觉连成一体**（不是浮空的孤立卡片）：

- **面板容器（同面延续）**：面板底色 SHALL 与承载它的筛选行同面（`--bg-page`），SHALL 满宽（无左右外边距）、顶边为方角并与筛选行底边**无缝衔接**，使面板在视觉上自筛选行底部延续而出；底边 SHALL 为 `--radius-card` 圆角并带极淡柔阴影（`--shadow-card` 或同级）以表达展开层级；面板与筛选行之间、以及面板内部各分区之间 SHALL NOT 存在任何横线分隔（`border`）。`prefers-reduced-motion` 降级（仅透明度过渡，无位移/弹性）。
- **内容轴对齐**：面板左右内边距 SHALL 为 `--spacing-lg`（等同筛选行内容边），选项行/标题/自定义输入行的左右内边距 SHALL 为 `--spacing-md`（等同胶囊内边距），使面板内文字与胶囊内文字落在同一条内容轴上。
- **选项选中规则**：选中项 SHALL 为纯红底（`--color-primary`）+ 白色文字（`--color-on-primary`）+ 白色线性对勾图标，与顶部按钮选中态 100% 一致；未选中项 SHALL 为深灰文字（`--text-primary`/`--text-secondary`）+ 透明底，点击反馈用极浅灰底（`--bg-soft`），全程不碰红系、保持中性；所有选项行高一致、文字垂直居中、右侧对勾位置固定。
- **标题与节奏**：面板标题字重 SHALL 为 600（`--weight-semibold`），与全局模块标题规范统一。
- **自定义区**：价格弹层的自定义区间区 SHALL 仅以上方间距与预设区相隔，SHALL NOT 使用分隔线。
- **遮罩**：下拉/弹层展开时的背景遮罩 SHALL 为纯黑色半透明（`--overlay-scrim`，`rgba(0,0,0,0.4)`，不带任何红调），保证面板层级清晰且不污染主色。
- **图标线宽**：筛选图标、下拉箭头、对勾、收起箭头 SHALL 统一 2px 线宽、圆角端点，与全局图标风格一致。
- 胶囊 ≥44px 命中区保持不变。

#### Scenario: 减少动效偏好开启
- **WHEN** 系统开启 `prefers-reduced-motion`
- **THEN** 下拉/弹层展开收起不使用位移或弹性过冲动画（仅透明度过渡）

#### Scenario: 点击遮罩关闭
- **WHEN** 面板/弹层展开时用户点击遮罩区域
- **THEN** 对应面板/弹层关闭且不改变当前选择

#### Scenario: 面板与筛选行同面延续
- **WHEN** 展开食堂下拉或价格弹层
- **THEN** 面板底色与筛选行一致、满宽且顶边与筛选行底边无缝衔接，视觉上自筛选行延续而出，而非一张独立浮空的卡片

#### Scenario: 面板内无横线分隔
- **WHEN** 展开价格弹层查看预设与自定义区间
- **THEN** 预设区与自定义区之间无任何分隔线，仅以间距相隔

#### Scenario: 面板内容与胶囊同一内容轴
- **WHEN** 面板展开且筛选行同时可见
- **THEN** 面板内选项、标题与输入文字的左边缘与胶囊内文字左边缘对齐

#### Scenario: 食堂面板选中纯红
- **WHEN** 展开食堂下拉并选中某项
- **THEN** 选中项呈纯红底 + 白字 + 白对勾，未选项为深灰字 + 透明底 + 浅灰点击反馈

#### Scenario: 价格面板与食堂面板选中语言一致
- **WHEN** 展开价格弹层并选中某预设
- **THEN** 预设选中项与食堂面板选中项同为纯红底白字白对勾，无浅红色差异

#### Scenario: 遮罩无红调
- **WHEN** 任一面板展开
- **THEN** 背景遮罩为纯黑半透明，不混入任何红色调

### Requirement: 唯一筛选栏组件且无悬空引用
`FilterBar` SHALL 是小程序筛选栏的**唯一**组件；合并后 `HomeFilterChip`/`CanteenFilter`/`HomePriceSheet` 不得再存在，亦不得被任何页面或组件引用。其编译产物 SHALL 能解析所有被引用的组件，不得请求不存在的文件（含不得引用已删除的 `Pressable`）。

#### Scenario: 旧组件已删除且无残留引用
- **WHEN** 合并完成后对 `client/src` 执行引用静态检查
- **THEN** `HomeFilterChip`/`CanteenFilter`/`HomePriceSheet` 文件已删除，且全仓 grep 不含这三组件名与 `Pressable`

#### Scenario: mp-weixin 编译干净
- **WHEN** 清理 `client/dist` 后重新 `npm run dev:mp-weixin`
- **THEN** 编译不报 `ENOENT ... Pressable.wxml/.wxss` 或任何悬空组件错误

### Requirement: 筛选行与下方内容视觉连续（无分隔线）
承载 `FilterBar` 的筛选行 SHALL 与其正下方的内容区（`scroll-view`）**视觉连续**：二者 SHALL 使用同一背景面，且筛选行底部 SHALL NOT 存在任何分隔线（含 `border-bottom`）、阴影或留白间隙，使筛选区与内容区在视觉上呈现为一体。

#### Scenario: 首页筛选行无分隔线
- **WHEN** 查看首页红头之下的筛选行与其下方瀑布流内容区
- **THEN** 两者之间无发丝线、无阴影、无间隙，背景色连续

#### Scenario: 发现页结果态筛选行无分隔线
- **WHEN** 发现页处于搜索结果态、筛选行下方为结果列表
- **THEN** 筛选行与结果列表之间无分隔线，背景色连续
