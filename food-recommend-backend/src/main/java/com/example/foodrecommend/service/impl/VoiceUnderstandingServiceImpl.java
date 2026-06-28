package com.example.foodrecommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.AiModelConfig;
import com.example.foodrecommend.dto.TagInputDTO;
import com.example.foodrecommend.entity.PromptTemplate;
import com.example.foodrecommend.mapper.PromptTemplateMapper;
import com.example.foodrecommend.service.VoiceUnderstandingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Agent 0 实现: 语音理解
 * 用 LLM 将自然语言口语提取为结构化 TagInputDTO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceUnderstandingServiceImpl implements VoiceUnderstandingService {

    private final AiModelConfig aiModelConfig;
    private final PromptTemplateMapper promptTemplateMapper;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TagInputDTO parseVoiceText(String voiceText) {
        if (voiceText == null || voiceText.trim().isEmpty()) {
            return null;
        }

        AiModelConfig.ModelProperties config = aiModelConfig.getScript();
        if (config == null || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            config = aiModelConfig.getRerank();
        }

        PromptTemplate template = promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getCode, "VOICE_UNDERSTAND")
                        .eq(PromptTemplate::getStatus, 1)
        );
        if (template == null) {
            log.warn("未找到语音理解提示词模板，无法解析语音");
            return null;
        }

        try {
            String prompt = template.getContent().replace("{{voiceText}}", voiceText);

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("max_tokens", config.getMaxTokens());
            body.put("temperature", 0.3);

            log.info("Agent0-语音理解: 解析语音文本, len={}", voiceText.length());

            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("语音理解模型调用失败, code={}", response.code());
                    return null;
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode choices = root.path("choices");
                if (!choices.isArray() || choices.size() == 0) {
                    throw new BusinessException("语音理解模型返回结构异常");
                }
                String content = choices.get(0).path("message").path("content").asText();
                if (content.isEmpty()) {
                    content = choices.get(0).path("message").path("reasoning_content").asText();
                }
                if (content.isEmpty()) {
                    log.warn("语音理解模型返回空内容");
                    return null;
                }

                String jsonStr = extractJson(content);
                TagInputDTO tags = objectMapper.readValue(jsonStr, TagInputDTO.class);
                log.info("Agent0-语音理解完成: peopleCount={}, scene={}",
                        tags.getPeopleCount(), tags.getDiningScene());
                return tags;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("语音理解失败: {}", e.getMessage());
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
