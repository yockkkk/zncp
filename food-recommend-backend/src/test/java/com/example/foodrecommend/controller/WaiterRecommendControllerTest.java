package com.example.foodrecommend.controller;

import com.example.foodrecommend.dto.FeedbackRequestDTO;
import com.example.foodrecommend.entity.Dish;
import com.example.foodrecommend.entity.RecommendationRecord;
import com.example.foodrecommend.mapper.RecommendationFeedbackMapper;
import com.example.foodrecommend.mapper.RecommendationRecordMapper;
import com.example.foodrecommend.security.JwtAuthenticationFilter;
import com.example.foodrecommend.security.UserPrincipal;
import com.example.foodrecommend.service.DishService;
import com.example.foodrecommend.service.RecommendService;
import com.example.foodrecommend.service.UserProfileService;
import com.example.foodrecommend.service.RecommendationHistoryService;
import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest slice tests for WaiterRecommendController.
 *
 * Adaptations:
 * - Real JwtAuthenticationFilter depends on UserMapper (needs sqlSessionFactory). We
 *   provide it as @MockBean and in @BeforeEach configure it to call through the
 *   filter chain so MockMvc requests actually reach the dispatcher servlet.
 * - @MapperScan on FoodRecommendApplication registers all mappers; we @MockBean every
 *   mapper so MyBatis never demands a sqlSessionFactory in the slice context.
 * - Authentication is injected via SecurityMockMvcRequestPostProcessors.authentication().
 * - @PreAuthorize("hasRole('WAITER')") is enforced by SecurityConfig (loaded by @WebMvcTest).
 * - 401/403 for unauthenticated: we accept either value depending on entry-point config.
 */
@WebMvcTest(controllers = WaiterRecommendController.class)
class WaiterRecommendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RecommendService recommendService;

    @MockBean
    private RecommendationRecordMapper recordMapper;

    @MockBean
    private RecommendationFeedbackMapper feedbackMapper;

    @MockBean
    private DishService dishService;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private RecommendationHistoryService historyService;

    // SecurityConfig requires JwtAuthenticationFilter; mock it and configure call-through below.
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.example.foodrecommend.security.JwtTokenProvider jwtTokenProvider;

    // @MapperScan registers all mappers; mock every mapper to satisfy MyBatis bean creation.
    @MockBean
    private com.example.foodrecommend.mapper.UserMapper userMapper;

    @MockBean
    private com.example.foodrecommend.mapper.DishMapper dishMapper;

    @MockBean
    private com.example.foodrecommend.mapper.PromptTemplateMapper promptTemplateMapper;

    /**
     * Configure the JwtAuthenticationFilter mock to pass requests through the filter chain.
     * Without this, the Mockito mock swallows the request and MockMvc gets no response.
     */
    @BeforeEach
    void configureFilterCallThrough() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    // Helper: create a SecurityContext with WAITER principal for MockMvc requests.
    // Using securityContext() instead of authentication() because the real SecurityConfig
    // uses STATELESS session, which means SecurityContextHolderFilter won't load auth from
    // session — securityContext() sets it directly on the request attribute.
    private SecurityContext waiterSecurityContext() {
        UserPrincipal principal = new UserPrincipal(1L, "testWaiter", "WAITER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_WAITER")));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        return ctx;
    }

    private Dish dishWithStock(Long id, int stock) {
        Dish d = new Dish();
        d.setId(id);
        d.setName("测试菜品");
        d.setStock(stock);
        d.setPrice(new BigDecimal("45.00"));
        return d;
    }

    private RecommendationRecord record(Long id) {
        RecommendationRecord r = new RecommendationRecord();
        r.setId(id);
        r.setWaiterId(1L);
        return r;
    }

    @Test
    void submitFeedback_adoptTrue_stockSufficientDeductsStock() throws Exception {
        when(recordMapper.selectById(100L)).thenReturn(record(100L));
        when(feedbackMapper.selectCount(any())).thenReturn(0L);
        when(dishService.getById(10L)).thenReturn(dishWithStock(10L, 5));
        when(dishService.deductStock(10L, 2)).thenReturn(1);
        when(feedbackMapper.insert(any())).thenReturn(1);
        when(recordMapper.updateById(any())).thenReturn(1);

        FeedbackRequestDTO req = new FeedbackRequestDTO();
        req.setAdopted(true);
        req.setAdoptedDishId(10L);
        req.setQuantity(2);

        mockMvc.perform(post("/api/waiter/feedback/100")
                        .with(securityContext(waiterSecurityContext()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(dishService).deductStock(10L, 2);
    }

    @Test
    void submitFeedback_duplicateDish_returnsBadRequest() throws Exception {
        when(recordMapper.selectById(100L)).thenReturn(record(100L));
        when(feedbackMapper.selectCount(any())).thenReturn(1L);  // already adopted once

        FeedbackRequestDTO req = new FeedbackRequestDTO();
        req.setAdopted(true);
        req.setAdoptedDishId(10L);
        req.setQuantity(1);

        mockMvc.perform(post("/api/waiter/feedback/100")
                        .with(securityContext(waiterSecurityContext()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("重复采纳")));
    }

    @Test
    void submitFeedback_outOfStock_returnsBadRequest() throws Exception {
        when(recordMapper.selectById(100L)).thenReturn(record(100L));
        when(feedbackMapper.selectCount(any())).thenReturn(0L);
        when(dishService.getById(10L)).thenReturn(dishWithStock(10L, 0));  // stock = 0

        FeedbackRequestDTO req = new FeedbackRequestDTO();
        req.setAdopted(true);
        req.setAdoptedDishId(10L);
        req.setQuantity(1);

        mockMvc.perform(post("/api/waiter/feedback/100")
                        .with(securityContext(waiterSecurityContext()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("库存不足")));
    }

    @Test
    void submitFeedback_missingAdopted_validationFailsBadRequest() throws Exception {
        // adopted is @NotNull — sending without it should trigger 400
        String badJson = "{\"adoptedDishId\":10}";

        mockMvc.perform(post("/api/waiter/feedback/100")
                        .with(securityContext(waiterSecurityContext()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitFeedback_unauthenticated_rejected() throws Exception {
        FeedbackRequestDTO req = new FeedbackRequestDTO();
        req.setAdopted(false);

        // Without auth, Spring Security should reject before hitting the controller.
        // CSRF token is provided so failure is from missing auth, not CSRF.
        var result = mockMvc.perform(post("/api/waiter/feedback/100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        // 401 for unauthenticated OR 403 depending on configured entry point
        assertThat(result.getResponse().getStatus()).isIn(401, 403);
    }
}
