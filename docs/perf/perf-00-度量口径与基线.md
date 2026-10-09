# 性能度量口径与基线（P0 冻结）

> 本文是后续 P1→P4 每一轮优化的**对照基准**。所有数字都可由一条命令复现，
> 且刻意只收录**同口径可自比**的量——优化是否有效，只跟本文的表比，不跟感觉比。

## 1. 复现命令

| 侧 | 命令 | 产物 |
| --- | --- | --- |
| 服务端 | `cd server && mvn -q test -Dtest=DishReadPathBenchmarkTest,IpRateLimiterBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false` | 控制台 `METRIC\|key\|value\|unit\|note` 行 + `target/perf-metrics.tsv`（追加写） |
| 服务端（P1 缓存复测，同口径对照） | `cd server && mvn -q test -Dtest=DishCacheBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false` | 同上（`*_cached` 系列指标，见 §8） |
| 客户端（静态） | `cd client && npm run measure:perf` | 同上格式 + `dist/perf-metrics.tsv` |
| 客户端（请求层运行时） | `cd client && npm run probe:request` | 同上格式（stdout） |

度量实现：`server/src/test/java/com/bjtufood/perf/PerfMetrics.java`、
`client/scripts/measure-perf.mjs`、`client/scripts/probe-request-layer.mjs`。

## 2. 口径边界（先说清楚这些数字**不能**说明什么）

本机没有可用的 MySQL 实例，也没有真机/微信开发者工具环境，因此：

- **Mapper 调用次数**是数据库成本的**下界**，不是 SQL 真实耗时。它回答「一次请求要过几次库」，
  缓存化后这个数会掉，DB 压力必然跟着掉；但掉 1 次调用不等于省下固定的毫秒数。
- **`latency_ms_*`** 是 JVM 内**聚合算法**在合成数据（5000 行 attributes JSON）上的耗时，
  不含网络、不含 SQL 执行。它只用于「同一算法优化前后」的自比。
- **限流吞吐**是纯内存结构在 8 线程下的操作数/秒，反映的是**锁竞争形态**，不是接口 QPS 上限。
- **包体积**是 `dist/build/mp-weixin` 下**未压缩源文件**的字节合计，与微信开发者工具显示的
  包体积口径不同（后者含自身压缩与统计差异），只用于同口径自比。
- **请求次数**由探针在 Node 里跑真实 `src/api/http.ts` 数出，传输层被桩替代：
  数的是「逻辑请求 → 传输出口」的次数，不含真实网络与后端耗时。

## 3. 服务端基线（3 次运行，取中位数；波动区间一并给出）

| 指标 | 基线 | 单位 | 波动 | 说明 |
| --- | ---: | --- | --- | --- |
| `server.mapper_calls.dish_detail` | 3 | 次/请求 | 稳定 | 详情联表 + 浏览计数自增 + 维度字典 |
| `server.mapper_calls.dish_attributes_edit` | 4 | 次/请求 | 稳定 | 存在性校验 + 单菜 attributes + 维度字典 + 全库 attributes 扫描 |
| `server.mapper_calls.dish_list` | 1 | 次/请求 | 稳定 | 单条分页联表查询 |
| `server.mapper_calls.dish_views` | 1 | 次/请求 | 稳定 | 每次进入首页都查一次在售大类集合 |
| `server.mapper_calls.guess_like` | 1 | 次/请求 | 稳定 | 每次进入发现态都查一次 |
| `server.dish_attributes_edit.latency_ms_cold` | 33.46 | ms | 22.2 ~ 34.1 | 预热后首次调用（rows=5000, dims=4） |
| `server.dish_attributes_edit.latency_ms_warm` | 11.67 | ms | 9.6 ~ 40.6 | 同参数重复 4 次的均值 |
| `server.payload_bytes.dish_list_page` | 2502 | B | 稳定 | **10 行/页**（首页 `HOME_PAGE_SIZE`；**搜索页是 20 行**，其体积另计） |
| `server.payload_bytes.dish_list_page_non_null` | 2397 | B | 稳定 | 对照：`null` 字段不下发 |
| `server.payload_bytes.dish_detail` | 753 | B | 稳定 | 公开 11 字段 |
| `server.payload_bytes.dish_detail_non_null` | 732 | B | 稳定 | 对照：`null` 字段不下发 |
| `server.payload_bytes.dish_attributes_edit` | 1073 | B | 稳定 | 4 维 × 每维 20 个参考候选 |
| `server.rate_limiter.throughput_ops_per_sec_abuse` | 1 088 796 | ops/s | 0.90M ~ 1.42M | 生产口径（≤2 条/分钟），8 线程、多数走拒绝分支 |
| `server.rate_limiter.throughput_ops_per_sec_normal` | 1 866 188 | ops/s | 1.30M ~ 2.15M | 宽松规则（≤100/分钟），全部放行 |

