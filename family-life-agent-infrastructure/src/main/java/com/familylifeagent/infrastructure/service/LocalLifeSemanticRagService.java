package com.familylifeagent.infrastructure.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LocalLifeSemanticRagService {

    private static final Map<String, List<String>> KNOWLEDGE_BASE = new LinkedHashMap<>();

    static {
        register(List.of("牙口不好", "没牙", "软烂"), List.of("粥底火锅", "食材软烂", "口味清淡"));
        register(List.of("清淡", "养生", "不吃辣", "老人吃"), List.of("口味清淡", "杭帮菜", "粤菜", "汤底清淡"));
        register(List.of("重口味", "想吃辣", "过瘾"), List.of("川菜", "重麻重辣", "营业到凌晨"));
        register(List.of("腿脚不便", "轮椅", "走不动", "老人不方便"), List.of("无障碍通道", "有电梯", "临街一楼", "轮椅专座", "提供轮椅租借"));
        register(List.of("放电", "溜娃", "闹腾", "带小孩", "游乐区"), List.of("提供宝宝椅", "亲子乐园", "海洋球池", "全场软包", "儿童绘本区", "温奶器"));
        register(List.of("同学聚会", "人多", "聚餐", "热闹"), List.of("包厢多", "巨幕包厢", "隔音极佳"));
        register(List.of("长辈衣服", "买给父母", "老人衣服"), List.of("中老年女装", "保暖内衣", "纯棉材质", "宽敞试衣间"));
        register(List.of("小孩衣服", "宝宝衣服"), List.of("童装", "A类材质", "纯棉材质"));
        register(List.of("做饭", "买菜", "太晚了买东西"), List.of("生鲜", "半成品菜", "24小时便利", "支持配送"));
        register(List.of("不舒服", "看病", "买药", "发烧", "受伤"), List.of("药店", "医院/诊所", "24小时营业", "急诊", "执业药师驻店", "三甲医院"));
    }

    public String expandSemanticTags(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return "";
        }

        Set<String> expandedTags = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : KNOWLEDGE_BASE.entrySet()) {
            if (userInput.contains(entry.getKey())) {
                expandedTags.addAll(entry.getValue());
            }
        }

        if (expandedTags.isEmpty()) {
            return "";
        }

        return "【本地知识库提示】：根据用户的模糊诉求，建议优先在数据库工具中检索或参考以下客观标签："
                + new ArrayList<>(expandedTags);
    }

    private static void register(List<String> intents, List<String> tags) {
        for (String intent : intents) {
            KNOWLEDGE_BASE.put(intent, tags);
        }
    }
}
