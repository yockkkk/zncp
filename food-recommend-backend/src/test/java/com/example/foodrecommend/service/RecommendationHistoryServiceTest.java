package com.example.foodrecommend.service;

import com.example.foodrecommend.config.FeedbackBoostProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
        "recommend.feedback-boost.enabled=true",
        "recommend.feedback-boost.min-samples=3",
        "recommend.feedback-boost.top-k-similar=20",
        "recommend.feedback-boost.similarity-threshold=0.75",
        "recommend.feedback-boost.weight=0.15",
        "recommend.feedback-boost.boost-cap=5",
        "recommend.feedback-boost.collection-name=recommendation_history"
})
class RecommendationHistoryServiceTest {

    @Autowired FeedbackBoostProperties props;
    @Autowired RecommendationHistoryService historyService;

    @MockBean okhttp3.OkHttpClient mockHttp;
    @MockBean EmbeddingService mockEmbed;

    private okhttp3.Response buildResp(String body) {
        return new okhttp3.Response.Builder()
            .request(new okhttp3.Request.Builder().url("http://x").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("ok")
            .body(okhttp3.ResponseBody.create(body, okhttp3.MediaType.parse("application/json")))
            .build();
    }

    @Test
    void properties_bind_with_defaults() {
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getMinSamples()).isEqualTo(3);
        assertThat(props.getCollectionName()).isEqualTo("recommendation_history");
        assertThat(props.getWeight()).isEqualTo(0.15);
        assertThat(props.getBoostCap()).isEqualTo(5);
    }

    @Test
    void lookupBoost_disabled_returns_empty() {
        // 测试环境 properties 中 enabled=true，临时切关：用 ReflectionTestUtils
        org.springframework.test.util.ReflectionTestUtils.setField(props, "enabled", false);
        try {
            assertThat(historyService.lookupBoost("任意 query")).isEmpty();
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(props, "enabled", true);
        }
    }

    @Test
    void lookupBoost_aggregates_when_above_min_samples() throws Exception {
        when(mockEmbed.getEmbedding(anyString())).thenReturn(List.of(0.1f, 0.2f, 0.3f));
        String json = "{\"result\":["
            + "{\"id\":1,\"score\":0.9,\"payload\":{\"adoptedDishIds\":[10,20]}},"
            + "{\"id\":2,\"score\":0.8,\"payload\":{\"adoptedDishIds\":[10]}},"
            + "{\"id\":3,\"score\":0.78,\"payload\":{\"adoptedDishIds\":[20,30]}}"
            + "]}";
        okhttp3.Call call = mock(okhttp3.Call.class);
        when(call.execute()).thenReturn(buildResp(json));
        when(mockHttp.newCall(any())).thenReturn(call);

        Map<Long,Integer> boost = historyService.lookupBoost("辣 多人 清真");

        assertThat(boost).containsEntry(10L, 2).containsEntry(20L, 2).containsEntry(30L, 1);
    }

    @Test
    void lookupBoost_returns_empty_when_below_min_samples() throws Exception {
        when(mockEmbed.getEmbedding(anyString())).thenReturn(List.of(0.1f));
        String json = "{\"result\":["
            + "{\"id\":1,\"score\":0.9,\"payload\":{\"adoptedDishIds\":[10]}},"
            + "{\"id\":2,\"score\":0.8,\"payload\":{\"adoptedDishIds\":[20]}}"
            + "]}";
        okhttp3.Call call = mock(okhttp3.Call.class);
        when(call.execute()).thenReturn(buildResp(json));
        when(mockHttp.newCall(any())).thenReturn(call);

        assertThat(historyService.lookupBoost("x")).isEmpty();
    }

    @Test
    void lookupBoost_returns_empty_on_qdrant_error() throws Exception {
        when(mockEmbed.getEmbedding(anyString())).thenReturn(List.of(0.1f));
        okhttp3.Call call = mock(okhttp3.Call.class);
        when(call.execute()).thenThrow(new java.io.IOException("boom"));
        when(mockHttp.newCall(any())).thenReturn(call);

        assertThat(historyService.lookupBoost("x")).isEmpty();
    }
}
