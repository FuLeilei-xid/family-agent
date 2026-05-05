package com.familylifeagent.infrastructure.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lightweight memory consolidator inspired by nanobot's Consolidator.
 *
 * <p>When the history file grows beyond a threshold, this component
 * compacts it by replacing older entries with a summarization hint,
 * keeping only the most recent N entries plus one consolidated entry.</p>
 */
@Component
public class MemoryConsolidator {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidator.class);

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_HISTORY_BEFORE_CONSOLIDATION = 500;
    private static final int KEEP_AFTER_CONSOLIDATION = 200;

    private final FamilyMemoryStore store;

    public MemoryConsolidator(FamilyMemoryStore store) {
        this.store = store;
    }

    /**
     * Check if consolidation is needed and perform it.
     * Returns true if consolidation happened.
     */
    public boolean maybeConsolidate() {
        List<MemoryEntry> all = store.readAllHistory();
        if (all.size() <= MAX_HISTORY_BEFORE_CONSOLIDATION) {
            return false;
        }

        log.info("History file has {} entries, consolidating...", all.size());

        // Keep the most recent KEEP_AFTER_CONSOLIDATION entries
        List<MemoryEntry> toKeep = all.subList(
                Math.max(0, all.size() - KEEP_AFTER_CONSOLIDATION),
                all.size());
        List<MemoryEntry> toRemove = all.subList(0,
                Math.max(0, all.size() - KEEP_AFTER_CONSOLIDATION));

        // Build a consolidated summary
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("[Consolidated History - ")
                .append(LocalDateTime.now().format(TS_FMT))
                .append("]\n");
        summaryBuilder.append("Earlier conversation entries (").append(toRemove.size())
                .append(" entries) have been consolidated.\n");
        summaryBuilder.append("Summary of past interactions:\n");

        // Extract key patterns from removed entries
        int recommendationCount = 0;
        int detailQueryCount = 0;
        int voucherQueryCount = 0;
        for (MemoryEntry entry : toRemove) {
            String content = entry.content();
            if (content == null) continue;
            if (content.contains("推荐") || content.contains("找") || content.contains("search"))
                recommendationCount++;
            if (content.contains("详情") || content.contains("detail") || content.contains("Detail"))
                detailQueryCount++;
            if (content.contains("优惠") || content.contains("券") || content.contains("voucher"))
                voucherQueryCount++;
        }
        summaryBuilder.append("- 推荐/搜索交互：约 ").append(recommendationCount).append(" 次\n");
        summaryBuilder.append("- 详情查询：约 ").append(detailQueryCount).append(" 次\n");
        summaryBuilder.append("- 优惠查询：约 ").append(voucherQueryCount).append(" 次\n");
        summaryBuilder.append("- 被合并条目数：").append(toRemove.size()).append("\n");

        // Create summary entry with cursor matching the last removed entry
        int summaryCursor = toRemove.isEmpty() ? 0 : toRemove.get(toRemove.size() - 1).cursor();
        MemoryEntry summary = new MemoryEntry(
                summaryCursor,
                LocalDateTime.now().format(TS_FMT),
                summaryBuilder.toString().trim()
        );

        // Replace old entries with the summary, keep recent ones
        List<MemoryEntry> newHistory = new java.util.ArrayList<>();
        newHistory.add(summary);
        newHistory.addAll(toKeep);

        // Rewrite the history file
        store.compactHistory(KEEP_AFTER_CONSOLIDATION);

        log.info("Consolidation complete: {} entries kept (+ 1 consolidated)", toKeep.size());
        return true;
    }

    /**
     * Extract semantic insights from recent history and write to MEMORY.md.
     * This is a lightweight version of nanobot's Dream Phase 1.
     */
    public void extractInsightsToMemory() {
        List<MemoryEntry> recentHistory = store.readAllHistory();
        if (recentHistory.isEmpty()) return;

        // Read existing memory
        String existingMemory = store.readMemory();

        // Build a simple summary of recurring patterns
        StringBuilder insights = new StringBuilder();
        if (!existingMemory.isBlank()) {
            insights.append(existingMemory);
            if (!existingMemory.endsWith("\n")) insights.append("\n");
        }

        insights.append("\n## 会话洞察摘要 (")
                .append(LocalDateTime.now().format(TS_FMT))
                .append(")\n");
        insights.append("基于 ").append(recentHistory.size()).append(" 条历史记录\n");

        // Analyze intent distribution
        long shopTypeChanges = recentHistory.stream()
                .filter(e -> e.content() != null && e.content().contains("shopType"))
                .count();
        long areaChanges = recentHistory.stream()
                .filter(e -> e.content() != null && e.content().contains("area"))
                .count();
        long budgetChanges = recentHistory.stream()
                .filter(e -> e.content() != null && (e.content().contains("预算") || e.content().contains("budget")))
                .count();

        if (shopTypeChanges > 0)
            insights.append("- 店铺类型变更次数：").append(shopTypeChanges).append("\n");
        if (areaChanges > 0)
            insights.append("- 区域变更次数：").append(areaChanges).append("\n");
        if (budgetChanges > 0)
            insights.append("- 预算调整次数：").append(budgetChanges).append("\n");

        store.writeMemory(insights.toString().trim());
        log.info("Extracted {} insight entries to MEMORY.md", recentHistory.size());
    }
}
