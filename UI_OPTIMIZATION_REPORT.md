# HERBMODA UI 优化交付说明

## 基线

- Repository: `ltzaqmlp/tiktok-shop-data-system`
- Baseline commit: `e796c5d0454680bf5eb9ac9440f51b41a93a7f08`
- Backend build version: `2026.09.07-product-period-v3`

## 本次优化范围

本次按《HERBMODA 前端 UI 视觉优化实施规范 v2.0》执行视觉层优化，业务字段、接口、路由、权限、数据计算和页面功能保持基线逻辑。

主要视觉调整：

- 全局 Design Tokens：黑白/石墨主色，中性背景、边框、状态色、统一圆角和交互态。
- App Shell：HERBMODA 品牌标识、黑灰导航、浅灰激活背景和左侧强调线。
- 登录页：使用 Radiant Oil Capsules 为主视觉、Glowing Tomato Facial Mask 为辅助视觉，保留原 400px 登录卡和原表单逻辑。
- Dashboard：6 KPI、趋势、漏斗、广告、订单状态、售后、访客/转化模块顺序不变；图表统一为 `#344B40 / #AEB6B1 / #D9DEDA` 中性体系。
- 数据导入、日报、用户/角色/菜单/日志：沿用原字段与操作，只统一表格、筛选、表单、标签和选中态视觉。
- 响应式：保留既有 1100 / 767 / 600 等断点逻辑，没有新增会隐藏业务字段的断点规则。

## 业务不变校验

对关键业务层和完整后端源码执行 Git blob SHA 校验，共检查 39 个基线不应修改文件，结果：`39 / 39` 与基线完全一致。覆盖：

- `frontend/src/api/*`
- `frontend/src/router/index.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/main.ts`
- 前端 package / tsconfig / vite 配置
- 完整 backend Java 业务代码、SQL migrations、import aliases 与后端测试

允许变化的文件集中在 UI presentation：全局 CSS、页面 scoped CSS、图表 presentation 配置、状态标签 presentation，以及新增品牌图片资源。

## 自动检查

### 已通过

```text
npm test
9 tests / 9 passed / 0 failed
```

`frontend/package-lock.json` 已执行 `npm install --package-lock-only --offline` 做结构一致性校验。

### 当前执行环境无法完成的检查

`npm run build` 在当前沙箱未安装 `node_modules` 的情况下返回 `vue-tsc: not found`。本环境同时无法解析 npm registry，不能在线执行 `npm ci` 安装依赖，因此这里不能宣称生产构建已验证通过。

在可联网的正常 Node.js 环境中请执行：

```bash
cd frontend
npm ci
npm test
npm run build
```

后端如需完整复测：

```bash
cd backend
mvn -B test
```

当前沙箱未安装 Maven，因此后端 Maven 测试未在此环境执行；后端源码已通过上述基线 SHA 不变校验。

## 品牌资源

- `frontend/src/assets/brand/herbmoda-logo-light.webp`
- `frontend/src/assets/brand/radiant-oil-capsules-main.png`
- `frontend/src/assets/brand/glowing-tomato-mask.png`
