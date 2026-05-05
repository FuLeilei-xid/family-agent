package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.infrastructure.tool.ShopSearchTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ShopRecommendationService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    public List<ShopSearchTool.ShopSearchAdvancedResult> rankShops(List<ShopSearchTool.ShopSearchAdvancedResult> searchResults,
                                                                   FamilyProfileDTO familyProfileDTO) {
        if (searchResults == null || searchResults.isEmpty()) {
            return List.of();
        }
        List<ShopSearchTool.ShopSearchAdvancedResult> rankedResults = new ArrayList<>();
        for (ShopSearchTool.ShopSearchAdvancedResult result : searchResults) {
            if (result == null) {
                continue;
            }
            List<ShopSearchTool.AdvancedShopItem> rankedShops = rankShopItems(result.shops(), familyProfileDTO);
            rankedResults.add(new ShopSearchTool.ShopSearchAdvancedResult(
                    result.area(),
                    result.averageBudgetRange(),
                    result.shopType(),
                    result.suitableForElderly(),
                    result.hasChildrenPlayArea(),
                    rankedShops
            ));
        }
        return rankedResults;
    }

    public List<ShopSearchTool.AdvancedShopItem> rankShopItems(List<ShopSearchTool.AdvancedShopItem> shops,
                                                               FamilyProfileDTO familyProfileDTO) {
        if (shops == null || shops.isEmpty()) {
            return List.of();
        }
        return shops.stream()
                .filter(Objects::nonNull)
                .map(shop -> new ScoredShop(shop, calculateRecommendationScore(shop, familyProfileDTO)))
                .sorted(Comparator.comparingInt(ScoredShop::score).reversed()
                        .thenComparing(scored -> scored.shop().rating(), Comparator.nullsLast(Comparator.reverseOrder())))
                .map(scored -> rebuildShop(scored.shop(), scored.score(), familyProfileDTO))
                .toList();
    }

    public int calculateRecommendationScore(ShopSearchTool.AdvancedShopItem shop, FamilyProfileDTO familyProfileDTO) {
        int score = 0;
        if (Boolean.TRUE.equals(shop.suitableForElderly()) && hasElderly(familyProfileDTO)) {
            score += 10;
        }
        if (isPriceInBudget(shop.averagePrice(), familyProfileDTO != null ? familyProfileDTO.getBudgetRange() : null)) {
            score += 10;
        }
        return score;
    }

    private ShopSearchTool.AdvancedShopItem rebuildShop(ShopSearchTool.AdvancedShopItem shop,
                                                        int score,
                                                        FamilyProfileDTO familyProfileDTO) {
        return new ShopSearchTool.AdvancedShopItem(
                shop.shopId(),
                shop.shopName(),
                shop.area(),
                shop.averagePrice(),
                shop.rating(),
                shop.suitableForElderly(),
                shop.hasChildrenPlayArea(),
                buildRecommendationReason(shop, score, familyProfileDTO)
        );
    }

    private String buildRecommendationReason(ShopSearchTool.AdvancedShopItem shop,
                                             int score,
                                             FamilyProfileDTO familyProfileDTO) {
        List<String> fragments = new ArrayList<>();
        fragments.add("该店位于" + shop.area() + "，评分" + formatRating(shop.rating()));

        String originalReason = shop.recommendationReason();
        if (originalReason != null && !originalReason.isBlank()) {
            String normalizedReason = originalReason.replace('，', '、').replace('。', ' ').trim();
            fragments.add(normalizedReason);
        }

        if (isPriceInBudget(shop.averagePrice(), familyProfileDTO != null ? familyProfileDTO.getBudgetRange() : null)) {
            fragments.add("人均消费符合您的预算");
        }
        if (Boolean.TRUE.equals(shop.suitableForElderly()) && hasElderly(familyProfileDTO)) {
            fragments.add("对老人更友好");
        }
        if (Boolean.TRUE.equals(shop.hasChildrenPlayArea()) && familyProfileDTO != null && Boolean.TRUE.equals(familyProfileDTO.getHasChild())) {
            fragments.add("带孩子也更方便");
        }
        fragments.add("推荐时请直接引用店铺原名和 shopId=" + shop.shopId());
        return String.join("，", deduplicate(fragments)) + "。";
    }

    private List<String> deduplicate(List<String> fragments) {
        List<String> values = new ArrayList<>();
        for (String fragment : fragments) {
            if (fragment == null || fragment.isBlank() || values.contains(fragment)) {
                continue;
            }
            values.add(fragment);
        }
        return values;
    }

    private boolean hasElderly(FamilyProfileDTO familyProfileDTO) {
        return familyProfileDTO != null && Boolean.TRUE.equals(familyProfileDTO.getHasElderly());
    }

    private boolean isPriceInBudget(Integer averagePrice, String budgetRange) {
        if (averagePrice == null || budgetRange == null || budgetRange.isBlank()) {
            return false;
        }
        List<Integer> numbers = extractNumbers(budgetRange);
        if (numbers.isEmpty()) {
            return false;
        }
        if (numbers.size() == 1) {
            if (budgetRange.contains("以内") || budgetRange.contains("以下") || budgetRange.contains("不超过")) {
                return averagePrice <= numbers.get(0);
            }
            if (budgetRange.contains("以上") || budgetRange.contains("不少于")) {
                return averagePrice >= numbers.get(0);
            }
            return averagePrice <= numbers.get(0);
        }
        int min = Math.min(numbers.get(0), numbers.get(1));
        int max = Math.max(numbers.get(0), numbers.get(1));
        return averagePrice >= min && averagePrice <= max;
    }

    private List<Integer> extractNumbers(String text) {
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group()));
        }
        return numbers;
    }

    private String formatRating(Double rating) {
        return rating == null ? "未知" : String.format("%.1f", rating);
    }

    private record ScoredShop(ShopSearchTool.AdvancedShopItem shop, int score) {
    }
}
