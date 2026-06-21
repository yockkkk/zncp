# API 速查表

> 完整交互式文档见 Swagger UI: `http://<host>:8080/swagger-ui.html`

所有响应统一格式：
```json
{ "code": 200, "message": "ok", "data": {...} }
```

需鉴权的端点请在 `Authorization` Header 携带 `Bearer <jwt>`。

## 认证 `/api/auth/**`

| Method | Path | 角色 | 简介 |
|---|---|---|---|
| POST | `/api/auth/login` | 无 | 密码登录 |
| POST | `/api/auth/wx-login` | 无 | 微信 jscode2session |
| POST | `/api/auth/register` | OWNER | 老板创建服务员账号 |

**示例: 密码登录**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

## 服务员-推荐 `/api/waiter/**`

| Method | Path | 角色 | 简介 |
|---|---|---|---|
| GET | `/api/waiter/dishes` | WAITER | 可推荐菜品列表 |
| POST | `/api/waiter/recommend` | WAITER | 标签+场景推荐（5 Agent） |
| POST | `/api/waiter/recommend/voice` | WAITER | 语音推荐（Agent 0 + 5 Agent） |
| GET | `/api/waiter/customer/profile?phone=` | WAITER | 顾客长期记忆 |
| GET | `/api/waiter/history` | WAITER | 我的推荐历史（含反馈） |
| GET | `/api/waiter/history/{id}` | WAITER | 推荐详情 |
| POST | `/api/waiter/feedback/{recordId}` | WAITER | 反馈/采纳/扣库存 |
| GET | `/api/waiter/revenue` | WAITER | 我的营业额 |

**示例: 语音推荐**
```bash
curl -X POST http://localhost:8080/api/waiter/recommend/voice \
  -H "Authorization: Bearer <jwt>" \
  -F "voiceText=两个客人吃晚餐中等预算"
```

**示例: 反馈采纳**
```bash
curl -X POST http://localhost:8080/api/waiter/feedback/123 \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"adopted":true,"adoptedDishId":456,"quantity":2}'
```

## 店主-管理 `/api/owner/**`

| Method | Path | 角色 | 简介 |
|---|---|---|---|
| GET | `/api/owner/analytics/overview` | OWNER | 数据概览（采纳率、营业额、热门菜品） |
| GET | `/api/owner/records` | OWNER | 全部推荐记录（可按 waiterId/adopted 过滤） |
| GET | `/api/owner/records/{id}` | OWNER | 推荐记录详情 |
| GET | `/api/owner/staff` | OWNER | 员工列表 |
| POST | `/api/owner/staff` | OWNER | 新增员工 |
| PUT | `/api/owner/staff/{id}/status` | OWNER | 启用/禁用员工 |
| DELETE | `/api/owner/staff/{id}` | OWNER | 删除员工 |

## 菜品 `/api/owner/dishes/**`

| Method | Path | 角色 | 简介 |
|---|---|---|---|
| GET | `/api/owner/dishes` | OWNER | 菜品列表 |
| GET | `/api/owner/dishes/{id}` | OWNER | 菜品详情 |
| POST | `/api/owner/dishes` | OWNER | 新增菜品 |
| PUT | `/api/owner/dishes/{id}` | OWNER | 修改菜品 |
| DELETE | `/api/owner/dishes/{id}` | OWNER | 删除菜品 |
| POST | `/api/owner/dishes/vector/batch-rebuild` | OWNER | 批量重建向量 |
| POST | `/api/owner/dishes/{id}/vector/rebuild` | OWNER | 单菜品向量重建 |

## 上传 `/api/upload`

| Method | Path | 角色 | 简介 |
|---|---|---|---|
| POST | `/api/upload` | 已认证 | 上传文件到 OSS，返回 URL |

## 推荐-兼容 `/api/recommend/**`（已废弃）

| Method | Path | 角色 | 简介 |
|---|---|---|---|
| POST | `/api/recommend/image` | WAITER/OWNER | 图片推荐（已废弃，用 /api/waiter/recommend） |
| GET | `/api/recommend/history` | WAITER/OWNER | 历史记录（已废弃，用 /api/waiter/history） |

（运行后端，访问 Swagger UI 实时查看完整端点；本文档作为快速速查，详细字段以 Swagger 为准。）
