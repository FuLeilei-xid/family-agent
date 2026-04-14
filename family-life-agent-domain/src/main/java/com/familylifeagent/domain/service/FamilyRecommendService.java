package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.domain.prompt.FamilyAgentPromptTemplate;
import com.familylifeagent.infrastructure.tool.ShopSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class FamilyRecommendService {

    private final ChatClient familyChatClient;
    private final FamilyAgentPromptTemplate promptTemplate;
    private final ShopSearchTool shopSearchTool;

    public FamilyRecommendService(ChatClient familyChatClient,
                                  FamilyAgentPromptTemplate promptTemplate,
                                  ShopSearchTool shopSearchTool) {
        this.familyChatClient = familyChatClient;
        this.promptTemplate = promptTemplate;
        this.shopSearchTool = shopSearchTool;
    }

    public String recommendOnce(String message, FamilyProfileDTO profileDTO) {
        String systemPrompt = promptTemplate.render(profileDTO);
        return familyChatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .tools(shopSearchTool)
                .call()
                .content();
    }

    public Flux<String> recommendStream(String message, FamilyProfileDTO profileDTO) {
        String systemPrompt = promptTemplate.render(profileDTO);
        return familyChatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .tools(shopSearchTool)
                .stream()
                .content();
    }
}
