package com.familylifeagent.trigger.controller;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.domain.service.FamilyRecommendService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/family")
public class FamilyAgentController {

    private final FamilyRecommendService familyRecommendService;

    public FamilyAgentController(FamilyRecommendService familyRecommendService) {
        this.familyRecommendService = familyRecommendService;
    }

    @PostMapping("/profile/save")
    public Map<String, Object> saveProfile(@RequestBody FamilyProfileDTO familyProfileDTO) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "家庭画像保存成功");
        result.put("data", familyProfileDTO);
        return result;
    }

    @GetMapping("/profile/query")
    public FamilyProfileDTO queryProfile(@RequestParam(value = "userId", required = false) Long userId) {
        return defaultProfile(userId);
    }

    @PostMapping("/recommend/chat")
    public Map<String, Object> recommendChat(@RequestBody(required = false) Map<String, Object> request) {
        String message = request != null && request.get("message") != null
                ? String.valueOf(request.get("message"))
                : "请为一家三口推荐西湖附近适合老人和孩子的晚餐。";

        String reply = familyRecommendService.recommendOnce(message, defaultProfile(1001L));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", "ai-session-001");
        result.put("reply", reply);
        return result;
    }

    @GetMapping(path = "/recommend/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter recommendChatStream(@RequestParam(value = "message", required = false) String message) {
        SseEmitter emitter = new SseEmitter(0L);
        String prompt = message != null && !message.isBlank()
                ? message
                : "今晚一家三口想在西湖附近吃饭，老人清淡，小孩想吃面，预算100元以内。";

        Disposable disposable = familyRecommendService.recommendStream(prompt, defaultProfile(1001L))
                .subscribe(
                        chunk -> sendChunk(emitter, chunk),
                        emitter::completeWithError,
                        emitter::complete
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

    private FamilyProfileDTO defaultProfile(Long userId) {
        FamilyProfileDTO profile = new FamilyProfileDTO();
        profile.setUserId(userId != null ? userId : 1001L);
        profile.setFamilySize(3);
        profile.setHasElderly(Boolean.TRUE);
        profile.setHasChild(Boolean.TRUE);
        profile.setElderlyPreference("清淡");
        profile.setChildPreference("面食");
        profile.setBudgetRange("50-120");
        profile.setDefaultArea("西湖");
        profile.setDistancePreference("近");
        return profile;
    }
}
