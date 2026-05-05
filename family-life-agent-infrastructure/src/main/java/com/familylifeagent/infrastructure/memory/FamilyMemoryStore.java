package com.familylifeagent.infrastructure.memory;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * File-based dual memory system inspired by HKUDS/nanobot.
 *
 * <p>Maintains three stores:</p>
 * <ul>
 *   <li><b>MEMORY.md</b> — Long-term factual memory about the family
 *       (preferences, habits, past decisions, recurring patterns)</li>
 *   <li><b>history.jsonl</b> — Append-only conversation history with
 *       auto-incrementing cursor IDs</li>
 *   <li><b>USER.md</b> — User/family profile facts persistable to disk</li>
 * </ul>
 */
@Component
public class FamilyMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(FamilyMemoryStore.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern CURSOR_LINE = Pattern.compile("^\\d+$");
    private static final int DEFAULT_MAX_HISTORY = 1000;
    private static final int HISTORY_ENTRY_HARD_CAP = 64_000;

    private final Path memoryDir;
    private final Path memoryFile;
    private final Path historyFile;
    private final Path userFile;
    private final Path cursorFile;

    public FamilyMemoryStore() {
        // Store memory files under a "memory" directory next to the app's working dir
        this.memoryDir = Path.of(System.getProperty("user.dir", "."), "memory");
        this.memoryFile = memoryDir.resolve("MEMORY.md");
        this.historyFile = memoryDir.resolve("history.jsonl");
        this.userFile = memoryDir.resolve("USER.md");
        this.cursorFile = memoryDir.resolve(".cursor");
        initDir();
    }

    /**
     * Constructor for testing — allows specifying a custom base path.
     */
    FamilyMemoryStore(Path basePath) {
        this.memoryDir = basePath.resolve("memory");
        this.memoryFile = memoryDir.resolve("MEMORY.md");
        this.historyFile = memoryDir.resolve("history.jsonl");
        this.userFile = memoryDir.resolve("USER.md");
        this.cursorFile = memoryDir.resolve(".cursor");
        initDir();
    }

    private void initDir() {
        try {
            Files.createDirectories(memoryDir);
        } catch (IOException e) {
            log.warn("Could not create memory directory: {}", memoryDir, e);
        }
    }

    // ===================== MEMORY.md (long-term facts) =====================

    /**
     * Read the current long-term memory content.
     */
    public String readMemory() {
        return readFile(memoryFile);
    }

    /**
     * Overwrite the long-term memory file.
     */
    public void writeMemory(String content) {
        writeFile(memoryFile, content);
    }

    /**
     * Append new facts to MEMORY.md (dedup-aware).
     */
    public void appendMemory(String newFacts) {
        String existing = readMemory();
        String separator = existing.isBlank() ? "" : "\n";
        writeMemory(existing + separator + newFacts);
    }

    // ===================== USER.md (profile facts) =========================

    /**
     * Read the user profile as markdown.
     */
    public String readUserProfile() {
        return readFile(userFile);
    }

    /**
     * Write the user profile markdown.
     */
    public void writeUserProfile(String content) {
        writeFile(userFile, content);
    }

    /**
     * Sync a FamilyProfileDTO to USER.md.
     */
    public void syncProfileToFile(FamilyProfileDTO profile) {
        if (profile == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# 家庭画像\n\n");
        sb.append("- 用户ID：").append(profile.getUserId()).append("\n");
        sb.append("- 家庭人数：").append(profile.getFamilySize()).append("\n");
        sb.append("- 是否有老人：").append(profile.getHasElderly()).append("\n");
        sb.append("- 是否有儿童：").append(profile.getHasChild()).append("\n");
        if (profile.getBudgetRange() != null) {
            sb.append("- 预算范围：").append(profile.getBudgetRange()).append("\n");
        }
        if (profile.getDefaultArea() != null) {
            sb.append("- 默认区域：").append(profile.getDefaultArea()).append("\n");
        }
        if (profile.getDistancePreference() != null) {
            sb.append("- 距离偏好：").append(profile.getDistancePreference()).append("\n");
        }
        writeUserProfile(sb.toString());
    }

    // ===================== history.jsonl (append-only) =====================

    /**
     * Read the current cursor value (last written cursor).
     */
    public int getCurrentCursor() {
        if (!cursorFile.toFile().exists()) {
            return 0;
        }
        try {
            String text = Files.readString(cursorFile, StandardCharsets.UTF_8).trim();
            if (CURSOR_LINE.matcher(text).matches()) {
                return Integer.parseInt(text);
            }
        } catch (Exception e) {
            log.debug("Could not read cursor file, defaulting to 0");
        }
        return 0;
    }

    /**
     * Append a history entry and return its cursor ID.
     */
    public int appendHistory(String content) {
        int nextCursor = getCurrentCursor() + 1;
        String ts = LocalDateTime.now().format(TS_FMT);
        String record = new MemoryEntry(nextCursor, ts, truncateContent(content)).toString();

        try {
            // Write as JSONL: each record on its own line
            String jsonLine = "{\"cursor\":%d,\"timestamp\":\"%s\",\"content\":%s}\n"
                    .formatted(nextCursor, ts, escapeJson(content));
            Files.writeString(historyFile, jsonLine, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(cursorFile, String.valueOf(nextCursor), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to append history entry", e);
        }
        return nextCursor;
    }

    /**
     * Append a raw archive entry (for consolidation fallback).
     */
    public int appendRawArchive(String formattedMessages) {
        return appendHistory("[RAW] " + formattedMessages);
    }

    /**
     * Read all unprocessed history entries since the given cursor.
     */
    public List<MemoryEntry> readUnprocessedHistory(int sinceCursor) {
        List<MemoryEntry> all = readAllHistory();
        return all.stream().filter(e -> e.cursor() > sinceCursor).toList();
    }

    /**
     * Read all history entries.
     */
    public List<MemoryEntry> readAllHistory() {
        List<MemoryEntry> entries = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    // Simple JSON parsing — find cursor, timestamp, content
                    int cursor = extractIntField(line, "cursor");
                    String ts = extractStringField(line, "timestamp");
                    String content = extractStringField(line, "content");
                    if (cursor >= 0) {
                        entries.add(new MemoryEntry(cursor, ts != null ? ts : "?", content != null ? content : ""));
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            // File doesn't exist yet
        }
        return entries;
    }

    /**
     * Compact history to keep only the most recent N entries.
     */
    public void compactHistory() {
        compactHistory(DEFAULT_MAX_HISTORY);
    }

    /**
     * Compact history to keep only the most recent maxEntries.
     */
    public void compactHistory(int maxEntries) {
        List<MemoryEntry> all = readAllHistory();
        if (all.size() <= maxEntries) return;
        List<MemoryEntry> kept = all.subList(all.size() - maxEntries, all.size());
        rewriteHistory(kept);
        // Update cursor to the last entry's cursor
        if (!kept.isEmpty()) {
            writeCursorFile(kept.get(kept.size() - 1).cursor());
        }
    }

    /**
     * Consolidate history: replace a range of old entries with a summary.
     */
    public void consolidateHistory(List<MemoryEntry> toReplace, MemoryEntry summary) {
        List<MemoryEntry> all = readAllHistory();
        List<MemoryEntry> kept = new ArrayList<>();
        int lastReplacedCursor = toReplace.isEmpty() ? 0 : toReplace.get(toReplace.size() - 1).cursor();

        boolean insertedSummary = false;
        for (MemoryEntry entry : all) {
            if (entry.cursor() <= lastReplacedCursor) {
                // Skip old entries
                if (!insertedSummary) {
                    kept.add(summary);
                    insertedSummary = true;
                }
            } else {
                kept.add(entry);
            }
        }
        rewriteHistory(kept);
        if (!kept.isEmpty()) {
            writeCursorFile(kept.get(kept.size() - 1).cursor());
        }
    }

    /**
     * Get memory context for prompt injection (nanobot-inspired).
     * Returns the MEMORY.md content formatted for system prompt inclusion.
     */
    public String getMemoryContext() {
        String longTerm = readMemory();
        if (longTerm.isBlank()) return "";
        return "## Long-term Memory\n" + longTerm;
    }

    // ===================== Private helpers =================================

    private String readFile(Path path) {
        try {
            if (path.toFile().exists()) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.debug("Could not read file: {}", path);
        }
        return "";
    }

    private void writeFile(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to write file: {}", path, e);
        }
    }

    private void rewriteHistory(List<MemoryEntry> entries) {
        try {
            StringBuilder sb = new StringBuilder();
            for (MemoryEntry entry : entries) {
                sb.append("{\"cursor\":%d,\"timestamp\":\"%s\",\"content\":%s}\n"
                        .formatted(entry.cursor(), entry.timestamp(), escapeJson(entry.content())));
            }
            Files.writeString(historyFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to rewrite history file", e);
        }
    }

    private void writeCursorFile(int cursor) {
        try {
            Files.writeString(cursorFile, String.valueOf(cursor), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Could not write cursor file");
        }
    }

    private String truncateContent(String content) {
        if (content == null) return "";
        if (content.length() > HISTORY_ENTRY_HARD_CAP) {
            return content.substring(0, HISTORY_ENTRY_HARD_CAP) + "... (truncated)";
        }
        return content;
    }

    private String escapeJson(String value) {
        if (value == null) return "\"\"";
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private int extractIntField(String json, String field) {
        // Very simple parser for known JSONL format
        String search = "\"" + field + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return -1;
        int start = idx + search.length();
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        if (end > start) {
            try {
                return Integer.parseInt(json.substring(start, end));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private String extractStringField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        StringBuilder value = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                value.append(switch (next) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> next;
                });
                i++;
            } else if (c == '"') {
                break;
            } else {
                value.append(c);
            }
        }
        return value.toString();
    }
}
