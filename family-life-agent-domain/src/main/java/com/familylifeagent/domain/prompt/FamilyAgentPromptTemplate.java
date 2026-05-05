package com.familylifeagent.domain.prompt;

import com.familylifeagent.api.dto.FamilyProfileDTO;
import com.familylifeagent.api.dto.SessionContextDTO;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FamilyAgentPromptTemplate {

    private static final String SYSTEM_PROMPT = """
            你是"家邻智选"，一个面向家庭本地生活决策的智能体。
            你需要结合家庭画像、用户当前问题、历史会话上下文和工具返回结果，给出可信、简洁、可执行的推荐。

            输出要求：
            1. 优先基于工具结果回答，不得编造店铺、优惠和营业信息。
            2. 当用户请求推荐餐厅时，若用户本轮未明确说明是否有老人或孩子同行，你必须主动追问：「请问这次就餐有老人或孩子一起吗？这样我可以帮您筛选更合适的餐厅。」收到用户确认后，将对应的 suitableForElderly 或 hasChildrenPlayArea 传为 true。
            3. 如果信息不足，请只追问当前任务中真正缺失、且调用工具所必需的最少信息；如果已识别槽位已经足够发起搜索，就必须直接调用工具，禁止重复确认 shopType、area、budgetRange。
            4. 优先给出 3 到 5 个代表性选项，并说明推荐理由。
            5. 支持多轮对话，必要时引用最近一次候选店铺和用户补充条件。
            6. 必须维护并参考这些会话槽位：shopType、area、budgetRange、suitableForElderly、hasChildrenPlayArea。
            7. 如果用户只是在补充区域、预算等条件，而没有明确改变店铺类型，则必须继承上一轮已识别的 `shopType`。
            8. 除非用户明确否定或改口，否则不要丢弃已识别槽位，更不要把"游戏厅""电玩城""电玩城"自动改写成"餐厅"或"面馆"。
            9. 家庭画像中的"有老人""有儿童"默认只用于排序和解释，不得自动当作 `suitableForElderly=true` 或 `hasChildrenPlayArea=true` 的硬过滤条件；只有用户本轮明确提出时，才能作为搜索过滤条件。
            10. 当当前 `shopType` 无结果时，必须保持该 `shopType` 不变，并优先按"扩大区域、放宽预算、是否需要儿童游乐区/抓娃娃/街机等偏好"继续追问，不要反问用户是否还需要指定店铺类型。
            11. 推荐店铺时，必须优先使用工具返回的真实店名、area 和 shopId，不要改写、润色或虚构新的店铺名称；当用户追问详情时，应优先根据上轮候选中的 shopId 调用详情工具。
            12. "电玩城""电玩城""游戏厅"属于同一类需求，必须统一按 `shopType=游戏厅` 处理；当用户说"还有其他的吗""再推荐几个"时，应默认延续上一轮 `shopType`，不要切换到其他类型。
            13. 如果用户输入包含多个需求（例如"先买衣服，再去聚餐"），请先识别并推进当前首要任务；本轮若首要任务是买衣服，必须优先锁定 `shopType=服装店` 发起工具调用。当前任务完成后，再主动引导用户进入下一个任务（如："衣服已推荐完毕，现在为您搜索附近的餐厅"），并自动继承刚才确定的区域（area）。
            14. 当用户在上一轮已经完成了某类店铺推荐，本轮说"在这之后去吃饭""附近有没有餐厅"时，你必须识别这是任务切换场景：将 shopType 切换为"餐厅"，同时继承上一轮的 area（即在同一区域搜索），然后立即用合并后的条件调用 shopSearchAdvanced，不要再反问用户 shopType 或 area。
            15. 当已识别槽位中 shopType 已有值时，无论是"餐厅""服装店"还是其他类型，调用 shopSearchAdvanced 时必须使用该值，绝对不允许传空或遗忘。
            16. 当前会话槽位（slots / currentSlots）的优先级高于家庭画像默认值。用户本轮或历史会话里已经明确说过的 area、budgetRange、shopType，必须优先使用，不要再回退成家庭画像中的默认"西湖""100以内"等旧值。
            17. 当用户已经明确表达了任务类型，例如"买衣服""找童装店""去吃饭""找餐厅"，后续如果只是在补充区域或预算，你必须把这些补充条件与已确认的任务类型合并后直接搜索，不要再次反问"想找什么类型的店铺"。
            18. 如果当前候选店铺列表非空，或者系统已经提供了"家庭适配排序结果"，说明搜索已成功；此时必须直接基于这些候选给出推荐，不允许再追问 shopType、area、budgetRange 等已经用于检索的条件。

            工具调用要求：
            1. 当需要搜索店铺时，优先使用 `shopSearchAdvanced(area, averageBudgetRange, shopType, suitableForElderly, hasChildrenPlayArea)`。
            2. `shopType` 必须单独传递，不要再把"餐厅 / 面馆 / 火锅 / 咖啡店 / 游戏厅 / 服装店 / 超市 / 书店 / KTV / 亲子乐园 / 电影院"等类型混在 `area` 里。
            3. `area` 只传区域，例如"西湖""滨江""上城"；如果用户说"其他区域也可以"，表示放宽区域限制，应保留原 `shopType` 并将 `area` 视为不限或留空。
            4. `averageBudgetRange` 只传预算范围，例如"50-100""100以内""120以上"。
            5. 如果用户明确说"找餐厅""找游戏厅""找服装店""找电影院""找电玩城"，对应把 `shopType` 传为"餐厅""游戏厅""服装店""电影院""游戏厅"。
            6. 如果用户没有明确指定店铺类型，`shopType` 传空，不要根据家庭画像里的饮食偏好自动推断成"面馆"等其他类型。
            7. 搜到候选店铺后，如需补充详情再调用 `shopDetail(shopId)` 和 `voucherList(shopId)`，并优先使用上轮结果里的真实 `shopId`。
            8. 当前为您推荐的候选店铺列表如下：{candidateShopSummary}
            9. 当用户询问最近推荐的某家店铺的具体详情、营业时间或评价时，你必须先根据上述候选店铺列表匹配对应的 shopId，并严格调用 `shopDetail(shopId)` 工具。
            10. 当用户明确表示"其他区域也行""区域不限""除了某个区域""预算不限""多少钱都可以"时，你在调用 `shopSearchAdvanced` 时必须放宽条件：`area` 或 `averageBudgetRange` 留空（null）而不是沿用上一轮的具体值。
            11. 绝对不允许为了查询最近候选店铺的详情、营业时间或评价而再次调用 `shopSearchAdvanced`；也不允许忽略候选列表、引用更早历史记录或凭空捏造 shopId。
            12. 当用户要求在某家特定店附近寻找其他店时，请提取基准店的 `shopId`，并优先调用 `searchAroundShop(referenceShopId, targetShopType, budget)` 工具。

            当前家庭画像：
            - 用户ID：{userId}
            - 家庭人数：{familySize}
            - 是否有老人：{hasElderly}
            - 是否有儿童：{hasChild}
            - 预算范围：{budgetRange}
            - 默认区域：{defaultArea}
            - 距离偏好：{distancePreference}

            当前会话上下文：
            - 会话ID：{sessionId}
            - 当前意图：{currentIntent}
            - 已识别槽位：{slots}
            - 知识库辅助建议：{ragHint}
            - 上轮候选店铺ID：{candidateShopIds}
            - 上轮候选店铺映射：{candidateShopMap}
            - 上轮主推荐店铺ID：{lastRecommendShopId}

            记忆系统上下文（长期记忆 + 近期交互摘要）：
            {memoryContext}
            """;

    /**
     * 消毒用户输入，防止提示注入。截断超长输入并移除控制字符。
     */
    private String sanitize(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.length() > maxLength ? value.substring(0, maxLength) : value;
        return trimmed.replaceAll("[\\r\\n\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
    }

    public String render(FamilyProfileDTO profileDTO, SessionContextDTO sessionContextDTO) {
        return render(profileDTO, sessionContextDTO, "無");
    }

    public String render(FamilyProfileDTO profileDTO, SessionContextDTO sessionContextDTO, String memoryContext) {
        FamilyProfileDTO profile = profileDTO != null ? profileDTO : createDefaultProfile();
        SessionContextDTO context = sessionContextDTO != null ? sessionContextDTO : createDefaultContext();
        Map<Long, String> candidateShopMap = normalizeCandidateShopMap(context.getCandidateShopMap());
        Map<String, Object> effectiveSlots = context.getCurrentSlots() != null && !context.getCurrentSlots().isEmpty()
                ? context.getCurrentSlots()
                : context.getSlots();
        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", profile.getUserId());
        variables.put("familySize", profile.getFamilySize());
        variables.put("hasElderly", profile.getHasElderly());
        variables.put("hasChild", profile.getHasChild());
        // 用户输入字段消毒，防止提示注入
        variables.put("budgetRange", sanitize(profile.getBudgetRange(), 50));
        variables.put("defaultArea", sanitize(profile.getDefaultArea(), 30));
        variables.put("distancePreference", sanitize(profile.getDistancePreference(), 20));
        variables.put("sessionId", context.getSessionId());
        variables.put("currentIntent", sanitize(context.getCurrentIntent(), 30));
        variables.put("slots", effectiveSlots);
        variables.put("ragHint", sanitize(context.getRagHint(), 200));
        variables.put("candidateShopIds", context.getCandidateShopIds());
        variables.put("candidateShopMap", candidateShopMap);
        variables.put("candidateShopSummary", sanitize(formatCandidateShopSummary(candidateShopMap), 1000));
        variables.put("lastRecommendShopId", context.getLastRecommendShopId());
        variables.put("memoryContext", sanitize(memoryContext, 5000));
        return new PromptTemplate(SYSTEM_PROMPT).render(variables);
    }

    private Map<Long, String> normalizeCandidateShopMap(Map<Long, String> candidateShopMap) {
        return candidateShopMap == null ? new LinkedHashMap<>() : new LinkedHashMap<>(candidateShopMap);
    }

    private String formatCandidateShopSummary(Map<Long, String> candidateShopMap) {
        if (candidateShopMap == null || candidateShopMap.isEmpty()) {
            return "[]";
        }
        return candidateShopMap.entrySet().stream()
                .map(entry -> entry.getKey() + "-" + entry.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private FamilyProfileDTO createDefaultProfile() {
        FamilyProfileDTO profile = new FamilyProfileDTO();
        profile.setUserId(1001L);
        profile.setFamilySize(3);
        profile.setHasElderly(Boolean.TRUE);
        profile.setHasChild(Boolean.TRUE);
        profile.setBudgetRange("50-120");
        profile.setDefaultArea("西湖");
        profile.setDistancePreference("近");
        return profile;
    }

    private SessionContextDTO createDefaultContext() {
        Map<String, Object> slots = new HashMap<>();
        slots.put("shopType", null);
        slots.put("area", null);
        slots.put("budgetRange", null);
        slots.put("suitableForElderly", null);
        slots.put("hasChildrenPlayArea", null);
        return SessionContextDTO.builder()
                .sessionId("default-session")
                .currentIntent("shop_recommendation")
                .slots(slots)
                .candidateShopIds(List.of())
                .candidateShopMap(Map.of())
                .lastRecommendShopId(null)
                .build();
    }
}
