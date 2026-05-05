package com.familylifeagent.infrastructure.memory;

/**
 * A single entry in the append-only history.jsonl memory store.
 * Each entry has an auto-incrementing cursor, a timestamp, and content.
 */
public record MemoryEntry(int cursor, String timestamp, String content) {
}
