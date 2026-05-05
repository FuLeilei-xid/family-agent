#!/usr/bin/env python3
"""Generate 500 realistic shops with full details across 13 shop types."""

import random
random.seed(42)

# ========== 区块（杭州各区 + 真实街道）==========
DISTRICTS = {
    "西湖": ["文三路", "教工路", "天目山路", "曙光路", "保俶路", "浙大路", "古墩路", "玉古路"],
    "滨江": ["江南大道", "东信大道", "滨盛路", "江晖路", "春晓路", "江陵路", "长河路"],
    "上城": ["延安路", "解放路", "庆春路", "中山中路", "秋涛路", "清泰街", "平海路"],
    "拱墅": ["湖墅南路", "莫干山路", "大关路", "萍水街", "丽水路", "上塘路"],
    "余杭": ["文一西路", "高教路", "良睦路", "古墩路延伸段", "五常大道", "荆长路"],
    "萧山": ["市心路", "金城路", "建设一路", "通惠路", "博奥路"],
    "临平": ["迎宾路", "藕花洲大街", "新丰路", "星河路"],
    "钱塘": ["学源街", "云涛南路", "下沙路", "天城东路"],
}

# ========== 店铺类型 ==========
SHOP_TYPES = {
    1:  "餐厅", 2: "面馆/简餐", 3: "火锅店", 4: "咖啡/茶饮",
    5:  "游戏厅", 6: "服装店", 7: "超市/便利店", 8: "书店",
    9:  "KTV", 10: "亲子乐园", 11: "电影院", 12: "药店", 13: "医院/诊所",
}

