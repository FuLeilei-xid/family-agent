package com.familylifeagent.infrastructure.tool;

import com.familylifeagent.infrastructure.entity.ShopDetailEntity;
import com.familylifeagent.infrastructure.entity.ShopEntity;
import com.familylifeagent.infrastructure.entity.ShopTypeEntity;
import com.familylifeagent.infrastructure.entity.VoucherEntity;
import com.familylifeagent.infrastructure.repository.ShopRepository;
import com.familylifeagent.infrastructure.repository.VoucherRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ShopSearchTool {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm前可用");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final ShopRepository shopRepository;
    private final VoucherRepository voucherRepository;

    public ShopSearchTool(ShopRepository shopRepository, VoucherRepository voucherRepository) {
        this.shopRepository = shopRepository;
        this.voucherRepository = voucherRepository;
    }

    @Tool(description = "根据用户的家庭聚餐需求搜索适合的店铺，返回带有标签和简单说明的候选店铺列表，支持按店铺名称、区域、标签和类型搜索，如餐厅、游戏厅、服装店")
    public List<ShopCandidate> shopSearch(String keyword) {
        return shopRepository.searchByKeyword(keyword).stream()
                .map(this::toShopCandidate)
                .toList();
    }

    @Tool(description = "按区域、人均预算范围、店铺类型、是否适合老人、是否有儿童游乐区搜索店铺，返回3个便于大模型比较和重排的候选店铺；推荐时必须优先引用返回的真实店名和 shopId，不要改写成数据库中不存在的店名")
    public ShopSearchAdvancedResult shopSearchAdvanced(String area,
                                                       String averageBudgetRange,
                                                       String shopType,
                                                       Boolean suitableForElderly,
                                                       Boolean hasChildrenPlayArea) {
        BudgetRange budgetRange = parseBudgetRange(averageBudgetRange);
        List<AdvancedShopItem> shops = shopRepository.searchAdvanced(
                        normalizeText(area),
                        normalizeText(shopType),
                        suitableForElderly,
                        hasChildrenPlayArea,
                        budgetRange.minPrice(),
                        budgetRange.maxPrice())
                .stream()
                .limit(3)
                .map(this::toAdvancedShopItem)
                .toList();
        return new ShopSearchAdvancedResult(
                valueOrDefault(area, "西湖"),
                valueOrDefault(averageBudgetRange, "50-100"),
                valueOrDefault(shopType, "不限"),
                suitableForElderly,
                hasChildrenPlayArea,
                shops
        );
    }

    @Tool(description = "根据基准店铺ID查询其所在区域，并在该区域内按目标店铺类型和预算搜索附近可选店铺，返回前3家；当用户要求在某家特定店附近寻找其他店时优先使用")
    public ShopSearchAdvancedResult searchAroundShop(Long referenceShopId,
                                                     String targetShopType,
                                                     String budget) {
        Long resolvedReferenceShopId = referenceShopId != null ? referenceShopId : 1001L;
        ShopEntity referenceShop = shopRepository.findDetailById(resolvedReferenceShopId)
                .orElseThrow(() -> new IllegalArgumentException("基准店铺不存在: " + resolvedReferenceShopId));
        String area = referenceShop.getArea();
        BudgetRange budgetRange = parseBudgetRange(budget);
        List<AdvancedShopItem> shops = shopRepository.searchAdvanced(
                        normalizeText(area),
                        normalizeText(targetShopType),
                        null,
                        null,
                        budgetRange.minPrice(),
                        budgetRange.maxPrice())
                .stream()
                .filter(shop -> !resolvedReferenceShopId.equals(shop.getId()))
                .limit(3)
                .map(this::toAdvancedShopItem)
                .toList();
        return new ShopSearchAdvancedResult(
                valueOrDefault(area, "西湖"),
                valueOrDefault(budget, "50-100"),
                valueOrDefault(targetShopType, "不限"),
                null,
                null,
                shops
        );
    }

    @Tool(description = "根据店铺ID查询营业时间、特色项目和用户评价摘要，帮助大模型做进一步推荐解释；优先使用搜索结果里返回的 shopId 发起查询")
    public ShopDetailResult shopDetail(Long shopId) {
        Long resolvedId = shopId != null ? shopId : 1001L;
        ShopEntity shop = shopRepository.findDetailById(resolvedId)
                .orElseThrow(() -> new IllegalArgumentException("店铺不存在: " + resolvedId));
        ShopDetailEntity detail = shop.getDetail();
        return new ShopDetailResult(
                shop.getId(),
                shop.getShopName(),
                shop.getBusinessHours(),
                splitCsv(detail != null ? detail.getSignatureDishes() : null),
                detail != null ? valueOrDefault(detail.getReviewSummary(), "暂无评价摘要") : "暂无评价摘要"
        );
    }

    @Tool(description = "根据店铺ID查询当前可用优惠券，返回店铺名称、优惠内容、适用范围和有效期；优先使用搜索结果里返回的 shopId 发起查询")
    public VoucherListResult voucherList(Long shopId) {
        Long resolvedId = shopId != null ? shopId : 1001L;
        ShopEntity shop = shopRepository.findDetailById(resolvedId)
                .orElseThrow(() -> new IllegalArgumentException("店铺不存在: " + resolvedId));
        List<VoucherInfo> vouchers = voucherRepository.findByShopIdOrderByValidToAsc(resolvedId).stream()
                .map(this::toVoucherInfo)
                .toList();
        return new VoucherListResult(shop.getId(), shop.getShopName(), vouchers);
    }

    private ShopCandidate toShopCandidate(ShopEntity shop) {
        return new ShopCandidate(
                shop.getId(),
                shop.getShopName(),
                shop.getArea(),
                shop.getAveragePrice(),
                shop.getRating(),
                shop.getOpenNow(),
                buildDisplayTags(shop)
        );
    }

    private AdvancedShopItem toAdvancedShopItem(ShopEntity shop) {
        ShopDetailEntity detail = shop.getDetail();
        ShopTypeEntity type = shop.getShopType();
        String typeName = type != null ? valueOrDefault(type.getTypeName(), "店铺") : "店铺";
        String baseReason = detail != null ? valueOrDefault(detail.getReviewSummary(), "适合家庭场景") : "适合家庭场景";
        return new AdvancedShopItem(
                shop.getId(),
                shop.getShopName(),
                shop.getArea(),
                shop.getAveragePrice(),
                shop.getRating(),
                shop.getSuitableForElderly(),
                shop.getHasChildrenPlayArea(),
                typeName + "类型，" + baseReason
        );
    }

    private VoucherInfo toVoucherInfo(VoucherEntity voucher) {
        return new VoucherInfo(
                voucher.getVoucherCode(),
                voucher.getTitle(),
                voucher.getApplicableScope(),
                voucher.getValidTo().format(DATE_TIME_FORMATTER)
        );
    }

    private List<String> buildDisplayTags(ShopEntity shop) {
        List<String> tags = new java.util.ArrayList<>();
        if (shop.getShopType() != null && shop.getShopType().getTypeName() != null && !shop.getShopType().getTypeName().isBlank()) {
            tags.add(shop.getShopType().getTypeName());
        }
        tags.addAll(splitTagText(shop.getTags()));
        return tags.stream().distinct().toList();
    }

    private BudgetRange parseBudgetRange(String budgetRange) {
        if (budgetRange == null || budgetRange.isBlank()) {
            return new BudgetRange(null, null);
        }
        List<Integer> values = NUMBER_PATTERN.matcher(budgetRange)
                .results()
                .map(matchResult -> matchResult.group())
                .map(Integer::parseInt)
                .toList();
        if (values.isEmpty()) {
            return new BudgetRange(null, null);
        }
        if (values.size() == 1) {
            Integer value = values.get(0);
            if (budgetRange.contains("以上") || budgetRange.contains("不少于")) {
                return new BudgetRange(value, null);
            }
            return new BudgetRange(null, value);
        }
        int min = Math.min(values.get(0), values.get(1));
        int max = Math.max(values.get(0), values.get(1));
        return new BudgetRange(min, max);
    }

    private List<String> splitTagText(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split("、|,"))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private List<String> splitCsv(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(",|，"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record BudgetRange(Integer minPrice, Integer maxPrice) {
    }

    public record ShopCandidate(Long id,
                                String name,
                                String area,
                                Integer averagePrice,
                                Double score,
                                Boolean openNow,
                                List<String> tags) {
    }

    public record ShopSearchAdvancedResult(String area,
                                           String averageBudgetRange,
                                           String shopType,
                                           Boolean suitableForElderly,
                                           Boolean hasChildrenPlayArea,
                                           List<AdvancedShopItem> shops) {
    }

    public record AdvancedShopItem(Long shopId,
                                   String shopName,
                                   String area,
                                   Integer averagePrice,
                                   Double rating,
                                   Boolean suitableForElderly,
                                   Boolean hasChildrenPlayArea,
                                   String recommendationReason) {
    }

    public record ShopDetailResult(Long shopId,
                                   String shopName,
                                   String businessHours,
                                   List<String> signatureDishes,
                                   String reviewSummary) {
    }

    public record VoucherListResult(Long shopId,
                                    String shopName,
                                    List<VoucherInfo> vouchers) {
    }

    public record VoucherInfo(String voucherId,
                              String title,
                              String applicableScope,
                              String validPeriod) {
    }
}
