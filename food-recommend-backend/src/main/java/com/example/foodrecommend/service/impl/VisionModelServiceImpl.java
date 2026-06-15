package com.example.foodrecommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.AiModelConfig;
import com.example.foodrecommend.dto.UserProfileDTO;
import com.example.foodrecommend.entity.PromptTemplate;
import com.example.foodrecommend.mapper.PromptTemplateMapper;
import com.example.foodrecommend.service.VisionModelService;
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
public class VisionModelServiceImpl implements VisionModelService {

    private final AiModelConfig aiModelConfig;
    private final PromptTemplateMapper promptTemplateMapper;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    private volatile String cachedVisionPrompt;

    private static final String DEFAULT_VISION_PROMPT =
        "你是一个餐饮推荐系统的顾客场景分析助手。请根据图片进行非身份识别式场景分析。"
        + "从环境、衣着、氛围等角度综合判断，输出JSON：{peopleCount(人数), ageRange(年龄段), "
        + "diningScene(场景), estimatedConsumptionLevel(低/中等/高), possiblePreferences(偏好数组), "
        + "healthGoal(健康目标), recommendationKeywords(关键词数组)}。不识别身份，只输出JSON。";

    private String getVisionPrompt() {
        if (cachedVisionPrompt != null) return cachedVisionPrompt;
        try {
            PromptTemplate t = promptTemplateMapper.selectOne(
                    new LambdaQueryWrapper<PromptTemplate>()
                            .eq(PromptTemplate::getCode, "VISION_ANALYZE_CUSTOMER")
                            .eq(PromptTemplate::getStatus, 1));
            if (t != null && t.getContent() != null && !t.getContent().isEmpty()) {
                cachedVisionPrompt = t.getContent();
                log.info("视觉提示词已从数据库加载");
                return cachedVisionPrompt;
            }
        } catch (Exception e) {
            log.warn("从数据库加载视觉提示词失败，使用默认提示词: {}", e.getMessage());
        }
        log.info("使用默认视觉提示词");
        return DEFAULT_VISION_PROMPT;
    }

    @Override
    public UserProfileDTO analyzeImage(String imageUrl) {
        AiModelConfig.ModelProperties config = aiModelConfig.getVision();
        String prompt = getVisionPrompt();

        try {
            List<Map<String, Object>> contentList = new ArrayList<>();
            contentList.add(Map.of("type", "text", "text", prompt));
            contentList.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", contentList)));
            body.put("max_tokens", config.getMaxTokens());
            body.put("temperature", 0.3);

            log.info("调用多模态模型分析图片, url={}", imageUrl);

            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    String errBody = response.body() != null ? response.body().string() : "";
                    log.error("多模态模型调用失败, code={}, body={}", response.code(), errBody);
                    throw new BusinessException("多模态模型调用失败: " + response.code() + " " + errBody);
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                String content = root.path("choices").get(0).path("message").path("content").asText();

                if ((content == null || content.isEmpty()) && !root.path("choices").get(0).path("message").path("reasoning_content").isMissingNode()) {
                    content = root.path("choices").get(0).path("message").path("reasoning_content").asText();
                    log.info("从 reasoning_content 提取内容, 长度: {}", content.length());
                }

                if (content == null || content.isEmpty()) {
                    String finishReason = root.path("choices").get(0).path("finish_reason").asText();
                    log.error("多模态模型空内容, finish_reason={}, usage={}", finishReason, root.path("usage").toString());
                    throw new BusinessException("多模态模型返回空内容, finish_reason=" + finishReason);
                }

                log.info("多模态模型响应长度: {}", content.length());

                String jsonStr = extractJson(content);
                return objectMapper.readValue(jsonStr, UserProfileDTO.class);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("多模态模型调用异常", e);
            throw new BusinessException("图片分析失败: " + e.getMessage());
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
