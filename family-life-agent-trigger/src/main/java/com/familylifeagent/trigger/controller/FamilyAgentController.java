package com.familylifeagent.trigger.controller;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.api.dto.SessionContextDTO;
import com.familylifeagent.domain.service.FamilyMemoryService;
import com.familylifeagent.domain.service.FamilyProfileService;
import com.familylifeagent.domain.service.FamilyRecommendService;
import com.familylifeagent.domain.service.SessionContextService;
import com.familylifeagent.infrastructure.tool.ShopSearchTool;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/family")
public class FamilyAgentController {

    private static final Logger log = LoggerFactory.getLogger(FamilyAgentController.class);

    private static final String INTENT_SHOP_RECOMMENDATION = "shop_recommendation";
    private static final String INTENT_SHOP_SEARCH = "shop_search";

    private final FamilyRecommendService familyRecommendService;
    private final FamilyProfileService familyProfileService;
    private final SessionContextService sessionContextService;
    private final FamilyMemoryService familyMemoryService;

    public FamilyAgentController(FamilyRecommendService familyRecommendService,
                                 FamilyProfileService familyProfileService,
                                 SessionContextService sessionContextService,
                                 FamilyMemoryService familyMemoryService) {
        this.familyRecommendService = familyRecommendService;
        this.familyProfileService = familyProfileService;
        this.sessionContextService = sessionContextService;
        this.familyMemoryService = familyMemoryService;
    }

