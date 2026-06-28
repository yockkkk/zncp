package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.dto.*;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.foodrecommend.config.AiModelConfig;
import okhttp3.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private final OssService ossService;
    private final VisionModelService visionModelService;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final AiRerankService aiRerankService;
    private final DishService dishService;
    private final RecommendationRecordMapper recordMapper;
    private final AiModelConfig aiModelConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 5 Agent 服务 ==========
    private final ScenePerceptionService scenePerceptionService;       // Agent 1
    private final UserProfileService userProfileService;               // Agent 2
    private final DishMatchingService dishMatchingService;             // Agent 3
    private final RecommendationRankingService recommendationRankingService; // Agent 4
    private final ScriptGenerationService scriptGenerationService;     // Agent 5
    private final VoiceUnderstandingService voiceUnderstandingService; // Agent 0

    // ========== 旧版：图片推荐（保持兼容）==========

    @Override
    public RecommendResultDTO recommendByImage(MultipartFile file, Long userId) {
        String imageUrl = ossService.uploadFile(file);

        UserProfileDTO profile = visionModelService.analyzeImage(imageUrl);

        String queryText = userProfileService.buildQueryText(profile);

        List<Dish> filteredDishes = dishMatchingService.matchDishes(queryText, profile, 20);

        RerankResultDTO rerankResult = aiRerankService.rerank(profile, filteredDishes);

        Long recordId = saveRecommendationRecord(userId, null, imageUrl, null, null,
                profile, queryText, rerankResult.getRecommendations(), (ScriptResultDTO) null);

        RecommendResultDTO result = new RecommendResultDTO();
        result.setRecordId(recordId);
        result.setImageUrl(imageUrl);
        result.setUserProfile(profile);
        result.setSummary(rerankResult.getSummary());
        result.setRecommendations(rerankResult.getRecommendations());
        return result;
    }

    // ========== 新版：标签+场景推荐（5 Agent 管线）==========

    @Override
    public RecommendWithScriptDTO recommendByTags(RecommendRequestDTO request,
                                                   MultipartFile sceneImage,
                                                   Long waiterId) {
        TagInputDTO tags = parseTagInput(request.getTagInputJson());
        return executeAgentPipeline(tags, sceneImage, waiterId, request.getTagInputJson());
    }

    // ========== 语音推荐（Agent 0 + 5 Agent 管线）==========

    @Override
    public RecommendWithScriptDTO recommendByVoice(String voiceText,
                                                    MultipartFile sceneImage,
                                                    Long waiterId) {
        TagInputDTO tags = voiceUnderstandingService.parseVoiceText(voiceText);
        if (tags == null) {
            throw new BusinessException("语音理解失败，请重试或使用标签面板");
        }
        log.info("Agent0-语音→标签: {}", tags);
        return executeAgentPipeline(tags, sceneImage, waiterId, voiceText);
    }

    @Override
    public String transcribeAudio(MultipartFile audioFile) {
        // 1. 上传音频文件到 OSS
        String audioUrl = ossService.uploadFile(audioFile);
        log.info("语音音频上传成功, URL: {}", audioUrl);

        // 2. 提交 DashScope ASR 任务
        String apiKey = aiModelConfig.getEmbedding().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new BusinessException("未配置 DashScope API Key");
        }

        try {
            Map<String, Object> input = new HashMap<>();
            input.put("file_urls", List.of(audioUrl));

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("language_hints", List.of("zh"));

            Map<String, Object> body = new HashMap<>();
            body.put("model", "paraformer-v2");
            body.put("input", input);
            body.put("parameters", parameters);

            Request submitRequest = new Request.Builder()
                    .url("https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-DashScope-Async", "enable")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body),
                            MediaType.parse("application/json")))
                    .build();

            String taskId;
            try (Response response = httpClient.newCall(submitRequest).execute()) {
                String bodyStr = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("ASR 任务提交失败, code={}, body={}", response.code(), bodyStr);
                    throw new BusinessException("语音识别提交失败: " + response.code() + " " + bodyStr);
                }

                JsonNode root = objectMapper.readTree(bodyStr);
                taskId = root.path("output").path("task_id").asText();
                if (taskId == null || taskId.isEmpty()) {
                    log.error("ASR 任务未返回 task_id, response={}", root.toString());
                    throw new BusinessException("语音识别任务创建失败");
                }
            }

            log.info("ASR 任务已提交, task_id={}", taskId);

            // 3. 轮询 ASR 任务状态 (最多轮询 45 次，每次等待 1000ms)
            String text = null;
            int maxPollCount = 45;
            int pollIntervalMs = 1000;

            for (int i = 0; i < maxPollCount; i++) {
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException("语音识别被中断");
                }

                Request checkRequest = new Request.Builder()
                        .url("https://dashscope.aliyuncs.com/api/v1/tasks/" + taskId)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .get()
                        .build();

                try (Response response = httpClient.newCall(checkRequest).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        log.warn("ASR 任务状态查询失败, code={}", response.code());
                        continue;
                    }

                    String checkBodyStr = response.body().string();
                    JsonNode root = objectMapper.readTree(checkBodyStr);
                    String status = root.path("output").path("task_status").asText();
                    log.info("ASR 任务状态 [{}]: {}", taskId, status);

                    if ("SUCCEEDED".equals(status)) {
                        // paraformer-v2 不直接返回文本，需要从 transcription_url 拉取
                        JsonNode resultsNode = root.path("output").path("results");
                        if (resultsNode.isArray() && resultsNode.size() > 0) {
                            JsonNode firstResult = resultsNode.get(0);
                            String subStatus = firstResult.path("subtask_status").asText();
                            if (!"SUCCEEDED".equalsIgnoreCase(subStatus)) {
                                String subMsg = firstResult.path("message").asText();
                                log.error("ASR subtask 失败, status={}, msg={}", subStatus, subMsg);
                                throw new BusinessException("语音识别失败: " + subMsg);
                            }
                            String transcriptionUrl = firstResult.path("transcription_url").asText();
                            if (transcriptionUrl == null || transcriptionUrl.isEmpty()) {
                                log.warn("ASR 未返回 transcription_url, firstResult={}", firstResult);
                                text = firstResult.path("text").asText();
                            } else {
                                log.info("ASR 拉取转写结果文件: {}", transcriptionUrl);
                                try {
                                    java.net.URI parsedUri = java.net.URI.create(transcriptionUrl);
                                    String scheme = parsedUri.getScheme();
                                    String host = parsedUri.getHost();
                                    if (scheme == null || !scheme.equalsIgnoreCase("https")
                                            || host == null
                                            || !(host.equalsIgnoreCase("dashscope.aliyuncs.com")
                                                    || host.toLowerCase().endsWith(".aliyuncs.com"))) {
                                        throw new BusinessException("非法转写结果地址");
                                    }
                                } catch (IllegalArgumentException iae) {
                                    throw new BusinessException("非法转写结果地址");
                                }
                                Request fetchReq = new Request.Builder().url(transcriptionUrl).get().build();
                                try (Response fetchResp = httpClient.newCall(fetchReq).execute()) {
                                    if (!fetchResp.isSuccessful() || fetchResp.body() == null) {
                                        throw new BusinessException("拉取转写结果失败, code=" + fetchResp.code());
                                    }
                                    String fetchBodyStr = fetchResp.body().string();
                                    JsonNode tRoot = objectMapper.readTree(fetchBodyStr);
                                    JsonNode transcripts = tRoot.path("transcripts");
                                    StringBuilder sb = new StringBuilder();
                                    if (transcripts.isArray()) {
                                        for (JsonNode t : transcripts) {
                                            String tText = t.path("text").asText("");
                                            if (!tText.isEmpty()) {
                                                sb.append(tText);
                                                continue;
                                            }
                                            // fallback: 拼接 sentences[].text
                                            JsonNode sentences = t.path("sentences");
                                            if (sentences.isArray()) {
                                                for (JsonNode s : sentences) {
                                                    sb.append(s.path("text").asText(""));
                                                }
                                            }
                                        }
                                    }
                                    text = sb.toString();
                                }
                            }
                        }
                        if (text == null) {
                            text = "";
                        }
                        break;
                    } else if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                        String errMsg = root.path("output").path("message").asText();
                        log.error("ASR 任务识别失败, task_id={}, msg={}", taskId, errMsg);
                        throw new BusinessException("语音识别失败: " + errMsg);
                    }
                }
            }

            if (text == null) {
                throw new BusinessException("语音识别超时，请稍后重试");
            }

            log.info("语音识别成功, 文本: {}", text);
            return text;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("语音识别调用异常", e);
            throw new BusinessException("语音识别异常: " + e.getMessage());
        }
    }

    // ========== 共享管线：Agent 1-5 ==========

    private RecommendWithScriptDTO executeAgentPipeline(TagInputDTO tags,
                                                         MultipartFile sceneImage,
                                                         Long waiterId,
                                                         String tagInputJson) {
        String sceneImageUrl = null;
        SceneContextDTO sceneContext = null;
        if (sceneImage != null && !sceneImage.isEmpty()) {
            sceneImageUrl = ossService.uploadFile(sceneImage);
            sceneContext = scenePerceptionService.analyzeScene(sceneImageUrl);
        }

        UserProfileDTO profile = userProfileService.buildProfile(tags, sceneContext);
        String queryText = userProfileService.buildQueryText(profile);

        List<Dish> candidateDishes = dishMatchingService.matchDishes(queryText, profile, 10);

        RerankResultDTO rerankResult = recommendationRankingService.rank(profile, candidateDishes);

        // 优化：Agent4 已合并产出话术，则跳过 Agent5（节省一次 LLM 调用 ~30s）
        ScriptResultDTO scriptResult;
        if (rerankResult.getOpeningScript() != null && !rerankResult.getOpeningScript().isEmpty()
                && rerankResult.getDishScripts() != null && !rerankResult.getDishScripts().isEmpty()) {
            scriptResult = new ScriptResultDTO();
            scriptResult.setOpeningScript(rerankResult.getOpeningScript());
            scriptResult.setDishScripts(rerankResult.getDishScripts());
            log.info("Agent5-话术已由 Agent4 合并产出 (opening={}字, dishes={}道), 跳过单独调用",
                    rerankResult.getOpeningScript().length(), rerankResult.getDishScripts().size());
        } else {
            scriptResult = scriptGenerationService.generateScripts(
                    profile, rerankResult.getRecommendations());
        }

        Long recordId = saveRecommendationRecord(null, tags.getPhone(), sceneImageUrl,
                waiterId, tagInputJson,
                profile, queryText,
                rerankResult.getRecommendations(),
                scriptResult);

        RecommendWithScriptDTO result = new RecommendWithScriptDTO();
        result.setRecordId(recordId);
        result.setUserProfile(profile);
        result.setSummary(rerankResult.getSummary());
        result.setRecommendations(rerankResult.getRecommendations());
        result.setSceneContext(sceneContext);
        if (scriptResult != null) {
            result.setOpeningScript(scriptResult.getOpeningScript());
            result.setDishScripts(scriptResult.getDishScripts());
        }
        return result;
    }

    // ========== 向量管理 ==========

    @Override
    public void batchRebuildVectors() {
        vectorSearchService.initCollection();

        List<Dish> dishes = dishService.listAll();
        int success = 0;
        int fail = 0;
        List<Dish> toUpdate = new ArrayList<>();

        for (Dish dish : dishes) {
            try {
                rebuildSingleDishVector(dish);
                dish.setVectorStatus(1);
                toUpdate.add(dish);
                success++;
            } catch (Exception e) {
                fail++;
                log.error("菜品 {} 向量生成失败: {}", dish.getId(), e.getMessage());
            }
        }

        if (!toUpdate.isEmpty()) {
            dishService.updateBatchById(toUpdate);
        }

        log.info("批量向量生成完成: 成功={}, 失败={}", success, fail);
    }

    @Override
    public void rebuildDishVector(Long dishId) {
        Dish dish = dishService.getById(dishId);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        vectorSearchService.initCollection();
        rebuildSingleDishVector(dish);
        dish.setVectorStatus(1);
        dishService.updateById(dish);
    }

    // ========== 私有方法 ==========

    private void rebuildSingleDishVector(Dish dish) {
        String embeddingText = dishService.buildDishEmbeddingText(dish);
        List<Float> vector = embeddingService.getEmbedding(embeddingText);

        DishVectorDTO payload = new DishVectorDTO();
        payload.setDishId(dish.getId());
        payload.setName(dish.getName());
        payload.setCategory(dish.getCategory());
        double price = dish.getPrice() != null ? dish.getPrice().doubleValue() : 0;
        payload.setPrice(price);
        payload.setCalories(dish.getCalories() != null ? dish.getCalories() : 0);
        payload.setProtein(dish.getProtein() != null ? dish.getProtein().doubleValue() : 0);
        payload.setTaste(dish.getTaste());
        payload.setTags(splitToList(dish.getTags()));
        payload.setSuitablePeople(splitToList(dish.getSuitablePeople()));
        payload.setScene(splitToList(dish.getScene()));
        payload.setGrossMargin(dish.getGrossMargin() != null ? dish.getGrossMargin().doubleValue() : 0.60);

        vectorSearchService.upsertDishVector(dish.getId(), vector, payload);
    }

    private Long saveRecommendationRecord(Long userId, String phone, String imageUrl,
                                           Long waiterId, String tagInputJson,
                                           UserProfileDTO profile, String queryText,
                                           List<RecommendDishDTO> results,
                                           ScriptResultDTO scriptResult) {
        try {
            RecommendationRecord record = new RecommendationRecord();
            record.setUserId(userId);
            record.setPhone(phone);
            record.setWaiterId(waiterId);
            record.setImageUrl(imageUrl);
            record.setTagInputJson(tagInputJson);
            record.setUserProfileJson(objectMapper.writeValueAsString(profile));
            record.setQueryText(queryText);

            String dishIds = results.stream()
                    .filter(r -> r.getDishId() != null)
                    .map(r -> String.valueOf(r.getDishId()))
                    .collect(Collectors.joining(","));
            record.setRecommendedDishIds(dishIds);
            record.setResultJson(objectMapper.writeValueAsString(results));

            if (scriptResult != null) {
                record.setScriptResultJson(objectMapper.writeValueAsString(scriptResult));
            }

            recordMapper.insert(record);
            return record.getId();
        } catch (Exception e) {
            log.error("保存推荐记录失败", e);
            throw new BusinessException("保存推荐记录失败: " + e.getMessage());
        }
    }

    private TagInputDTO parseTagInput(String tagInputJson) {
        try {
            return objectMapper.readValue(tagInputJson, TagInputDTO.class);
        } catch (Exception e) {
            log.error("解析标签输入JSON失败", e);
            throw new BusinessException("标签输入格式错误");
        }
    }

    private List<String> splitToList(String str) {
        if (str == null || str.isEmpty()) return List.of();
        return Arrays.asList(str.split("[,，]"));
    }
}
