# 反馈反哺排序（Feedback Boost）设计 Spec

- 日期：2026-06-22
- 状态：Approved（待 plan）
- 作者：协作 brainstorm 产出
- 阶段：闭环补全 · 第二阶段子项目 1/4

---

## 1. 目标

让"被服务员采纳过的菜品"在**相似场景**下的下次推荐里更靠前，把已有的反馈采纳数据回灌到 Agent 3 召回排序里，闭环掉"推荐 → 采纳 → 学习"的最后一公里。

**非目标（YAGNI）：**
- 不做时间衰减
- 不做服务员维度个性化
- 不做负反馈降权
- 不动 Agent 4 LLM prompt / 不动毛利加权
- 不做 Qdrant collection 自动清理

---

## 2. 设计决策（已确认）

| 决策点 | 选择 | 理由 |
|---|---|---|
| 反哺主线 | 场景/画像反哺 | 粒度适中、业务意义明显 |
| 同场景定义 | embedding 相似度 | 表示力最强、可绕开标签集稀疏 |
| 注入点 | Agent 3 召回阶段 boost | 可解释、不增 LLM token、不影响重排可控性 |
| 冷启动策略 | 样本足够才 boost（默认 ≥3） | 早期不产生雷点样量推荐 |
| Embedding 存储 | 复用 Qdrant，新 collection | 代码复用现有 VectorSearchService |

---

## 3. 架构与数据流

```
[采纳成功]
  WaiterRecommendController#feedback
    └→ RecommendationHistoryService.indexAdoption(record)        ← 新增（异步）
         ├─ embedding = DashScope.embed(record.queryText)
         └─ Qdrant.upsert(collection="recommendation_history",
              id=recordId, vector=embedding,
              payload={adoptedDishIds, waiterId, createTime})

[新一次推荐]
  DishMatchingServiceImpl.match(queryText, userProfile)
    ├─ 既有：Qdrant dish 检索 + 安全/价格/库存过滤
    ├─ NEW: RecommendationHistoryService.lookupBoost(queryEmbedding)   ← 新增
    │      → Map<dishId, score>   (相似 record ≥ threshold & 数量 ≥ minSamples)
    ├─ 应用 boost：finalScore = baseSalesScore + w × boost(dishId)
    └─ 按 finalScore 排序 → 送 Agent 4
```

**关键不变量：**
1. 安全规则过滤（清真/过敏/糖尿病）**始终在 boost 之前**完成 —— boost 永远不能让被禁菜送出去
2. boost 由配置项 `recommend.feedback-boost.enabled` 控制；关闭时所有路径 short-circuit，行为与现状字节级一致
3. indexAdoption 异步且失败降级，**采纳主流程不受影响**
4. lookupBoost 失败降级返回空 map，**推荐主流程不受影响**

---

## 4. 组件与接口

### 4.1 新增：`RecommendationHistoryService`

```java
public interface RecommendationHistoryService {
    /** 异步：把一条已采纳 record 写入历史向量库 */
    void indexAdoption(Long recordId, String queryText,
                       List<Long> adoptedDishIds, Long waiterId);

    /** 查相似历史采纳 → 返回每个 dishId 的累计采纳次数（已通过样本阈值） */
    Map<Long, Integer> lookupBoost(String queryText);
}
```

**实现要点：**
- `indexAdoption` 由 `@Async` 线程池执行
- `lookupBoost`：embedding → Qdrant search top-K (默认 20) → 过滤 score ≥ `similarityThreshold` (默认 0.75) → 命中数 < `minSamples` (默认 3) → 返回空 map
- 所有异常 catch → log warn → 返回空 map

### 4.2 修改：`DishMatchingServiceImpl`

在现有 sales 排序之前插入 boost 计算：

```java
Map<Long, Integer> boost = historyService.lookupBoost(queryText);
if (!boost.isEmpty()) {
    dishes.sort((a, b) -> Double.compare(
        score(b, boost.getOrDefault(b.getId(), 0)),
        score(a, boost.getOrDefault(a.getId(), 0))));
} else {
    // 既有 sales 排序
}

double score(Dish d, int boostCount) {
    double base = normalize(d.getSales());        // 归一到 0..1
    double bst  = Math.min(boostCount, cap) / (double) cap;
    return base + weight * bst;
}
```

`normalize` 取 `sales / (maxSalesInCandidates + 1)`，避免除零。

### 4.3 修改：`WaiterRecommendController#feedback`

采纳分支末尾追加一行（在事务提交后、返回 200 前）：

```java
historyService.indexAdoption(recordId, record.getQueryText(),
                             adoptedDishIds, waiterId);
```

调用是 `@Async`，不参与事务、不阻塞响应。