**读数结论**：

1. `warm ≈ cold`（11.7ms vs 33.5ms，且 warm 波动高达 4 倍）→ 候选值聚合**完全没有缓存**，
   每次编辑弹层打开都重扫全库 attributes。这是服务端最明确的一处可优化点。
2. `NON_NULL` 只省下 2.8%（详情）~ 4.2%（列表页）的字节 → **收益小**。
   值得做（顺手、零风险），但不该被当成「序列化优化」的主要理由；真正的大头在缓存与请求次数。
3. 限流器在 8 线程下已到 10⁶ ops/s 量级，但**拒绝分支反而比放行分支慢约 40%**
   （1.09M vs 1.87M）：拒绝路径同样要拿全局锁并遍历窗口，且 abuse 场景下 key 基数大 8000 倍。
   单把 `synchronized` 锁是唯一的扩展性约束——优化方向是**消除全局锁**（分片 / 无锁），
   而不是调大配额。
   （两场景的 key 基数相同，都是 8000 个 IP，差别只在走哪条分支，
   因此这 40% 的差值可直接归因于**拒绝分支的临界区成本**——它同样要独占整把锁。）
4. **详情端点是「非幂等 GET」** —— 每次 `200` 向 `dish_view_log` INSERT 一行浏览明细（见上表 `dish_detail` 的"浏览明细写入"）⇒ **详情端点不可缓存、不可 CDN 化**（缓存会让浏览量失真）；网页/客户端侧据此**不得对 `GET /dishes/{id}` 做响应缓存**（见 [client A3 菜品详情](../func/client/A-浏览与发现/A3-菜品详情.md)）。若将来要上 CDN / 缓存，**必须先**把浏览量改为异步或去重计数，并**重跑本基线**。
5. **两处基线随「取值字典 / 视图表驱动」落地必然变化，须重跑**：① `server.mapper_calls.dish_attributes_edit`（当前含"全库 attributes 扫描"，A4 落地后改为查取值字典表）；② `server.mapper_calls.dish_detail`（当前 3 次含"维度字典"，A4 落地后出参需按**取值 ID 翻译中文**，计数与耗时都会变）。

## 4. 客户端静态度量基线

> 采集时点：`HEAD=db486aa0`（工作区含**其它并行工作流**的未提交改动：评价接口改造 +
> `openapi.json` / `api.d.ts` 重新生成 + 若干死导出清理）。结构性指标（源码行数、死导出数）
> 会随 HEAD 与并发改动漂移，因此**每一轮采集都必须同时记下当次的 HEAD 与工作区脏状态**，
> 否则「优化后变好了」可能只是别人顺手删了代码。

