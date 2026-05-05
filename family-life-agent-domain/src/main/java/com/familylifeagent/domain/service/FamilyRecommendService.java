package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.api.dto.SessionContextDTO;
import com.familylifeagent.domain.prompt.FamilyAgentPromptTemplate;
import com.familylifeagent.infrastructure.tool.ShopSearchTool;
import com.familylifeagent.infrastructure.tool.ShopActionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Service
public class FamilyRecommendService {

    private static final Logger log = LoggerFactory.getLogger(FamilyRecommendService.class);

    private final ChatClient familyChatClient;
    private final FamilyAgentPromptTemplate promptTemplate;
    private final ShopSearchTool shopSearchTool;
    private final ShopActionTool shopActionTool;
    private final ShopRecommendationService shopRecommendationService;
    private final FamilyMemoryService familyMemoryService;

    public FamilyRecommendService(ChatClient familyChatClient,
                                  FamilyAgentPromptTemplate promptTemplate,
                                  ShopSearchTool shopSearchTool,
                                  ShopActionTool shopActionTool,
                                  ShopRecommendationService shopRecommendationService,
                                  FamilyMemoryService familyMemoryService) {
        this.familyChatClient = familyChatClient;
        this.promptTemplate = promptTemplate;
        this.shopSearchTool = shopSearchTool;
        this.shopActionTool = shopActionTool;
        this.shopRecommendationService = shopRecommendationService;
        this.familyMemoryService = familyMemoryService;
    }

    public String recommendOnce(String message,
                                FamilyProfileDTO profileDTO,
                                SessionContextDTO sessionContextDTO,
                                ShopSearchTool.ShopSearchAdvancedResult advancedResult) {
        String memoryContext = familyMemoryService.getMemoryPromptContext();
        String systemPrompt = promptTemplate.render(profileDTO, sessionContextDTO, memoryContext);
        String enhancedPrompt = appendAdvancedResultIfPresent(systemPrompt, advancedResult);
        return familyChatClient.prompt()
                .system(enhancedPrompt)
                .user(message)
                .tools(shopSearchTool, shopActionTool)
                .call()
                .content();
    }

    public Flux<String> recommendStream(String message,
                                        FamilyProfileDTO profileDTO,
                                        SessionContextDTO sessionContextDTO,
                                        ShopSearchTool.ShopSearchAdvancedResult advancedResult) {
        String memoryContext = familyMemoryService.getMemoryPromptContext();
        String systemPrompt = promptTemplate.render(profileDTO, sessionContextDTO, memoryContext);
        String enhancedPrompt = appendAdvancedResultIfPresent(systemPrompt, advancedResult);
        return familyChatClient.prompt()
                .system(enhancedPrompt)
                .user(message)
                .tools(shopSearchTool, shopActionTool)
                .stream()
                .content();
    }

    /**
     * 仅在实际上有搜索结果时才拼接排序信息，避免 null/空结果带来的无用 Token。
     */
    private String appendAdvancedResultIfPresent(String systemPrompt,
                                                  ShopSearchTool.ShopSearchAdvancedResult advancedResult) {
        ShopSearchTool.ShopSearchAdvancedResult aligned = alignAdvancedResult(null, advancedResult);
        if (aligned == null || aligned.shops() == null || aligned.shops().isEmpty()) {
            return systemPrompt;
        }
        return systemPrompt + "\n\n家庭适配排序结果（优先参考）：\n" + aligned;
    }

