# 知行食记 · 从零上线全流程指南（零基础版）

> 目标读者：没有部署过服务器的小白。按步骤照做即可上线。
> 技术栈：Spring Boot 后端（Java 21 + MySQL 8）+ 小程序（uni-app）+ Web 管理端（Vue3）。
> 全文以**阿里云/腾讯云（Ubuntu 22.04 服务器）**为例，其他云厂商操作类似。

---

## 0. 整体流程（先看懂再动手）

```
① 买服务器 + 买域名 + ICP备案（1~3周，最耗时，先启动）
② 登录服务器（SSH）
③ 装环境：Java 21 / MySQL 8 / Nginx
④ 建数据库 + 导入表和数据
⑤ 上传并启动后端 jar
⑥ 配 HTTPS + 反向代理（域名 → 8080）
⑦ 构建小程序/Web 产物
⑧ 微信后台配域名白名单 + 隐私协议
⑨ 开发者工具上传 → 提交审核 → 发布
```

> ⚠️ 备案要 1~3 周，**第①步现在就去买**，不要等。

---

## ① 购买服务器 + 域名 + 备案

### 1.1 买服务器（任选一家）
| 云厂商 | 入门机 | 价格参考 |
|--------|--------|---------|
| 阿里云「轻量应用服务器」 | 2核2G/40G | ~100元/年（学生认证更便宜） |
| 腾讯云「轻量应用服务器」 | 同 | 同 |

- **镜像选 Ubuntu 22.04**（后面命令都是它的）
- 地域选**国内**（学生访问快，但必须备案）

### 1.2 买域名
- 在**同一家云厂商**买域名（如 `example.com`，一年几十元），备案在同一家会更快
- 你需要的子域名：`api.example.com`（后端接口）、`admin.example.com`（Web 管理端，可选）

### 1.3 ICP 备案
- 云厂商控制台 → 找到「ICP 备案」入口 → 按引导填资料
- 需要：身份证、手机号、可能人脸核验
- 周期：**1~3 周**，提交后等待即可（这是最卡时间的环节）

### 1.4 开通安全组（重要！）
云厂商控制台 → 服务器 → 安全组/防火墙，放行端口：
| 端口 | 用途 | 是否对外 |
|------|------|---------|
| 22 | SSH 登录 | 是 |
| 80 | HTTP（Nginx/证书验证） | 是 |
| 443 | HTTPS | 是 |
| 8080 | 后端（**只对 Nginx 开放，可先不放行或限制 IP**） | 否 |

> 云厂商「轻量服务器」通常在防火墙页面；ECS 在安全组。规则：协议 TCP，端口如 `22/22`，源 `0.0.0.0/0`。

---

## ② 登录服务器（SSH）

Windows 自带终端，直接敲：

```powershell
ssh root@你的服务器公网IP
```

> 首次登录会让你设/输 root 密码（买服务器时设置）。之后可改用密钥登录（本期先不用管）。

验证登录成功：
```bash
cat /etc/os-release   # 应显示 Ubuntu 22.04
```

---

## ③ 安装环境（一条条执行）

### 3.1 更新系统
```bash
sudo apt update && sudo apt upgrade -y
```

### 3.2 安装 Java 21
```bash
sudo apt install -y openjdk-21-jdk
java -version   # 应显示 21.x
```

### 3.3 安装 MySQL 8
```bash
sudo apt install -y mysql-server
sudo systemctl enable mysql && sudo systemctl start mysql
sudo mysql_secure_installation   # 按提示设置 root 密码、删匿名用户、禁用远程 root
```

### 3.4 安装 Nginx
```bash
sudo apt install -y nginx
sudo systemctl enable nginx && sudo systemctl start nginx
# 浏览器访问 http://服务器IP 能看到 Nginx 欢迎页即成功
```

---

## ④ 创建数据库并导入数据

### 4.1 登录 MySQL
```bash
sudo mysql
```

