package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.api.dto.SessionContextDTO;
import com.familylifeagent.infrastructure.memory.FamilyMemoryStore;
import com.familylifeagent.infrastructure.memory.MemoryConsolidator;
import com.familylifeagent.infrastructure.memory.MemoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Domain service that integrates the dual memory system
 * (inspired by HKUDS/nanobot) into the family agent.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Record user messages and agent responses to history.jsonl</li>
 *   <li>Sync family profile changes to USER.md</li>
 *   <li>Provide memory context for prompt injection</li>
 *   <li>Trigger consolidation when history grows too large</li>
 * </ul>
 */
@Service
public class FamilyMemoryService {

    private static final Logger log = LoggerFactory.getLogger(FamilyMemoryService.class);

    private final FamilyMemoryStore memoryStore;
    private final MemoryConsolidator consolidator;

    public FamilyMemoryService(FamilyMemoryStore memoryStore, MemoryConsolidator consolidator) {
        this.memoryStore = memoryStore;
        this.consolidator = consolidator;
    }

    /**
     * Record a user message to history.
     */
    public int recordUserMessage(String message, String sessionId) {
        if (message == null || message.isBlank()) return -1;
        String entry = "[session:" + sessionId + "] USER: " + message;
        int cursor = memoryStore.appendHistory(entry);
        maybeConsolidate();
        return cursor;
    }

    /**
     * Record an agent response to history.
     */
    public int recordAgentResponse(String response, String sessionId) {
        if (response == null || response.isBlank()) return -1;
        String truncated = response.length() > 500 ? response.substring(0, 500) + "..." : response;
        String entry = "[session:" + sessionId + "] ASSISTANT: " + truncated;
        int cursor = memoryStore.appendHistory(entry);
        maybeConsolidate();
        return cursor;
    }

    /**
     * Record a tool call result to history.
     */
    public int recordToolCall(String toolName, String summary, String sessionId) {
        if (toolName == null) return -1;
        String entry = "[session:" + sessionId + "] TOOL[" + toolName + "]: " +
                (summary != null ? summary : "");
        int cursor = memoryStore.appendHistory(entry);
        maybeConsolidate();
        return cursor;
    }

    /**
     * Sync family profile to USER.md.
     */
    public void syncProfile(FamilyProfileDTO profile) {
        if (profile == null) return;
        memoryStore.syncProfileToFile(profile);
        log.info("Synced family profile (userId={}) to USER.md", profile.getUserId());
    }

    /**
     * Sync session context to history for multi-turn awareness.
     */
    public void syncSessionToHistory(SessionContextDTO context) {
        if (context == null) return;
        Map<String, Object> slots = context.getSlots();
        if (slots != null && !slots.isEmpty()) {
            String slotSummary = "SLOTS: " + slots.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none");
            memoryStore.appendHistory("[session:" + context.getSessionId() + "] " + slotSummary);
        }
    }

    /**
     * Inject memory context into the prompt with hard caps per section.
     * Progressive disclosure: only inject concise summaries, not full files.
     */
    public String getMemoryPromptContext() {
        StringBuilder ctx = new StringBuilder();

        // Long-term memory (MEMORY.md) — cap to last ~5000 chars
        String longTerm = memoryStore.readMemory();
        if (!longTerm.isBlank()) {
            String trimmed = longTerm.length() > 5000
                    ? "..." + longTerm.substring(longTerm.length() - 5000)
                    : longTerm;
            ctx.append("## 长期记忆\n").append(trimmed).append("\n\n");
        }

        // User profile (USER.md) — cap to 500 chars
        String userProfile = memoryStore.readUserProfile();
        if (!userProfile.isBlank()) {
            String trimmed = userProfile.length() > 500
                    ? userProfile.substring(0, 500) + "..."
                    : userProfile;
            ctx.append("## 用户偏好\n").append(trimmed).append("\n\n");
        }

        // Recent history — last 20 entries, each capped at 2000 chars
        List<MemoryEntry> history = memoryStore.readAllHistory();
        if (!history.isEmpty()) {
            int start = Math.max(0, history.size() - 20);
            ctx.append("## 近期交互\n");
            for (int i = start; i < history.size(); i++) {
                MemoryEntry entry = history.get(i);
                String preview = entry.content();
                if (preview != null && preview.length() > 2000) {
                    preview = preview.substring(0, 2000) + "...";
                }
                ctx.append("- ").append(preview).append("\n");
            }
        }

        // Hard total cap
        String result = ctx.toString();
        if (result.length() > 5000) {
            result = result.substring(0, 5000) + "...";
        }
        return result;
    }

    /**
     * Get memory context for prompt template injection.
     */
    public String getMemoryHint() {
        String ctx = getMemoryPromptContext();
        if (ctx.isBlank()) return "无";
        return ctx.length() > 500 ? ctx.substring(0, 500) + "..." : ctx;
    }

    /**
     * Extract insights from history to long-term memory.
     * (Lightweight version of nanobot's Dream Phase 1)
     */
    public void extractInsights() {
        consolidator.extractInsightsToMemory();
    }

    /**
     * Read all history entries.
     */
    public List<MemoryEntry> readHistory() {
        return memoryStore.readAllHistory();
    }

    /**
     * Read current long-term memory.
     */
    public String readMemory() {
        return memoryStore.readMemory();
    }

    /**
     * Write to long-term memory.
     */
    public void writeMemory(String content) {
        memoryStore.writeMemory(content);
    }

    /**
     * Read USER.md content.
     */
    public String readUserProfile() {
        return memoryStore.readUserProfile();
    }

    private void maybeConsolidate() {
        try {
            consolidator.maybeConsolidate();
        } catch (Exception e) {
            log.warn("Memory consolidation check failed", e);
        }
    }
}
