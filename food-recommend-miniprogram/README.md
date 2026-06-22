# 智能餐饮推荐系统 - 微信小程序（服务员端）

## 导入步骤

1. 微信开发者工具 → 项目 → 导入项目 → 选择本目录
2. **修改 appid**：编辑 `project.config.json` 中的 `appid`，改为你自己的真实小程序 appid（或保留 `touristappid` 走体验模式）
3. **配置后端域名**：登录 [微信公众平台](https://mp.weixin.qq.com)，开发管理 → 服务器域名 → request 合法域名加入：
   - `https://<your-backend-domain>`
4. 工具左下角"编译"，扫码或模拟器预览

## 后端 base URL

修改 `utils/api.js` 中的 `BASE_URL` 为你的后端地址。本地调试建议在工具的"详情 → 本地设置"勾选"不校验合法域名"。

## 语音插件

使用了 `WechatSI` 同声传译插件（详见 `app.json`）。该插件无需额外开通，但需在 `app.json` 的 `plugins` 配置中已声明。

## 代码规范

```bash
npm install
npm run lint
```