### 4.4 Qdrant collection schema：`recommendation_history`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | recordId（幂等：同 record 多次采纳覆盖写入） |
| vector | float[N] | 与现有 dish collection 同维度（由 `VectorSearchService` 配置决定，无需新增配置） |
| payload.adoptedDishIds | List\<Long\> | 该次采纳的菜 |
| payload.waiterId | Long | 备用：未来做服务员个性化时不用迁数据 |
| payload.createTime | epochMs | 备用：未来做时间衰减 |

启动时 `QdrantInitializer` 检测不存在 → 自动 createCollection（复用现 dish collection 初始化模板）。

### 4.5 新增表：`feedback_index_dlq`

存放 indexAdoption 重试仍失败的死信，方便后续手动补偿。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AI | |
| record_id | BIGINT | 失败的 recordId |
| error | TEXT | 异常 message |
| retry_count | INT | 已重试次数 |
| create_time | DATETIME | |

无破坏性变更，纯新增。

---

## 5. 配置项

`application.yml` 新增节，绑定到 `FeedbackBoostProperties`：

```yaml
recommend:
  feedback-boost:
    enabled: true
    collection-name: recommendation_history
    min-samples: 3
    top-k-similar: 20
    similarity-threshold: 0.75
    weight: 0.15
    boost-cap: 5
```

所有 knob 都通过 `@ConfigurationProperties` 绑定，不散落 magic number。

---

## 6. 错误处理矩阵

| 故障点 | 行为 | 用户感知 |
|---|---|---|
| Qdrant `recommendation_history` collection 不存在 | 启动时自动建 | 无 |
| indexAdoption 失败（async） | `@Retryable` 重试 1 次 → 仍失败则写 `feedback_index_dlq` | 无（采纳已成功） |
| lookupBoost embedding 服务 down | catch → log warn → 返回空 map | 推荐照常出 |
| lookupBoost Qdrant 超时（≤500ms） | 同上 | 推荐照常出 |
| `enabled=false` | 路径全部 short-circuit | 完全无新行为 |

**核心原则：反哺是锦上添花，任何一处失败都不阻塞推荐/采纳主流。**

---

## 7. 测试计划

在现有 30 个单测基础上新增。

### 7.1 单元测试（6 个）

1. `RecommendationHistoryServiceTest.indexAdoption_writes_to_qdrant` —— mock Qdrant client，验证 upsert payload
2. `..._lookupBoost_returns_empty_when_below_min_samples` —— mock 命中 2 条 → 返回空
3. `..._lookupBoost_filters_by_similarity_threshold` —— mock 返回 score=0.6 → 不计入
4. `..._lookupBoost_aggregates_adopted_dish_counts` —— mock 3 条命中 → 验证频次合并
5. `DishMatchingServiceImplTest.boost_does_not_bypass_safety_filter` —— 历史采纳含清真禁忌菜，新请求清真=true → 该菜仍被过滤
6. `..._cold_start_falls_back_to_sales_order` —— history 空 → 排序与现状一致

### 7.2 集成测试（2 个）

7. `FeedbackToBoostFlowIT` —— 真 H2 + 嵌入式 Qdrant container：标签 X 推荐 → 采纳菜 A → 同标签 X 再推荐 → A 排序应靠前
8. `FeedbackBoostDisabledIT` —— `enabled=false` 时输出与现状字节级一致

### 7.3 离线评估脚本（不入 CI）

`scripts/eval_feedback_boost.py`：从生产 `recommendation_record` 表 leave-one-out → 算 hit-rate@5 / NDCG@5 对比 boost 前后。docs/dev-guide.md 增加使用说明小节。

---

## 8. 监控埋点

后端结构化日志加 4 个事件（沿用现有 `[traceId]` MDC）：

- `boost.lookup.hit` —— count, avgSimilarity
- `boost.lookup.miss` —— reason: below_min_samples / qdrant_error / disabled
- `boost.applied` —— recordId, top-3 boostedDishIds
- `index.adoption` —— recordId, dishIdsCount

为后续老板看板（子项目 2）准备好原始事件流。

---

## 9. 变更摘要

| 类别 | 数量 | 说明 |
|---|---|---|
| 新增 service | 1 | `RecommendationHistoryService` + impl |
| 新增 properties | 1 | `FeedbackBoostProperties` |
| 新增 Qdrant collection | 1 | `recommendation_history` |
| 新增表 | 1 | `feedback_index_dlq`（纯新增、无破坏性变更） |
| 修改文件 | 2 | `DishMatchingServiceImpl` (~15 行)、`WaiterRecommendController#feedback` (+1 行) |
| 新增测试 | 8 | 6 单元 + 2 集成 |

---

## 10. 后续（不在本 spec 范围）

- 时间衰减权重（payload 已留 createTime）
- 服务员维度个性化（payload 已留 waiterId）
- 负反馈降权
- 老板看板把 boost 日志接进来（子项目 2 范畴）
