# 智能餐饮推荐系统 · 开发者上手指南

---

## 一、环境前置

| 依赖 | 版本要求 | 备注 |
|---|---|---|
| JDK | 17 | 必须是 Java 17，Spring Boot 3.2.5 要求 |
| Maven | 3.9+ | 后端构建工具 |
| Node.js | 18+ | 前端/小程序依赖 |
| MySQL | 8.0+ | 业务数据库 |
| Qdrant | latest | 向量数据库，建议 Docker 启动 |
| 微信开发者工具 | latest | 服务员语音小程序调试 |

**Windows 11 典型路径示例：**

```text
JDK 17：C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot\
Maven：C:\tools\apache-maven-3.9.x\
Node：C:\Program Files\nodejs\
微信开发者工具：C:\Program Files (x86)\Tencent\微信web开发者工具\
```

---

## 二、快速启动

> 完整 docker-compose 一键启动详见 [部署手册](deployment.md)。以下为纯手动启动步骤，适合本地联调。

```bash
# 1. 启动 Qdrant（Docker）
docker run -d --name food-qdrant -p 6333:6333 \
  -v ./qdrant_storage:/qdrant/storage qdrant/qdrant

# 2. 初始化 MySQL 数据库
mysql -u root -p < food-recommend-backend/src/main/resources/db/schema.sql
mysql -u root -p food_recommend < food-recommend-backend/src/main/resources/db/data.sql

# 3. 复制配置模板
cp food-recommend-backend/src/main/resources/application-template.yml \
   food-recommend-backend/src/main/resources/application.yml
# 编辑 application.yml，填入真实 API Key

# 4. 启动后端
cd food-recommend-backend
mvn spring-boot:run

# 5. 启动店主/服务员 Web 前端
cd food-recommend-frontend
npm install
npm run dev
# 默认运行在 http://localhost:5174

# 6. 初始化菜品向量（首次启动后执行一次）
curl -X POST http://localhost:8080/api/owner/dishes/vector/batch-rebuild
```

---

## 三、目录结构

```text
.
├── food-recommend-backend/           # Spring Boot 后端
│   └── src/main/java/com/example/foodrecommend/
│       ├── controller/               # REST 控制器（owner/waiter/auth）
│       ├── service/                  # 接口定义
│       │   └── impl/                 # 6-Agent 实现类
│       │       ├── VoiceUnderstandingServiceImpl.java   # Agent 0
│       │       ├── ScenePerceptionServiceImpl.java      # Agent 1
│       │       ├── UserProfileServiceImpl.java          # Agent 2
│       │       ├── DishMatchingServiceImpl.java         # Agent 3
│       │       ├── RecommendationRankingServiceImpl.java # Agent 4
│       │       └── ScriptGenerationServiceImpl.java     # Agent 5
│       ├── entity/                   # MyBatis Plus 实体类
│       ├── dto/                      # 数据传输对象
│       ├── mapper/                   # MyBatis Mapper 接口
│       ├── config/                   # Spring 配置类
│       └── security/                 # JWT 鉴权过滤器
│   └── src/main/resources/
│       ├── application-template.yml  # 配置模板（提交到 git）
│       ├── application.yml           # 本地真实配置（.gitignore）
│       └── db/
│           ├── schema.sql            # 建表 DDL
│           └── data.sql              # 初始菜品数据 + 提示词模板
│
├── food-recommend-frontend/          # Vue 3 + Vite + Element Plus 前端
│   └── src/
│       ├── views/
│       │   ├── owner/                # 店主视图（菜品管理、营收看板）
│       │   └── waiter/               # 服务员视图（标签面板、历史记录）
│       ├── components/
│       │   └── TagPanel.vue          # 标签面板核心组件（勿随意改动业务逻辑）
│       └── router/                   # Vue Router
│
└── food-recommend-miniprogram/       # 微信小程序（服务员语音端）
    ├── pages/
    │   ├── index/                    # 语音推荐页（RecorderManager）
    │   └── history/                  # 历史记录页
    └── project.config.json           # 小程序 appid 配置
```

---

## 四、配置项说明

### 4.1 后端 `application.yml`

```yaml
# AI 模型（配置模板见 application-template.yml）
ai:
  vision:
    api-key: <MiMo API Key>      # Agent 1 场景感知
    model: mimo-v2-omni
  embedding:
    api-key: <DashScope API Key> # Agent 3 向量检索
    model: text-embedding-v3
    dimensions: 1024
  rerank:
    api-key: <MiMo API Key>      # Agent 0/4/5
    model: mimo-v2.5-pro

# Qdrant
qdrant:
  host: localhost
  port: 6333
  collection-name: dish_vector_collection

# 阿里云 OSS（本地开发可设 enabled: false 跳过）
oss:
  enabled: false
  endpoint: oss-cn-xxx.aliyuncs.com
  bucket-name: your-bucket

# JWT
jwt:
  secret: <至少32位随机字符串>
  expiration: 86400000
```

