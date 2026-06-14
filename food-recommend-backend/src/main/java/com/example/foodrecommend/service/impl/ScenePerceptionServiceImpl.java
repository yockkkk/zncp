package com.example.foodrecommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.config.AiModelConfig;
import com.example.foodrecommend.dto.SceneContextDTO;
import com.example.foodrecommend.entity.PromptTemplate;
import com.example.foodrecommend.mapper.PromptTemplateMapper;
import com.example.foodrecommend.service.ScenePerceptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Agent 1 实现: 场景感知
 * 使用视觉模型分析用餐环境，不识别面孔，不判断身份
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScenePerceptionServiceImpl implements ScenePerceptionService {

    private final AiModelConfig aiModelConfig;
    private final PromptTemplateMapper promptTemplateMapper;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Override
    public SceneContextDTO analyzeScene(String sceneImageUrl) {
        if (sceneImageUrl == null || sceneImageUrl.isEmpty()) {
            return null;
        }

        AiModelConfig.ModelProperties config = aiModelConfig.getVision();

        PromptTemplate template = promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getCode, "SCENE_ANALYZE")
                        .eq(PromptTemplate::getStatus, 1)
        );
        if (template == null) {
            log.warn("未找到场景分析提示词模板，跳过场景分析");
            return null;
        }

        try {
            List<Map<String, Object>> contentList = new ArrayList<>();
            contentList.add(Map.of("type", "text", "text", template.getContent()));
            contentList.add(Map.of("type", "image_url", "image_url", Map.of("url", sceneImageUrl)));

            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", contentList)));
            body.put("max_tokens", config.getMaxTokens());

            log.info("Agent1-场景感知: 分析场景图片, url={}", sceneImageUrl);

            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("场景分析模型调用失败, code={}", response.code());
                    return null;
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                String content = root.path("choices").get(0).path("message").path("content").asText();

                if (content == null || content.isEmpty()) {
                    log.warn("场景分析模型返回空内容");
                    return null;
                }

                String jsonStr = extractJson(content);
                SceneContextDTO sceneContext = objectMapper.readValue(jsonStr, SceneContextDTO.class);
                log.info("Agent1-场景感知完成: tableType={}, atmosphere={}",
                        sceneContext.getTableType(), sceneContext.getAtmosphere());
                return sceneContext;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("场景分析失败，继续使用标签输入: {}", e.getMessage());
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