| 指标 | 基线 | 单位 | 读数 |
| --- | ---: | --- | --- |
| `client.bundle.total_kb` | 431.76 | KB | 233 个产物文件（未压缩口径） |
| `client.bundle.main_kb` | 315.41 | KB | **占产物 73%**——分包还没帮到主包 |
| `client.bundle.subpackage.detail_kb` | 32.79 | KB | 详情页包 |
| `client.bundle.subpackage.correction_kb` | 37.77 | KB | 菜品问题反馈包（最大的分包） |
| `client.bundle.subpackage.feedback_kb` | 11.38 | KB | 意见反馈包 |
| `client.bundle.subpackage.auth_kb` | 7.76 | KB | 认证包 |
| `client.bundle.subpackage.my-reviews_kb` | 7.76 | KB | 我的评价包 |
| `client.bundle.subpackage.notifications_kb` | 6.94 | KB | 通知包 |
| `client.bundle.subpackage.privacy_kb` | 6.11 | KB | 隐私包 |
| `client.bundle.subpackage.profile_kb` | 5.83 | KB | 个人主页包 |
| `client.bundle.largest_file_kb` | 115.29 | KB | `static/images/home-bg.jpg`——**单张背景图占产物 27%**，全在主包里 |
| `client.src.loc` | 14 123 | 行 | 101 个源码文件（排除 generated / static） |
| `client.vue.deep_list_refs` | 13 | 处 | `ref<数组>`：逐元素深层代理，大列表首要优化位 |
| `client.vue.shallow_ref_usages` | 0 | 处 | 浅响应用量为零——深响应开销一分未省 |
| `client.vue.computed_usages` | 93 | 处 | 每处都是一个依赖订阅 |
| `client.vue.watch_usages` | 9 | 处 | 同上 |
| `client.image.tags` | 19 | 处 | 模板内 `<image>` 总数 |
| `client.image.lazy_load_coverage` | 0% | % | **19 个 `<image>` 无一带 `lazy-load`**（UI 稿的目标态是带 `lazy-load`，**实测未落地**） |
| `client.console_calls` | 16 | 处 | 生产端残留日志（小程序里是真实开销 + 外泄内部结构） |
| `client.unused_exports` | 6 | 个 | `DishEditAttribute`、`SurfacedError`、`RequestData`、`UPLOAD_TIMEOUT_MS`、`createApp`、`AttributeEditor` |
| `client.api.call_sites` | 50 | 处 | 40 个 api 请求函数的调用点总数 |
| `client.api.dictionary_call_sites` | 6 | 处 | 字典类端点调用点（缓存 / 合并的作用面） |

**读数结论**：主包 315KB 里有 115KB 是一张 `home-bg.jpg`——**先压图，再谈代码瘦身**；
深响应容器 13 处 / `shallowRef` 0 处、图片懒加载 0%，是两处「零风险、口径明确」的改进位。

## 5. 客户端请求层运行时基线（真实 `http.ts` + 桩传输层，数传输出口次数）

| 场景 | 基线 | 优化后期望 | 该场景在问什么 |
| --- | ---: | ---: | --- |
| `concurrent_same_get` | 5 | **1** | 5 个组件同一时刻要同一份字典——并发同源请求有没有被合并 |
| `repeated_same_get` | 3 | **1** | 退出再进首页，同一份字典重发 3 次 |
| `concurrent_same_post` | 2 | **2** | 反向断言：写操作**不得**被合并/缓存 |
| `concurrent_distinct_get` | 2 | **2** | 反向断言：不同参数的 GET **不得**被误合并 |
| `first_screen_home` | 4 | 4 | 首屏真实序列：`/banners` `/dishes/views` `/dishes` `/dishes/for-you` |
| `first_screen_home_revisit` | 8 | **4** | 返回首页再走一遍序列：回访**零新增**字典请求才算达标 |
| `detail_edit_attributes` | 2 | **1** | 同一菜品连续打开编辑弹层，候选值重复拉取 |
| `unauthorized_retry_amplification` | 1 | 1 | 401 在「静默重登不可用」下只发 1 次；真实环境若重登可用则为 2（原始 + 1 次重试），属登录态竞态代价而非缺陷 |
| `network_failure_attempts` | 1 | 1 | 传输失败**没有**隐藏的重试放大（请求层不自愈重试，交调用方退避） |

9 个场景合计 **28 次**传输。其中 **11 次**属于「同一份数据被重复请求」：
`concurrent_same_get` 多出的 4 次 + `repeated_same_get` 多出的 2 次 + 回访多出的 4 次 +
`detail_edit_attributes` 多出的 1 次。⚠️ 这些场景是**独立计量**的（每个场景都从零开始计数），
11 次不能理解成「一次会话的真实请求数」，它的含义是：请求层缓存 + in-flight 去重
在四类真实形态上各自能吃掉的量。

## 6. P1 / P2 验收阈值（下一轮必须超过这些数）

