# 智能餐饮推荐系统 · 架构与技术方案

---

## 图一：三端拓扑图

```mermaid
flowchart LR
  subgraph 客户端
    OwnerWeb[店主 Web<br/>Vue 3 + Vite<br/>店主菜品管理 / 营收看板]
    WaiterWeb[服务员 Web<br/>Vue 3 + Vite<br/>标签面板推荐 / 个人业绩]
    MiniProg[服务员微信小程序<br/>语音录制 / 历史记录]
  end

  subgraph 后端
    Spring[Spring Boot 3.2.5<br/>Java 17<br/>6-Agent 推荐管线]
    MySQL[(MySQL 8<br/>业务数据)]
    Qdrant[(Qdrant<br/>向量数据库)]
  end

  subgraph 外部依赖
    MiMo[MiMo LLM<br/>mimo-v2-omni<br/>mimo-v2.5-pro]
    DashScope[DashScope<br/>text-embedding-v3]
    OSS[阿里云 OSS<br/>场景图片存储]
    WX[微信 jscode2session<br/>API]
  end

  OwnerWeb -- JWT HTTP --> Spring
  WaiterWeb -- JWT HTTP --> Spring
  MiniProg -- JWT HTTP --> Spring
  MiniProg -- wx.login --> WX
  WX -- openid --> Spring
  Spring --- MySQL
  Spring --- Qdrant
  Spring -- Agent1/Agent0/4/5 --> MiMo
  Spring -- Agent3 Embedding --> DashScope
  Spring -- 场景图片 --> OSS
```

---

## 图二：6-Agent 推荐时序图

```mermaid
sequenceDiagram
  participant C as 客户端（Web/小程序）
  participant R as RecommendService
  participant A0 as Agent 0\nVoiceUnderstanding
  participant A1 as Agent 1\nScenePerception
  participant A2 as Agent 2\nUserProfile
  participant A3 as Agent 3\nDishMatching
  participant A4 as Agent 4\nRanking
  participant A5 as Agent 5\nScriptGeneration
  participant DB as MySQL / Qdrant

  C->>R: POST /api/waiter/recommend/tags（标签） 或\nPOST /api/waiter/recommend/voice（语音文本）

  alt 语音路径
    R->>A0: parseVoiceText(voiceText)
    A0-->>R: TagInputDTO
  end

  opt 有场景图片
    R->>A1: analyzeScene(imageUrl)
    A1-->>R: SceneContextDTO
  end

  R->>A2: buildProfile(TagInputDTO, SceneContextDTO)
  A2-->>R: UserProfileDTO + queryText

  R->>A3: matchDishes(queryText, UserProfileDTO, top20)
  A3->>DB: DashScope Embedding + Qdrant search
  DB-->>A3: 候选菜品向量 ID 列表
  A3->>DB: MySQL 查询菜品详情
  DB-->>A3: List<Dish>
  A3-->>R: 安全规则过滤后的 List<Dish>

  R->>A4: rank(UserProfileDTO, List<Dish>)
  A4-->>R: RerankResultDTO（含毛利率隐形加权）

  R->>A5: generateScripts(UserProfileDTO, 推荐菜品)
  A5-->>R: ScriptResultDTO（开场白 + 每道菜话术）

  R->>DB: 保存 recommendation_record
  R-->>C: RecommendWithScriptDTO
```

---

## 图三：ER 图