### 4.2 建库 + 建专用账号（替换 `你的密码`）
```sql
CREATE DATABASE bjtu_food DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'bjtu'@'localhost' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON bjtu_food.* TO 'bjtu'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 4.3 导入表结构和种子数据
在**你的电脑**上，把项目的两个 SQL 文件上传到服务器（用 **MobaXterm** 或 WinSCP 拖拽上传到 `/root/`），然后：

```bash
mysql -u bjtu -p bjtu_food < /root/schema.sql
mysql -u bjtu -p bjtu_food < /root/seed_data.sql
# 如果你是从旧库升级，再执行（可重复执行，幂等）：
mysql -u bjtu -p bjtu_food < /root/migration_phase6.sql
mysql -u bjtu -p bjtu_food < /root/migration_phase7.sql
```

验证：
```bash
mysql -u bjtu -p -e "USE bjtu_food; SHOW TABLES;"   # 应看到全部表
```

---

## ⑤ 部署后端

### 5.1 在你电脑上打包
打开 PowerShell，进入项目目录：
```powershell
cd d:\workspace\code\project\bjtu_food\server
mvn clean package -DskipTests
```
产物：`server/target/*.jar`（比如 `server-1.0.0.jar`）

### 5.2 上传 jar 到服务器
用 MobaXterm/WinSCP 把 jar 传到 `/root/app/`（先建目录：`mkdir -p /root/app`）

### 5.3 创建生产配置
在服务器上执行：
```bash
mkdir -p /root/app
nano /root/app/application-prod.yml
```
粘贴以下内容（**改 4 处**：数据库密码、JWT 密钥、邮箱授权码、域名）：

```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bjtu_food?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: bjtu
    password: 你的数据库密码        # 改这里
    driver-class-name: com.mysql.cj.jdbc.Driver
  mail:
    host: smtp.qq.com               # 用 QQ 邮箱发验证码
    port: 465
    username: 你的QQ邮箱@qq.com     # 改这里
    password: 你的邮箱授权码        # 改这里（QQ邮箱→设置→账户→开启SMTP→生成授权码）
    default-encoding: UTF-8

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl   # 生产关 SQL 日志

jwt:
  secret: 换成至少32位的随机字符串     # 改这里（生产安全）
  expiration: 3153600000000           # 不设登录超时，长期有效

upload:
  path: /root/app/uploads              # 图片存储目录

app:
  public-base-url: https://api.example.com   # 改这里（你的域名）
```

保存：`Ctrl+O` 回车，`Ctrl+X` 退出。

### 5.4 用 systemd 让后端常驻（重启自动拉起）
```bash
sudo nano /etc/systemd/system/bjtu-food.service
```
粘贴：
```ini
[Unit]
Description=Bjtu Food Backend
After=network.target mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=/root/app
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=prod /root/app/server-1.0.0.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```
保存后：
```bash
sudo systemctl daemon-reload
sudo systemctl enable bjtu-food
sudo systemctl start bjtu-food
sudo systemctl status bjtu-food    # 应显示 active (running)
```

### 5.5 验证后端
```bash
curl http://localhost:8080/api/canteens    # 应返回 JSON 食堂列表
```
> 若启动失败：`sudo journalctl -u bjtu-food -n 50` 看日志。

---

## ⑥ 配置 HTTPS + 反向代理

### 6.1 先让域名指向服务器
云厂商控制台 → 域名解析（DNS）→ 添加记录：
```
主机记录: api     类型: A  记录值: 你的服务器公网IP
主机记录: admin   类型: A  记录值: 你的服务器公网IP   （可选）
```

### 6.2 安装免费证书工具
```bash
sudo apt install -y certbot python3-certbot-nginx
```

### 6.3 配置 Nginx（一次性）
```bash
sudo nano /etc/nginx/sites-available/bjtu-food
```
粘贴：
```nginx
# 后端 API
server {
    listen 80;
    server_name api.example.com;          # 改这里（你的域名）

    location /api/ {
        proxy_pass http://127.0.0.1:8080;   # 转发到 Spring Boot
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
        client_max_body_size 20m;           # 上传图片允许 20M
    }

    # 图片访问（上传目录）
    location /uploads/ {
        alias /root/app/uploads/;
    }
}
```
保存后启用：
```bash
sudo ln -s /etc/nginx/sites-available/bjtu-food /etc/nginx/sites-enabled/
sudo nginx -t          # 测试配置，显示 ok
sudo systemctl reload nginx
```

### 6.4 申请免费 HTTPS 证书（certbot 自动完成）
```bash
sudo certbot --nginx -d api.example.com
```
- 按提示输入邮箱 → 同意协议 → 证书自动签发并改写 Nginx 为 HTTPS
- 之后访问 `https://api.example.com/api/canteens` 应返回 JSON ✅

### 6.5 验证图片上传目录权限
```bash
mkdir -p /root/app/uploads && chmod -R 777 /root/app/uploads
```

---

## ⑦ 构建小程序 / Web 产物

### 7.1 小程序（在你自己电脑上）
```powershell
cd d:\workspace\code\project\bjtu_food\client
$env:VITE_API_BASE_URL="https://api.example.com/api"   # 改你的域名
npm run build:mp-weixin
```
产物在 `client/dist/build/mp-weixin/`，之后用微信开发者工具打开这个目录上传。

### 7.2 Web 管理端（可选，先上线小程序可跳过）
```powershell
cd d:\workspace\code\project\bjtu_food\web
$env:VITE_API_BASE_URL="https://api.example.com/api"
npm run build
```
产物在 `web/dist/`。如需在线访问，再在 Nginx 加一个 `admin.example.com` 站点（本期可选）。

---

## ⑧ 微信后台配置

登录 [微信公众平台](https://mp.weixin.qq.com)：

### 8.1 服务器域名白名单
开发管理 → 开发设置 → 服务器域名：
- **request 合法域名**：`https://api.example.com`
- **uploadFile 合法域名**：`https://api.example.com`
- 不需要 downloadFile（图片走 uploads，但小程序 image 加载域名也要加！→ 把 `https://api.example.com` 也加进 downloadFile 合法域名）

### 8.2 隐私协议
设置 → 服务内容声明 → 用户隐私保护指引：
- 声明收集：**学号、手机号（邮箱）、位置信息、相册（上传图片）**
- 用途：登录注册、评价/动态/发布、定位食堂

### 8.3 类目设置
小程序首页设置类目，选「生活服务 → 生活服务」或「教育」相关类目。

---

## ⑨ 上传 → 提交审核 → 发布

1. 打开**微信开发者工具** → 导入 `client/dist/build/mp-weixin`
2. AppID 填项目已有的 `wx2bd6e4b461467b74`
3. 点右上角「上传」→ 填版本号（如 1.0.0）和备注
4. 微信公众平台 → 版本管理 → 开发版本 → 提交审核
5. 等审核（1~7 天）→ 审核通过 → 点击「发布」

---

## 🚑 常见问题排查

| 现象 | 原因 | 解决 |
|------|------|------|
| 小程序请求失败 | 域名没配白名单 / 没 HTTPS | 回第⑧步核对白名单；`curl https://api.example.com/api/canteens` 验证 |
| 后端启动失败 | 配置错误 | `sudo journalctl -u bjtu-food -n 50` 看日志 |
| 端口 8080 被占 | 重复启动 | `sudo ss -tlnp \| grep 8080` 找到 PID `kill` |
| 图片上传 413 | Nginx body 太小 | 检查 `client_max_body_size 20m` |
| 备案未完成 | — | 备案前只能 http 测试；备案通过后才能配正式 HTTPS 白名单 |
| 验证码邮件发不出 | SMTP 授权码错 | QQ邮箱设置→账户→开启 SMTP 服务→获取授权码 |

---

## 📦 资源清单（你需要准备的）
- [ ] 云服务器（Ubuntu 22.04，2核2G 起步）
- [ ] 域名一个（如 `example.com`）
- [ ] ICP 备案（1~3 周）
- [ ] QQ 邮箱 + SMTP 授权码（发验证码用）
- [ ] 微信小程序账号（已有 AppID）
