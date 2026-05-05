package com.familylifeagent.infrastructure.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryConsolidator.
 */
class MemoryConsolidatorTest {

    private FamilyMemoryStore store;
    private MemoryConsolidator consolidator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        store = new FamilyMemoryStore(tempDir);
        consolidator = new MemoryConsolidator(store);
    }

    @Test
    void testNoConsolidationWhenBelowThreshold() {
        // Add fewer entries than the consolidation threshold
        for (int i = 0; i < 10; i++) {
            store.appendHistory("entry " + (i + 1));
        }
        boolean didConsolidate = consolidator.maybeConsolidate();
        assertFalse(didConsolidate, "Should not consolidate below threshold");
        assertEquals(10, store.readAllHistory().size());
    }

    @Test
    void testExtractInsightsToMemory() {
        store.appendHistory("用户: 想找一家适合老人的餐厅");
        store.appendHistory("用户: 在西湖附近，预算100以内");
        store.appendHistory("TOOL[shopSearchAdvanced]: 搜索区域=西湖 类型=餐厅 结果数=3");

        consolidator.extractInsightsToMemory();
        String memory = store.readMemory();
        assertNotNull(memory);
        assertTrue(memory.contains("会话洞察") || memory.contains("历史记录"));
    }

    @Test
    void testMultipleAppendsMaintainOrder() {
        store.appendHistory("first");
        store.appendHistory("second");
        store.appendHistory("third");

        List<MemoryEntry> all = store.readAllHistory();
        assertEquals(3, all.size());
        assertTrue(all.get(0).content().contains("first"));
        assertTrue(all.get(1).content().contains("second"));
        assertTrue(all.get(2).content().contains("third"));
    }
}
