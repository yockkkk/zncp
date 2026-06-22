package com.example.foodrecommend.service;

import com.example.foodrecommend.config.FeedbackBoostProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void properties_bind_with_defaults() {
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getMinSamples()).isEqualTo(3);
        assertThat(props.getCollectionName()).isEqualTo("recommendation_history");
        assertThat(props.getWeight()).isEqualTo(0.15);
        assertThat(props.getBoostCap()).isEqualTo(5);
    }
}
