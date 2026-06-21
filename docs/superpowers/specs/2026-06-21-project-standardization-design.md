# 智能餐饮推荐系统 · 规范化设计文档

- **日期**：2026-06-21
- **作者**：项目负责人 + Claude (superpowers/brainstorming)
- **范围**：本 spec 仅覆盖"**规范化**"子项目。"**闭环补全**"子项目将在规范化完成后单独走一次 brainstorm，不在本文档内。

---

## 1. Context

本项目为毕业设计：智能餐饮推荐系统，已具备完整功能（Spring Boot 后端 + Vue 3 前端 + 微信小程序 + MySQL + Qdrant + 6-Agent 推荐管线 + 双角色 JWT 鉴权 + 微信语音入口）。

当前的问题：
- 工程素养层面薄弱：缺少全局异常统一、参数校验、API 文档、结构化日志、单测、Docker、CI
- 文档散落根目录：3 份中文 `.md` + 1 份 `.pdf` 平铺，且内容与现实代码部分脱节（如旧版"上传图片推荐"叙事仍在）
- 前端/小程序缺少 Lint、环境隔离、404、占位图标、清晰的部署说明

预期效果：答辩时能演示工程化全貌（Swagger / CI 绿勾 / `docker compose up` 一键起 / 单测覆盖核心业务），同时不改变任何业务功能。

---

## 2. Scope & Non-Goals

### 范围内
- 后端：全局异常 + Bean Validation + Swagger/OpenAPI + 结构化日志 + 单测（~22-26 用例）+ Dockerfile + actuator/health
- 前端：ESLint/Prettier + 环境变量隔离 + 路由懒加载 + 404 + 统一错误处理审计
- 小程序：ESLint + 占位 tab icon 替换 + appid 占位化 + 单独 README
- 文档：迁移到 `docs/`，**根据真实代码重写**三份旧文档；新增 `deployment.md`、`api.md`、`screenshots/`
- Ops：`docker-compose.yml`（MySQL + Qdrant + Backend，前端 profile 可选）+ `.env.example` + GitHub Actions（编译/测试/前端 build/小程序 lint）
- 根 `README.md` 重写

### 不做（明确排除）
- 不引入 Testcontainers / 集成测试 / 重型可观测性栈
- 不重排后端包结构（auth/dish/recommend 分包推迟到"闭环补全"考虑）
- 不做 DTO/Entity/VO 三层分离
- 不动业务逻辑代码
- 不做任何"反馈反哺推荐排序"、"老板分析看板增强"、"提示词在线编辑"等闭环类增功能 —— 那些属于另一个 spec

---

## 3. 实施策略：分层小步提交到主干

直接在 `main` 上分 7 个独立 commit。每个 commit：
- 单独可编译可运行
- 单独可 revert
- 答辩任意时间节点都能演示

不开长分支、不做大爆炸 PR。

---

## 4. 七个 Commit 的详细设计

### Commit 1 · 文档结构迁移 + **内容重写**

**目的**：让文档名副其实，内容对齐真实代码。

**步骤**：
1. `git mv 智能餐饮推荐系统_产品技术白皮书.md docs/whitepaper.md`
2. `git mv 智能餐饮推荐系统_开发文档_本地开发版.md docs/dev-guide.md`
3. `git mv 智能餐饮推荐系统_技术方案图.md docs/architecture.md`
4. `git mv 文档.pdf docs/whitepaper.pdf`
5. 通读三份旧文件，把仍然成立的事实保留，对齐当前代码后重写：

**`docs/whitepaper.md`**：产品定位（店主/服务员双角色 + 微信小程序语音入口）、6-Agent 架构图、核心业务流（标签推荐 / 语音推荐 / 反馈采纳 / 营收统计）、关键创新点（多人配菜安全过滤、长期记忆、向量检索 + AI 重排两层）、技术选型理由。

**`docs/dev-guide.md`**：环境前置（JDK 17 / Node 18+ / MySQL 8 / Qdrant / 微信开发者工具）；一句话启动；三端目录结构说明；配置项说明（`application.yml` 关键 key、`.env` 模板、小程序 appid / API base）；常见任务（新增菜品 → 重建向量、新增 Agent、改提示词、调试某条链路）。

**`docs/architecture.md`**：mermaid 三端拓扑图、6-Agent 时序图、ER 图（覆盖所有核心业务表：用户、菜品、推荐记录、反馈、提示词模板等，实际数量以 `schema.sql` 为准）、JWT 鉴权流程图、向量构建与检索流程图。

6. 占位空文件：`docs/deployment.md`、`docs/api.md`、`docs/screenshots/.gitkeep`（后续 commit 填）
7. 重写根 `README.md`：一句话简介 + 架构图（mermaid）+ 技术栈 badge + 快速开始 + 文档导航表 + 三端预览截图占位

### Commit 2 · 后端：全局异常 + Bean Validation + Result 统一

