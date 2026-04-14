package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.SessionContextDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionContextService {

    private final Map<String, SessionContextDTO> sessionCache = new ConcurrentHashMap<>();

    public SessionContextDTO getOrCreate(String sessionId) {
        return sessionCache.computeIfAbsent(sessionId, key -> SessionContextDTO.builder()
                .sessionId(key)
                .currentIntent("restaurant_recommendation")
                .slots(new LinkedHashMap<>())
                .candidateShopIds(new ArrayList<>())
                .lastRecommendShopId(null)
                .build());
    }

    public SessionContextDTO updateForQuestion(String sessionId, String message) {
        SessionContextDTO context = getOrCreate(sessionId);
        Map<String, Object> slots = new LinkedHashMap<>(context.getSlots() != null ? context.getSlots() : Map.of());
        slots.putAll(extractSlots(message));
        context.setCurrentIntent(detectIntent(message));
        context.setSlots(slots);
        if (context.getCandidateShopIds() == null || context.getCandidateShopIds().isEmpty()) {
            context.setCandidateShopIds(new ArrayList<>(List.of(1L, 2L, 3L)));
            context.setLastRecommendShopId(1L);
        }
        sessionCache.put(sessionId, context);
        return context;
    }

    public SessionContextDTO refreshCandidates(String sessionId, List<Long> candidateShopIds) {
        SessionContextDTO context = getOrCreate(sessionId);
        context.setCandidateShopIds(new ArrayList<>(candidateShopIds));
        context.setLastRecommendShopId(candidateShopIds.isEmpty() ? null : candidateShopIds.get(0));
        sessionCache.put(sessionId, context);
        return context;
    }

    private String detectIntent(String message) {
        if (message == null || message.isBlank()) {
            return "restaurant_recommendation";
        }
        if (message.contains("优惠") || message.contains("券")) {
            return "voucher_query";
        }
        if (message.contains("详情") || message.contains("营业到几点") || message.contains("怎么样")) {
            return "shop_detail_query";
        }
        return "restaurant_recommendation";
    }

    private Map<String, Object> extractSlots(String message) {
        Map<String, Object> slots = new LinkedHashMap<>();
        if (message == null || message.isBlank()) {
            return slots;
        }
        if (message.contains("预算")) {
            slots.put("budget", extractBudgetText(message));
        }
        if (message.contains("一家三口")) {
            slots.put("familySize", 3);
        } else if (message.contains("两人") || message.contains("两口")) {
            slots.put("familySize", 2);
        }
        if (message.contains("老人")) {
            slots.put("hasElderly", true);
        }
        if (message.contains("孩子") || message.contains("小孩") || message.contains("儿童")) {
            slots.put("hasChild", true);
        }
        if (message.contains("西湖")) {
            slots.put("area", "西湖");
        }
        return slots;
    }

    private String extractBudgetText(String message) {
        int index = message.indexOf("预算");
        return index >= 0 ? message.substring(index).split("，|。|,|\\s")[0] : message;
    }
}
