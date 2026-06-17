package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.dto.*;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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

        List<Dish> candidateDishes = dishMatchingService.matchDishes(queryText, profile, 20);

        RerankResultDTO rerankResult = recommendationRankingService.rank(profile, candidateDishes);

        ScriptResultDTO scriptResult = scriptGenerationService.generateScripts(
                profile, rerankResult.getRecommendations());

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
