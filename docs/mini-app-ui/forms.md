# 表单页合集（forms）

> **页组合并文档（2026-08-02）**：由 `publish-dish.md` / `publish-moment.md` / `submit-stall.md` / `review.md` / `feedback.md` 五份页面文档合并而来（方案 A：按页组合并，只保留每页差异化内容）。
> 五页均为「提交类表单页」，共用同一套表单模式（见下方「通用表单模式」），各自仅字段/校验/提交行为不同。
> 路由与代码：见各页小节；全部为 `pages-user` 分包（publish-* / submit-stall）或主包（feedback）或 `pages-detail` 分包（review）。

## 通用表单模式（五页共用，不再逐页重复）

- **页面壳**：`Header showBack` + `scroll-view`；提交主按钮单主 CTA（`AppButton` loading/disabled 态清晰）。
- **分区**：`CardSection`（title 渲染 accent 标题）承载各字段区；`ImageUploader`（180rpx 单元格，移除/加号 IconSvg）承载图片；`textarea` + 字数计数。
- **无障碍**：字段可见 label；必填 `*` 用 `--color-error`；**裁定「区块标题即 label」策略成立**（textarea/input 靠 CardSection 标题 + placeholder 承担 label 语义）。
- **字号**：字段输入/描述统一 `font-size: 32rpx`（`--font-body` 28rpx 仅作字段 label）；字数计数 `tabular-nums`。
- **Token**：`--bg-page/--bg-card/--text-primary/secondary/tertiary`、`--color-primary`(选中/激活)、`--color-error`(必填)、`--border-color`(字段分割)、`--radius-card/--radius-icon/--radius-tag/--radius-btn`、`--spacing-*`(4/8pt)、`--font-body/--font-aux/--font-tiny`。
- **按压**：可点元素（chip/type-btn/picker/图片加号）`.active scale(var(--press-scale))`；AppButton 内部同。
- **提交流**：校验（登录 `requireAuth` + 必填/格式）→ API 提交 → toast → `navigateBack`。
- **红线**：金额仅在 api 层分↔元（页面禁 `/100` 裸算）；无 emoji（图标全 IconSvg 注册 key）；无底部 Sheet 的页用原生 picker/chooseImage。

---

## 1. 发布菜品（publish-dish）

- 路由：`/pages/pages-user/publish-dish`；源文件 `src/pages/pages-user/publish-dish.vue`
- 定位：发布/编辑菜品（编辑态 `?id` 预填）；提交后 `status=pending` 待审核。
- 入口：**方案 Y**——经「我的」→「我要贡献」弹层（`ContributeSheet`）「发布菜品」进入（不内联，独立表单页）。

### 布局要点
1. 基本信息：名称 input（req）、价格 input（type=digit，**元**）。
2. 所属食堂/档口：两个 picker（档口依赖食堂，级联加载）。
3. 口味标签：`tag-grid` 多选 chip（必吃推荐/招牌菜/辣味/素食/面食/清真/西餐/甜品）。
4. 菜品图片（≤9）：`ImageUploader :max="9"`。
5. 描述：`textarea`（maxlength 500）+ 字数计数。

### 关键交互
- 价格以「元」输入，api 层 `yuanToFen` 转分（页面无 `/100`）。
- 选食堂后级联加载档口；标签多选 toggle（底色+文字高亮）。
- 提交校验（名称/价格>0/食堂档口）→ `publishDish`/`updateMyDish` → toast → navigateBack。

### 特有裁定/整改
- ✅ 已整改：tag-chip 已补按压反馈（`.tag-chip:active scale(var(--press-scale))`）；字数计数 tabular-nums；输入 32rpx。

---

## 2. 发布动态（publish-moment）

- 路由：`/pages/pages-user/publish-moment`；源文件 `src/pages/pages-user/publish-moment.vue`
- 定位：发布/编辑动态（编辑态 `?id` 预填并重提审核）。
- 入口：**发动态唯一主入口**（方案 Y）；发菜品/提交档口经 ContributeSheet 分流到各自独立表单页，本页不内联。

### 布局要点
1. 正文 `textarea`（auto-height，maxlength 500）+ 字数计数。
2. `SectionTitle 关联对象`（extra「选填」）+ `related-picker`（开 `RelatedPickerSheet`）。
3. `SectionTitle 图片`（extra「最多 9 张」）+ `ImageUploader :max="9"`。
- 底部 `submit-bar`（fixed，`--action-bar-height` + 安全区避让）：AppButton（发布/保存并重新提交）。

### 关键交互
- 超 500 字禁提交；关联对象 Sheet 选中高亮、二次点击取消、确认关闭。
- `RelatedPickerSheet`：spring 0.8/0.3 + ic-close + 下拉关闭 + reduced-motion（通用弹层规范）。
- 提交：`requireAuth` → `publishMoment`/`updateMoment` → toast → navigateBack。

### 特有裁定/整改
- ✅ 字数计数 tabular-nums；textarea 32rpx；「区块标题即 label」成立。

---

## 3. 提交档口·食堂（submit-stall）

- 路由：`/pages/pages-user/submit-stall`；源文件 `src/pages/pages-user/submit-stall.vue`
- 定位：提交档口或补充食堂信息；支持 `?type=canteen` 预选；提交 `status=pending`。
- 入口：经「我要贡献」弹层「提交档口·食堂」进入（方案 Y）。