# ========== 各类型店铺名模板 ==========
SHOP_NAMES = {
    1: [  # 餐厅
        "外婆家({area}店)", "绿茶餐厅({area}店)", "新白鹿({area}店)", "老头儿油爆虾({area}店)",
        "张生记({area}店)", "楼外楼({area}分店)", "山外山菜馆({area}店)", "奎元馆({area}店)",
        "状元馆({area}店)", "花中城({area}店)", "绿茶致青春({area}店)", "新开元({area}店)",
        "德明饭店({area}店)", "兰边碗({area}店)", "群乐饭店({area}店)", "福缘居({area}店)",
        "叶马茶楼({area}店)", "江南驿({area}店)", "卤儿道道({area}店)", "老方一贴({area}店)",
        "竹家庄({area}店)", "鹿港小镇({area}店)", "荣小馆({area}店)", "品尚江南({area}店)",
        "知味小馆({area}店)", "杭州酒家({area}店)", "杭帮菜博物馆餐厅({area}店)", "弄堂里({area}店)",
    ],
    2: [  # 面馆/简餐
        "奎元馆面店({area}店)", "慧娟面馆({area}店)", "菊英面店({area}店)", "方老大面馆({area}店)",
        "阿能面馆({area}店)", "流芳面馆({area}店)", "荣鲜面馆({area}店)", "巧媳妇面馆({area}店)",
        "小狗面馆({area}店)", "松木场面馆({area}店)", "忠儿面馆({area}店)", "吴山烤禽面馆({area}店)",
        "新丰小吃({area}店)", "知味观味庄({area}店)", "楼外楼面馆({area}店)", "片儿川面馆({area}店)",
    ],
    3: [  # 火锅店
        "海底捞({area}店)", "哥老官({area}店)", "小龙坎({area}店)", "大龙燚({area}店)",
        "蜀大侠({area}店)", "巴邑火锅({area}店)", "捞王锅物料理({area}店)", "左庭右院({area}店)",
        "呷哺呷哺({area}店)", "谭鸭血({area}店)", "贤合庄({area}店)", "凑凑火锅({area}店)",
        "电台巷火锅({area}店)", "辣府({area}店)", "川味观({area}店)", "皇城老妈({area}店)",
    ],
    4: [  # 咖啡/茶饮
        "星巴克({area}店)", "瑞幸咖啡({area}店)", "M Stand({area}店)", "Seesaw({area}店)",
        "皮爷咖啡({area}店)", "代数学家({area}店)", "喜茶({area}店)", "奈雪的茶({area}店)",
        "霸王茶姬({area}店)", "茶颜悦色({area}店)", "古茗({area}店)", "蜜雪冰城({area}店)",
        "Tims天好咖啡({area}店)", "八角杯咖啡({area}店)", "Manner({area}店)", "algebraist({area}店)",
    ],
    5: [  # 游戏厅
        "汤姆熊欢乐世界({area}店)", "风云再起({area}店)", "大玩家({area}店)", "星际传奇({area}店)",
        "乐玩星球({area}店)", "城市英雄({area}店)", "环游嘉年华({area}店)", "电玩巴士({area}店)",
        "魔都电玩城({area}店)", "极速电玩({area}店)", "乐酷电玩({area}店)", "玩刻({area}店)",
    ],
    6: [  # 服装店
        "优衣库({area}店)", "ZARA({area}店)", "H&M({area}店)", "URBAN REVIVO({area}店)",
        "MUJI无印良品({area}店)", "热风({area}店)", "巴拉巴拉童装({area}店)", "安奈儿({area}店)",
        "太平鸟({area}店)", "伊芙丽({area}店)", "江南布衣({area}店)", "速写({area}店)",
        "雅戈尔({area}店)", "海澜之家({area}店)", "GXG({area}店)", "森马({area}店)",
    ],
    7: [  # 超市
        "盒马鲜生({area}店)", "山姆会员商店({area}店)", "永辉超市({area}店)", "联华超市({area}店)",
        "华润万家({area}店)", "世纪联华({area}店)", "物美超市({area}店)", "大润发({area}店)",
        "ole精品超市({area}店)", "BHG生活超市({area}店)", "罗森({area}店)", "全家便利店({area}店)",
    ],
    8: [  # 书店
        "钟书阁({area}店)", "言几又({area}店)", "西西弗书店({area}店)", "单向空间({area}店)",
        "晓风书屋({area}店)", "纯真年代书吧({area}店)", "新华书店({area}店)", "博库书城({area}店)",
        "猫的天空之城({area}店)", "PageOne({area}店)", "樊登书店({area}店)", "茑屋书店({area}店)",
    ],
    9: [  # KTV
        "纯K({area}店)", "银乐迪({area}店)", "好乐迪({area}店)", "麦歌({area}店)",
        "唛歌({area}店)", "星聚会({area}店)", "PartyKing({area}店)", "魅KTV({area}店)",
    ],
    10: [ # 亲子乐园
        "奈尔宝家庭中心({area}店)", "MELAND({area}店)", "卡通尼乐园({area}店)", "小猪佩奇玩趣世界({area}店)",
        "弹力猩球({area}店)", "咕噜岛({area}店)", "幻贝家({area}店)", "宝乐迪({area}店)",
        "孩子王游乐园({area}店)", "乐高探索中心({area}店)", "奇乐儿({area}店)", "麦鲁小城({area}店)",
    ],
    11: [ # 电影院
        "万达影城({area}店)", "CGV影城({area}店)", "百老汇影城({area}店)", "博纳国际影城({area}店)",
        "大地影院({area}店)", "德信影城({area}店)", "中影国际({area}店)", "金逸影城({area}店)",
        "卢米埃影城({area}店)", "时代院线({area}店)", "光影影城({area}店)", "保利国际影城({area}店)",
    ],
    12: [ # 药店
        "九洲大药房({area}店)", "老百姓大药房({area}店)", "海王星辰({area}店)", "英特药房({area}店)",
        "华东大药房({area}店)", "胡庆余堂({area}店)", "方回春堂({area}店)", "桐君堂({area}店)",
    ],
    13: [ # 医院/诊所
        "浙大一院({area}院区)", "浙大二院({area}院区)", "邵逸夫医院({area}院区)", "省人民医院({area}院区)",
        "市一医院({area}分院)", "省中医院({area}分院)", "省儿保({area}院区)", "树兰医院({area}院区)",
        "牙博士口腔({area}店)", "通策口腔({area}店)", "艾维口腔({area}店)", "绿城口腔({area}店)",
    ],
}