### 4.2 微信小程序 `wx.appid` / `wx.secret`

微信凭证**不**存在 `application.yml` 中，通过 JVM System Property 传入：

```bash
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dwx.appid=你的appid -Dwx.secret=你的secret"
```

### 4.3 前端 `.env`

```bash
# food-recommend-frontend/.env.local
VITE_API_BASE=http://localhost:8080
```

### 4.4 小程序 `project.config.json`

```json
{
  "appid": "你的小程序appid",
  ...
}
```

服务器域名白名单须在微信公众平台 → 开发 → 开发设置 → 服务器域名中配置后端域名。本地调试可开启"不校验合法域名"。

---

## 五、常见任务

### 5.1 新增菜品并生成向量

1. 通过店主 Web 界面或直接插入 MySQL 新增菜品。
2. 调用单菜品向量重建接口：

```http
POST /api/owner/dishes/{id}/vector/rebuild
```

### 5.2 新增 Agent

1. 在 `service/` 下新建接口，例如 `MyNewAgentService.java`。
2. 在 `service/impl/` 下新建实现类 `MyNewAgentServiceImpl.java`。
3. 在 `RecommendServiceImpl.java` 中注入该服务，并在 `executeAgentPipeline()` 方法中接入调用链。

### 5.3 修改 Agent 提示词

提示词存储在 MySQL `prompt_template` 表，无需改代码：

```sql
-- 查看当前提示词
SELECT code, name FROM prompt_template;

-- 修改重排序提示词
UPDATE prompt_template SET content = '...新提示词内容...'
WHERE code = 'RERANK_DISH_RECOMMEND';

-- 可修改的提示词 code：
-- RERANK_DISH_RECOMMEND   -- Agent 4 重排序
-- GENERATE_WAITER_SCRIPT  -- Agent 5 话术生成
-- VOICE_UNDERSTAND        -- Agent 0 语音理解
```

### 5.4 调试推荐链路

每次推荐请求均有 `traceId`（MDC）串联全链路日志：

```bash
# 在后端日志中过滤某次推荐的所有日志
grep "traceId=abc123" logs/app.log
```

Agent 各阶段输出均有 `log.info()` 打印，格式：

```
Agent0-语音→标签: {...}
Agent2-用户画像: {...}
Agent3-候选菜品数: 20, 过滤后: 12
Agent4-重排序结果: {...}
```

---

## 反馈反哺与离线评估

### 配置开关

所有 knob 在 `application.yml` 的 `recommend.feedback-boost.*`（见 `application-template.yml`）。整体关闭：`enabled: false`。

```yaml
recommend:
  feedback-boost:
    enabled: true                          # 总开关
    collection-name: recommendation_history
    top-k-similar: 20                      # Qdrant 检索近邻数量
    similarity-threshold: 0.75            # 最低余弦相似度
    min-samples: 3                         # 触发 boost 的最小样本数
    weight: 0.15                           # boost 加成权重
    boost-cap: 5                           # 单菜品最大 boost 加成次数上限
```

### 数据流简述

1. 服务员在 `POST /api/waiter/feedback/{recordId}` 提交 `adopted=true` + `adoptedDishId`
2. `WaiterRecommendController.submitFeedback()` 在写入 `recommendation_feedback` 表后，
   异步调用 `RecommendationHistoryService.indexAdoption()`（`@Async` + `@Retryable`，不阻塞主流）
3. `indexAdoption` 生成 query embedding，写 Qdrant `recommendation_history` collection
4. 下次同类查询进入 `DishMatchingServiceImpl`，`lookupBoost()` 取回相似历史 → 按 weight/cap 叠加 boost 分
5. 若 Qdrant 写入失败两次，`@Recover` 写 `feedback_index_dlq` 表，不影响主推荐链路

### 失败处理

| 场景 | 结果 |
|---|---|
| Qdrant 不可用 | `@Retryable` 重试 2 次后 `@Recover` 写 DLQ，主流程正常返回 |
| embedding 服务失败 | `indexAdoption` 提前 return，不写 Qdrant，不写 DLQ |
| `lookupBoost` 异常 | catch 返回空 map，推荐降级为无 boost 结果 |

### 离线评估

```bash
python scripts/eval_feedback_boost.py --db-url $MYSQL_URL --window-days 30
```

对比 boost 前后 hit-rate@5 / NDCG@5；不入 CI。
