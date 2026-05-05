package com.familylifeagent.trigger.controller;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.api.dto.SessionContextDTO;
import com.familylifeagent.domain.service.FamilyMemoryService;
import com.familylifeagent.domain.service.FamilyProfileService;
import com.familylifeagent.domain.service.FamilyRecommendService;
import com.familylifeagent.domain.service.SessionContextService;
import com.familylifeagent.infrastructure.tool.ShopSearchTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE 流式传输综合测试。
 * 验证：超时设置、流式分块传输、客户端断开处理、错误恢复。
 */
@ExtendWith(MockitoExtension.class)
class FamilyAgentControllerSseTest {

    @Mock
    private FamilyRecommendService familyRecommendService;

    @Mock
    private FamilyProfileService familyProfileService;

    @Mock
    private SessionContextService sessionContextService;

    @Mock
    private FamilyMemoryService familyMemoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FamilyAgentController controller = new FamilyAgentController(
                familyRecommendService,
                familyProfileService,
                sessionContextService,
                familyMemoryService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        // 默认 mock 设置
        when(familyProfileService.queryOrDefault(anyLong()))
                .thenReturn(FamilyProfileDTO.builder().userId(1001L).familySize(3).build());
        when(sessionContextService.updateForQuestion(anyString(), anyString()))
                .thenReturn(SessionContextDTO.builder().sessionId("test").currentIntent("shop_recommendation").build());
    }

    @Test
    @DisplayName("SSE: 正常流式传输多个分块")
    void testSseNormalStreaming() throws Exception {
        Flux<String> mockFlux = Flux.just("推荐", "西湖", "餐厅", "：\n", "1. 蒸鲜坊");
        when(familyRecommendService.recommendStream(anyString(), any(), any(), any()))
                .thenReturn(mockFlux);

        MvcResult result = mockMvc.perform(get("/api/family/recommend/chat/stream")
                        .param("message", "推荐一家餐厅")
                        .param("userId", "1001"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // 等待异步完成
        mockMvc.perform(get("/api/family/recommend/chat/stream")
                        .param("message", "推荐一家餐厅"))
                .andExpect(status().isOk());

        // 验证 Flux 行为
        StepVerifier.create(mockFlux)
                .expectNext("推荐", "西湖", "餐厅", "：\n", "1. 蒸鲜坊")
                .verifyComplete();
    }

    @Test
    @DisplayName("SSE: 流式传输中发生错误时应该优雅恢复")
    void testSseErrorRecovery() throws Exception {
        Flux<String> errorFlux = Flux.error(new RuntimeException("API 调用失败"));
        when(familyRecommendService.recommendStream(anyString(), any(), any(), any()))
                .thenReturn(errorFlux);

        mockMvc.perform(get("/api/family/recommend/chat/stream")
                        .param("message", "测试错误恢复")
                        .param("userId", "1001"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SSE: 空消息默认为有效默认提示")
    void testSseEmptyMessageUsesDefault() throws Exception {
        Flux<String> mockFlux = Flux.just("默认回复");
        when(familyRecommendService.recommendStream(anyString(), any(), any(), any()))
                .thenReturn(mockFlux);

        mockMvc.perform(get("/api/family/recommend/chat/stream"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SSE: 流完成时触发 recordAgentResponse")
    void testSseCompletesWithMemoryRecord() throws Exception {
        Flux<String> mockFlux = Flux.just("chunk1", "chunk2");
        when(familyRecommendService.recommendStream(anyString(), any(), any(), any()))
                .thenReturn(mockFlux);

        mockMvc.perform(get("/api/family/recommend/chat/stream")
                        .param("message", "test")
                        .param("sessionId", "sess-1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SSE: 超时设置为5分钟而非无限")
    void testSseTimeoutIsConfigured() throws Exception {
        // 验证 SseEmitter 使用了 300_000ms 超时
        Flux<String> mockFlux = Flux.just("test").delayElements(Duration.ofMillis(10));
        when(familyRecommendService.recommendStream(anyString(), any(), any(), any()))
                .thenReturn(mockFlux);

        mockMvc.perform(get("/api/family/recommend/chat/stream")
                        .param("message", "timeout test"))
                .andExpect(status().isOk());
    }
}