# ========== 各类型的详情数据 ==========
SIGNATURE_DISHES = {
    1: ["清蒸鲈鱼,龙井虾仁,西湖醋鱼", "东坡肉,宋嫂鱼羹,干炸响铃", "叫花鸡,杭三鲜,葱包烩",
        "糖醋里脊,钱江肉丝,片儿川", "红烧肉,炒二冬,春笋步鱼", "老鸭煲,一品豆腐,龙井虾仁",
        "剁椒鱼头,梅干菜扣肉,桂花糖藕", "葱油蛏子,千张包,雪菜黄鱼"],
    2: ["片儿川,虾爆鳝面,猪肝面", "牛肉粉丝汤,葱油拌面,大排面", "番茄鸡蛋面,腰花面,黄鱼面",
        "酸菜鱼片面,三鲜面,大肠面", "笋干老鸭面,辣肉面,雪菜肉丝面"],
    3: ["麻辣牛肉,鲜鸭血,现炸酥肉", "牛油锅底,毛肚,鹅肠,黄喉", "虾滑,鱼片,肥牛卷,午餐肉",
        "耗儿鱼,鱿鱼须,郡肝,贡菜", "牛蛙,牛百叶,鸭舌,红糖糍粑"],
    4: ["拿铁,燕麦拿铁,冷萃咖啡", "海盐芝士奶茶,多肉葡萄,芝芝莓莓", "生椰拿铁,茉莉奶绿,桂花乌龙",
        "香草拿铁,澳白,冰美式", "抹茶拿铁,杨枝甘露,榛果拿铁"],
}

# Detail templates by type
DETAIL_REVIEWS = {
    1: ["杭帮菜老店，菜品少油少盐，食材以蒸煮为主，口味清淡适合老人。",
        "主打本地家常菜，分量足，价格亲民，常年排队。",
        "环境雅致，包厢私密性好，适合家庭聚餐和商务宴请。",
        "明档厨房，所见即所得，海鲜河鲜现点现做。",
        "融合菜系，既有传统杭帮也有川粤风味，选择丰富。",
        "排队较长但值得等待，糖醋里脊和龙井虾仁是招牌。",
        "装修风格偏新中式，有儿童区，服务员会主动帮忙分餐。"],
    2: ["面条筋道，汤头鲜美，是本地人从小吃到大老字号面馆。",
        "出餐快，性价比高，中午人特别多需要拼桌。",
        "浇头现炒，分量足，猪肝面和腰花面是人气王。",
        "24小时营业的深夜食堂，冬天一碗热面暖到心里。"],
    3: ["牛油锅底香醇浓郁，毛肚七上八下刚刚好。",
        "环境热闹，服务员会帮调蘸料，等位时有免费小吃。",
        "食材新鲜，鸭血是鲜鸭血，不是那种盒装的。",
        "适合朋友聚会，冬天吃特别暖，但还是有点辣。"],
    4: ["咖啡豆品质稳定，有燕麦奶可选，低因也有。",
        "店铺虽小但氛围感十足，适合办公或下午发呆。",
        "手冲有几种豆可选，冷萃夏天每天一杯。",
        "奶茶用料良心，水果茶新鲜现切，不会太甜。"],
    5: ["抓娃娃机多，大概每5次能抓到一个，不算太坑。",
        "周末人很多，游戏机种类齐全，有篮球机和赛车。",
        "环境偏嘈杂，灯光闪烁，适合年轻人，不太适合老人。",
        "有会员积分，1000积分可以兑换一个娃娃。"],
    6: ["款式更新快，基础款质量不错，价格亲民。",
        "导购不会一直跟着，试衣间充足不用排队。",
        "尺码齐全，从小码到加大码都有，材质标签清楚。",
        "童装区有试衣间和游乐区，小朋友换衣服不会闹。"],
    7: ["生鲜区品类丰富，蔬菜水果新鲜度好，日日清。",
        "支持APP下单30分钟送达，懒人福音。",
        "进口零食区特别大，日韩欧美都有，价格比代购便宜。",
        "晚8点后有折扣，熟食区和烘焙区是下班后的幸福。"],
    8: ["阅读区域充足，有免费座位和付费自习区。",
        "书品更新快，可以坐一天，咖啡味道也不错。",
        "儿童绘本区很大，周末经常有亲子阅读活动。",
        "安静，适合自习和工作，但高峰期座位紧张。"],
    9: ["包厢音质好，曲库更新快，抖音热歌都有。",
        "环境干净，无烟味，有自助小食和饮料。",
        "晚上和周末需要提前预约，生日当天有免费布置。",
        "巨幕包厢适合团建，可容纳20人。"],
    10: ["游乐设施定期消毒，进门要测体温洗手。",
        "海洋球池很大，滑梯种类多，小朋友可以玩一整天。",
        "有定位手环和专人看护，家长可以稍微放松。",
        "全场软包防撞，母婴室设备齐全。"],
    11: ["影厅座椅舒适，音效震撼，IMAX厅值得一看。",
        "售票处在线选座方便，爆米花有咸甜两种。",
        "家庭套票划算，周末亲子场经常满座。",
        "有免费毛毯借用，冷气有点大。"],
    12: ["药品齐全，有执业药师驻店，可以咨询用药。",
        "24小时营业，深夜急需很方便，支持医保。",
        "中药房可以代煎，也可以做成丸剂。",
        "国药老字号，药材品质有保障。"],
    13: ["门诊量较大，建议提前预约挂号。",
        "仪器设备先进，医生经验丰富。",
        "急诊24小时开放，有发热门诊专区。",
        "有无障碍通道和轮椅租借服务。"],
}

