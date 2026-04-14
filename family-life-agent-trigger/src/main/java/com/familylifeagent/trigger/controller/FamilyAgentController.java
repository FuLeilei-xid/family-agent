package com.familylifeagent.trigger.controller;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.api.dto.SessionContextDTO;
import com.familylifeagent.domain.service.FamilyProfileService;
import com.familylifeagent.domain.service.FamilyRecommendService;
import com.familylifeagent.domain.service.SessionContextService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/family")
public class FamilyAgentController {

    private final FamilyRecommendService familyRecommendService;
    private final FamilyProfileService familyProfileService;
    private final SessionContextService sessionContextService;

    public FamilyAgentController(FamilyRecommendService familyRecommendService,
                                 FamilyProfileService familyProfileService,
                                 SessionContextService sessionContextService) {
        this.familyRecommendService = familyRecommendService;
        this.familyProfileService = familyProfileService;
        this.sessionContextService = sessionContextService;
    }

    @PostMapping("/profile/save")
    public Map<String, Object> saveProfile(@RequestBody FamilyProfileDTO familyProfileDTO) {
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
        Long userId = request != null && request.get("userId") != null
                ? Long.valueOf(String.valueOf(request.get("userId")))
                : 1001L;
        String sessionId = request != null && request.get("sessionId") != null
                ? String.valueOf(request.get("sessionId"))
                : UUID.randomUUID().toString();

        FamilyProfileDTO profile = familyProfileService.queryOrDefault(userId);
        SessionContextDTO context = sessionContextService.updateForQuestion(sessionId, message);
        String reply = familyRecommendService.recommendOnce(message, profile, context);
        sessionContextService.refreshCandidates(sessionId, List.of(1L, 2L, 3L));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("reply", reply);
        result.put("context", context);
        return result;
    }

    @GetMapping(path = "/recommend/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter recommendChatStream(@RequestParam(value = "message", required = false) String message,
                                          @RequestParam(value = "userId", required = false) Long userId,
                                          @RequestParam(value = "sessionId", required = false) String sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        String prompt = message != null && !message.isBlank()
                ? message
                : "今晚一家三口想在西湖附近吃饭，老人清淡，小孩想吃面，预算100元以内。";
        Long resolvedUserId = userId != null ? userId : 1001L;
        String resolvedSessionId = sessionId != null && !sessionId.isBlank() ? sessionId : UUID.randomUUID().toString();

        FamilyProfileDTO profile = familyProfileService.queryOrDefault(resolvedUserId);
        SessionContextDTO context = sessionContextService.updateForQuestion(resolvedSessionId, prompt);

        Disposable disposable = familyRecommendService.recommendStream(prompt, profile, context)
                .subscribe(
                        chunk -> sendChunk(emitter, chunk),
                        emitter::completeWithError,
                        () -> {
                            sessionContextService.refreshCandidates(resolvedSessionId, List.of(1L, 2L, 3L));
                            emitter.complete();
                        }
                );

        emitter.onCompletion(disposable::dispose);
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
            emitter.completeWithError(e);
        }
    }
}
