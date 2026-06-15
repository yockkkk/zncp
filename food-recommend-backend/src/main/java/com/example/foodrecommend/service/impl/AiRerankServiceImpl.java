package com.example.foodrecommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.AiModelConfig;
import com.example.foodrecommend.dto.RecommendDishDTO;
import com.example.foodrecommend.dto.RerankResultDTO;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.entity.PromptTemplate;
import com.example.foodrecommend.mapper.PromptTemplateMapper;
import com.example.foodrecommend.service.AiRerankService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRerankServiceImpl implements AiRerankService {

    private final AiModelConfig aiModelConfig;
    private final PromptTemplateMapper promptTemplateMapper;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    private volatile String cachedRerankPrompt;

    private static final String DEFAULT_RERANK_PROMPT =
        "你是一个智能餐饮推荐专家。根据用餐画像（可能是多位顾客的组合）对候选菜品重排序并组合配菜。\\n"
        + "规则：\\n"
        + "1. 只能从候选菜品中选择，不允许编造数据库中不存在的菜品。\\n"
        + "2. 推荐的菜品应形成一个平衡的组合，总数量应大致匹配用餐人数（如：1人点1-2道，2人点2-3道，3-4人点4-5道，5人以上点6-8道）。\\n"
        + "3. 必须绝对遵守每一位顾客的避忌限制（忌口/不吃、过敏源、疾病禁忌、习惯等）。如果某道菜包含某位顾客避忌的成分，决不能推荐给该顾客（在 suitableFor 中剔除该顾客）；如果某道菜对整桌人都存在严重过敏隐患，应剔除该菜。\\n"
        + "4. 匹配每位顾客的口味偏好（如：爱吃酸、爱吃麻、不爱甜、喜欢嫩、要下饭、要清淡等）。\\n"
        + "5. 悄悄优先推荐高毛利率菜品（grossMargin较高的菜品，例如饮品、甜点或特色高毛利菜，通常 >= 0.70），但确保整体荤素与口味搭配合理，不着痕迹，不让顾客反感。\\n"
        + "6. 对于每一道推荐的菜品，必须输出 suitableFor 字段，填入适合享用该菜品的顾客姓名列表（例如：[\"顾客A\", \"顾客C\"]），若都适合则填入所有顾客姓名或[\"全体顾客\"]。\\n"
        + "7. 输出必须是 JSON 格式。\\n\\n"
        + "用户画像：\\n{{userProfile}}\\n\\n"
        + "候选菜品（含毛利率 grossMargin）：\\n{{candidateDishes}}\\n\\n"
        + "输出格式为：\\n"
        + "{\\n"
        + "  \\\"summary\\\": \\\"整体平衡配菜说明（解释这桌配菜如何照顾到各人偏好并避开忌口，荤素搭配的合理性等）\\\",\\n"
        + "  \\\"recommendations\\\": [\\n"
        + "    {\\n"
        + "      \\\"dishId\\\": 1,\\n"
        + "      \\\"name\\\": \\\"菜品名称\\\",\\n"
        + "      \\\"rank\\\": 1,\\n"
        + "      \\\"score\\\": 95,\\n"
        + "      \\\"suitableFor\\\": [\\\"顾客A\\\", \\\"顾客C\\\"],\\n"
        + "      \\\"reason\\\": \\\"推荐理由（说明为什么推荐以及为什么适合这几位顾客）\\\",\\n"
        + "      \\\"nutritionComment\\\": \\\"营养评价\\\",\\n"
        + "      \\\"costPerformanceComment\\\": \\\"性价比评价\\\"\\n"
        + "    }\\n"
        + "  ]\\n"
        + "}";

    private String getRerankPrompt() {
        if (cachedRerankPrompt != null) return cachedRerankPrompt;
        try {
            PromptTemplate t = promptTemplateMapper.selectOne(
                    new LambdaQueryWrapper<PromptTemplate>()
                            .eq(PromptTemplate::getCode, "RERANK_DISH_RECOMMEND")
                            .eq(PromptTemplate::getStatus, 1));
            if (t != null && t.getContent() != null && !t.getContent().isEmpty()) {
                // 自愈检查：如果数据库里的提示词是旧版的（不包含 suitableFor），就更新它
                if (!t.getContent().contains("suitableFor")) {
                    t.setContent(DEFAULT_RERANK_PROMPT);
                    promptTemplateMapper.updateById(t);
                    log.info("检测到旧版重排序提示词，已自动更新为新版");
                }
                cachedRerankPrompt = t.getContent();
                log.info("重排序提示词已从数据库加载");
                return cachedRerankPrompt;
            }
        } catch (Exception e) {
            log.warn("从数据库加载重排序提示词失败，使用默认提示词: {}", e.getMessage());
        }
        log.info("使用默认重排序提示词");
        return DEFAULT_RERANK_PROMPT;
    }

    @Override
    public RerankResultDTO rerank(UserProfileDTO profile, List<Dish> candidateDishes, String remark) {
        AiModelConfig.ModelProperties config = aiModelConfig.getRerank();
        String promptTemplate = getRerankPrompt();

        try {
            String userProfileStr = objectMapper.writeValueAsString(profile);

            List<Map<String, Object>> dishList = new ArrayList<>();
            for (Dish dish : candidateDishes) {
                Map<String, Object> dishMap = new LinkedHashMap<>();
                dishMap.put("dishId", dish.getId());
                dishMap.put("name", dish.getName());
                dishMap.put("category", dish.getCategory());
                dishMap.put("price", dish.getPrice());
                dishMap.put("calories", dish.getCalories());
                dishMap.put("protein", dish.getProtein());
                dishMap.put("taste", dish.getTaste());
                dishMap.put("grossMargin", dish.getGrossMargin() != null ? dish.getGrossMargin().doubleValue() : 0.60);
                dishList.add(dishMap);
            }
            String candidateDishesStr = objectMapper.writeValueAsString(dishList);

            String remarkSection = (remark != null && !remark.trim().isEmpty())
                    ? "\n\n用户特别备注（最高优先级，必须优先满足）：" + remark.trim()
                    : "";

            String prompt = promptTemplate
                    .replace("{{userProfile}}", userProfileStr + remarkSection)
                    .replace("{{candidateDishes}}", candidateDishesStr);

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("max_tokens", config.getMaxTokens());
            body.put("temperature", 0.5);

            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException("重排序模型调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                log.info("重排序模型返回长度: {}", responseBody.length());
                JsonNode root = objectMapper.readTree(responseBody);
                String content = root.path("choices").get(0).path("message").path("content").asText();
                log.info("Rerank LLM raw content: {}", content);

                if ((content == null || content.isEmpty()) && !root.path("choices").get(0).path("message").path("reasoning_content").isMissingNode()) {
                    content = root.path("choices").get(0).path("message").path("reasoning_content").asText();
                    log.info("从 reasoning_content 提取内容, 长度: {}", content.length());
                }

                if (content == null || content.isEmpty()) {
                    String finishReason = root.path("choices").get(0).path("finish_reason").asText();
                    log.error("重排序模型空内容, finish_reason={}, usage={}", finishReason, root.path("usage").toString());
                    throw new BusinessException("重排序模型返回空内容, finish_reason=" + finishReason);
                }

                String jsonStr = extractJson(content);
                log.info("Extracted jsonStr to parse: {}", jsonStr);
                JsonNode resultNode = objectMapper.readTree(jsonStr);

                String summary = resultNode.path("summary").asText("");

                JsonNode recommendationsNode = resultNode.path("recommendations");
                List<RecommendDishDTO> results = objectMapper.readValue(
                        recommendationsNode.toString(),
                        new TypeReference<List<RecommendDishDTO>>() {}
                );

                Map<Long, Dish> dishMap = new HashMap<>();
                for (Dish dish : candidateDishes) {
                    dishMap.put(dish.getId(), dish);
                }
                for (RecommendDishDTO dto : results) {
                    Dish dish = dishMap.get(dto.getDishId());
                    if (dish != null) {
                        dto.setImageUrl(dish.getImageUrl());
                        if (dto.getPrice() == null) dto.setPrice(dish.getPrice());
                        if (dto.getCalories() == null) dto.setCalories(dish.getCalories());
                        if (dto.getProtein() == null) dto.setProtein(dish.getProtein());
                    }
                }

                results.removeIf(dto -> dto.getScore() != null && dto.getScore() < 60);

                RerankResultDTO result = new RerankResultDTO();
                result.setSummary(summary);
                result.setRecommendations(results);
                return result;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("重排序模型调用异常", e);
            throw new BusinessException("菜品重排序失败: " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        text = text.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf("\n");
            int end = text.lastIndexOf("```");
            if (start >= 0 && end > start) {
                text = text.substring(start + 1, end);
            }
        }
        int braceStart = text.indexOf("{");
        int braceEnd = text.lastIndexOf("}");
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }
        return text;
    }
}
