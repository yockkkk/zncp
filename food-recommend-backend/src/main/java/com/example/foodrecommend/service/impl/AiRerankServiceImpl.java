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
        "你是一个智能餐饮推荐专家。根据用户画像对候选菜品重排序。规则：\\n"
        + "1. 只从候选菜品选择，不编造\\n"
        + "2. 综合考虑营养、性价比、人数、场景、口味\\n"
        + "3. 减脂偏好优先低脂高蛋白低热量\\n"
        + "4. 多人用餐优先适合分享的套餐\\n"
        + "5. 尽可能多样化推荐，避免每次都相同组合\\n"
        + "6. 输出JSON\\n\\n用户画像：\\n{{userProfile}}\\n\\n候选菜品：\\n{{candidateDishes}}\\n\\n"
        + "输出：{\"summary\":\"整体推荐说明\",\"recommendations\":[{\"dishId\":1,\"name\":\"菜品名\","
        + "\"rank\":1,\"score\":95,\"reason\":\"推荐理由\",\"nutritionComment\":\"营养评价\","
        + "\"costPerformanceComment\":\"性价比评价\"}]}";

    private String getRerankPrompt() {
        if (cachedRerankPrompt != null) return cachedRerankPrompt;
        try {
            PromptTemplate t = promptTemplateMapper.selectOne(
                    new LambdaQueryWrapper<PromptTemplate>()
                            .eq(PromptTemplate::getCode, "RERANK_DISH_RECOMMEND")
                            .eq(PromptTemplate::getStatus, 1));
            if (t != null && t.getContent() != null && !t.getContent().isEmpty()) {
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