| 指标 | 基线 | 阈值 | 落在哪一轮 |
| --- | ---: | --- | --- |
| `server.mapper_calls.dish_attributes_edit` | 4 | ≤ 2（字典 + 候选聚合走缓存） | P1 |
| `server.mapper_calls.dish_views` / `guess_like` | 1 | 命中缓存时 0（冷路径仍为 1） | P1 |
| `server.dish_attributes_edit.latency_ms_warm` | 11.67 ms | ≤ 1 ms | P1 |
| `server.rate_limiter.throughput_ops_per_sec_*` | 1.09M / 1.87M | 均 ≥ 基线，且拒绝/放行分支差值 < 10% | P1 |
| `server.payload_bytes.*_non_null` 被采用 | 2502 / 753 | −2.8% ~ −4.2%（顺手做，不作主要理由） | P1 |
| `client.request.concurrent_same_get` | 5 | 1 | P2 |
| `client.request.repeated_same_get` | 3 | 1 | P2 |
| `client.request.first_screen_home_revisit` | 8 | 4 | P2 |
| `client.request.detail_edit_attributes` | 2 | 1 | P2 |
| `client.request.concurrent_same_post` / `concurrent_distinct_get` | 2 / 2 | **必须保持 2 / 2** | P2（防过度缓存） |
| `client.image.lazy_load_coverage` | 0% | ≥ 90% | P3 |
| `client.vue.deep_list_refs` / `shallow_ref_usages` | 13 / 0 | 列表态深响应 ref 归零、`shallowRef` ≥ 3 | P3 |
| `client.unused_exports` | **6**（原 12，因并行清理下降，见 §7-6） | 0 | P3 |
| `client.bundle.main_kb` | 315.41 | ≤ 220（`home-bg.jpg` 压缩或改走云存储） | P4 |

## 7. 度量与装配过程中踩到的坑（写下来，避免下一轮重犯）

1. **Mockito 的 `mockingDetails().getInvocations().clear()` 不清账本**：它返回的是快照视图，
   clear 不回写 ⇒ 直接读列得到的是**累积值**（列表页显示 8 次，真实是 1 次）；计数指标须取**前后差值**。
2. **本仓是 CRLF**：按 `\n` 切行后行尾残留 `\r`，而 `.` 不匹配 `\r`、`$` 也不在 `\r` 前成立
   → 条件编译正则**一条都没匹配上**，`#ifdef` / `#ifndef` 两条传输分支同时留在探针代码里，
   每个请求被数成两次（首跑所有数字都恰好翻倍）。切行必须用 `/\r?\n/`。
3. **`transportWxCloud` 消费的是 `success`/`fail` 回调而不是返回值**：桩若只
   `return Promise.resolve(...)`，回调永不触发 → 每个请求干等 12s 超时，
   数出来的是「超时次数」而不是「请求次数」，且场景间隔高达 12s。
4. **PowerShell 会吞掉 `-Dtest="A,B"` 里的引号**：结果是 surefire 收到空筛选、跑成全量测试
   （曾据此误判「基线日志被别的进程删了」）。跨平台脚本里应写成
   `cmd /c "mvn ... -Dtest=A,B ..."` 或整参单引号包裹。
5. **包体积指标读的是「上一次构建」的 `dist`**：源码改了但不重新 `npm run build:mp-weixin`，
   `client.bundle.*` 一个都不会变（本轮采集就是例子：源码行数变了 23 行，bundle 数字分毫未动）。
   每轮度量前**必须先跑一次生产构建**，否则会把「没重编」误读成「瘦身无效」。
6. **同一工作区存在并行编辑时，结构性指标会漂**：本轮第二次采集中 `client.unused_exports`
   从 12 掉到 6、api 函数从 41 变 40，全部来自另一条工作流的清理，而不是性能改动。
   应对办法就是 §4 开头那条：把 HEAD 与工作区状态一起记进采集记录。
7. **`SimpleCacheManager` 少了 `initializeCaches()` → `getCache()` 恒返回 `null`，整层缓存静默失效**：
   Spring 6.1 的 `AbstractCacheManager.getCache()` 已**不再惰性初始化**，而 `AbstractCacheResolver`
   拿到 `null` 只打一条 debug 日志就把该缓存名跳过。症状是「启动正常、业务正常、命中率永远是 0」。
   生产里这一步由容器生命周期回调完成，所以只有**自己 new 管理器**（工厂方法、基准测试）才会踩到——
   已在 `CacheConfig.buildCacheManager()` 内固化，调用方不需要知道这个细节。