    @PostMapping("/profile/save")
    public Map<String, Object> saveProfile(@Valid @RequestBody FamilyProfileDTO familyProfileDTO) {
        // FamilyProfileService.save() now syncs to USER.md internally
        FamilyProfileDTO saved = familyProfileService.save(familyProfileDTO);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "家庭画像保存成功");
        result.put("data", saved);
        return result;
    }

    @GetMapping("/profile/query")
    public FamilyProfileDTO queryProfile(@RequestParam(value = "userId", required = false) Long userId) {
        return familyProfileService.queryOrDefault(userId != null ? userId : 1001L);
    }

    @PostMapping("/recommend/chat")
    public Map<String, Object> recommendChat(@RequestBody(required = false) Map<String, Object> request) {
        String message = request != null && request.get("message") != null
                ? String.valueOf(request.get("message"))
                : "请为一家三口推荐西湖附近适合老人和孩子的晚餐。";
        Long userId = safeParseLong(request, "userId", 1001L);
        String sessionId = request != null && request.get("sessionId") != null
                ? String.valueOf(request.get("sessionId"))
                : UUID.randomUUID().toString();

        // Record user message to memory
        familyMemoryService.recordUserMessage(message, sessionId);

        FamilyProfileDTO profile = familyProfileService.queryOrDefault(userId);
        SessionContextDTO context = sessionContextService.updateForQuestion(sessionId, message);

        // Sync session context to memory
        familyMemoryService.syncSessionToHistory(context);

        ShopSearchTool.ShopSearchAdvancedResult advancedResult = shouldRefreshCandidates(context)
                ? familyRecommendService.prepareAdvancedRecommendation(profile, context)
                : null;

        // Record tool call result to memory
        if (advancedResult != null) {
            int shopCount = advancedResult.shops() != null ? advancedResult.shops().size() : 0;
            familyMemoryService.recordToolCall(
                    "shopSearchAdvanced",
                    "搜索区域=" + advancedResult.area()
                            + " 类型=" + advancedResult.shopType()
                            + " 结果数=" + shopCount,
                    sessionId);
            context = sessionContextService.refreshCandidates(sessionId, advancedResult);
        }

        String reply = familyRecommendService.recommendOnce(message, profile, context, advancedResult);

        // Record agent response to memory
        familyMemoryService.recordAgentResponse(reply, sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("reply", reply);
        result.put("context", context);
        return result;
    }

    /** SSE 流式传输超时时间（5分钟），防止客户端断开后连接泄露 */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    @GetMapping(path = "/recommend/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter recommendChatStream(@RequestParam(value = "message", required = false) String message,
                                          @RequestParam(value = "userId", required = false) Long userId,
                                          @RequestParam(value = "sessionId", required = false) String sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String prompt = message != null && !message.isBlank()
                ? message
                : "今晚一家三口想在西湖附近吃饭，老人清淡，小孩想吃面，预算100元以内。";
        Long resolvedUserId = userId != null ? userId : 1001L;
        String resolvedSessionId = sessionId != null && !sessionId.isBlank() ? sessionId : UUID.randomUUID().toString();

        // Record user message to memory
        familyMemoryService.recordUserMessage(prompt, resolvedSessionId);

        FamilyProfileDTO profile = familyProfileService.queryOrDefault(resolvedUserId);
        SessionContextDTO context = sessionContextService.updateForQuestion(resolvedSessionId, prompt);

        // Sync session context to memory
        familyMemoryService.syncSessionToHistory(context);

        ShopSearchTool.ShopSearchAdvancedResult advancedResult = shouldRefreshCandidates(context)
                ? familyRecommendService.prepareAdvancedRecommendation(profile, context)
                : null;

        // Record tool call result to memory
        if (advancedResult != null) {
            int shopCount = advancedResult.shops() != null ? advancedResult.shops().size() : 0;
            familyMemoryService.recordToolCall(
                    "shopSearchAdvanced",
                    "搜索区域=" + advancedResult.area()
                            + " 类型=" + advancedResult.shopType()
                            + " 结果数=" + shopCount,
                    resolvedSessionId);
            context = sessionContextService.refreshCandidates(resolvedSessionId, advancedResult);
        }

        StringBuilder fullReply = new StringBuilder();
        AtomicReference<Disposable> disposableRef = new AtomicReference<>();
        Disposable disposable = familyRecommendService.recommendStream(prompt, profile, context, advancedResult)
                .onErrorResume(err -> {
                    log.error("SSE stream error for session {}: {}", resolvedSessionId, err.getMessage());
                    return Flux.just("[系统提示] 服务暂时不可用，请稍后再试。");
                })
                .subscribe(
                        chunk -> {
                            sendChunk(emitter, chunk);
                            fullReply.append(chunk);
                        },
                        err -> {
                            // Send error as SSE event instead of completeWithError,
                            // which triggers Spring's JSON serialization on an already-committed stream
                            log.warn("SSE subscription error for session {}: {}", resolvedSessionId, err.getMessage());
                            try {
                                emitter.send(SseEmitter.event().name("error")
                                        .data("服务异常，请稍后再试: " + err.getMessage()));
                            } catch (IOException ignored) {
                            }
                            recordAndComplete(emitter, disposableRef.get(), fullReply, resolvedSessionId);
                        },
                        () -> {
                            recordAndComplete(emitter, disposableRef.get(), fullReply, resolvedSessionId);
                        }
                );
        disposableRef.set(disposable);

        emitter.onCompletion(() -> {
            disposable.dispose();
        });
        emitter.onTimeout(() -> {
            disposable.dispose();
            emitter.complete();
        });

        return emitter;
    }

    private void sendChunk(SseEmitter emitter, String chunk) {
        try {
            emitter.send(SseEmitter.event().name("message").data(chunk));
        } catch (IOException e) {
            log.warn("Failed to send SSE chunk: {}", e.getMessage());
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("流传输中断: " + e.getMessage()));
            } catch (IOException ignored) {
            }
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    private void recordAndComplete(SseEmitter emitter, Disposable disposable, StringBuilder fullReply, String sessionId) {
        if (fullReply.length() > 0) {
            try {
                familyMemoryService.recordAgentResponse(fullReply.toString(), sessionId);
            } catch (Exception e) {
                log.warn("Failed to record agent response to memory: {}", e.getMessage());
            }
        }
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private boolean shouldRefreshCandidates(SessionContextDTO context) {
        if (context == null) {
            return false;
        }
        String currentIntent = context.getCurrentIntent();
        return INTENT_SHOP_RECOMMENDATION.equals(currentIntent)
                || INTENT_SHOP_SEARCH.equals(currentIntent);
    }

    /**
     * 安全解析 Map 中的 Long 值，转换失败时返回默认值。
     */
    private Long safeParseLong(Map<String, Object> map, String key, Long defaultValue) {
        if (map == null) return defaultValue;
        Object value = map.get(key);
        if (value == null) return defaultValue;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse '{}' as Long: {}", key, value, e);
            return defaultValue;
        }
    }
}
