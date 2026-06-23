package com.example.foodrecommend.controller;

import com.example.foodrecommend.dto.FeedbackRequestDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.mapper.DishMapper;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.security.UserPrincipal;
import com.example.foodrecommend.service.RecommendationHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 端到端集成测试：service waiter 提交 adopted=true 反馈时，
 * 控制器应调用 historyService.indexAdoption(...)（@Async 异步）。
 *
 * 使用直接 controller 调用（无 MockMvc）以绕过 @WebMvcTest 的安全/web 层，
 * 避免重蹈 WaiterRecommendControllerTest 的 5 个上下文加载问题。
 */
@SpringBootTest
@Sql("classpath:db/schema.sql")
@TestPropertySource(properties = {
        "recommend.feedback-boost.enabled=true",
        "recommend.feedback-boost.min-samples=1",
        "recommend.feedback-boost.top-k-similar=5",
        "recommend.feedback-boost.similarity-threshold=0.5",
        "recommend.feedback-boost.weight=0.1",
        "recommend.feedback-boost.boost-cap=3",
        "recommend.feedback-boost.collection-name=recommendation_history"
})
class FeedbackToBoostFlowIT {

    // Mock the real historyService impl (needs Qdrant/HTTP) but spy on the interface
    // so we can verify calls without needing a real Qdrant.
    // Note: @SpyBean wraps the real bean — but indexAdoption tries HTTP to Qdrant and
    // will just log a warn/dlq on failure (non-blocking). We only verify the *call* was
    // made, not the Qdrant outcome.
    @SpyBean
    RecommendationHistoryService historyService;

    // Mock HTTP client so indexAdoption doesn't throw unexpected NPE
    @MockBean
    okhttp3.OkHttpClient mockHttp;

    // Mock embedding so indexAdoption doesn't call real AI
    @MockBean
    com.example.foodrecommend.service.EmbeddingService mockEmbed;

    // Mock DLQ mapper to avoid H2 issues in recover path
    @MockBean
    com.example.foodrecommend.mapper.FeedbackIndexDlqMapper dlqMapper;

    @Autowired
    WaiterRecommendController controller;

    @Autowired
    RecommendationRecordMapper recordMapper;

    @Autowired
    DishMapper dishMapper;

    private Long recordId;
    private Long dishId;

    @BeforeEach
    void setUp() {
        // Set up Spring Security context so @PreAuthorize("hasRole('WAITER')") passes
        UserPrincipal principal = new UserPrincipal(1L, "testWaiter", "WAITER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_WAITER")));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        // Insert a dish with sufficient stock
        Dish dish = new Dish();
        dish.setName("IT测试菜品");
        dish.setStock(99);
        dish.setStatus(1);
        dish.setVectorStatus(1);
        dish.setPrice(new BigDecimal("38.00"));
        dishMapper.insert(dish);
        dishId = dish.getId();

        // Insert a recommendation_record so the controller can look it up
        RecommendationRecord record = new RecommendationRecord();
        record.setWaiterId(1L);
        record.setQueryText("辣 多人 清真 IT测试");
        recordMapper.insert(record);
        recordId = record.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void feedback_adoption_triggers_index_call() {
        UserPrincipal principal = new UserPrincipal(1L, "testWaiter", "WAITER");

        FeedbackRequestDTO req = new FeedbackRequestDTO();
        req.setAdopted(true);
        req.setAdoptedDishId(dishId);
        req.setQuantity(1);

        controller.submitFeedback(recordId, req, principal);

        // indexAdoption is @Async — wait up to 2 s for the async thread to invoke it
        verify(historyService, timeout(2000).times(1))
                .indexAdoption(eq(recordId), anyString(), anyList(), eq(1L));
    }

    @Test
    void feedback_not_adopted_does_not_trigger_index_call() throws Exception {
        UserPrincipal principal = new UserPrincipal(1L, "testWaiter", "WAITER");

        FeedbackRequestDTO req = new FeedbackRequestDTO();
        req.setAdopted(false);

        controller.submitFeedback(recordId, req, principal);

        // Give async executor a moment then assert no interaction
        Thread.sleep(300);
        org.mockito.Mockito.verify(historyService, org.mockito.Mockito.never())
                .indexAdoption(any(), any(), any(), any());
    }
}