    public ShopSearchTool.ShopSearchAdvancedResult prepareAdvancedRecommendation(FamilyProfileDTO profileDTO,
                                                                                 SessionContextDTO sessionContextDTO) {
        Map<String, Object> slots = resolveEffectiveSlots(sessionContextDTO);
        String shopType = getStringSlot(slots, "shopType");
        String area = getStringSlot(slots, "area");
        String budgetRange = getStringSlot(slots, "budgetRange");
        Boolean suitableForElderly = getBooleanSlot(slots, "suitableForElderly");
        Boolean hasChildrenPlayArea = getBooleanSlot(slots, "hasChildrenPlayArea");

        String resolvedArea = firstNonBlank(area, profileDTO != null ? profileDTO.getDefaultArea() : null);
        String resolvedBudgetRange = firstNonBlank(budgetRange, profileDTO != null ? profileDTO.getBudgetRange() : null);

        ShopSearchTool.ShopSearchAdvancedResult searchResult = shopSearchTool.shopSearchAdvanced(
                resolvedArea,
                resolvedBudgetRange,
                shopType,
                suitableForElderly,
                hasChildrenPlayArea
        );
        ShopSearchTool.ShopSearchAdvancedResult alignedResult = alignAdvancedResultWithResolvedInputs(
                sessionContextDTO,
                searchResult,
                resolvedArea,
                resolvedBudgetRange,
                shopType,
                suitableForElderly,
                hasChildrenPlayArea
        );
        ShopSearchTool.ShopSearchAdvancedResult rankedResult = shopRecommendationService.rankShops(List.of(alignedResult), profileDTO)
                .stream()
                .findFirst()
                .orElse(alignedResult);
        logRankedShops(profileDTO, rankedResult, resolvedArea, resolvedBudgetRange, shopType);
        return rankedResult;
    }

    public List<Long> extractCandidateShopIds(ShopSearchTool.ShopSearchAdvancedResult advancedResult) {
        if (advancedResult == null || advancedResult.shops() == null || advancedResult.shops().isEmpty()) {
            return List.of();
        }
        return advancedResult.shops().stream()
                .map(ShopSearchTool.AdvancedShopItem::shopId)
                .toList();
    }

    private ShopSearchTool.ShopSearchAdvancedResult alignAdvancedResult(SessionContextDTO sessionContextDTO,
                                                                        ShopSearchTool.ShopSearchAdvancedResult advancedResult) {
        Map<String, Object> slots = resolveEffectiveSlots(sessionContextDTO);
        String expectedShopType = getStringSlot(slots, "shopType");
        if (expectedShopType == null || expectedShopType.isBlank()) {
            return advancedResult;
        }
        if (advancedResult == null) {
            return new ShopSearchTool.ShopSearchAdvancedResult(
                    getStringSlot(slots, "area"),
                    getStringSlot(slots, "budgetRange"),
                    expectedShopType,
                    getBooleanSlot(slots, "suitableForElderly"),
                    getBooleanSlot(slots, "hasChildrenPlayArea"),
                    List.of()
            );
        }
        if (expectedShopType.equals(advancedResult.shopType())) {
            return advancedResult;
        }
        log.warn("高级搜索结果类型与会话槽位不一致: expectedShopType={}, actualShopType={}", expectedShopType, advancedResult.shopType());
        return new ShopSearchTool.ShopSearchAdvancedResult(
                advancedResult.area(),
                advancedResult.averageBudgetRange(),
                expectedShopType,
                advancedResult.suitableForElderly(),
                advancedResult.hasChildrenPlayArea(),
                List.of()
        );
    }

    private Map<String, Object> resolveEffectiveSlots(SessionContextDTO sessionContextDTO) {
        if (sessionContextDTO == null) {
            return Map.of();
        }
        Map<String, Object> currentSlots = sessionContextDTO.getCurrentSlots();
        if (currentSlots != null && !currentSlots.isEmpty()) {
            return currentSlots;
        }
        return sessionContextDTO.getSlots() != null ? sessionContextDTO.getSlots() : Map.of();
    }