### 布局要点
1. 类型切换 `.type-switch`：两个 `type-btn`（提交档口/补充食堂，激活主色填充，**图标+文字**）。
2. 基础信息：名称(input, req)、所属食堂(picker，仅 stall)、位置(input)、营业时间(input，仅 stall)。
3. 图片（≤9，`type` 决定文案）：`ImageUploader :max="9"`。
4. 描述：`textarea`（maxlength 500）。
- 底部 `.submit-wrap`（滚动内）：AppButton「提交审核」。

### 关键交互
- 类型切换显隐字段（`type=canteen` 时隐藏食堂/营业时间）；选食堂 picker 级联。
- 提交校验（名称/档口需食堂）→ `post('/my/stalls', payload)` → toast「提交成功，待审核」→ navigateBack。

### 特有裁定/整改
- ✅ 类型切换图标已补：`IconSvg name="stall"` / `name="canteen"`（均已注册，激活态主色填充+图标+文字三重语义）。
- 🔧 **开发侧整改**：① `.field-input`/`.desc-input` 字号由 28rpx 提到 32rpx（submit-stall.vue）；② 营业时间 input 补 `tabular-nums`。

---

## 4. 发表评价（review）

- 路由：`/pages/pages-detail/review`；源文件 `src/pages/pages-detail/review.vue`
- 定位：对菜品发表评价；同一用户重复评价由后端 400 冲突提示。

### 布局要点
1. 评分：`Rating v-model :readonly=false :show-text :star-size=48`（star 图标）。
2. 评价内容：`textarea`（maxlength 500）+ 字数计数。
3. 图片（≤3）：`image-list`（预览 + 移除 close + 加号上传，`uni.chooseImage`）。
4. 同步到社区动态：`share-row`（自绘 Apple toggle，`--color-primary` 开态）——评价与动态打通（2026-08-03）。
- 底部 `submit-bar`（fixed，`--action-bar-height` + 安全区）：AppButton「提交评价」（disabled 当 rating=0 或内容空；loading=uploading）。
- 三区块小标题用纯 text `.section-label`（字段语义 label，允许不强制 accent 条）。

### 关键交互
- 评分默认 5；内容非空且 rating>0 才可提交；图片逐张 `uploadImage`（单张失败跳过）→ `submitReview` → toast → navigateBack。
- 移除按钮用 `--badge-dark-bg`/`--badge-dark-text`（暗底白字语义 token）。
- 「同步到社区动态」：勾选后提交 `shareToMoment=true`，后端在评价落库后同步生成一条**approved**（评价可见即动态可见，不审核）关联菜品的动态，直接上社区广场；评价无正文时不生成。

### 特有裁定/整改
- ✅ 已整改：图片加号按压反馈（`.image-upload:active scale(var(--press-scale))`）；字数计数 tabular-nums；textarea 32rpx。
- ✅ 2026-08-03 评价与动态双向打通：评价 → 动态（本页开关同步）；动态 → 评价（动态详情点关联菜品打开 `DishDetailSheet` 评价区）；菜品 → 动态（`DishDetailSheet`「关联动态」区块）。契约：`ReviewReq.shareToMoment`；`MomentService.publishFromReview`（approved）。

---

## 5. 意见反馈（feedback）

- 路由：`/pages/feedback/index`；源文件 `src/pages/feedback/index.vue`
- 定位：提交意见反馈；登录后提交至后端。

### 布局要点
1. `CardSection title="反馈类型"`：类型 chip 行（suggestion/error/other，选中高亮）。
2. `CardSection title="反馈内容"`：`textarea`（maxlength 1000, auto-height）+ 字数计数。
3. `CardSection title="联系方式（选填）"`：`input` 邮箱/微信。
- 底部 `submit-bar`（fixed，`--action-bar-height` + 安全区）：AppButton「提交反馈」。

### 关键交互
- 类型 chip 单选（底色+文字高亮）；超 1000 字禁提交；`requireAuth` → `submitFeedback` → toast「提交成功」→ navigateBack。

### 特有裁定/整改
- ✅ 字数计数 tabular-nums；输入 32rpx；「区块标题即 label」成立。

---

## 6. 个人信息（profile-edit）

- 路由：`/pages/pages-user/profile-edit/index`；源文件 `src/pages/pages-user/profile-edit/index.vue`（分包 pages-user）
- 定位：编辑昵称 / 头像等个人信息；保存提交后端。
- 布局要点：头像（单图上传，复用 ImageUploader）+ 昵称输入；底部 `submit-bar`（`--action-bar-height` + 安全区）AppButton「保存」。
- 关键交互：头像经 `uni.chooseImage` 单图上传（ImageUploader/单图例外已登记）；昵称校验非空/长度 → `userStore.updateProfile`（`PUT /auth/profile`）→ toast + navigateBack。
- 特有裁定/整改：头像上传走 ImageUploader 复用；昵称超长禁提交。

---

## 交付前验证（表单页通用）

- [ ] 375px：各分区/字段不溢出；图片格整齐。
- [ ] 暗色对比：必填 `*`/选中态/disabled 文字 ≥4.5:1（token 满足，交付前统一真机实测）。
- [ ] 44pt：chip/input/picker/提交按钮 ≥44pt。
- [ ] 安全区：fixed 提交栏不被 home indicator 遮挡。
- [ ] 动态字号：输入/标签不截断。
