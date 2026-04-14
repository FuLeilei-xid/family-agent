package com.familylifeagent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContextDTO {

    private String sessionId;

    private String currentIntent;

    private Map<String, Object> slots;

    private List<Long> candidateShopIds;

    private Long lastRecommendShopId;
}
