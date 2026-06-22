package com.example.foodrecommend.service.impl;

import com.example.foodrecommend.config.AiModelConfig;
import com.example.foodrecommend.dto.TagInputDTO;
import com.example.foodrecommend.entity.PromptTemplate;
import com.example.foodrecommend.mapper.PromptTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for VoiceUnderstandingServiceImpl.
 *
 * Adaptations:
 * - Real impl returns null (not throws) for most failure paths — tests verify null return.
 * - For the HTTP-backed paths we mock OkHttpClient.newCall(...).execute() to return a
 *   stubbed Response, so no real LLM calls are made.
 * - null/empty voice text → returns null directly (no HTTP call).
 * - Template not found → returns null (warn + return null).
 */
@ExtendWith(MockitoExtension.class)
class VoiceUnderstandingServiceImplTest {

    @Mock
    private AiModelConfig aiModelConfig;

    @Mock
    private PromptTemplateMapper promptTemplateMapper;

    @Mock
    private OkHttpClient httpClient;

    @InjectMocks
    private VoiceUnderstandingServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void injectRealObjectMapper() throws Exception {
        // @InjectMocks uses the @Mock ObjectMapper; replace with real instance via reflection
        var field = VoiceUnderstandingServiceImpl.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(service, objectMapper);
    }

    @Test
    void parseVoiceText_nullInput_returnsNull() {
        assertThat(service.parseVoiceText(null)).isNull();
    }

    @Test
    void parseVoiceText_emptyInput_returnsNull() {
        assertThat(service.parseVoiceText("   ")).isNull();
    }

    @Test
    void parseVoiceText_templateNotFound_returnsNull() {
        AiModelConfig.ModelProperties props = new AiModelConfig.ModelProperties();
        props.setApiKey("test-key");
        props.setBaseUrl("http://fake");
        props.setModel("gpt-test");
        when(aiModelConfig.getScript()).thenReturn(props);
        when(promptTemplateMapper.selectOne(any())).thenReturn(null);

        TagInputDTO result = service.parseVoiceText("我想吃点清淡的");

        assertThat(result).isNull();
    }

    @Test
    void parseVoiceText_modelReturnsValidJson_populatesFields() throws IOException {
        AiModelConfig.ModelProperties props = new AiModelConfig.ModelProperties();
        props.setApiKey("test-key");
        props.setBaseUrl("http://fake");
        props.setModel("gpt-test");
        props.setMaxTokens(500);
        when(aiModelConfig.getScript()).thenReturn(props);

        PromptTemplate template = new PromptTemplate();
        template.setContent("请解析：{{voiceText}}");
        when(promptTemplateMapper.selectOne(any())).thenReturn(template);

        // Stub OkHttpClient to return a valid LLM response JSON
        String llmJson = objectMapper.writeValueAsString(
                java.util.Map.of("choices", java.util.List.of(
                        java.util.Map.of("message",
                                java.util.Map.of("content",
                                        "{\"diningScene\":\"家庭\",\"peopleCount\":\"3-4\",\"tastePreferences\":[\"清淡\"]}")))));

        ResponseBody responseBody = ResponseBody.create(llmJson, MediaType.parse("application/json"));
        Response fakeResponse = new Response.Builder()
                .request(new Request.Builder().url("http://fake/chat/completions").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build();

        Call mockCall = org.mockito.Mockito.mock(Call.class);
        when(httpClient.newCall(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(fakeResponse);

        TagInputDTO result = service.parseVoiceText("我们一家三口想吃清淡一点的");

        assertThat(result).isNotNull();
        assertThat(result.getDiningScene()).isEqualTo("家庭");
        assertThat(result.getPeopleCount()).isEqualTo("3-4");
        assertThat(result.getTastePreferences()).contains("清淡");
    }

    @Test
    void parseVoiceText_modelReturnsEmptyContent_returnsNull() throws IOException {
        AiModelConfig.ModelProperties props = new AiModelConfig.ModelProperties();
        props.setApiKey("test-key");
        props.setBaseUrl("http://fake");
        props.setModel("gpt-test");
        props.setMaxTokens(500);
        when(aiModelConfig.getScript()).thenReturn(props);

        PromptTemplate template = new PromptTemplate();
        template.setContent("请解析：{{voiceText}}");
        when(promptTemplateMapper.selectOne(any())).thenReturn(template);

        // Empty content + empty reasoning_content
        String llmJson = objectMapper.writeValueAsString(
                java.util.Map.of("choices", java.util.List.of(
                        java.util.Map.of("message",
                                java.util.Map.of("content", "", "reasoning_content", "")))));

        ResponseBody responseBody = ResponseBody.create(llmJson, MediaType.parse("application/json"));
        Response fakeResponse = new Response.Builder()
                .request(new Request.Builder().url("http://fake/chat/completions").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build();

        Call mockCall = org.mockito.Mockito.mock(Call.class);
        when(httpClient.newCall(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(fakeResponse);

        TagInputDTO result = service.parseVoiceText("blah blah");
        assertThat(result).isNull();
    }
}
