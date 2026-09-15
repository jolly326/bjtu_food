# 食在交大 · Web 管理后台

面向校方管理员的运营后台（Vue 3 + Vite + TypeScript + Element Plus，仅 ADMIN 使用，与学生端小程序解耦）。

## 启动

```sh
npm install
npm run dev    # 本地开发（默认 5173，后端 API 见 src/api/config.ts）
npm run build  # 类型检查（vue-tsc）+ 生产构建
```

本仓库包管理器统一为 **npm**（唯一锁文件为 `package-lock.json`），请勿引入 pnpm/yarn/bun 锁文件。
