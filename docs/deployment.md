# 部署手册

## 本地一键启动

1. **拉取代码 + 准备环境变量**
   ```bash
   git clone <repo>
   cd <repo>
   cp .env.example .env
   # 编辑 .env：填入 MYSQL_PASSWORD / JWT_SECRET / MIMO_API_KEY / DASHSCOPE_API_KEY 等
   ```

   > **注意**：`application.yml` 已加入 `.gitignore`。如果你是首次克隆，需要从模板创建它：
   > ```bash
   > cp food-recommend-backend/src/main/resources/application-template.yml \
   >    food-recommend-backend/src/main/resources/application.yml
   > ```
   > Docker 部署时无需此步骤，所有配置通过 `.env` 注入。

2. **启动后端栈**
   ```bash
   docker compose up -d --build
   docker compose ps     # 等 backend 变 (healthy)
   curl http://localhost:8080/actuator/health
   ```

3. **启动前端（开发模式）**
   ```bash
   cd food-recommend-frontend
   npm install
   npm run dev   # http://localhost:5173
   ```

4. **启动前端（生产模式，可选）**
   ```bash
   docker compose --profile with-frontend up -d frontend
   # 访问 http://localhost
   ```

5. **微信小程序**
   - 微信开发者工具导入 `food-recommend-miniprogram/`
   - 修改 `project.config.json` 中的 `appid` 为你自己的小程序 appid
   - 在微信公众平台后台配置 `request` 合法域名为后端域名

## 常见故障

- **backend 启动失败 `Communications link failure`** — 等 MySQL `(healthy)` 后再看 `docker compose logs backend`
- **`/swagger-ui.html` 404** — 检查 Security 配置是否放行
- **推荐返回 500** — 检查 `MIMO_API_KEY` 和 `DASHSCOPE_API_KEY` 是否填了
- **小程序登录失败** — `WX_APPID` / `WX_SECRET` 必须真实

## 数据初始化

`schema.sql` + `data.sql` 在 MySQL 首次启动时自动执行（通过 docker volume 的 `/docker-entrypoint-initdb.d/`）。如需重新初始化：
```bash
docker compose down -v   # 删除卷
docker compose up -d
```

## 向量重建

数据初始化后，调用 `POST /api/owner/dishes/rebuild-vectors`（需 OWNER 角色 JWT）触发批量向量构建。
