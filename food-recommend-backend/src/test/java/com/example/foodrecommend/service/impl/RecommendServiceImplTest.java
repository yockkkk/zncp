package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.common.BusinessException;
import com.example.foodrecommend.dto.*;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RecommendServiceImpl.
 *
 * Adaptations:
 * - Real impl has `ObjectMapper objectMapper = new ObjectMapper()` as field initializer,
 *   not a constructor-injected bean. @InjectMocks cannot override a field initializer,
 *   so saveRecommendationRecord will use its own real ObjectMapper — that's fine.
 * - All external collaborators are mocked. No DB, no HTTP, no LLM.
 * - recommendByTags uses tagInputJson string that gets parsed by ObjectMapper.
 */
@ExtendWith(MockitoExtension.class)
class RecommendServiceImplTest {

    @Mock private OssService ossService;
    @Mock private VisionModelService visionModelService;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorSearchService vectorSearchService;
    @Mock private AiRerankService aiRerankService;
    @Mock private DishService dishService;
    @Mock private RecommendationRecordMapper recordMapper;
    @Mock private ScenePerceptionService scenePerceptionService;
    @Mock private UserProfileService userProfileService;
    @Mock private DishMatchingService dishMatchingService;
    @Mock private RecommendationRankingService recommendationRankingService;
    @Mock private ScriptGenerationService scriptGenerationService;
    @Mock private VoiceUnderstandingService voiceUnderstandingService;

    @InjectMocks
    private RecommendServiceImpl service;

    private RerankResultDTO fakeRerankResult() {
        RerankResultDTO r = new RerankResultDTO();
        r.setSummary("test summary");
        r.setRecommendations(List.of());
        return r;
    }

    private UserProfileDTO fakeProfile() {
        return new UserProfileDTO();
    }

    private ScriptResultDTO fakeScript() {
        ScriptResultDTO s = new ScriptResultDTO();
        s.setOpeningScript("您好，今天为您推荐…");
        return s;
    }

    @Test
    void recommendByTags_withoutSceneImage_scenePerceptionNotCalled() throws Exception {
        when(userProfileService.buildProfile(any(), isNull())).thenReturn(fakeProfile());
        when(userProfileService.buildQueryText(any())).thenReturn("queryText");
        when(dishMatchingService.matchDishes(anyString(), any(), anyInt()))
                .thenReturn(List.of());
        when(recommendationRankingService.rank(any(), any())).thenReturn(fakeRerankResult());
        when(scriptGenerationService.generateScripts(any(), any())).thenReturn(fakeScript());
        when(recordMapper.insert(any())).thenReturn(1);

        RecommendRequestDTO request = new RecommendRequestDTO();
        request.setTagInputJson("{\"peopleCount\":\"2\",\"diningScene\":\"约会\",\"budgetLevel\":\"中等\"}");

        RecommendWithScriptDTO result = service.recommendByTags(request, null, 10L);

        assertThat(result).isNotNull();
        verify(scenePerceptionService, never()).analyzeScene(any());
        verify(ossService, never()).uploadFile(any());
    }

    @Test
    void recommendByTags_withSceneImage_scenePerceptionCalledFirst() {
        MultipartFile sceneImage = new MockMultipartFile("img", "scene.jpg",
                "image/jpeg", new byte[]{1, 2, 3});

        when(ossService.uploadFile(sceneImage)).thenReturn("http://oss/scene.jpg");
        SceneContextDTO fakeScene = new SceneContextDTO();
        when(scenePerceptionService.analyzeScene("http://oss/scene.jpg")).thenReturn(fakeScene);
        when(userProfileService.buildProfile(any(), eq(fakeScene))).thenReturn(fakeProfile());
        when(userProfileService.buildQueryText(any())).thenReturn("queryText");
        when(dishMatchingService.matchDishes(anyString(), any(), anyInt())).thenReturn(List.of());
        when(recommendationRankingService.rank(any(), any())).thenReturn(fakeRerankResult());
        when(scriptGenerationService.generateScripts(any(), any())).thenReturn(fakeScript());
        when(recordMapper.insert(any())).thenReturn(1);

        RecommendRequestDTO request = new RecommendRequestDTO();
        request.setTagInputJson("{\"peopleCount\":\"2\",\"diningScene\":\"商务\",\"budgetLevel\":\"高端\"}");

        RecommendWithScriptDTO result = service.recommendByTags(request, sceneImage, 5L);

        InOrder order = inOrder(ossService, scenePerceptionService, userProfileService,
                dishMatchingService, recommendationRankingService, scriptGenerationService);
        order.verify(ossService).uploadFile(sceneImage);
        order.verify(scenePerceptionService).analyzeScene(anyString());
        order.verify(userProfileService).buildProfile(any(), any());
        order.verify(dishMatchingService).matchDishes(anyString(), any(), anyInt());
        order.verify(recommendationRankingService).rank(any(), any());
        order.verify(scriptGenerationService).generateScripts(any(), any());

        assertThat(result.getSceneContext()).isSameAs(fakeScene);
    }

    @Test
    void recommendByVoice_happyPath_voiceUnderstandingCalledFirst() {
        TagInputDTO parsedTags = new TagInputDTO();
        parsedTags.setPeopleCount("1");
        parsedTags.setBudgetLevel("中等");
        when(voiceUnderstandingService.parseVoiceText("我想吃辣的")).thenReturn(parsedTags);
        when(userProfileService.buildProfile(eq(parsedTags), isNull())).thenReturn(fakeProfile());
        when(userProfileService.buildQueryText(any())).thenReturn("queryText");
        when(dishMatchingService.matchDishes(anyString(), any(), anyInt())).thenReturn(List.of());
        when(recommendationRankingService.rank(any(), any())).thenReturn(fakeRerankResult());
        when(scriptGenerationService.generateScripts(any(), any())).thenReturn(fakeScript());
        when(recordMapper.insert(any())).thenReturn(1);

        RecommendWithScriptDTO result = service.recommendByVoice("我想吃辣的", null, 3L);

        assertThat(result).isNotNull();
        InOrder order = inOrder(voiceUnderstandingService, userProfileService, dishMatchingService);
        order.verify(voiceUnderstandingService).parseVoiceText("我想吃辣的");
        order.verify(userProfileService).buildProfile(any(), any());
        order.verify(dishMatchingService).matchDishes(anyString(), any(), anyInt());
    }

    @Test
    void recommendByVoice_voiceUnderstandingReturnsNull_throwsBusinessException() {
        when(voiceUnderstandingService.parseVoiceText(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.recommendByVoice("gibberish", null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("语音理解失败");
    }
}
