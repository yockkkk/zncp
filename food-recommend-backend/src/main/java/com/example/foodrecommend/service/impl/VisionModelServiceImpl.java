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

    @Override
    public UserProfileDTO analyzeImage(String imageUrl) {
        AiModelConfig.ModelProperties config = aiModelConfig.getVision();

        PromptTemplate template = promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getCode, "VISION_ANALYZE_CUSTOMER")
                        .eq(PromptTemplate::getStatus, 1)
        );
        if (template == null) {
            throw new BusinessException("未找到图片分析提示词模板");
        }

        try {
            List<Map<String, Object>> contentList = new ArrayList<>();
            contentList.add(Map.of("type", "text", "text", template.getContent()));
            contentList.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", contentList)));
            body.put("max_tokens", config.getMaxTokens());

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

                if (content == null || content.isEmpty()) {
                    String finishReason = root.path("choices").get(0).path("finish_reason").asText();
                    throw new BusinessException("多模态模型返回空内容, finish_reason=" + finishReason);
                }

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
