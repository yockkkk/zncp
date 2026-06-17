# 智味AI-基于多模态场景感知的智能餐饮个性化推荐系统

> 基于多模态大模型与向量数据库的新一代智能餐饮推荐与数字化营收管理平台  
> 一张图片，读懂顾客；多智能体协同，精准配餐。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.x-4fc08d)](https://vuejs.org/)
[![JDK](https://img.shields.io/badge/JDK-17-orange)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## 项目简介

本系统实现了一套完整的智能餐饮个性化推荐与数字化营收管理解决方案：

```
标签 + 场景图片双输入 → 场景感知 Agent (Agent 1) → 用户画像 Agent (Agent 2)
→ 菜品向量检索匹配 (Agent 3) → 智能排序重组 (Agent 4) → AI话术生成 (Agent 5)
→ 结果多终端输出与闭环反馈 → 营收与流水数据看板
```

**核心创新点**：
1. **多模态与长期记忆自适应**：支持“常规推荐模式”与“多人配菜模式”双模式。常规模式支持手机号查询，能够自适应分析熟客的历史口味轨迹与长效食物禁忌，实现一键套用与自愈式偏好管理。
2. **多智能体 (5 Agent) 推荐管线**：场景感知（可选照片输入）、结构化画像、向量语义匹配、高毛利隐形推广重排序、个性化点单推荐话术生成。
3. **闭环反馈与数字营收**：推荐结果具备服务员“采纳”反馈机制，并能够根据实际采纳菜品与份数实时折算销售业绩（流水）。
4. **老板/服务员双端联动**：服务员有个人业绩看板（累计推荐、采纳率、销售额），老板有全局总流水统计看板，全面助力餐厅数字化转型。

---

## 技术架构

```
┌─────────────────────────────────────────┐
│        前端  Vue 3 + Element Plus        │
│          http://localhost:5174          │
└──────────────────┬──────────────────────┘
                   │ HTTP /api
┌──────────────────▼──────────────────────┐
│          后端  Spring Boot 3.2.5        │
│           http://localhost:8080          │
│                                          │
│   ┌──────────┐  ┌────────────────────┐  │
│   │ Controller│  │   5-Agent Pipeline │  │
│   │  接口层   │  │  推荐引擎调度      │  │
│   └──────────┘  └────────────────────┘  │
└──────┬──────────────┬───────────────────┘
       │              │
┌──────▼──────┐ ┌─────▼──────────────────────┐
│    MySQL    │ │        AI 模型平台          │
│  业务数据库  │ │                             │
│             │ │  MiMo-V2-Omni  → 场景分析   │
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
| 前端 | Vue 3 + Vite + Element Plus + Pinia | SPA 单页应用，自适应响应式看板 |
| 后端 | Spring Boot 3.2.5 + MyBatis Plus | RESTful 安全隔离接口 |
| 业务数据库 | MySQL 8.0 | 菜品信息、推荐记录、采纳反馈、员工及账户表 |
| 向量数据库 | Qdrant | 1024维语义向量存储与多路混召检索 |
| 对象存储 | 阿里云 OSS / 本地 Mock | 用户上传现场环境图片存储 |
| AI 引擎 | 阿里云大模型 API (DashScope 等) | 多模态解析、Embedding向量生成、排序大模型与话术生成 |

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8.0+
- Qdrant (Docker 部署或 Windows 直接执行)

### 1. 克隆项目

```bash
git clone https://github.com/yockkkk/zncp.git
cd zncp
```

### 2. 启动 MySQL 并初始化数据库

```bash
# 创建数据库和表
mysql -u root -p < food-recommend-backend/src/main/resources/db/schema.sql

# 插入菜品数据、提示词模板及老板、服务员初始账号
mysql -u root -p < food-recommend-backend/src/main/resources/db/data.sql
```

### 3. 启动 Qdrant 向量数据库

```bash
docker run -d --name food-qdrant -p 6333:6333 qdrant/qdrant
```

### 4. 复制并填充配置文件

```bash
cp food-recommend-backend/src/main/resources/application-template.yml \
   food-recommend-backend/src/main/resources/application.yml
```
配置项包括：数据库账号密码、阿里云大模型 API-KEY 及 OSS 密钥。

### 5. 启动后端 Spring Boot

```bash
cd food-recommend-backend
mvn spring-boot:run
```

### 6. 初始化导入菜品向量至 Qdrant

```bash
curl -X POST http://localhost:8080/api/owner/dishes/vector/batch-rebuild
```

### 7. 启动前端

```bash
cd food-recommend-frontend
npm install
npm run dev
```

---

## 核心 API 概览

| 模块 | 方法 | 路径 | 权限要求 | 说明 |
|---|---|---|---|---|
| 认证 | POST | `/api/auth/login` | 公开 | 用户（老板/服务员）登录 |
| 推荐 | POST | `/api/waiter/recommend` | 服务员 | 标签 + 场景双输入 5 Agent 智能推荐 |
| 历史记录 | GET | `/api/waiter/history` | 服务员 | 查看服务员自己的历史推荐记录 |
| 历史记录 | GET | `/api/waiter/history/{id}` | 服务员 | 查看单条记录 of 智能画像与采纳详情 |
| 反馈采纳 | POST | `/api/waiter/feedback/{recordId}` | 服务员 | 对推荐菜品提交采纳数量及评价（扣减库存） |
| 服务员流水 | GET | `/api/waiter/revenue` | 服务员 | 查询服务员自己累计推荐并被采纳的销售额 |
| 看板概览 | GET | `/api/owner/analytics/overview` | 老板 | 查看总销售额、推荐数、在岗员工及服务员营业额排行榜 |
| 菜品管理 | GET | `/api/owner/dishes` | 老板 | 查询所有菜品（含库存、状态、向量生成情况） |
| 菜品管理 | PUT | `/api/owner/dishes/{id}` | 老板 | 修改菜品，包括一键上架/下架切换 |
| 推荐明细 | GET | `/api/owner/records/{id}` | 老板 | 获取全局特定推荐记录的画像明细及多客点单智能分析 |

---

## 项目结构

```
zncp/
├── food-recommend-backend/                     # Spring Boot 后端项目
│   ├── src/main/
│   │   ├── java/com/example/foodrecommend/
│   │   │   ├── common/                          # 全局规范及自定义异常
│   │   │   ├── controller/                      # 接口层 (老板/服务员双角色安全隔离)
│   │   │   ├── dto/                             # 结构化画像 (UserProfileDTO, GuestProfile) 等数据传输对象
│   │   │   ├── entity/                          # 数据实体 (Dish, RecommendationRecord)
│   │   │   └── service/impl/                    # 5-Agent 推荐管线、Rerank、Qdrant 向量检索等实现
│   │   └── resources/
│   │       ├── db/                              # 数据库 schema 及假数据
│   │       └── application.yml                  # 配置文件
│   └── pom.xml
│
├── food-recommend-frontend/                    # Vue 3 前端项目
│   ├── src/
│   │   ├── api/index.js                        # Axios API 请求封装
│   │   ├── layouts/MainLayout.vue              # 导航与工作台侧边栏布局
│   │   ├── router/index.js                     # 双角色路由鉴权配置
│   │   └── views/
│   │       ├── LoginView.vue                   # 登录页面（防多语种误翻译 zh-CN 自适应）
│   │       ├── WaiterRecommendView.vue         # 服务员 5 Agent 智能推荐工作台
│   │       ├── HistoryView.vue                 # 服务员个人历史纪录与业绩看板（销售额统计）
│   │       ├── DishManageView.vue              # 菜品管理与库存/上下架配置页
│   │       ├── OwnerDashboardView.vue          # 老板营业分析看板（总销售额、排行榜）
│   │       └── OwnerRecordsView.vue            # 老板全局推荐溯源页
│   └── index.html                              # 入口 HTML
│
└── README.md                                    # 说明文档
```

---

## 相关文档

- [开发文档（本地开发版）](智能餐饮推荐系统_开发文档_本地开发版.md)
- [产品技术白皮书](智能餐饮推荐系统_产品技术白皮书.md)
- [技术方案图](智能餐饮推荐系统_技术方案图.md)

---

## 许可证

本项目基于 MIT 许可证开源。
