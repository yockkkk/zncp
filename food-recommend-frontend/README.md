# 智能餐饮推荐系统 - 前端

Vue 3 + Vite + Element Plus + Pinia.

## 开发

```bash
npm install
npm run dev   # http://localhost:5173
```

需要后端运行在 http://localhost:8080（默认）；若不同，修改 `.env.development` 的 `VITE_API_BASE`。

## 构建

```bash
npm run build
```
产物在 `dist/`，部署到 nginx 等静态托管。生产模式默认 `VITE_API_BASE=/api`（通过 nginx 反向代理到后端）。

## 代码规范

```bash
npm run lint        # 检查
npm run lint:fix    # 自动修复
```