SERVICE_FEATURES = {
    1: ["支持电子支付,可开发票,有包厢", "可预订,免费停车,有宝宝椅",
        "明档厨房,支持外卖,生日布置", "大堂和包厢分区,扫码点餐,有包厢"],
    2: ["扫码点餐,吃完即走", "支持外卖,有冷气", "支持电子支付,出餐快"],
    3: ["提供围裙,免费眼镜布,手机套", "免费小吃等位,支持预订", "有包厢,提供一次性围裙"],
    4: ["免费WiFi,可堂食可外带", "有充电插座,支持会员积分", "有沙发座,可外送"],
    5: ["可办卡积分,支持团购", "有会员专属优惠", "提供币多送多活动"],
    6: ["支持7天退换,自助收银", "支持电子支付,礼品包装", "有导购协助"],
    7: ["支持配送到家,自助结账", "会员折扣,生鲜加工服务", "APP下单,支持电子支付"],
    8: ["可现场办会员,有阅读灯", "支持邮寄,有自习区", "安静区和非安静区分开"],
    9: ["支持提前预约,自助餐饮", "生日免费布置", "提供麦克风套"],
    10: ["提供定位手环,员工全程看护", "提供防滑袜", "入场需测体温"],
    11: ["提供3D眼镜,免费毛毯", "在线选座,自助取票", "家庭套票,轮椅专座"],
    12: ["医保可用,执业药师驻店", "免费量血压,代煎中药", "送药上门,24小时"],
    13: ["支持医保,可预约挂号", "有急诊,代煎中药", "无障碍通道,轮椅租借"],
}

