# 智能餐饮推荐系统

> 基于多模态大模型与向量数据库的新一代智能餐饮推荐引擎  
> 一张图片，读懂顾客；一套算法，精准推荐。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.x-4fc08d)](https://vuejs.org/)
[![JDK](https://img.shields.io/badge/JDK-17-orange)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## 项目简介

本系统实现了一套完整的智能餐饮推荐解决方案：

```
图片上传 → OSS云端存储 → 多模态AI分析用户画像 → Embedding语义向量化
→ Qdrant向量检索召回 → MySQL查询菜品详情 → AI大模型重排序 → 前端展示结果
```

**核心创新点**：无需用户注册登录、无需历史行为数据，仅凭一张现场图片即可秒级完成场景感知与精准推荐。所有推荐结果100%来自真实菜品库，杜绝AI编造。

---

## 技术架构

```
┌─────────────────────────────────────────┐
│              前端  Vue 3 + Element Plus  │
│            http://localhost:5173         │
└──────────────────┬──────────────────────┘
                   │ HTTP /api
┌──────────────────▼──────────────────────┐
│          后端  Spring Boot 3.2.5        │
│           http://localhost:8080          │
│                                          │
│   ┌──────────┐  ┌────────────────────┐  │
│   │ Controller│  │  RecommendService  │  │
│   │  接口层   │  │  推荐流程编排        │  │
│   └──────────┘  └────────────────────┘  │
└──────┬──────────────┬───────────────────┘
       │              │
┌──────▼──────┐ ┌─────▼──────────────────────┐
│    MySQL    │ │        AI 模型平台          │
│  业务数据库  │ │                             │
│             │ │  MiMo-V2-Omni  → 图片分析   │
│  • dish     │ │  text-embedding-v3 → 向量化 │
│  • record   │ │  MiMo-V2.5-Pro → 重排序    │
│  • prompt   │ └─────────────────────────────┘
└─────────────┘
       │
┌──────▼──────┐
│   Qdrant    │
│  向量数据库  │
│  1024维向量  │
└─────────────┘
```

### 技术栈

| 层级 | 技术 | 说明 |
|---|---|---|
| 前端 | Vue 3 + Vite + Element Plus + Vue Router | SPA 单页应用 |
| 后端 | Spring Boot 3 + MyBatis Plus | RESTful API |
| 业务数据库 | MySQL 8.0 | 菜品、推荐记录、提示词 |
| 向量数据库 | Qdrant | 1024维语义向量存储与检索 |
| 对象存储 | 阿里云 OSS | 用户上传图片存储 |
| AI - 视觉 | MiMo-V2-Omni | 多模态图片场景分析 |
| AI - 嵌入 | text-embedding-v3 (DashScope) | 文本语义向量化 |
| AI - 排序 | MiMo-V2.5-Pro | 候选菜品智能重排序 |

### 三个AI模型各司其职

```
MiMo-V2-Omni       →  看图片 → 生成用户画像（人数、场景、偏好、消费力）
text-embedding-v3  →  文字 → 1024维数学向量（语义指纹）
MiMo-V2.5-Pro      →  排序 → 综合评分 + 推荐理由 + 营养评价 + 性价比分析
```

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8.0+
- Qdrant（Docker 或 Windows 可执行文件）

### 1. 克隆项目

```bash
git clone https://github.com/yockkkk/zncp.git
cd zncp
```

### 2. 启动 MySQL 并初始化数据库

```bash
# 创建数据库和表
mysql -u root -p < food-recommend-backend/src/main/resources/db/schema.sql

# 插入50条菜品假数据和提示词模板
mysql -u root -p < food-recommend-backend/src/main/resources/db/data.sql
```

### 3. 启动 Qdrant

```bash
# Docker 方式
docker run -d --name food-qdrant -p 6333:6333 qdrant/qdrant

# 或下载 Windows 可执行文件直接运行 qdrant.exe
```

### 4. 配置后端

```bash
# 复制模板配置
cp food-recommend-backend/src/main/resources/application-template.yml \
   food-recommend-backend/src/main/resources/application.yml

# 编辑 application.yml 填入你的密钥
```

需要配置的项：

```yaml
spring.datasource.password          # MySQL 密码
ai.vision.api-key                   # MiMo 视觉模型 API Key
ai.embedding.api-key                # DashScope Embedding API Key
ai.rerank.api-key                   # MiMo 重排序模型 API Key
oss.access-key-id / access-key-secret  # 阿里云 OSS 密钥（可选，本地可用 mock）
```

### 5. 启动后端

```bash
cd food-recommend-backend
mvn spring-boot:run
```

后端启动后访问：http://localhost:8080

### 6. 生成菜品向量

```bash
curl -X POST http://localhost:8080/api/admin/dish/vector/batch-rebuild
```

该接口将 MySQL 中的 50 条菜品文本转为语义向量写入 Qdrant。

### 7. 启动前端

```bash
cd food-recommend-frontend
npm install
npm run dev
```

前端启动后访问：http://localhost:5173

### 8. 测试推荐流程

1. 浏览器打开 http://localhost:5173
2. 上传一张用餐场景图片
3. 点击"开始智能推荐"
4. 查看 AI 生成的用户画像和推荐结果

---

## 项目结构

```
zncp/
├── food-recommend-backend/                     # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/foodrecommend/
│       │   ├── FoodRecommendApplication.java   # 启动类
│       │   ├── common/                          # Result、异常处理
│       │   ├── config/                          # AI模型、Qdrant、OSS、跨域配置
│       │   ├── controller/                      # RecommendController、DishController
│       │   ├── dto/                             # UserProfileDTO、RecommendDishDTO 等
│       │   ├── entity/                          # Dish、RecommendationRecord、PromptTemplate
│       │   ├── mapper/                          # MyBatis Plus Mapper 接口
│       │   └── service/impl/                    # 推荐、AI、向量搜索等核心服务
│       └── resources/
│           ├── application-template.yml         # 配置模板（需复制为 application.yml）
│           └── db/
│               ├── schema.sql                   # 建表脚本
│               └── data.sql                     # 50条菜品 + 提示词模板
│
├── food-recommend-frontend/                    # Vue 3 前端
│   ├── vite.config.js                          # Vite 配置（含 API 代理）
│   └── src/
│       ├── main.js                             # 入口
│       ├── App.vue                             # 根组件
│       ├── api/index.js                        # Axios 封装
│       ├── router/index.js                     # 路由配置
│       ├── layouts/MainLayout.vue              # 侧边栏布局
│       └── views/
│           ├── RecommendView.vue               # 智能推荐页
│           ├── DishManageView.vue              # 菜品管理页
│           └── HistoryView.vue                 # 推荐历史页
│
├── README.md
├── 智能餐饮推荐系统_开发文档_本地开发版.md        # 开发文档
├── 智能餐饮推荐系统_产品技术白皮书.md             # 产品技术白皮书
└── 智能餐饮推荐系统_技术方案图.md                # 技术方案图
```

---

## API 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/recommend/image` | 上传图片 → 完整推荐 |
| GET | `/api/recommend/history` | 查询推荐历史（最近50条） |
| GET | `/api/admin/dish` | 菜品列表 |
| POST | `/api/admin/dish` | 新增菜品 |
| PUT | `/api/admin/dish/{id}` | 修改菜品 |
| DELETE | `/api/admin/dish/{id}` | 删除菜品 |
| POST | `/api/admin/dish/vector/batch-rebuild` | 批量生成菜品向量 |
| POST | `/api/admin/dish/{id}/vector/rebuild` | 单个菜品重建向量 |
| POST | `/api/upload` | 图片上传 |

### 推荐接口返回示例

```json
{
  "code": 200,
  "message": "推荐成功",
  "data": {
    "recordId": 10001,
    "imageUrl": "https://java-ai7777777.oss-cn-beijing.aliyuncs.com/recommend/xxx.jpg",
    "userProfile": {
      "peopleCount": 2,
      "ageRange": "20-30",
      "diningScene": "朋友聚餐",
      "estimatedConsumptionLevel": "中等",
      "possiblePreferences": ["性价比", "营养均衡", "清淡"],
      "healthGoal": "日常均衡饮食"
    },
    "summary": "根据两位年轻用户朋友聚餐的场景，推荐营养均衡、价格适中的菜品组合",
    "recommendations": [
      {
        "dishId": 6,
        "name": "牛肉藜麦饭",
        "price": 32,
        "calories": 420,
        "protein": 38,
        "rank": 1,
        "score": 95,
        "reason": "蛋白质含量高，价格适中，适合年轻人的营养需求",
        "nutritionComment": "蛋白质38g，营养搭配均衡",
        "costPerformanceComment": "32元在同类中性价比较高"
      }
    ]
  }
}
```

---

## 相关文档

- [开发文档（本地开发版）](智能餐饮推荐系统_开发文档_本地开发版.md)
- [产品技术白皮书](智能餐饮推荐系统_产品技术白皮书.md)
- [技术方案图](智能餐饮推荐系统_技术方案图.md)

---

## License

MIT License

---

> 本项目为毕业设计/课程设计作品，仅供学习交流使用。