```mermaid
erDiagram
  sys_user {
    bigint id PK
    varchar username
    varchar password
    varchar real_name
    varchar role
    varchar phone
    varchar openid
    varchar unionid
    tinyint status
  }

  dish {
    bigint id PK
    varchar name
    varchar category
    decimal price
    int calories
    decimal protein
    decimal fat
    decimal carbohydrate
    varchar taste
    varchar suitable_people
    varchar scene
    varchar tags
    varchar image_url
    text description
    int sales
    int stock
    tinyint status
    tinyint vector_status
    decimal gross_margin
  }

  recommendation_record {
    bigint id PK
    bigint user_id FK
    varchar phone
    bigint waiter_id FK
    varchar image_url
    varchar scene_image_url
    text tag_input_json
    text user_profile_json
    text query_text
    varchar recommended_dish_ids
    text result_json
    text script_result_json
    tinyint adopted
    bigint adopted_dish_id
    int adopted_quantity
  }

  recommendation_feedback {
    bigint id PK
    bigint record_id FK
    bigint waiter_id FK
    bigint adopted_dish_id FK
    int quantity
    tinyint rating
    varchar note
  }

  prompt_template {
    bigint id PK
    varchar code
    varchar name
    text content
    varchar type
    tinyint status
  }

  customer_profile {
    bigint id PK
    bigint record_id FK
    varchar age_range
    int people_count
    varchar consumption_level
    varchar dining_scene
    varchar preference_tags
    varchar health_goal
    text raw_result_json
  }

  sys_user ||--o{ recommendation_record : "waiter_id"
  sys_user ||--o{ recommendation_feedback : "waiter_id"
  recommendation_record ||--o{ recommendation_feedback : "record_id"
  recommendation_record ||--o| customer_profile : "record_id"
  dish ||--o{ recommendation_feedback : "adopted_dish_id"
```

---

## 图四：JWT 鉴权流程

```mermaid
flowchart TD
  subgraph 密码登录路径
    PW_REQ[POST /api/auth/login<br/>username + password] --> PW_VERIFY{校验密码<br/>BCrypt}
    PW_VERIFY -- 通过 --> JWT_ISSUE[签发 JWT<br/>包含 userId + role]
    PW_VERIFY -- 失败 --> PW_ERR[401 Unauthorized]
  end

  subgraph 微信登录路径
    WX_REQ[POST /api/auth/wx-login<br/>code] --> WX_API[调用微信 jscode2session<br/>获取 openid]
    WX_API --> WX_LOOKUP{查 sys_user<br/>WHERE openid = ?}
    WX_LOOKUP -- 存在 --> JWT_ISSUE
    WX_LOOKUP -- 不存在 --> WX_CREATE[创建 WAITER 账号<br/>写入 openid]
    WX_CREATE --> JWT_ISSUE
  end

  JWT_ISSUE --> TOKEN[返回 JWT Token]

  TOKEN --> API_REQ[后续请求<br/>Authorization: Bearer token]
  API_REQ --> FILTER[JwtAuthenticationFilter<br/>解析 + 验签]
  FILTER -- 有效 --> SECURITY[写入 SecurityContext<br/>userId + role]
  FILTER -- 无效/过期 --> ERR401[401 Unauthorized]

  SECURITY --> OWNER_API[/api/owner/**<br/>需要 OWNER 角色]
  SECURITY --> WAITER_API[/api/waiter/**<br/>需要 WAITER 角色]
```

---

## 图五：向量构建与检索流程

```mermaid
flowchart TD
  subgraph 菜品向量构建（批量初始化 / 单菜品重建）
    DISH_DB[(MySQL dish 表)] --> BUILD_TEXT[拼接 embeddingText<br/>名称+分类+价格+口味+适合人群+场景+标签]
    BUILD_TEXT --> EMBED_API[DashScope<br/>text-embedding-v3<br/>输出 1024 维向量]
    EMBED_API --> QDRANT_UPSERT[Qdrant upsert<br/>id=dishId<br/>vector=1024维<br/>payload=菜品元数据]
    QDRANT_UPSERT --> UPDATE_STATUS[MySQL dish.vector_status = 1]
  end

  subgraph 推荐时检索
    QUERY[Agent 2 生成 queryText<br/>基于 UserProfileDTO] --> EMBED_QUERY[DashScope<br/>text-embedding-v3<br/>输出 1024 维查询向量]
    EMBED_QUERY --> QDRANT_SEARCH[Qdrant search<br/>top-20 相似菜品 ID]
    QDRANT_SEARCH --> MYSQL_FETCH[MySQL 查询菜品详情]
    MYSQL_FETCH --> SAFETY_FILTER[Agent 3 安全规则过滤<br/>清真 / 过敏源 / 疾病 / 素食]
    SAFETY_FILTER --> AI_RERANK[Agent 4 LLM 重排序<br/>结合 gross_margin 加权]
    AI_RERANK --> RESULT[最终推荐菜品列表]
  end
```