VOUCHER_TEMPLATES = [
    ("满300减50", "全场通用", "FULL_REDUCTION", 300, 50),
    ("满200减30", "全场通用", "FULL_REDUCTION", 200, 30),
    ("满100减15", "指定商品可用", "FULL_REDUCTION", 100, 15),
    ("满500减80", "全场通用", "FULL_REDUCTION", 500, 80),
    ("全场8.8折", "除特价商品", "DISCOUNT", 0, 0),
    ("新人专享立减20", "首次消费可用", "SPECIAL", 0, 20),
    ("满150减25", "指定品类可用", "FULL_REDUCTION", 150, 25),
    ("买一送一", "指定饮品", "SPECIAL", 0, 0),
    ("工作日午餐9折", "工作日11:00-14:00", "DISCOUNT", 0, 0),
    ("家庭套餐立减40", "2大人1小孩可用", "SPECIAL", 250, 40),
]


def gen():
    shop_id = 1001
    voucher_id = 5001
    sql = []
    used_names = {}

    # 保证每种类型至少有若干店铺
    type_counts = {t: 0 for t in SHOP_TYPES}
    target_per_type = 38  # 500 / 13 ≈ 38

    sql.append("SET FOREIGN_KEY_CHECKS = 0;")
    sql.append("TRUNCATE TABLE voucher;")
    sql.append("TRUNCATE TABLE shop_detail;")
    sql.append("TRUNCATE TABLE shop;")
    sql.append("TRUNCATE TABLE shop_type;")
    sql.append("SET FOREIGN_KEY_CHECKS = 1;")
    sql.append("")
    sql.append("-- 店铺类型")
    sql.append("INSERT INTO shop_type (id, type_code, type_name, description) VALUES")
    type_lines = []
    for tid, tname in SHOP_TYPES.items():
        type_code_map = {1: 'RESTAURANT', 2: 'NOODLE', 3: 'HOTPOT', 4: 'CAFE',
                         5: 'GAME_HALL', 6: 'CLOTHING', 7: 'SUPERMARKET', 8: 'BOOKSTORE',
                         9: 'KTV', 10: 'FAMILY_ENTERTAINMENT', 11: 'CINEMA', 12: 'PHARMACY', 13: 'HOSPITAL'}
        type_lines.append(f"({tid}, '{type_code_map[tid]}', '{tname}', '提供{tname}相关服务')")
    sql.append(",\n".join(type_lines) + ";")
    sql.append("")

    shops_sql = []
    details_sql = []
    vouchers_sql = []

    # 先均匀分配
    shops_per_type = {}
    for t in range(1, 14):
        # 某些类型多放些（餐厅、超市、咖啡店更常见）
        if t in (1, 7, 4):
            n = random.randint(60, 70)
        elif t in (2, 3, 6):
            n = random.randint(45, 55)
        elif t in (5, 11, 10):
            n = random.randint(30, 40)
        else:
            n = random.randint(18, 30)
        shops_per_type[t] = n

    total = sum(shops_per_type.values())
    # 调整到正好500
    while total < 500:
        t = 1  # 多给餐厅
        shops_per_type[t] += 1
        total += 1
    while total > 500:
        t = list(shops_per_type.keys())[-1]
        if shops_per_type[t] > 10:
            shops_per_type[t] -= 1
            total -= 1

    for shop_type_id, count in shops_per_type.items():
        names = SHOP_NAMES[shop_type_id]
        for _ in range(count):
            area = random.choice(list(DISTRICTS.keys()))
            street = random.choice(DISTRICTS[area])
            door_num = random.randint(1, 999)
            address = f"{area}区{street}{door_num}号"
            name_tpl = random.choice(names)
            shop_name = name_tpl.format(area=area)
            # 避免重名
            if shop_name in used_names:
                shop_name = shop_name.replace(f"({area}店)", f"({area}{random.randint(2,10)}号店)")
            used_names[shop_name] = True

            avg_price = random.choice([25, 35, 45, 55, 65, 80, 95, 110, 130, 160, 200, 250, 300, 350])
            rating = round(random.uniform(3.8, 5.0), 1)
            suitable = 1 if random.random() < 0.55 else 0
            children = 1 if shop_type_id in (6, 10) or random.random() < 0.25 else 0
            open_hour = random.choice(["08:00-22:00", "10:00-21:30", "10:30-22:00", "11:00-02:00",
                                       "09:00-21:00", "06:00-20:00", "10:00-24:00", "00:00-24:00",
                                       "07:00-23:00", "12:00-02:00"])

            # Tags
            tag_pool = []
            if suitable:
                tag_pool.append(random.choice(["口味清淡", "有电梯", "无障碍通道", "防滑地砖", "包厢多"]))
            if children:
                tag_pool.append(random.choice(["提供宝宝椅", "儿童游乐区", "儿童餐", "母婴室相邻"]))
            tag_pool.append(random.choice(["包厢多", "有包厢", "明档厨房", "大堂宽敞", "临街店铺"]))
            tag_pool.append(random.choice(["支持外卖", "免费WiFi", "冷气充足", "有停车场", "环境安静"]))
            tags = ",".join(random.sample(tag_pool, min(3, len(tag_pool))))

            shops_sql.append(
                f"INSERT INTO shop (id, shop_name, shop_type_id, area, address, average_price, rating, "
                f"open_now, suitable_for_elderly, has_children_play_area, business_hours, tags) VALUES "
                f"({shop_id}, '{shop_name}', {shop_type_id}, '{area}', '{address}', {avg_price}, "
                f"{rating}, 1, {suitable}, {children}, '{open_hour}', '{tags}');"
            )

            # Detail
            review = random.choice(DETAIL_REVIEWS[shop_type_id])
            dishes = random.choice(SIGNATURE_DISHES.get(shop_type_id, [""]))
            service = random.choice(SERVICE_FEATURES[shop_type_id])
            env = random.choice(["环境雅致，灯光温暖", "装修简约现代", "格局宽敞明亮",
                                 "中式风格，有书画装饰", "偏日式简约风", "工业风装修",
                                 "温馨家庭风格", "干净整洁"])
            details_sql.append(
                f"INSERT INTO shop_detail (shop_id, review_summary, signature_dishes, service_features, environment_desc) VALUES "
                f"({shop_id}, '{review}', '{dishes}', '{service}', '{env}');"
            )

            # Voucher (~70% have vouchers, some have 2)
            n_vouchers = random.choices([0, 1, 2], weights=[30, 55, 15])[0]
            for vi in range(n_vouchers):
                v = random.choice(VOUCHER_TEMPLATES)
                title = v[0]
                scope = v[1]
                dtype = v[2]
                threshold = v[3]
                discount = v[4]
                days = random.randint(30, 365)
                valid_from = f"2026-01-01 00:00:00"
                valid_to = f"2026-12-31 23:59:59"
                status = "ACTIVE" if random.random() < 0.85 else random.choice(["ACTIVE", "ACTIVE", "EXPIRING_SOON"])
                vouchers_sql.append(
                    f"INSERT INTO voucher (id, shop_id, voucher_code, title, applicable_scope, discount_type, "
                    f"threshold_amount, discount_amount, valid_from, valid_to, status) VALUES "
                    f"({voucher_id}, {shop_id}, 'V{voucher_id}', '{title}', '{scope}', '{dtype}', "
                    f"{threshold}, {discount}, '{valid_from}', '{valid_to}', '{status}');"
                )
                voucher_id += 1

            shop_id += 1

    sql.append("-- 店铺主数据")
    sql.extend(shops_sql)
    sql.append("")
    sql.append("-- 店铺详情")
    sql.extend(details_sql)
    sql.append("")
    sql.append("-- 优惠券")
    sql.extend(vouchers_sql)

    with open("/mnt/e/ZTE/progect/family-life-agent/family-agent/family-life-agent-app/src/main/resources/500_shops.sql", "w", encoding="utf-8") as f:
        f.write("\n".join(sql))

    print(f"Generated {shop_id - 1001} shops, {voucher_id - 5001} vouchers")
    print(f"Type distribution: {shops_per_type}")


if __name__ == "__main__":
    gen()
