package com.familylifeagent.infrastructure.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ShopSearchTool {

    @Tool(description = "根据用户的家庭聚餐需求搜索适合的店铺，返回带有标签和简单说明的候选店铺列表")
    public List<ShopCandidate> shopSearch(String keyword) {
        List<ShopCandidate> shops = new ArrayList<>();
        shops.add(new ShopCandidate(1L, "西湖清汤面馆", "西湖", 68, 4.7, true, List.of("清淡", "面食", "老人友好", "儿童友好")));
        shops.add(new ShopCandidate(2L, "湖畔家常小馆", "西湖", 88, 4.6, true, List.of("家常菜", "安静", "适合家庭聚餐")));
        shops.add(new ShopCandidate(3L, "邻里蒸鲜坊", "西湖", 96, 4.8, true, List.of("蒸菜", "低油少辣", "老人友好")));
        if (keyword == null || keyword.isBlank()) {
            return shops;
        }
        String normalized = keyword.toLowerCase();
        return shops.stream()
                .filter(shop -> shop.name().toLowerCase().contains(normalized)
                        || shop.area().toLowerCase().contains(normalized)
                        || shop.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(normalized)))
                .toList();
    }

    public record ShopCandidate(Long id,
                                String name,
                                String area,
                                Integer averagePrice,
                                Double score,
                                Boolean openNow,
                                List<String> tags) {
    }
}
