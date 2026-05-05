package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.SessionContextDTO;
import com.familylifeagent.infrastructure.service.LocalLifeSemanticRagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 意图检测增强测试 — 验证新增的 "附近"、"第X家"、"商城" 等模式。
 */
@ExtendWith(MockitoExtension.class)
class SessionContextIntentEnhancementTest {

    @Mock
    private LocalLifeSemanticRagService ragService;

    private SessionContextService contextService;

    @BeforeEach
    void setUp() {
        when(ragService.expandSemanticTags(anyString())).thenReturn("");
        contextService = new SessionContextService(ragService, null);
    }

    // ==================== "附近" 触发搜索意图 ====================

    @Test
    @DisplayName("'附近' 应触发 shop_search 意图")
    void testNearbyTriggersSearchIntent() {
        SessionContextDTO ctx = contextService.updateForQuestion("test-nearby", "附近有商城吗");
        assertEquals("shop_search", ctx.getCurrentIntent(), "'附近'应触发搜索意图");
    }

    @Test
    @DisplayName("'附近还有别的餐厅吗' 应触发 shop_search 意图")
    void testNearbyRestaurantSearch() {
        contextService.updateForQuestion("test-nearby2", "西湖附近推荐餐厅");
        SessionContextDTO ctx = contextService.updateForQuestion("test-nearby2", "附近还有别的餐厅吗");
        assertEquals("shop_search", ctx.getCurrentIntent());
    }

    // ==================== "第X家" 触发详情意图 ====================

    @Test
    @DisplayName("'第二家' 应触发 shop_detail 意图")
    void testNthShopTriggersDetailIntent() {
        SessionContextDTO ctx = contextService.updateForQuestion("test-nth", "第二家");
        assertEquals("shop_detail", ctx.getCurrentIntent(), "'第二家'应触发详情意图");
    }

    @Test
    @DisplayName("'第三家怎么样' 应触发 shop_detail 意图")
    void testNthShopWithZenyang() {
        SessionContextDTO ctx = contextService.updateForQuestion("test-nth2", "第三家怎么样");
        assertEquals("shop_detail", ctx.getCurrentIntent(), "'第三家怎么样'应触发详情意图（'怎么样'也是详情关键词）");
    }

    @Test
    @DisplayName("'第5家'（阿拉伯数字）应触发 shop_detail 意图")
    void testNthShopArabicNumeral() {
        SessionContextDTO ctx = contextService.updateForQuestion("test-nth3", "第5家");
        assertEquals("shop_detail", ctx.getCurrentIntent(), "阿拉伯数字'第5家'应触发详情意图");
    }

    @Test
    @DisplayName("'第12家'（两位阿拉伯数字）应触发 shop_detail 意图")
    void testNthShopMultiDigit() {
        SessionContextDTO ctx = contextService.updateForQuestion("test-nth4", "第12家");
        assertEquals("shop_detail", ctx.getCurrentIntent(), "多位阿拉伯数字'第12家'应触发详情意图");
    }

    // ==================== "商城" 搜索 ====================

    @Test
    @DisplayName("'附近有商城吗' 应提取 shopType=商场")
    void testMallDetection() {
        SessionContextDTO ctx = contextService.updateForQuestion("test-mall", "附近有商城吗");
        assertEquals("商场", ctx.getSlots().get("shopType"), "应提取 shopType=商场");
    }

    // ==================== "营业到几点" 触发详情 ====================

    @Test
    @DisplayName("'这家店营业到几点' 应触发 shop_detail")
    void testBusinessHoursTriggersDetail() {
        SessionContextDTO ctx = contextService.updateForQuestion("test-hours", "这家店营业到几点");
        assertEquals("shop_detail", ctx.getCurrentIntent(), "'营业到几点'应触发详情意图");
    }

    // ==================== 槽位继承 ====================

    @Test
    @DisplayName("追问时应继承上一轮的 shopType")
    void testSlotInheritanceOnFollowUp() {
        contextService.updateForQuestion("test-inherit", "帮我推荐一些餐厅");
        SessionContextDTO ctx = contextService.updateForQuestion("test-inherit", "第二家店的详细信息");
        assertEquals("shop_detail", ctx.getCurrentIntent());
        assertEquals("餐厅", ctx.getSlots().get("shopType"), "shopType应继承上一轮的'餐厅'");
    }

    // ==================== 区域放宽 ====================

    @Test
    @DisplayName("'其他区域也可以' 应清空 area 槽位")
    void testAreaRelaxation() {
        contextService.updateForQuestion("test-relax", "西湖附近推荐餐厅");
        SessionContextDTO ctx = contextService.updateForQuestion("test-relax", "其他区域也可以");
        assertEquals(null, ctx.getSlots().get("area"), "区域放宽后area应为null");
    }
}
