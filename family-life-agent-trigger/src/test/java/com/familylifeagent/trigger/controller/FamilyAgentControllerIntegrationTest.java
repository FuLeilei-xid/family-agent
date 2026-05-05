package com.familylifeagent.trigger.controller;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.api.dto.SessionContextDTO;
import com.familylifeagent.domain.service.FamilyMemoryService;
import com.familylifeagent.domain.service.FamilyProfileService;
import com.familylifeagent.domain.service.FamilyRecommendService;
import com.familylifeagent.domain.service.SessionContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller 层集成测试，验证端点行为、参数验证和错误处理。
 */
@ExtendWith(MockitoExtension.class)
class FamilyAgentControllerIntegrationTest {

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
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 画像保存 ====================

    @Test
    @DisplayName("POST /api/family/profile/save: 正常保存画像返回成功")
    void testSaveProfileSuccess() throws Exception {
        FamilyProfileDTO saved = FamilyProfileDTO.builder()
                .userId(1001L).familySize(3)
                .hasElderly(true).hasChild(true)
                .build();
        when(familyProfileService.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/family/profile/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1001,\"familySize\":3,\"hasElderly\":true,\"hasChild\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("家庭画像保存成功"));
    }

    @Test
    @DisplayName("POST /api/family/profile/save: userId为null应返回400")
    void testSaveProfileNullUserId() throws Exception {
        mockMvc.perform(post("/api/family/profile/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"familySize\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"))
                .andExpect(jsonPath("$.fieldErrors.userId").exists());
    }

    @Test
    @DisplayName("POST /api/family/profile/save: familySize为0应返回400")
    void testSaveProfileInvalidFamilySize() throws Exception {
        mockMvc.perform(post("/api/family/profile/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1001,\"familySize\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.familySize").exists());
    }

    @Test
    @DisplayName("POST /api/family/profile/save: 空body应返回400")
    void testSaveProfileEmptyBody() throws Exception {
        mockMvc.perform(post("/api/family/profile/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_failed"));
    }

    // ==================== 画像查询 ====================

    @Test
    @DisplayName("GET /api/family/profile/query: 查询已有用户画像")
    void testQueryProfileSuccess() throws Exception {
        when(familyProfileService.queryOrDefault(1001L))
                .thenReturn(FamilyProfileDTO.builder().userId(1001L).familySize(3).build());

        mockMvc.perform(get("/api/family/profile/query").param("userId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1001))
                .andExpect(jsonPath("$.familySize").value(3));
    }

    @Test
    @DisplayName("GET /api/family/profile/query: 不传userId应使用默认1001")
    void testQueryProfileDefaultUserId() throws Exception {
        when(familyProfileService.queryOrDefault(anyLong()))
                .thenReturn(FamilyProfileDTO.builder().userId(1001L).familySize(3).build());

        mockMvc.perform(get("/api/family/profile/query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1001));
    }

    // ==================== 推荐对话（非流式） ====================

    @Test
    @DisplayName("POST /api/family/recommend/chat: 正常推荐返回结果")
    void testRecommendChatSuccess() throws Exception {
        when(familyProfileService.queryOrDefault(anyLong()))
                .thenReturn(FamilyProfileDTO.builder().userId(1001L).familySize(3).build());
        when(sessionContextService.updateForQuestion(anyString(), anyString()))
                .thenReturn(SessionContextDTO.builder().sessionId("test-session").currentIntent("shop_recommendation").build());
        when(familyRecommendService.recommendOnce(anyString(), any(), any(), any()))
                .thenReturn("为您推荐西湖蒸鲜坊");

        mockMvc.perform(post("/api/family/recommend/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"推荐一家餐厅\",\"userId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.reply").value("为您推荐西湖蒸鲜坊"));
    }

    @Test
    @DisplayName("POST /api/family/recommend/chat: 空body使用默认消息")
    void testRecommendChatEmptyBody() throws Exception {
        when(familyProfileService.queryOrDefault(anyLong()))
                .thenReturn(FamilyProfileDTO.builder().userId(1001L).familySize(3).build());
        when(sessionContextService.updateForQuestion(anyString(), anyString()))
                .thenReturn(SessionContextDTO.builder().sessionId("test").currentIntent("shop_recommendation").build());
        when(familyRecommendService.recommendOnce(anyString(), any(), any(), any()))
                .thenReturn("默认回复");

        mockMvc.perform(post("/api/family/recommend/chat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").exists());
    }

    @Test
    @DisplayName("POST /api/family/recommend/chat: userId为非数字字符串应安全降级")
    void testRecommendChatInvalidUserId() throws Exception {
        when(familyProfileService.queryOrDefault(anyLong()))
                .thenReturn(FamilyProfileDTO.builder().userId(1001L).familySize(3).build());
        when(sessionContextService.updateForQuestion(anyString(), anyString()))
                .thenReturn(SessionContextDTO.builder().sessionId("test").currentIntent("shop_recommendation").build());
        when(familyRecommendService.recommendOnce(anyString(), any(), any(), any()))
                .thenReturn("回复");

        // userId是非法字符串，应安全降级为默认1001而非抛500
        mockMvc.perform(post("/api/family/recommend/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"test\",\"userId\":\"not-a-number\"}"))
                .andExpect(status().isOk());
    }

    // ==================== 推荐记录 ====================

    @Test
    @DisplayName("推荐成功应记录用户消息到记忆系统")
    void testMemoryRecordOnRecommend() throws Exception {
        when(familyProfileService.queryOrDefault(anyLong()))
                .thenReturn(FamilyProfileDTO.builder().userId(1001L).familySize(3).build());
        when(sessionContextService.updateForQuestion(anyString(), anyString()))
                .thenReturn(SessionContextDTO.builder().sessionId("test").currentIntent("shop_recommendation").build());
        when(familyRecommendService.recommendOnce(anyString(), any(), any(), any()))
                .thenReturn("reply");

        mockMvc.perform(post("/api/family/recommend/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"推荐餐厅\",\"userId\":1001}"))
                .andExpect(status().isOk());

        verify(familyMemoryService).recordUserMessage(eq("推荐餐厅"), anyString());
        verify(familyMemoryService).recordAgentResponse(eq("reply"), anyString());
    }
}