    private ShopSearchTool.ShopSearchAdvancedResult alignAdvancedResultWithResolvedInputs(
            SessionContextDTO sessionContextDTO,
            ShopSearchTool.ShopSearchAdvancedResult advancedResult,
            String resolvedArea,
            String resolvedBudgetRange,
            String resolvedShopType,
            Boolean suitableForElderly,
            Boolean hasChildrenPlayArea) {
        ShopSearchTool.ShopSearchAdvancedResult alignedResult = alignAdvancedResult(sessionContextDTO, advancedResult);
        if (alignedResult == null) {
            return new ShopSearchTool.ShopSearchAdvancedResult(
                    resolvedArea,
                    resolvedBudgetRange,
                    resolvedShopType,
                    suitableForElderly,
                    hasChildrenPlayArea,
                    List.of()
            );
        }
        return new ShopSearchTool.ShopSearchAdvancedResult(
                resolvedArea,
                resolvedBudgetRange,
                alignedResult.shopType(),
                alignedResult.suitableForElderly(),
                alignedResult.hasChildrenPlayArea(),
                alignedResult.shops()
        );
    }

    private boolean shouldAnswerDirectly(SessionContextDTO sessionContextDTO,
                                       ShopSearchTool.ShopSearchAdvancedResult advancedResult) {
        if (advancedResult == null || advancedResult.shops() == null || advancedResult.shops().isEmpty()) {
            return false;
        }
        Map<String, Object> slots = resolveEffectiveSlots(sessionContextDTO);
        return getStringSlot(slots, "shopType") != null;
    }

    private String buildDirectRecommendationReply(ShopSearchTool.ShopSearchAdvancedResult advancedResult) {
        List<ShopSearchTool.AdvancedShopItem> shops = advancedResult.shops();
        if (shops == null || shops.isEmpty()) {
            return "暂时没有找到符合条件的店铺，您可以试试放宽区域或预算。";
        }
        StringBuilder reply = new StringBuilder();
        reply.append("根据您的要求，我为您找到了以下店铺：\n\n");
        for (int i = 0; i < shops.size(); i++) {
            ShopSearchTool.AdvancedShopItem shop = shops.get(i);
            reply.append(i + 1)
                    .append(". ")
                    .append(shop.shopName())
                    .append("（shopId:")
                    .append(shop.shopId())
                    .append("）\n")
                    .append("- 位置：")
                    .append(shop.area())
                    .append("\n")
                    .append("- 人均消费：约")
                    .append(shop.averagePrice())
                    .append("元\n")
                    .append("- 评分：")
                    .append(shop.rating())
                    .append("分\n")
                    .append("- 推荐理由：")
                    .append(shop.recommendationReason())
                    .append("\n\n");
        }
        reply.append("如果您愿意，我还可以继续帮您查其中某家店的详情、营业时间或优惠信息。");
        return reply.toString().trim();
    }

    private String getStringSlot(Map<String, Object> slots, String key) {
        Object value = slots.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Boolean getBooleanSlot(Map<String, Object> slots, String key) {
        Object value = slots.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private void logRankedShops(FamilyProfileDTO profileDTO,
                                ShopSearchTool.ShopSearchAdvancedResult rankedResult,
                                String resolvedArea,
                                String resolvedBudgetRange,
                                String resolvedShopType) {
        if (rankedResult == null || rankedResult.shops() == null || rankedResult.shops().isEmpty()) {
            log.info("家庭适配排序结果: userId={}, budgetRange={}, area={}, shopType={}, 无候选店铺",
                    profileDTO != null ? profileDTO.getUserId() : null,
                    resolvedBudgetRange,
                    resolvedArea,
                    resolvedShopType);
            return;
        }

        log.info("家庭适配排序结果: userId={}, budgetRange={}, area={}, shopType={}",
                profileDTO != null ? profileDTO.getUserId() : null,
                resolvedBudgetRange,
                resolvedArea,
                rankedResult.shopType());

        for (int i = 0; i < rankedResult.shops().size(); i++) {
            ShopSearchTool.AdvancedShopItem shop = rankedResult.shops().get(i);
            int score = shopRecommendationService.calculateRecommendationScore(shop, profileDTO);
            log.info("第{}名：{} / {}分 / 人均{}元 / {}",
                    i + 1,
                    shop.shopName(),
                    score,
                    shop.averagePrice(),
                    shop.recommendationReason());
        }
    }
}
