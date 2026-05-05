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

    private Map<String, Object> currentSlots;

    private List<TaskDTO> pendingTasks;

    private List<Long> candidateShopIds;

    private Map<Long, String> candidateShopMap;

    private Long lastRecommendShopId;

    private String ragHint;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDTO {
        private String taskId;
        private String shopType;
        private String taskName;
        private String status;
    }
}
