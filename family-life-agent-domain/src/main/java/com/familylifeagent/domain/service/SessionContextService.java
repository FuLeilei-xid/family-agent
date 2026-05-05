package com.familylifeagent.domain.service;

import com.familylifeagent.api.dto.SessionContextDTO;
import com.familylifeagent.infrastructure.service.LocalLifeSemanticRagService;
import com.familylifeagent.infrastructure.tool.ShopSearchTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SessionContextService {

    private static final Logger log = LoggerFactory.getLogger(SessionContextService.class);

    private static final String INTENT_SHOP_RECOMMENDATION = "shop_recommendation";
    private static final String INTENT_SHOP_SEARCH = "shop_search";
    private static final String INTENT_SHOP_DETAIL = "shop_detail";
    private static final String INTENT_VOUCHER_QUERY = "voucher_query";

    private static final String SLOT_SHOP_TYPE = "shopType";
    private static final String SLOT_AREA = "area";
    private static final String SLOT_BUDGET_RANGE = "budgetRange";
    private static final String SLOT_SUITABLE_FOR_ELDERLY = "suitableForElderly";
    private static final String SLOT_HAS_CHILDREN_PLAY_AREA = "hasChildrenPlayArea";
    private static final String SLOT_CANDIDATE_INDEX = "candidateIndex";

    private static final List<String> AREA_KEYWORDS = List.of("西湖", "滨江", "上城", "拱墅", "余杭", "萧山", "临平", "钱塘");
    private static final Map<String, String> SHOP_TYPE_SYNONYM_MAP = createShopTypeSynonymMap();
    private static final Pattern BUDGET_RANGE_PATTERN = Pattern.compile("(\\d+\\s*[-到至]\\s*\\d+|\\d+\\s*(以内|以下|不超过|以上|不少于))");
    private static final Pattern CANDIDATE_INDEX_PATTERN = Pattern.compile("第([一二三四五六七八九十\\d]+)家");

    private static final String LLM_INTENT_PROMPT = """
            你是一个意图识别器。根据用户消息和上一轮槽位，提取当前意图和槽位值。
            只返回严格 JSON，不要解释。

            意图类型（5选1）：
            - shop_search: 用户想搜索/找/推荐店铺（含首次请求和补充条件）
            - shop_detail: 用户想查看某家店的详情/营业时间/评价（含"第X家""怎么样"）
            - voucher_query: 用户想查看或领取优惠券
            - shop_recommendation: 只是闲聊或简单追问，无需重新搜索
            - execute_action: 用户想预约/排号/领券等执行操作

            槽位键值（仅在有把握时填写，没有则填 null）：
            - shopType: 店铺类型。标准值: 餐厅,面馆/简餐,火锅店,咖啡/茶饮,游戏厅,服装店,超市/便利店,书店,KTV,亲子乐园,电影院,药店,医院/诊所,商场
            - area: 杭州区域。已知值: 西湖,滨江,上城,拱墅,余杭,萧山,临平,钱塘
            - budgetRange: 预算范围如"50-100""100以内""200以上"
            - suitableForElderly: 是否需要适合老人 (true/false)
            - hasChildrenPlayArea: 是否需要儿童游乐区 (true/false)
            - candidateIndex: 用户提到的第几家（1-5的数字）

            规则：
            1. "带爸妈吃饭""老人" → suitableForElderly=true
            2. "孩子""小孩""儿童" → hasChildrenPlayArea=true
            3. "换到XX区""换成XX" → 更新 area，继承上一轮 shopType
            4. "第一家""第二个" → intent=shop_detail，candidateIndex=对应数字
            5. "预约""排号""订座""预定" → intent=execute_action
            6. "领券""领取""使用" → intent=execute_action

            返回格式：{"intent":"...","slots":{"shopType":"...","area":"...","budgetRange":"...","suitableForElderly":null,"hasChildrenPlayArea":null,"candidateIndex":null}}
            """;

    private final Map<String, SessionContextDTO> sessionCache = new ConcurrentHashMap<>();
    private final LocalLifeSemanticRagService localLifeSemanticRagService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SessionContextService(LocalLifeSemanticRagService localLifeSemanticRagService,
                                  ChatClient chatClient) {
        this.localLifeSemanticRagService = localLifeSemanticRagService;
        this.chatClient = chatClient;
        this.objectMapper = new ObjectMapper();
    }

    // ==================== 公共方法 ====================

    public SessionContextDTO getOrCreate(String sessionId) {
        return sessionCache.computeIfAbsent(sessionId, key -> SessionContextDTO.builder()
                .sessionId(key)
                .currentIntent(INTENT_SHOP_RECOMMENDATION)
                .slots(createDefaultSlots())
                .candidateShopIds(new ArrayList<>())
                .candidateShopMap(new LinkedHashMap<>())
                .lastRecommendShopId(null)
                .ragHint("无")
                .build());
    }

    public SessionContextDTO updateForQuestion(String sessionId, String message) {
        SessionContextDTO context = getOrCreate(sessionId);
        Map<String, Object> slots = new LinkedHashMap<>(normalizeSlots(context.getSlots()));

        // 1. 优先用 LLM 做意图识别 + 槽位提取
        LlmIntentResult llmResult = tryLlmExtraction(message, slots);
        if (llmResult != null) {
            applyLlmResult(slots, llmResult);
            context.setCurrentIntent(llmResult.intent);
        } else {
            // 2. LLM 失败时 fallback 到关键词匹配
            log.debug("LLM intent extraction failed, falling back to keyword matching");
            String normalizedMessage = normalizeMessage(message);
            mergeSlots(slots, normalizedMessage);
            String currentIntent = detectIntent(normalizedMessage, slots);
            if (containsDetailIntent(normalizedMessage)) {
                currentIntent = INTENT_SHOP_DETAIL;
            }
            context.setCurrentIntent(currentIntent);
        }

        context.setSlots(slots);
        context.setCurrentSlots(new LinkedHashMap<>(slots));

        // 追问详情时更新 lastRecommendShopId，防止后续预约找错店
        String currentIntent = context.getCurrentIntent();
        if (INTENT_SHOP_DETAIL.equals(currentIntent) || "execute_action".equals(currentIntent)) {
            updateLastRecommendForFollowUp(context, slots);
        }

        if (context.getCandidateShopIds() == null) {
            context.setCandidateShopIds(new ArrayList<>());
        }
        if (context.getCandidateShopMap() == null) {
            context.setCandidateShopMap(new LinkedHashMap<>());
        }

        String ragHint = localLifeSemanticRagService.expandSemanticTags(message);
        context.setRagHint(ragHint == null || ragHint.isBlank() ? "无" : ragHint);

        sessionCache.put(sessionId, context);
        return context;
    }

    public SessionContextDTO refreshCandidates(String sessionId, ShopSearchTool.ShopSearchAdvancedResult advancedResult) {
        SessionContextDTO context = getOrCreate(sessionId);
        List<Long> candidateShopIds = new ArrayList<>();
        Map<Long, String> candidateShopMap = new LinkedHashMap<>();
        if (advancedResult != null && advancedResult.shops() != null) {
            for (ShopSearchTool.AdvancedShopItem shop : advancedResult.shops()) {
                if (shop == null || shop.shopId() == null) continue;
                candidateShopIds.add(shop.shopId());
                candidateShopMap.put(shop.shopId(), shop.shopName());
            }
        }
        context.setCandidateShopIds(candidateShopIds);
        context.setCandidateShopMap(candidateShopMap);
        context.setLastRecommendShopId(candidateShopIds.isEmpty() ? null : candidateShopIds.get(0));
        sessionCache.put(sessionId, context);
        return context;
    }

    // ==================== LLM 意图提取 ====================

    private LlmIntentResult tryLlmExtraction(String message, Map<String, Object> previousSlots) {
        if (message == null || message.isBlank() || chatClient == null) return null;
        try {
            String prevSlotsJson = objectMapper.writeValueAsString(previousSlots);
            String prompt = LLM_INTENT_PROMPT
                    + "\n上一轮槽位：" + prevSlotsJson
                    + "\n用户消息：\"" + message + "\""
                    + "\n现在返回 JSON：";

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) return null;

            // 提取 JSON（处理 markdown 代码块包裹）
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*|```\\s*", "").trim();
            }

            Map<String, Object> result = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            String intent = String.valueOf(result.getOrDefault("intent", INTENT_SHOP_RECOMMENDATION));
            @SuppressWarnings("unchecked")
            Map<String, Object> llmSlots = (Map<String, Object>) result.getOrDefault("slots", Map.of());

            log.info("LLM intent: {} slots: {}", intent, llmSlots);
            return new LlmIntentResult(intent, llmSlots);

        } catch (Exception e) {
            log.debug("LLM intent extraction error: {}", e.getMessage());
            return null;
        }
    }

    private void applyLlmResult(Map<String, Object> slots, LlmIntentResult result) {
        Map<String, Object> llmSlots = result.slots;
        if (llmSlots == null) return;

        if (llmSlots.containsKey(SLOT_SHOP_TYPE) && llmSlots.get(SLOT_SHOP_TYPE) != null) {
            slots.put(SLOT_SHOP_TYPE, llmSlots.get(SLOT_SHOP_TYPE));
        }
        if (llmSlots.containsKey(SLOT_AREA) && llmSlots.get(SLOT_AREA) != null) {
            slots.put(SLOT_AREA, llmSlots.get(SLOT_AREA));
        }
        if (llmSlots.containsKey(SLOT_BUDGET_RANGE) && llmSlots.get(SLOT_BUDGET_RANGE) != null) {
            slots.put(SLOT_BUDGET_RANGE, llmSlots.get(SLOT_BUDGET_RANGE));
        }
        if (llmSlots.containsKey(SLOT_SUITABLE_FOR_ELDERLY) && llmSlots.get(SLOT_SUITABLE_FOR_ELDERLY) != null) {
            Object val = llmSlots.get(SLOT_SUITABLE_FOR_ELDERLY);
            slots.put(SLOT_SUITABLE_FOR_ELDERLY, Boolean.TRUE.equals(val) || "true".equals(String.valueOf(val)));
        }
        if (llmSlots.containsKey(SLOT_HAS_CHILDREN_PLAY_AREA) && llmSlots.get(SLOT_HAS_CHILDREN_PLAY_AREA) != null) {
            Object val = llmSlots.get(SLOT_HAS_CHILDREN_PLAY_AREA);
            slots.put(SLOT_HAS_CHILDREN_PLAY_AREA, Boolean.TRUE.equals(val) || "true".equals(String.valueOf(val)));
        }
    }

    private record LlmIntentResult(String intent, Map<String, Object> slots) {}

    /**
     * 追问"第二家"/"这家"时，根据 candidateIndex 或当前讨论的店更新 lastRecommendShopId。
     * 防止后续预约操作错指到排名第一的店。
     */
    private void updateLastRecommendForFollowUp(SessionContextDTO context, Map<String, Object> slots) {
        List<Long> candidateIds = context.getCandidateShopIds();
        if (candidateIds == null || candidateIds.isEmpty()) return;

        // 1. LLM 提取的 candidateIndex（1-based）
        Object idxObj = slots.get(SLOT_CANDIDATE_INDEX);
        if (idxObj instanceof Number idx && idx.intValue() >= 1 && idx.intValue() <= candidateIds.size()) {
            context.setLastRecommendShopId(candidateIds.get(idx.intValue() - 1));
            log.info("Follow-up shop resolved by LLM index: {} -> shopId={}", idx.intValue(), context.getLastRecommendShopId());
            return;
        }
        // 2. 关键词 fallback: "第X家"
        if (slots.get(SLOT_SHOP_TYPE) == null) {
            Matcher m = CANDIDATE_INDEX_PATTERN.matcher(
                    context.getSlots().getOrDefault("_rawMessage", "").toString());
            if (!m.find()) {
                // 用户可能说"第二家"但 slots 里没有存原始消息。从 context 里也拿不到。
                // 兜底：如果只有一个候选店被讨论过且无新搜索，保持现状
            }
        }
        // 3. 兜底：如果 lastRecommendShopId 不空且 candidates 包含它，保持
        if (context.getLastRecommendShopId() != null
                && candidateIds.contains(context.getLastRecommendShopId())) {
            return;
        }
        // 4. 最后兜底：用第一个候选
        if (!candidateIds.isEmpty()) {
            context.setLastRecommendShopId(candidateIds.get(0));
        }
    }

    // ==================== 关键词 fallback（保留原有逻辑） ====================

    private Map<String, Object> createDefaultSlots() {
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put(SLOT_SHOP_TYPE, null);
        slots.put(SLOT_AREA, null);
        slots.put(SLOT_BUDGET_RANGE, null);
        slots.put(SLOT_SUITABLE_FOR_ELDERLY, null);
        slots.put(SLOT_HAS_CHILDREN_PLAY_AREA, null);
        return slots;
    }

    private Map<String, Object> normalizeSlots(Map<String, Object> slots) {
        Map<String, Object> normalized = createDefaultSlots();
        if (slots != null) normalized.putAll(slots);
        return normalized;
    }

    private void mergeSlots(Map<String, Object> slots, String message) {
        if (message == null || message.isBlank()) return;

        String detectedShopType = extractShopType(message);
        if (detectedShopType != null) {
            Object existingShopType = slots.get(SLOT_SHOP_TYPE);
            if (existingShopType != null && containsTaskCompleted(message)) {
                String alternativeType = extractAlternativeShopType(message, String.valueOf(existingShopType));
                slots.put(SLOT_SHOP_TYPE, alternativeType != null ? alternativeType : detectedShopType);
            } else {
                slots.put(SLOT_SHOP_TYPE, detectedShopType);
            }
        }

        if (isAreaRelaxed(message)) {
            slots.put(SLOT_AREA, null);
        } else {
            String detectedArea = extractArea(message);
            if (detectedArea != null) slots.put(SLOT_AREA, detectedArea);
        }

        if (isBudgetRelaxed(message)) {
            slots.put(SLOT_BUDGET_RANGE, null);
        } else {
            String detectedBudgetRange = extractBudgetRange(message);
            if (detectedBudgetRange != null) slots.put(SLOT_BUDGET_RANGE, detectedBudgetRange);
        }

        if (message.contains("老人")) slots.put(SLOT_SUITABLE_FOR_ELDERLY, !containsNegation(message, "老人"));
        if (message.contains("儿童游乐区") || message.contains("游乐区") || message.contains("儿童乐园"))
            slots.put(SLOT_HAS_CHILDREN_PLAY_AREA, !containsNegation(message, "游乐"));
    }

    private String detectIntent(String message, Map<String, Object> slots) {
        if (message == null || message.isBlank()) return INTENT_SHOP_RECOMMENDATION;
        if (containsVoucherIntent(message)) return INTENT_VOUCHER_QUERY;
        if (containsDetailIntent(message)) return INTENT_SHOP_DETAIL;
        if (containsSearchIntent(message, slots)) return INTENT_SHOP_SEARCH;
        return INTENT_SHOP_RECOMMENDATION;
    }

    private boolean containsVoucherIntent(String message) { return message.contains("优惠") || message.contains("券"); }

    private boolean containsDetailIntent(String message) {
        return message.contains("详细") || message.contains("详情") || message.contains("具体信息")
                || message.contains("具体情况") || message.contains("营业时间") || message.contains("营业到几点")
                || message.contains("几点关门") || message.contains("几点营业") || message.contains("评价")
                || message.contains("怎么样") || CANDIDATE_INDEX_PATTERN.matcher(message).find();
    }

    private boolean containsSearchIntent(String message, Map<String, Object> slots) {
        return extractShopType(message) != null || extractArea(message) != null
                || extractBudgetRange(message) != null || isAreaRelaxed(message) || isBudgetRelaxed(message)
                || message.contains("推荐") || message.contains("找") || message.contains("还有")
                || message.contains("再来") || message.contains("再推荐") || message.contains("附近") || slots == null;
    }

    private String extractShopType(String message) {
        if (message == null || message.isBlank()) return null;
        for (Map.Entry<String, String> entry : SHOP_TYPE_SYNONYM_MAP.entrySet()) {
            if (message.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private String extractAlternativeShopType(String message, String currentShopType) {
        if (message == null || currentShopType == null) return null;
        for (Map.Entry<String, String> entry : SHOP_TYPE_SYNONYM_MAP.entrySet()) {
            if (message.contains(entry.getKey()) && !entry.getValue().equals(currentShopType))
                return entry.getValue();
        }
        return null;
    }

    private static Map<String, String> createShopTypeSynonymMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("服装店", "服装店"); map.put("衣服", "服装店"); map.put("童装", "服装店");
        map.put("男装", "服装店"); map.put("女装", "服装店"); map.put("买件", "服装店");
        map.put("餐厅", "餐厅"); map.put("吃饭", "餐厅"); map.put("聚会", "餐厅");
        map.put("聚餐", "餐厅"); map.put("请客", "餐厅"); map.put("饭馆", "餐厅");
        map.put("吃个饭", "餐厅"); map.put("用餐", "餐厅");
        map.put("医院/诊所", "医院/诊所"); map.put("医院", "医院/诊所"); map.put("诊所", "医院/诊所");
        map.put("看病", "医院/诊所"); map.put("不舒服", "医院/诊所");
        map.put("电影院", "电影院"); map.put("电玩城", "游戏厅"); map.put("游戏城", "游戏厅");
        map.put("游戏厅", "游戏厅"); map.put("面馆", "面馆"); map.put("火锅店", "火锅店");
        map.put("火锅", "火锅店"); map.put("咖啡店", "咖啡店"); map.put("咖啡", "咖啡店");
        map.put("超市", "超市"); map.put("书店", "书店"); map.put("KTV", "KTV"); map.put("ktv", "KTV");
        map.put("亲子乐园", "亲子乐园"); map.put("药店", "药店"); map.put("商场", "商场");
        map.put("商城", "商场"); map.put("mall", "商场");
        return map;
    }

    private String extractArea(String message) {
        for (String area : AREA_KEYWORDS) if (message.contains(area)) return area;
        return null;
    }

    private String normalizeMessage(String message) {
        if (message == null) return null;
        return message.replace("附件", "附近").replace("童装店", "童装 服装店")
                .replace("衣服店", "衣服 服装店").replace("买衣服", "买衣服 服装店");
    }

    private boolean isAreaRelaxed(String message) {
        return message.contains("其他区域") || message.contains("其他区域也可以")
                || message.contains("别的区域也可以") || message.contains("其他地方也可以")
                || message.contains("区域不限") || message.contains("不限区域") || message.contains("除了")
                || message.contains("都可以") || message.contains("随便") || message.contains("扩大区域")
                || message.contains("放宽区域");
    }

    private boolean isBudgetRelaxed(String message) {
        return message.contains("预算不限") || message.contains("多少钱都可以") || message.contains("价格不限")
                || message.contains("预算都可以") || message.contains("预算随便")
                || message.contains("价格都可以") || message.contains("价格随便");
    }

    private String extractBudgetRange(String message) {
        Matcher matcher = BUDGET_RANGE_PATTERN.matcher(message);
        if (matcher.find()) return matcher.group(1).replaceAll("\\s+", "");
        if (message.contains("预算")) {
            int index = message.indexOf("预算");
            return index >= 0 ? message.substring(index).split("，|。|,|\\s")[0].replace("预算", "") : null;
        }
        return null;
    }

    private boolean containsNegation(String message, String keyword) {
        return message.contains("不要" + keyword) || message.contains("不适合" + keyword)
                || message.contains("不需要" + keyword) || message.contains("不要带" + keyword)
                || message.contains("不用" + keyword);
    }

    private boolean containsTaskCompleted(String message) {
        return message.contains("已选好") || message.contains("选好了") || message.contains("买好了")
                || message.contains("买完") || message.contains("已买") || message.contains("已完成")
                || message.contains("先买") || message.contains("先去") || message.contains("然后去")
                || message.contains("再去") || message.contains("之后去") || message.contains("再去找")
                || message.contains("之后");
    }
}