- 保留现有 `BusinessException`、`Result`
- 新建 `GlobalExceptionHandler`（`@RestControllerAdvice`），接管：
  - `BusinessException`（业务层抛出）
  - `MethodArgumentNotValidException`（@Valid 校验失败）
  - `ConstraintViolationException`
  - `AccessDeniedException`、`AuthenticationException`
  - `MaxUploadSizeExceededException`
  - `Exception` 兜底
  - 全部返回统一 `Result<?>`，HTTP 状态码合理映射
- pom 加 `spring-boot-starter-validation`
- 所有 Controller 入参加 `@Valid`；DTO 加约束注解（`@NotNull` / `@NotBlank` / `@Size` / `@Min` / `@Max`）
- 新建 `FeedbackRequestDTO`，把 `WaiterRecommendController.submitFeedback` 当前的 `Map<String, Object>` 裸接方式替换掉，删除手写 try-catch 解析

### Commit 3 · 后端：Swagger/OpenAPI + 结构化日志 + `api.md`

- pom 加 `springdoc-openapi-starter-webmvc-ui`
- 启用 `/swagger-ui.html`，集成 JWT Authorize 按钮
- 每个 Controller 加 `@Tag(name="..", description="..")`
- 每个 endpoint 加 `@Operation(summary="..", description="..")`
- 关键 DTO 加 `@Schema(description="..")`
- `logback-spring.xml` 替换默认：控制台简洁 + 文件按天滚动 + ERROR 单独文件
- 新建 `MdcFilter`：每个 HTTP 请求注入 `traceId`（UUID 前 8 位）；日志 pattern 带 `[%X{traceId}]`
- 新建 `LoggingAspect`：Controller 切面，记录方法入口/出口 + 耗时
- 手写 `docs/api.md`：精炼端点速查表（路径 / 方法 / 角色 / 请求体示例 / 响应示例），与 Swagger UI 并存

### Commit 4 · 后端单测（22-26 用例）

测试栈：JUnit 5 + Mockito + AssertJ。不引入 Testcontainers。

| 测试类 | 覆盖关键路径 |
|---|---|
| `UserServiceImplTest` | 密码登录成功 / 密码错误 / 用户不存在 / wxLogin 新建 WAITER / wxLogin 已存在用户复用 / jscode2session 失败 |
| `DishMatchingServiceImplTest` | 清真过滤排除猪肉 / 素食过滤排除荤腥 / 过敏排除（鱼 ≠ 鱼香）/ 糖尿病排除高糖 / 多人 GuestProfile 合并 |
| `VoiceUnderstandingServiceImplTest` | 标准语句 → TagInputDTO / 含多人偏好语句 / 含禁忌语句 / 模型返回空 → 抛异常 |
| `UserProfileServiceImplTest` | 单人 / 多人 guests 合并 / 含手机号拼接历史画像 / 不含手机号不查历史 |
| `RecommendServiceImplTest` | executeAgentPipeline 全 mock 跑通（含/不含场景图两条路径），verify 调用顺序 |
| `WaiterRecommendControllerTest`（`@WebMvcTest` 切片）| 采纳成功扣库存 / 重复采纳同菜品拒绝 / 库存不足拒绝 / quantity 非法默认 1 / 未鉴权 401 |

`mvn verify` 全过。

### Commit 5 · Docker / docker-compose（全栈后端）

- `food-recommend-backend/Dockerfile`：多阶段（`maven:3.9-eclipse-temurin-17` 编译 → `eclipse-temurin:17-jre` 运行），`USER nonroot`，`HEALTHCHECK` 打 `/actuator/health`
- 启用 actuator 仅 `health` 端点（`management.endpoints.web.exposure.include=health`），其余不暴露
- `food-recommend-frontend/Dockerfile`：多阶段（`node:20-alpine` build → `nginx:alpine` 托管 `dist/`），带 `nginx.conf` 反向代理 `/api` 到后端
- 根目录 `docker-compose.yml`：
  - `mysql:8.0`（卷持久化 + 挂载 `schema.sql` + `data.sql` 初始化）
  - `qdrant/qdrant:latest`（卷持久化）
  - `backend`（依赖 mysql、qdrant，env 从 `.env` 注入；`application.yml` 改成全部 `${ENV:default}` 形式）
  - `frontend`（profile=`with-frontend`，默认不起）
- 根 `.env.example`：所有密钥占位（MIMO_API_KEY、DASHSCOPE_API_KEY、JWT_SECRET、WX_APPID、WX_SECRET、MYSQL_PASSWORD）
- 真 `.env` 保持在 `.gitignore`
- 填 `docs/deployment.md`：完整一键起步骤、生产部署提示、常见故障排查

### Commit 6 · GitHub Actions CI

`.github/workflows/ci.yml`，触发 push / PR 到 `main`：
- Job 1 `backend-test`：actions/setup-java 17 + 缓存 maven + 起 mysql/qdrant service containers + `mvn verify`
- Job 2 `frontend-build`：actions/setup-node 20 + 缓存 npm + `npm ci && npm run build`
- Job 3 `miniprogram-lint`：actions/setup-node 20 + `npm ci && npm run lint`（Commit 7 引入配置）

