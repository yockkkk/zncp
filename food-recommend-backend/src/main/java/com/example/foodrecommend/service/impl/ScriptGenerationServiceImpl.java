package com.example.foodrecommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.AiModelConfig;
import com.example.foodrecommend.dto.*;
import com.example.foodrecommend.entity.PromptTemplate;
import com.example.foodrecommend.mapper.PromptTemplateMapper;
import com.example.foodrecommend.service.ScriptGenerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Agent 5 实现: 话术生成
 * 调用 LLM 生成服务员推荐话术
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptGenerationServiceImpl implements ScriptGenerationService {

    private final AiModelConfig aiModelConfig;
    private final PromptTemplateMapper promptTemplateMapper;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Override
    public ScriptResultDTO generateScripts(UserProfileDTO profile, List<RecommendDishDTO> recommendations) {
        AiModelConfig.ModelProperties config = aiModelConfig.getScript();

        // 如果未配置单独的 script 配置，使用 rerank 配置
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            config = aiModelConfig.getRerank();
        }

        PromptTemplate template = promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getCode, "GENERATE_WAITER_SCRIPT")
                        .eq(PromptTemplate::getStatus, 1)
        );
        if (template == null) {
            log.warn("未找到话术生成提示词模板，跳过话术生成");
            return null;
        }

        try {
            String userProfileStr = objectMapper.writeValueAsString(profile);
            String dishesStr = objectMapper.writeValueAsString(recommendations);

            String prompt = template.getContent()
                    .replace("{{userProfile}}", userProfileStr)
                    .replace("{{recommendedDishes}}", dishesStr);

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("max_tokens", config.getMaxTokens());

            log.info("Agent5-话术生成: 为 {} 道菜品生成话术", recommendations.size());

            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("话术生成模型调用失败, code={}", response.code());
                    return null;
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                String content = root.path("choices").get(0).path("message").path("content").asText();

                if (content == null || content.isEmpty()) {
                    log.warn("话术生成模型返回空内容");
                    return null;
                }

                String jsonStr = extractJson(content);
                log.info("Agent5-话术生成完成: 长度={}", jsonStr.length());
                return objectMapper.readValue(jsonStr, ScriptResultDTO.class);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("话术生成失败，继续返回推荐结果: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String text) {
        text = text.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf("\n");
            int end = text.lastIndexOf("```");
            if (start >= 0 && end > start) {
                text = text.substring(start + 1, end).trim();
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
