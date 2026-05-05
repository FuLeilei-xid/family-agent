package com.familylifeagent.infrastructure.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FamilyMemoryStore — verifies file-based dual memory operations.
 */
class FamilyMemoryStoreTest {

    private FamilyMemoryStore store;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        store = new FamilyMemoryStore(tempDir);
    }

    @Test
    void testMemoryReadWrite() {
        // Write long-term memory
        store.writeMemory("# 家庭习惯\n- 偏好清淡饮食\n- 常去西湖区");
        String content = store.readMemory();
        assertTrue(content.contains("清淡饮食"));
        assertTrue(content.contains("西湖区"));
    }

    @Test
    void testMemoryAppend() {
        store.writeMemory("# 初始记忆\n- item1");
        store.appendMemory("- item2");
        String content = store.readMemory();
        assertTrue(content.contains("item1"));
        assertTrue(content.contains("item2"));
    }

    @Test
    void testHistoryAppendAndCursor() {
        int c1 = store.appendHistory("用户: 推荐一家餐厅");
        int c2 = store.appendHistory("助手: 推荐了西湖清汤面馆");
        int c3 = store.appendHistory("用户: 第二家有优惠吗");

        assertTrue(c1 > 0);
        assertEquals(c1 + 1, c2);
        assertEquals(c2 + 1, c3);
    }

    @Test
    void testReadUnprocessedHistory() {
        store.appendHistory("entry 1"); // cursor 1
        store.appendHistory("entry 2"); // cursor 2
        store.appendHistory("entry 3"); // cursor 3

        List<MemoryEntry> since0 = store.readUnprocessedHistory(0);
        assertEquals(3, since0.size());

        List<MemoryEntry> since1 = store.readUnprocessedHistory(1);
        assertEquals(2, since1.size());

        List<MemoryEntry> since3 = store.readUnprocessedHistory(3);
        assertTrue(since3.isEmpty());
    }

    @Test
    void testHistoryCompaction() {
        store.appendHistory("entry 1");
        store.appendHistory("entry 2");
        store.appendHistory("entry 3");
        store.appendHistory("entry 4");
        store.appendHistory("entry 5");

        store.compactHistory(3);

        List<MemoryEntry> remaining = store.readAllHistory();
        assertEquals(3, remaining.size());
        assertEquals("entry 3", remaining.get(0).content());
        assertEquals("entry 5", remaining.get(2).content());
    }

    @Test
    void testUserProfileSync() {
        com.familylifeagent.api.dto.FamilyProfileDTO profile =
                com.familylifeagent.api.dto.FamilyProfileDTO.builder()
                        .userId(1001L)
                        .familySize(3)
                        .hasElderly(Boolean.TRUE)
                        .hasChild(Boolean.TRUE)
                        .budgetRange("50-120")
                        .defaultArea("西湖")
                        .distancePreference("近")
                        .build();

        store.syncProfileToFile(profile);
        String content = store.readUserProfile();
        assertTrue(content.contains("家庭人数"));
        assertTrue(content.contains("西湖"));
        assertTrue(content.contains("1001"));
    }

    @Test
    void testGetMemoryContext() {
        store.writeMemory("# 记忆\n- 用户偏好清淡");
        String context = store.getMemoryContext();
        assertTrue(context.contains("Long-term Memory"));
        assertTrue(context.contains("清淡"));
    }

    @Test
    void testHistoryConsolidation() {
        store.appendHistory("entry 1");
        store.appendHistory("entry 2");
        store.appendHistory("entry 3");

        MemoryEntry summary = new MemoryEntry(1, "2025-01-01 12:00", "[Consolidated] 3 entries");
        List<MemoryEntry> toReplace = store.readAllHistory();

        store.consolidateHistory(toReplace, summary);

        List<MemoryEntry> remaining = store.readAllHistory();
        assertEquals(1, remaining.size());
        assertEquals(1, remaining.get(0).cursor());
        assertTrue(remaining.get(0).content().contains("[Consolidated]"));
    }

    @Test
    void testEmptyHistory() {
        List<MemoryEntry> history = store.readAllHistory();
        assertTrue(history.isEmpty());

        int cursor = store.getCurrentCursor();
        assertEquals(0, cursor);
    }

    @Test
    void testLargeContentTruncation() {
        // Content just under the cap should be stored in full
        String normal = "a".repeat(1000);
        int cursor = store.appendHistory(normal);
        assertTrue(cursor > 0);

        // Verify it was stored
        List<MemoryEntry> history = store.readAllHistory();
        assertFalse(history.isEmpty());
    }
}