8. **手工 `new` 的 `CacheInterceptor` 不调 `afterSingletonsInstantiated()` 就永远不缓存**：
   `CacheAspectSupport.execute()` 的**首行**是 `if (!initialized) → 直接执行业务方法`；
   Spring 6.1 里 `afterPropertiesSet()` 只做 `Assert.state(cacheOperationSource != null)`，
   `initialized` 由 `SmartInitializingSingleton` 回调置位。容器必然调用，脱离容器手工挂 advice 时没人调。
   症状最迷惑：**advice 确实挂上了、注解也确实解析出来了（`getCacheOperations()` 能查到）、
   缓存条目却永远是 0，耗时与完全不缓存时逐毫秒一致，全程零报错**。
   → 可泛化的两条：① 脱离容器手工装配 Spring 组件时，它实现的每个 `*Aware` / `Initializing*` /
   `SmartInitializing*` 回调都要补齐（本轮 P1 就在这里连踩了 7、8 两个）；
   ② 光断言「变快了」不够，必须有一条**直接看缓存里有没有条目**的断言
   （`DishCacheBenchmarkTest#dishViewsHitCache` 里的 `cachedEntries(...)`），
   否则装配错误只会以「优化没效果」的形式出现，然后被误读成「这条路没用」。

## 8. P1 实测（服务端读路径：进程内缓存 + 锁粒度 + 出参瘦身）

> 采集时点：`HEAD=444734b6`，工作区含本轮 P1 改动，**同时含其它并行工作流的未提交改动**
> （评价接口改造、client 文档与 `api.d.ts` 重新生成等）。口径与 §2 完全一致：本机、无 MySQL、
> 无真机；Mapper 次数是 DB 成本下界，`latency_ms_*` 只用于同算法自比。
> 每项 **3 轮独立 JVM**（`mvn -Dtest=…` 各起一个进程）取中位数，波动区间一并给出。

| 指标 | 基线 | P1 中位数 | 波动 | 阈值（§6） | 判定 |
| --- | ---: | ---: | --- | --- | --- |
| `server.mapper_calls.dish_attributes_edit`（未缓存路径） | 4 | **3** | 稳定 | ≤ 2 | ⚠️ 见读数 1：口径变了，缓存本身不在这条路径上 |
| `server.mapper_calls.dish_attributes_edit_cached`（热路径） | **4**（无缓存 ⇒ 热=冷，与 §3 的 4 一致） | **1.000** | 稳定 | — | ✅ 只剩一次取行 |
| `server.dish_attributes_edit.latency_ms_warm`（未缓存路径） | 11.67 ms | 10.77 ms | 9.6 ~ 12.9 | — | 与基线同（符合预期：该路径不走缓存） |
| `server.dish_attributes_edit.latency_ms_warm_cached` | 11.67 ms | **0.72 ms** | 0.43 ~ 0.84 | ≤ 1 ms | ✅ **−94%** |
| `server.mapper_calls.dish_views_cached`（第二次进首页的增量） | 1 | **0** | 稳定 | 命中时 0 | ✅ |
| `server.cache.candidates_recompute_after_write` | — | 2 | 稳定 | > 0（失效必须真的发生） | ✅ 护栏 |
| `server.rate_limiter.throughput_ops_per_sec_abuse` | 1.09M | **3.62M** | 1.59M ~ 4.02M | ≥ 基线 | ✅ **×3.3** |
| `server.rate_limiter.throughput_ops_per_sec_normal` | 1.87M | **4.93M** | 2.93M ~ 7.44M | ≥ 基线 | ✅ **×2.6** |
| 出参 `NON_NULL` 已采用（`dish_list_page` / `dish_detail`） | 2502 / 753 B | 2397 / 732 B | 稳定 | 顺手做 | ✅ −4.2% / −2.8% |

**读数结论**：

1. **两处收益必须分开记，否则会高估缓存**。未缓存路径的 Mapper 次数 4 → 3 **不是缓存带来的**，
   而是 `listDishAttributes` 把「存在性校验」和「读 attributes」两次查询合并成一次 `selectById`
   （远程库上省一次 RTT，判定口径不变）。缓存的收益记在 `*_cached` 系列：热路径 3 → 1 次调用、
   warm 11.67ms → 0.72ms。§6 里 `≤ 2` 那条阈值是按「缓存吃掉字典 + 全库聚合两跳」写的，
   实际合并查询先吃掉了第三跳，因此以 `*_cached = 1` 为准判定达标。
2. **`dishViews` 缓存换来的是「回访零查询」**（第二次进首页 Mapper 增量 = 0）。
   代价写在代码注释里：菜品上/下架后筛选 chip 最多晚 2 分钟（TTL）出现，且晚出现的后果是
   「点进去空列表」而不是错误数据。`guess_like` **刻意不缓存**（按会话种子随机，缓存等于把
   发现态对所有人冻结成同一批菜——那是产品行为变更）。