三 job 并行；徽章贴到根 README。

### Commit 7 · 前端 + 小程序规范化

**前端 `food-recommend-frontend/`**
- ESLint + Prettier：`eslint-plugin-vue` (Vue3 推荐) + `@vue/eslint-config-prettier`；`.eslintrc.cjs` + `.prettierrc` + `.editorconfig`；脚本 `npm run lint` / `lint:fix`
- 环境隔离：`.env.development` / `.env.production`；`src/api/index.js` 用 `import.meta.env.VITE_API_BASE`
- 路由懒加载：所有 view 改为动态 `import()`
- 统一错误处理审计：axios interceptor 401 跳登录、其他错误 ElMessage.error；NProgress 顶部进度条
- 新增 `NotFoundView.vue` + 路由兜底
- 前端子项目 README 补全 `npm run build` + nginx 部署说明

**小程序 `food-recommend-miniprogram/`**
- `eslint-plugin-wechat-miniprogram` + 基础规则；CI 跑
- `utils/api.js` 审计：Bearer token 注入 / 全局错误 toast / 401 重新登录 / 网络异常友好提示 / timeout 180s 确认
- 替换占位 tab icon（提交真实图标文件）
- `project.config.json` 的 appid 改为占位；小程序子项目 README 写明替换步骤 + 微信公众平台域名白名单 + WechatSI 插件 appid

---

## 5. 跨 Commit 的约束

- **保留手动改动**：以下文件已被用户手动修改 —— 业务逻辑严禁触碰，仅允许 Formatter/Linter 引起的纯格式变更：
  - `food-recommend-frontend/src/components/TagPanel.vue`
  - `food-recommend-backend/src/main/java/com/example/foodrecommend/service/impl/DishMatchingServiceImpl.java`
  - `.../service/impl/UserProfileServiceImpl.java`
  - `.../service/UserProfileService.java`
  - `.../service/ScriptGenerationService.java`
  - `.../security/JwtAuthenticationFilter.java`
  - `.../entity/RecommendationFeedback.java`
  - `.../dto/AnalyticsDTO.java`
  - `.../dto/TagInputDTO.java`
  如格式化引发逻辑性 diff，需向用户确认后再合入。
- **数据库非破坏**：单测用 Mockito，无需重置 MySQL；`schema.sql` / `data.sql` 仅追加，不修改既有行。
- **API 路径不改**：所有现有 `/api/**` 端点路径保持。
- **私钥安全**：`application.yml` 真实密钥不进 git（现已在 `.gitignore`，本次维持）。

---

## 6. 关键文件路径清单（实施时定位用）

- 后端 Controller：`food-recommend-backend/src/main/java/com/example/foodrecommend/controller/*.java`
- 后端 Service Impl：`.../service/impl/*.java`
- 全局异常拟新增：`.../common/GlobalExceptionHandler.java`
- MDC 过滤器拟新增：`.../config/MdcFilter.java`
- 日志切面拟新增：`.../config/LoggingAspect.java`
- 日志配置拟新增：`food-recommend-backend/src/main/resources/logback-spring.xml`
- 单测目录：`food-recommend-backend/src/test/java/com/example/foodrecommend/...`
- 前端 API：`food-recommend-frontend/src/api/index.js`
- 前端路由：`food-recommend-frontend/src/router/index.js`
- 小程序 API：`food-recommend-miniprogram/utils/api.js`
- 小程序配置：`food-recommend-miniprogram/project.config.json`
- 待新增：`docker-compose.yml`、`.env.example`、`.github/workflows/ci.yml`、`docs/**`

---

## 7. 验证方式（每个 Commit 完成后跑一遍）

- **Commit 1**：`docs/` 下三份 md 通读，确认与代码事实一致；根 README 渲染无 broken link
- **Commit 2**：用 curl / Postman 故意发非法参数，确认返回统一 `Result` 结构、状态码合理
- **Commit 3**：浏览器打开 `http://localhost:8080/swagger-ui.html`，所有 endpoint 显示 + 能在 UI 内 Authorize 后调通；日志输出含 `[traceId]`
- **Commit 4**：`cd food-recommend-backend && mvn verify`，所有用例通过；覆盖率报告（Surefire）截图入 `docs/screenshots/`
- **Commit 5**：`docker compose up -d mysql qdrant backend`，等 healthcheck 通过；前端 dev 连接后端跑一次推荐 → 成功
- **Commit 6**：推送到 GitHub 后 Actions 三 Job 全绿；徽章在 README 显示
- **Commit 7**：
  - 前端：`npm run lint` 0 error；`npm run build` 成功；404 路由能命中
  - 小程序：微信开发者工具加载项目无报错（占位 appid 可工具内切体验版）；`npm run lint` 0 error

---

## 8. 后续

规范化完成后，启动"闭环补全"子项目的 brainstorm：候选方向包括反馈反哺排序（RL-lite）、老板分析看板增强、提示词在线编辑、库存与推荐联动、客户画像主页等。届时单独建 spec。
