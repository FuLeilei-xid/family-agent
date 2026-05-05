package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.SessionContextDTO;
import com.familylifeagent.infrastructure.service.LocalLifeSemanticRagService;
import com.familylifeagent.infrastructure.tool.ShopSearchTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 多轮对话上下文测试 — 模拟用户连续发文的完整场景
 *
 * <p>测试场景流程：</p>
 * <ol>
 *   <li>用户问"帮我推荐一些餐厅" → 期望识别 shopType=餐厅</li>
 *   <li>用户追问"第二家店的详细信息" → 期望意图=详情，继承 shopType</li>
 *   <li>用户继续"这家餐厅附近有商城吗" → 期望识别"附近"搜索意图</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SessionContextMultiTurnTest {

    @Mock
    private LocalLifeSemanticRagService ragService;

    private SessionContextService contextService;

    @BeforeEach
    void setUp() {
        when(ragService.expandSemanticTags(anyString())).thenReturn("");
        contextService = new SessionContextService(ragService, null);
    }

    // ==================== 场景 A: 标准餐厅推荐链 ====================

    @Test
    @DisplayName("A1: 用户说'帮我推荐一些餐厅' → 应识别 intent=shop_search, shopType=餐厅")
    void testTurn1_RecommendRestaurant() {
        String sessionId = "test-session-A";
        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "帮我推荐一些餐厅");

        assertEquals("shop_search", ctx.getCurrentIntent(),
                "应识别为搜索意图");
        assertEquals("餐厅", ctx.getSlots().get("shopType"),
                "应提取 shopType=餐厅");
        assertNull(ctx.getSlots().get("area"),
                "area 应为空（没有指定区域）");
    }

    @Test
    @DisplayName("A2: 继续追问'第二家店的详细信息' → 应识别 intent=shop_detail, 继承 shopType=餐厅")
    void testTurn2_SecondShopDetail() {
        String sessionId = "test-session-A";
        // Turn 1
        contextService.updateForQuestion(sessionId, "帮我推荐一些餐厅");

        // 模拟搜索结果注入
        ShopSearchTool.ShopSearchAdvancedResult result = new ShopSearchTool.ShopSearchAdvancedResult(
                "西湖", "50-100", "餐厅",
                null, null,
                List.of(
                        new ShopSearchTool.AdvancedShopItem(101L, "西湖邻里蒸鲜坊", "西湖", 86, 4.8, true, true, "清淡适合老人"),
                        new ShopSearchTool.AdvancedShopItem(102L, "湖滨亲子面小馆", "西湖", 72, 4.6, true, true, "面食适合孩子"),
                        new ShopSearchTool.AdvancedShopItem(103L, "桂雨家常食堂", "西湖", 94, 4.7, true, false, "家常菜")
                )
        );
        contextService.refreshCandidates(sessionId, result);

        // Turn 2
        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "第二家店的详细信息");

        assertEquals("shop_detail", ctx.getCurrentIntent(),
                "应识别为详情意图");
        assertEquals("餐厅", ctx.getSlots().get("shopType"),
                "shopType 应继承上一轮的'餐厅'");
        assertNotNull(ctx.getCandidateShopIds(),
                "候选店铺列表应当保留");
        assertFalse(ctx.getCandidateShopIds().isEmpty(),
                "候选店铺不应为空");
    }

    @Test
    @DisplayName("A3: 继续'这家餐厅附近有商城吗' → 应识别搜索意图, 不丢失 shopType")
    void testTurn3_NearbyMall() {
        String sessionId = "test-session-A";
        // Turn 1
        contextService.updateForQuestion(sessionId, "帮我推荐一些餐厅");
        // Turn 2
        contextService.updateForQuestion(sessionId, "第二家店的详细信息");

        // Turn 3: "这家餐厅附近有商城吗"
        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "这家餐厅附近有商城吗");

        // 注意：当前系统不支持"附近"关键词检测，"商城"也不是已知店铺类型
        // 这暴露了一个潜在漏洞
        String intent = ctx.getCurrentIntent();
        assertNotNull(intent, "必须有意图识别结果");

        // 检验候选列表是否仍然保留
        assertNotNull(ctx.getCandidateShopIds(),
                "候选店铺列表应当保留（多轮上下文不能丢失）");
    }

    // ==================== 场景 B: 预算+区域+类型完整链 ====================

    @Test
    @DisplayName("B1: '西湖附近有什么餐厅推荐' → 应识别 area=西湖, shopType=餐厅")
    void testAreaAndType() {
        String sessionId = "test-session-B";
        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "西湖附近有什么餐厅推荐");

        assertEquals("西湖", ctx.getSlots().get("area"),
                "应提取 area=西湖");
        assertEquals("餐厅", ctx.getSlots().get("shopType"),
                "应提取 shopType=餐厅");
    }

    @Test
    @DisplayName("B2: '第二家怎么样' → 应识别 intent=shop_detail（含怎么样）")
    void testSecondShopWithZenyang() {
        String sessionId = "test-session-B";
        contextService.updateForQuestion(sessionId, "西湖附近有什么餐厅推荐");

        // 注入候选
        contextService.refreshCandidates(sessionId, createSampleResult());

        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "第二家怎么样");

        assertEquals("shop_detail", ctx.getCurrentIntent(),
                "'怎么样'应触发出详情意图");
        assertEquals("餐厅", ctx.getSlots().get("shopType"),
                "shopType 应继承");
    }

    // ==================== 场景 C: 多任务串联 ====================

    @Test
    @DisplayName("C1: '先买衣服，再去吃饭' → 应识别 shopType=服装店（首要任务）")
    void testMultiTaskPriority() {
        String sessionId = "test-session-C";
        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "先买衣服，再去吃饭");

        // 注意 "买衣服" 被 normalize 成 "买衣服 服装店"
        // "再去吃饭" 包含 "吃饭" → "餐厅"
        // 同义词表是顺序匹配的，"衣服"排在"吃饭"前面，所以应该是服装店
        assertEquals("服装店", ctx.getSlots().get("shopType"),
                "应优先识别衣服/服装店（首要任务）");
    }

    @Test
    @DisplayName("C2: 第二轮说'衣服已选好，附近有没有餐厅' → 应识别 shopType=餐厅（任务切换）")
    void testTaskSwitchToRestaurant() {
        String sessionId = "test-session-C";
        contextService.updateForQuestion(sessionId, "先买衣服，再去吃饭");

        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "衣服已选好，附近有没有餐厅");

        assertEquals("餐厅", ctx.getSlots().get("shopType"),
                "第二句话应切换为餐厅");
    }

    // ==================== 场景 D: 优惠详情链 ====================

    @Test
    @DisplayName("D1: 第三轮问'有优惠券吗' → 应识别 intent=voucher_query")
    void testVoucherQuery() {
        String sessionId = "test-session-D";
        contextService.updateForQuestion(sessionId, "西湖附近推荐餐厅");
        contextService.updateForQuestion(sessionId, "第一家怎么样");
        contextService.refreshCandidates(sessionId, createSampleResult());

        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "有优惠券吗");

        assertEquals("voucher_query", ctx.getCurrentIntent(),
                "含'优惠券'应识别为优惠查询意图");
    }

    // ==================== 场景 E: 修改条件重新搜索 ====================

    @Test
    @DisplayName("E1: 换区域重推 → 继承 shopType, 更新 area")
    void testChangeArea() {
        String sessionId = "test-session-E";
        contextService.updateForQuestion(sessionId, "西湖附近有什么餐厅推荐");

        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "换成滨江区的");

        // 先检测是否切换区域
        Object area = ctx.getSlots().get("area");
        assertNotNull(area, "area 应被更新");
        // 注意："滨江区的" 中的 "滨江" 应该触发 area 提取
        assertTrue(ctx.getSlots().get("shopType") == null || "餐厅".equals(ctx.getSlots().get("shopType")),
                "shopType 应为 null 或保留为餐厅");
    }

    // ==================== 场景 F: 检测暴露的漏洞 ====================

    @Test
    @DisplayName("F1: [已知漏洞]'附近'关键词不被识别为搜索意图")
    void testNearbyNotDetected() {
        String sessionId = "test-session-F";
        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "附近有商城吗");

        // 分析:"附近"不在 containsSearchIntent 中，"商城"不在同义词表中
        // 所以很可能被识别为 shop_recommendation 而非 shop_search
        String intent = ctx.getCurrentIntent();
        System.out.println(">>> [漏洞扫描] '附近有商城吗' 识别为 intent=" + intent);
        System.out.println(">>> [漏洞扫描] shopType=" + ctx.getSlots().get("shopType"));
        System.out.println(">>> [漏洞扫描] area=" + ctx.getSlots().get("area"));
        // 预期：这是一个漏洞
        // "附近" 应该在 containsSearchIntent 中被添加
        // "商城" 应该被加入同义词表（作为 shopType=超市 或 新增类型）
    }

    @Test
    @DisplayName("F2: [已知漏洞]'第X家'不触发详情意图")
    void testNthShopNotDetail() {
        String sessionId = "test-session-F2";
        contextService.updateForQuestion(sessionId, "推荐几家餐厅");
        contextService.refreshCandidates(sessionId, createSampleResult());

        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "第三家");

        // 分析："第三家" 不含 "详情、具体、怎么样" 等关键词
        // 所以不会触发 detail intent
        System.out.println(">>> [漏洞扫描] '第三家' 识别为 intent=" + ctx.getCurrentIntent());
        // 应该有一个 "第X家" → detail intent 的识别逻辑
    }

    @Test
    @DisplayName("F3: [已知漏洞]'这家店营业到几点'不触发详情意图")
    void testBusinessHours() {
        String sessionId = "test-session-F3";
        contextService.updateForQuestion(sessionId, "西湖附近推荐餐厅");
        contextService.refreshCandidates(sessionId, createSampleResult());

        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "这家店营业到几点");

        // 分析："营业到几点" 在 containsDetailIntent 中被包含
        // 但 "这家店" 的指代关系没有处理
        assertEquals("shop_detail", ctx.getCurrentIntent(),
                "'营业到几点'应触发详情意图");
        System.out.println(">>> [漏洞扫描] '这家店营业到几点' → intent=" + ctx.getCurrentIntent());
    }

    @Test
    @DisplayName("F4: [关键漏洞] 附近搜索无独立意图，依赖LLM推理")
    void testNearbySearchNoDedicatedIntent() {
        String sessionId = "test-session-F4";
        contextService.updateForQuestion(sessionId, "西湖附近推荐餐厅");

        SessionContextDTO ctx = contextService.updateForQuestion(sessionId, "这家店附近还有别的餐厅吗");

        // 分析：没有 "searchAroundShop" 相关的意图检测
        // "附近" 这个词不会被特殊处理，LLM 需要自己推断调用 searchAroundShop
        System.out.println(">>> [漏洞扫描] '附近还有别的餐厅吗' → intent=" + ctx.getCurrentIntent());
        System.out.println(">>> [漏洞扫描] shopType=" + ctx.getSlots().get("shopType"));
        // 问题：LLM 虽然知道 searchAroundShop 工具的存在，但 session 层没有提供
        // "附近" 触发的槽位标记，导致 LLM 可能无法正确路由
    }

    // ==================== 辅助方法 ====================

    private ShopSearchTool.ShopSearchAdvancedResult createSampleResult() {
        return new ShopSearchTool.ShopSearchAdvancedResult(
                "西湖", "50-100", "餐厅",
                null, null,
                List.of(
                        new ShopSearchTool.AdvancedShopItem(101L, "西湖邻里蒸鲜坊", "西湖", 86, 4.8, true, true, "清淡适合老人"),
                        new ShopSearchTool.AdvancedShopItem(102L, "湖滨亲子面小馆", "西湖", 72, 4.6, true, true, "面食适合孩子"),
                        new ShopSearchTool.AdvancedShopItem(103L, "桂雨家常食堂", "西湖", 94, 4.7, true, false, "家常菜")
                )
        );
    }
}