3. **限流器按 key 加锁后无分支倒挂**：拒绝分支快于放行分支（3.62M vs 4.93M —— 拒绝路径提前返回、
   少写一次命中）。§6 那条「差值 < 10%」的正确读法是「**拒绝分支不得慢于放行分支**」，该约束满足。
4. **限流吞吐对机器负载极敏感**：同一份代码在全量测试并发跑时采到过 0.87M。因此该指标只在
   机器空闲、单独 `-Dtest=` 运行时采集，且只看「中位数是否高于基线上界」——
   abuse 最差样本（1.59M）仍高于基线上界（1.42M），结论稳。
5. **缓存没有改变任何结果**：`DishCacheBenchmarkTest` 用 `usingRecursiveComparison` 断言热路径
   返回值与冷路径逐项相等，并断言写后失效确实触发（`candidates_recompute_after_write = 2`）。
   命中率 33.3% 是**该测试场景自身的**（1 hit / 2 miss：冷一次 + 热一次 + 失效后一次），
   不是线上期望值，别拿它当 SLO。

**本轮改动清单（服务端）**：

| 文件 | 改了什么 | 判据 |
| --- | --- | --- |
| `common/config/CacheConfig.java`（新） | `@EnableCaching` + 三个命名缓存（TTL 2/10/2 分钟、单缓存上限 8 条、`recordStats`）；`buildCacheManager()` 为生产与基准测试**共用的装配入口** | 用 `SimpleCacheManager` 而非 `CaffeineCacheManager`：不动态建缓存 ⇒ 缓存名拼错会暴露，而不是退化成无 TTL 无上限的默认缓存 |
| `dish/service/DishAttributeCatalog.java`（新） | 维度字典 + 候选值聚合从 `DishServiceImpl` 抽出为独立 bean，`@Cacheable` 落在它身上；写侧 `invalidateCandidates()` | **必须抽类**：自调用不过代理，注解留在原类会静默失效。返回 `List.copyOf` / `unmodifiableXxx`——缓存值跨请求共享，可变即污染 |
| `dish/service/impl/DishServiceImpl.java` | 注入 catalog；`listDishViews` 加 `@Cacheable`；`listDishAttributes` 两次查询合并为一次 `selectById`；`addDish` / `updateDish` / 反馈采纳后显式失效 | 失效用显式调用而非 `@CacheEvict`：注解式失效在写事务提交前执行，并发读可把旧聚合回填并再陈旧一整个 TTL |
| `common/ratelimit/IpRateLimiter.java` | `synchronized(this)` → `ConcurrentHashMap` + 每 key 队列监视器；清理线程逐 key 持锁；取队列与清理摘 key 的竞态用「归属复检 + 至多 3 轮重试」兜住 | 限流语义不变：同一 IP 仍严格串行；复检不可省，否则命中会写进已脱离 map 的队列 → **静默少计一次配额**（方向偏宽松，限流最坏的偏差） |
| `common/config/JacksonConfig.java` | 出参 `NON_NULL` | 前提：端上可空字段一律走兜底，不区分「字段为 null」与「字段缺失」；全量 222 个测试（含契约/冒烟）通过 |
| `src/test/java/com/bjtufood/perf/DishCacheBenchmarkTest.java`（新） | 无 Spring 上下文、用**生产同一份**缓存装配复现缓存语义；3 条断言：热路径只剩 1 次取行、结果逐项不变、写后失效发生 | 见 §7 第 7、8 条：这一类断言是声明式缓存唯一的照妖镜 |

**给 P2 的接口**：服务端这轮把「重复计算」压掉了，但**重复请求**一次没动——§5 的 9 个场景里
仍有 11 次属于同一份数据被重复请求。下一轮在 `client/src/api/http.ts` 做请求层缓存 +
in-flight 去重，验收就是 §5 那张表的「优化后期望」列（四个场景各降到期望值，
且 `concurrent_same_post` / `concurrent_distinct_get` **必须保持 2 / 2**）。
服务端这轮的取舍也可以直接复用：缓存对象必须同时满足「全站共享 + 变化极慢 + 重算昂贵」，
只写显式失效 + TTL 兜底，并且**先写断言再上缓存**。

