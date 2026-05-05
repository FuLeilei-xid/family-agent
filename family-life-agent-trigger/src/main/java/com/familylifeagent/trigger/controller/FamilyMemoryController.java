package com.familylifeagent.trigger.controller;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.domain.service.FamilyMemoryService;
import com.familylifeagent.domain.service.FamilyProfileService;
import com.familylifeagent.infrastructure.memory.MemoryEntry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the dual memory system (inspired by HKUDS/nanobot).
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET  /api/family/memory - Read long-term memory (MEMORY.md)</li>
 *   <li>POST /api/family/memory - Write to long-term memory</li>
 *   <li>GET  /api/family/memory/history - Read conversation history (history.jsonl)</li>
 *   <li>GET  /api/family/memory/profile - Read profile memory (USER.md)</li>
 *   <li>POST /api/family/memory/sync-profile - Sync profile to memory</li>
 *   <li>POST /api/family/memory/extract-insights - Extract insights to MEMORY.md</li>
 *   <li>GET  /api/family/memory/context - Get formatted memory context for prompts</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/family/memory")
public class FamilyMemoryController {

    private final FamilyMemoryService familyMemoryService;
    private final FamilyProfileService familyProfileService;

    public FamilyMemoryController(FamilyMemoryService familyMemoryService,
                                  FamilyProfileService familyProfileService) {
        this.familyMemoryService = familyMemoryService;
        this.familyProfileService = familyProfileService;
    }

    // ===================== Long-term Memory (MEMORY.md) =====================

    @GetMapping
    public Map<String, Object> readMemory() {
        String content = familyMemoryService.readMemory();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "long_term_memory");
        result.put("file", "MEMORY.md");
        result.put("content", content.isBlank() ? "(empty)" : content);
        result.put("length", content.length());
        return result;
    }

    @PostMapping
    public Map<String, Object> writeMemory(@RequestBody Map<String, String> request) {
        String content = request.getOrDefault("content", "");
        familyMemoryService.writeMemory(content);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "长期记忆已更新");
        result.put("length", content.length());
        return result;
    }

    // ===================== Conversation History (history.jsonl) =============

    @GetMapping("/history")
    public Map<String, Object> readHistory(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<MemoryEntry> allHistory = familyMemoryService.readHistory();
        List<MemoryEntry> recent = allHistory.subList(
                Math.max(0, allHistory.size() - limit),
                allHistory.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "conversation_history");
        result.put("file", "history.jsonl");
        result.put("totalEntries", allHistory.size());
        result.put("returned", recent.size());
        result.put("entries", recent);
        return result;
    }

    // ===================== Profile Memory (USER.md) ========================

    @GetMapping("/profile")
    public Map<String, Object> readProfileMemory() {
        String content = familyMemoryService.readUserProfile();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "user_profile_memory");
        result.put("file", "USER.md");
        result.put("content", content.isBlank() ? "(empty)" : content);
        return result;
    }

    @PostMapping("/sync-profile")
    public Map<String, Object> syncProfile(@RequestBody(required = false) FamilyProfileDTO profile) {
        FamilyProfileDTO profileToSync = profile != null
                ? profile
                : familyProfileService.queryOrDefault(1001L);
        familyMemoryService.syncProfile(profileToSync);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "家庭画像已同步到记忆文件");
        result.put("userId", profileToSync.getUserId());
        return result;
    }

    // ===================== Insight Extraction ==============================

    @PostMapping("/extract-insights")
    public Map<String, Object> extractInsights() {
        familyMemoryService.extractInsights();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "会话洞察已提取到 MEMORY.md");
        return result;
    }

    // ===================== Memory Context ==================================

    @GetMapping("/context")
    public Map<String, Object> getMemoryContext() {
        String context = familyMemoryService.getMemoryHint();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "memory_context");
        result.put("context", context);
        return result;
    }
}
