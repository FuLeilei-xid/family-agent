package com.familylifeagent.domain.prompt;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.api.dto.SessionContextDTO;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FamilyAgentPromptTemplate {

    private static final String SYSTEM_PROMPT = """
            你是“家邻智选”，一个面向家庭本地生活决策的智能体。
            你需要结合家庭画像、用户当前问题、历史会话上下文和工具返回结果，给出可信、简洁、可执行的推荐。

            输出要求：
            1. 优先基于工具结果回答，不得编造店铺、优惠和营业信息。
            2. 充分考虑家庭成员画像，尤其是老人和儿童的饮食限制与偏好。
            3. 如果信息不足，请先主动追问最关键的缺失条件。
            4. 优先给出 3 到 5 个代表性选项，并说明推荐理由。
            5. 支持多轮对话，必要时引用最近一次候选店铺和用户补充条件。

            当前家庭画像：
            - 用户ID：{userId}
            - 家庭人数：{familySize}
            - 是否有老人：{hasElderly}
            - 是否有儿童：{hasChild}
            - 老人偏好：{elderlyPreference}
            - 儿童偏好：{childPreference}
            - 预算范围：{budgetRange}
            - 默认区域：{defaultArea}
            - 距离偏好：{distancePreference}

            当前会话上下文：
            - 会话ID：{sessionId}
            - 当前意图：{currentIntent}
            - 已识别槽位：{slots}
            - 上轮候选店铺ID：{candidateShopIds}
            - 上轮主推荐店铺ID：{lastRecommendShopId}
            """;

    public String render(FamilyProfileDTO profileDTO, SessionContextDTO sessionContextDTO) {
        FamilyProfileDTO profile = profileDTO != null ? profileDTO : createDefaultProfile();
        SessionContextDTO context = sessionContextDTO != null ? sessionContextDTO : createDefaultContext();
        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", profile.getUserId());
        variables.put("familySize", profile.getFamilySize());
        variables.put("hasElderly", profile.getHasElderly());
        variables.put("hasChild", profile.getHasChild());
        variables.put("elderlyPreference", profile.getElderlyPreference());
        variables.put("childPreference", profile.getChildPreference());
        variables.put("budgetRange", profile.getBudgetRange());
        variables.put("defaultArea", profile.getDefaultArea());
        variables.put("distancePreference", profile.getDistancePreference());
        variables.put("sessionId", context.getSessionId());
        variables.put("currentIntent", context.getCurrentIntent());
        variables.put("slots", context.getSlots());
        variables.put("candidateShopIds", context.getCandidateShopIds());
        variables.put("lastRecommendShopId", context.getLastRecommendShopId());
        return new PromptTemplate(SYSTEM_PROMPT).render(variables);
    }

    private FamilyProfileDTO createDefaultProfile() {
        FamilyProfileDTO profile = new FamilyProfileDTO();
        profile.setUserId(1001L);
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

    private SessionContextDTO createDefaultContext() {
        return SessionContextDTO.builder()
                .sessionId("default-session")
                .currentIntent("restaurant_recommendation")
                .slots(Map.of())
                .candidateShopIds(List.of())
                .lastRecommendShopId(null)
                .build();
    }
}
