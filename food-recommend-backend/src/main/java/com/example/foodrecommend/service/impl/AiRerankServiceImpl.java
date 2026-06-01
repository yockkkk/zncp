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
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRerankServiceImpl implements AiRerankService {

    private final AiModelConfig aiModelConfig;
    private final PromptTemplateMapper promptTemplateMapper;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Override
    public RerankResultDTO rerank(UserProfileDTO profile, List<Dish> candidateDishes) {
        AiModelConfig.ModelProperties config = aiModelConfig.getRerank();

        PromptTemplate template = getPromptTemplate();
        if (template == null) {
            throw new BusinessException("未找到重排序提示词模板");
        }

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

            String prompt = template.getContent()
                    .replace("{{userProfile}}", userProfileStr)
                    .replace("{{candidateDishes}}", candidateDishesStr);

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("max_tokens", config.getMaxTokens());

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

                if (content == null || content.isEmpty()) {
                    String finishReason = root.path("choices").get(0).path("finish_reason").asText();
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

    private PromptTemplate getPromptTemplate() {
        return promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getCode, "RERANK_DISH_RECOMMEND")
                        .eq(PromptTemplate::getStatus, 1)
        );
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
