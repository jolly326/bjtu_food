# 部署后自检清单（云托管）

> 用途：每次改完部署，**按顺序过一遍**。清单里的每一项都对应一个真实踩过的坑。
> 命名依据：`server/src/test/java/com/bjtufood/BuildInputConfigTest` 会校验其中
> 「配置文件必须入库」这一项，漏了直接构建失败。

## 0. 冒烟（30 秒，不通过不要往下走）

```powershell
# 必须 200。返回 401/400 说明 context-path 没生效或白名单没命中
curl.exe -s -o NUL -w "%{http_code}" https://<域名>/api/v1/dishes
```

| 结果 | 含义 |
|---|---|
| `200` | 通过，继续 |
| `401` | 登录被拒 → 跳到 §3 |
| `400` / `404` | 路由错 → 跳到 §1 |

## 1. 配置入库（对应 `BuildInputConfigTest`）

jar 内必须含这三个文件：

```powershell
jar tf server/target/bjtu-food-1.0.0.jar | Select-String 'BOOT-INF/classes/application'
```

应看到 `application.yml`、`application-prod.yml`、`application-dev.yml`。

> **为什么重要**：云构建上下文来自 GitHub 克隆，不是本地工作树。
> 文件一旦漏入库，**本地全绿、线上全挂**，且失败方式极隐蔽
> （`context-path` 失效 → 白名单全部失配 → 全站 401）。已发生过一次。

## 2. 启动日志确认 context-path

日志里必须出现：

```
Tomcat started on port 8080 (http) with context path '/api/v1'
```

> 若显示 `context path '/'` 或上下文名为 `[/]`，说明 `application.yml` 没进 jar，回到 §1。

## 3. 微信登录（`POST /auth/wechat-login`）

小程序端点，用一次真实 `wx.login` 的 code 调。**按返回的 errcode 判定**：

| 提示含 | 含义 | 处理 |
|---|---|---|
| `errcode=40125` | AppSecret 无效 | 公众平台重置 AppSecret → 更新环境变量 → **重建版本** |
| `errcode=40013` | AppID 无效 | 核对 `wx2bd6e4b461467b74` |
| `errcode=40029` / `40163` | code 无效/已用过 | 正常，重试会换新 code |
| `errcode=500` + 服务不可用 | 见 §5 | 按提示里的 errcode 走 |

## 4. 内容安全与图片上传

上传任意一张 ≤1MB 的图片即可触发 `imgSecCheck`。**现在失败提示会直接带 errcode**，按提示处理：

| 提示含 | 含义 | 处理 |
|---|---|---|
| `40164` | **出口 IP 未加白名单** | 公众平台 → IP白名单 填云托管出口 IP |
| `40125` / `40013` | AppID/Secret 不匹配 | 同 §3 |
| `48001` / `45011` | 调用频率超限 | 稍后重试 |
| `87014` | 图片违规 | 换图（这是正常的业务拦截） |

## 5. 出口 IP 白名单（最容易反复踩）

云托管出口 IP **可能随实例变化**，直接填单 IP 会反复失效。两个稳定做法：

1. 配 **NAT 网关**固定出口 IP，再把固定 IP 加入白名单（推荐）
2. 观察一段时间把 IP 段都加上

验证：

```powershell
# 在云托管容器内执行，确认出口 IP
curl -s ifconfig.me
```

## 6. 环境变量清单

云托管控制台需全部注入（**只改环境变量必须重建版本才生效**）：

| 变量 | 用途 | 缺失后果 |
|---|---|---|
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | 数据库 | 启动或查询失败 |
| `JWT_SECRET` | 签名密钥 | 启动 fail-fast |
| `ADMIN_TOKEN` | 管理端口令 | `/admin/**` 403 fail-closed |
| `WECHAT_APPID` / `WECHAT_SECRET` | 微信登录 | 登录 400/500 |
| `COS_BUCKET` / `COS_SECRET_ID` / `COS_SECRET_KEY` / `COS_REGION` | 图片存储 | 上传 400「图片存储未配置」 |
| `WECHAT_CLOUD_ENV` | 云存储环境 ID | 上传取链接失败 |
| `SPRING_PROFILES_ACTIVE=prod` | 启用 prod 配置 | actuator 暴露面不收敛 |

## 7. 不要开启「开放接口服务」

开启后平台以旁挂接管对 `api.weixin.qq.com` 的请求并用**自签证书**终止 TLS，
而 JVM 不信任该自签根 ⇒ 所有微信调用报 `SSLHandshakeException: PKIX path building failed`。

> 该开关状态**在版本创建时锁定**，改开关后必须**重建版本**才生效。

## 8. 排障时的日志锚点

| 搜这个 | 定位 |
|---|---|
| `code2Session 失败` | 微信登录 |
| `stable_token 获取失败` | 取 access_token |
| `imgSecCheck 调用失败` | 图片安检 |
| `msgSecCheck 调用失败` | 文本安检 |

每条都带 `errcode=`，直接对着上面的表处理即可。