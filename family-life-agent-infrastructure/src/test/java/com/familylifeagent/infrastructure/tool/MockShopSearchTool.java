package com.familylifeagent.infrastructure.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MockShopSearchTool {

    private static final List<ShopSearchTool.ShopCandidate> BASE_SHOPS = List.of(
            new ShopSearchTool.ShopCandidate(1L, "西湖清汤面馆", "西湖", 68, 4.7, true, List.of("清淡", "面食", "老人友好", "儿童友好")),
            new ShopSearchTool.ShopCandidate(2L, "湖畔家常小馆", "西湖", 88, 4.6, true, List.of("家常菜", "安静", "适合家庭聚餐")),
            new ShopSearchTool.ShopCandidate(3L, "邻里蒸鲜坊", "西湖", 96, 4.8, true, List.of("蒸菜", "低油少辣", "老人友好")),
            new ShopSearchTool.ShopCandidate(123L, "湖滨亲子欢乐餐厅", "西湖", 92, 4.9, true, List.of("亲子餐", "儿童游乐区", "家庭聚餐", "优惠力度大"))
    );

    private static final Map<Long, ShopSearchTool.ShopDetailResult> SHOP_DETAIL_MAP = Map.of(
            101L, new ShopSearchTool.ShopDetailResult(
                    101L,
                    "西湖邻里蒸鲜坊",
                    "10:30-21:00",
                    List.of("鲜虾蒸蛋", "南瓜蒸排骨", "菌菇蒸鸡"),
                    "蒸菜选择多，少油少辣，适合照顾老人饮食需求，整体评分较高。"
            ),
            102L, new ShopSearchTool.ShopDetailResult(
                    102L,
                    "湖滨亲子面小馆",
                    "10:00-21:30",
                    List.of("原味清汤牛肉面", "番茄鸡蛋面", "儿童玉米面"),
                    "整体口味温和，儿童餐选择多，亲子家庭反馈就餐体验轻松。"
            ),
            103L, new ShopSearchTool.ShopDetailResult(
                    103L,
                    "桂雨家常食堂",
                    "11:00-22:00",
                    List.of("山药排骨汤", "清蒸鲈鱼", "时蔬豆腐煲"),
                    "环境相对安静，适合家庭就餐，菜量稳定，周末晚餐需要稍微等位。"
            ),
            123L, new ShopSearchTool.ShopDetailResult(
                    123L,
                    "湖滨亲子欢乐餐厅",
                    "10:00-22:00",
                    List.of("奶油南瓜浓汤", "儿童星星意面", "鲜虾滑蛋饭"),
                    "儿童游乐区和亲子套餐评价较好，家庭客群多，周末晚餐人气较高。"
            )
    );

    private static final Map<Long, List<ShopSearchTool.VoucherInfo>> SHOP_VOUCHER_MAP = Map.of(
            101L, List.of(
                    new ShopSearchTool.VoucherInfo("V-1001", "满100减20", "堂食通用", "2026-04-30 22:00前可用"),
                    new ShopSearchTool.VoucherInfo("V-1002", "蒸菜家庭套餐减15元", "限家庭套餐", "2026-04-20 20:00前可用")
            ),
            102L, List.of(
                    new ShopSearchTool.VoucherInfo("V-2001", "满80减12", "堂食主食可用", "2026-05-05 22:00前可用"),
                    new ShopSearchTool.VoucherInfo("V-2002", "儿童套餐立减10元", "限儿童套餐", "2026-04-28 21:30前可用")
            ),
            103L, List.of(
                    new ShopSearchTool.VoucherInfo("V-3001", "满120减25", "全场菜品可用", "2026-04-26 21:00前可用"),
                    new ShopSearchTool.VoucherInfo("V-3002", "家庭双人餐88折", "指定套餐", "2026-04-18 20:30前可用")
            ),
            123L, List.of(
                    new ShopSearchTool.VoucherInfo("V-12301", "满150减35", "亲子套餐可用", "2026-05-08 22:00前可用"),
                    new ShopSearchTool.VoucherInfo("V-12302", "儿童餐第二份半价", "限儿童餐", "2026-04-25 21:00前可用")
            )
    );

    public List<ShopSearchTool.ShopCandidate> shopSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return BASE_SHOPS;
        }
        String normalized = keyword.toLowerCase();
        return BASE_SHOPS.stream()
                .filter(shop -> shop.name().toLowerCase().contains(normalized)
                        || shop.area().toLowerCase().contains(normalized)
                        || shop.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(normalized)))
                .toList();
    }

    public ShopSearchTool.ShopSearchAdvancedResult shopSearchAdvanced(String area,
                                                                      String averageBudgetRange,
                                                                      String shopType,
                                                                      Boolean suitableForElderly,
                                                                      Boolean hasChildrenPlayArea) {
        List<ShopSearchTool.AdvancedShopItem> shops = new ArrayList<>();
        shops.add(new ShopSearchTool.AdvancedShopItem(
                101L,
                "西湖邻里蒸鲜坊",
                "西湖",
                86,
                4.8,
                true,
                true,
                "蒸菜清淡、桌距宽松，适合带老人和孩子一起就餐"
        ));
        shops.add(new ShopSearchTool.AdvancedShopItem(
                102L,
                "湖滨亲子面小馆",
                "西湖",
                72,
                4.6,
                true,
                true,
                "面食选择多，儿童游乐角较受欢迎，晚餐时段出餐稳定"
        ));
        shops.add(new ShopSearchTool.AdvancedShopItem(
                103L,
                "桂雨家常食堂",
                "西湖",
                94,
                4.7,
                true,
                false,
                "家常菜偏清淡，包厢友好，适合预算中等的家庭聚餐"
        ));
        return new ShopSearchTool.ShopSearchAdvancedResult(
                valueOrDefault(area, "西湖"),
                valueOrDefault(averageBudgetRange, "50-100"),
                valueOrDefault(shopType, "不限"),
                suitableForElderly,
                hasChildrenPlayArea,
                shops
        );
    }

    public ShopSearchTool.ShopDetailResult shopDetail(Long shopId) {
        Long resolvedId = shopId != null ? shopId : 101L;
        return SHOP_DETAIL_MAP.getOrDefault(
                resolvedId,
                new ShopSearchTool.ShopDetailResult(
                        resolvedId,
                        resolveShopName(resolvedId),
                        "11:00-21:00",
                        List.of("招牌家常菜", "清汤时蔬", "儿童友好主食"),
                        "用户普遍反馈环境稳定，适合家庭场景，可作为兜底候选。"
                )
        );
    }

    public ShopSearchTool.VoucherListResult voucherList(Long shopId) {
        Long resolvedId = shopId != null ? shopId : 101L;
        List<ShopSearchTool.VoucherInfo> vouchers = SHOP_VOUCHER_MAP.getOrDefault(
                resolvedId,
                List.of(new ShopSearchTool.VoucherInfo("V-DEFAULT", "满100减10", "通用优惠", "2026-04-30 23:59前可用"))
        );
        return new ShopSearchTool.VoucherListResult(resolvedId, resolveShopName(resolvedId), vouchers);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String resolveShopName(Long shopId) {
        if (shopId == null) {
            return "默认家庭餐馆";
        }
        for (ShopSearchTool.ShopCandidate shop : BASE_SHOPS) {
            if (shopId.equals(shop.id())) {
                return shop.name();
            }
        }
        ShopSearchTool.ShopDetailResult detailResult = SHOP_DETAIL_MAP.get(shopId);
        if (detailResult != null) {
            return detailResult.shopName();
        }
        return "店铺" + shopId;
    }
}
