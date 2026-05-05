#!/usr/bin/env python3
"""
生成与当前 schema 一致的批量测试 SQL（默认 500 家店）。

输出内容包含：
1) 清空 voucher / shop_detail / shop / shop_type
2) 插入 13 类 shop_type
3) 插入 shop（含 suitable_for_elderly、has_children_play_area）
4) 插入 shop_detail
5) 插入 voucher

用法：
  python generate_sql.py
  python generate_sql.py --output family-life-agent-app/src/main/resources/500_shops.sql --seed 2026
"""

from __future__ import annotations

import argparse
import random
from dataclasses import dataclass
from pathlib import Path


AREAS = ["西湖", "滨江", "拱墅", "上城", "余杭"]

SHOP_TYPES = [
    (1, "RESTAURANT", "餐厅", "提供正餐、聚餐服务"),
    (2, "NOODLE", "面馆/简餐", "提供面食、快餐、小吃"),
    (3, "HOTPOT", "火锅店", "提供火锅、串串服务"),
    (4, "CAFE", "咖啡/茶饮", "提供咖啡、奶茶、烘焙甜品"),
    (5, "GAME_HALL", "游戏厅", "提供街机、抓娃娃等电玩设备"),
    (6, "CLOTHING", "服装店", "提供男女装、童装零售"),
    (7, "SUPERMARKET", "超市/便利店", "提供日常生鲜、日用品采购"),
    (8, "BOOKSTORE", "书店", "提供图书零售、阅读空间"),
    (9, "KTV", "KTV", "提供包厢唱歌娱乐"),
    (10, "FAMILY_ENTERTAINMENT", "亲子乐园", "提供海洋球、儿童攀爬等设施"),
    (11, "CINEMA", "电影院", "提供院线电影放映"),
    (12, "PHARMACY", "药店", "提供非处方药、医疗器械零售"),
    (13, "HOSPITAL", "医院/诊所", "提供全科、儿科、急诊等医疗服务"),
]


@dataclass(frozen=True)
class TypeConfig:
    type_name: str
    count: int
    avg_price: tuple[int, int]
    rating: tuple[float, float]
    hours: tuple[str, ...]
    tags: tuple[str, ...]


DISTRIBUTION: dict[int, TypeConfig] = {
    1: TypeConfig("餐厅", 120, (35, 180), (4.1, 5.0), ("10:00-21:30", "10:30-22:00"),
                  ("杭帮菜", "川菜", "粤菜", "口味清淡", "无障碍通道", "有电梯", "防滑地砖", "提供宝宝椅", "包厢多")),
    2: TypeConfig("面馆简餐", 80, (18, 65), (4.0, 4.9), ("06:30-20:30", "07:00-21:00"),
                  ("碳水快餐", "出餐快", "临街一楼", "扫码点单", "汤底清淡", "全麦面", "防滑地砖")),
    3: TypeConfig("火锅店", 40, (60, 220), (4.1, 4.9), ("11:00-23:00", "11:00-02:00"),
                  ("粥底火锅", "重麻重辣", "营业到凌晨", "食材软烂", "有宝宝椅", "排队较久", "防滑地砖")),
    4: TypeConfig("咖啡茶饮", 40, (20, 68), (4.0, 5.0), ("08:00-22:00", "09:00-23:00"),
                  ("无糖选项", "有沙发座", "免费WiFi", "提供热饮", "禁止吸烟", "环境安静", "无障碍通道")),
    5: TypeConfig("游戏厅", 15, (50, 150), (4.0, 4.8), ("10:00-22:00", "10:00-24:00"),
                  ("抓娃娃", "赛车模拟", "噪音较大", "灯光闪烁", "需兑换游戏币", "有电梯")),
    6: TypeConfig("服装店", 90, (80, 420), (4.0, 4.9), ("10:00-22:00", "09:30-21:30"),
                  ("童装", "中老年女装", "A类材质", "纯棉材质", "保暖内衣", "宽敞试衣间", "无障碍通道")),
    7: TypeConfig("超市便利店", 50, (15, 120), (4.0, 4.9), ("08:00-22:00", "00:00-24:00"),
                  ("生鲜", "半成品菜", "自助结账", "支持配送", "24小时便利", "无障碍通道", "防滑地砖")),
    8: TypeConfig("书店", 15, (20, 120), (4.1, 4.9), ("10:00-22:00", "09:30-21:30"),
                  ("儿童绘本区", "需保持安静", "提供阅读灯", "有软垫座位", "无障碍通道")),
    9: TypeConfig("KTV", 15, (60, 220), (4.0, 4.8), ("12:00-02:00", "13:00-03:00"),
                  ("巨幕包厢", "隔音极佳", "独立洗手间", "全场禁烟", "有电梯")),
    10: TypeConfig("亲子乐园", 15, (120, 320), (4.2, 5.0), ("10:00-21:00", "10:00-22:00"),
                   ("海洋球池", "提供防滑袜", "全场软包", "有母婴室", "温奶器", "无障碍通道")),
    11: TypeConfig("电影院", 8, (35, 100), (4.2, 4.9), ("10:00-24:00",),
                   ("IMAX巨幕", "提供毛毯", "家庭套票", "轮椅专座", "有电梯", "声光效果强")),
    12: TypeConfig("药店", 8, (10, 90), (4.1, 4.9), ("00:00-24:00", "08:00-23:00"),
                   ("24小时营业", "医保可用", "血压测量", "送药上门", "临街一层", "执业药师驻店")),
    13: TypeConfig("医院诊所", 4, (20, 180), (4.2, 4.9), ("00:00-24:00", "08:00-17:00"),
                   ("三甲医院", "社区全科", "儿科", "急诊", "无障碍通道", "提供轮椅租借", "有电梯")),
}


def sql_escape(value: str) -> str:
    return value.replace("'", "''")


def pick_tags(config: TypeConfig) -> list[str]:
    k = random.choice((3, 4, 5))
    return random.sample(list(config.tags), k=min(k, len(config.tags)))


def calc_suitable_for_elderly(tags: list[str]) -> int:
    elder_friendly_physical = {"无障碍通道", "有电梯", "防滑地砖", "临街一层", "提供轮椅租借"}
    return 1 if any(t in elder_friendly_physical for t in tags) else 0


def calc_has_children_play_area(type_id: int, tags: list[str]) -> int:
    child_keywords = {"海洋球池", "有母婴室", "温奶器", "提供防滑袜", "抓娃娃", "赛车模拟", "儿童绘本区", "提供宝宝椅"}
    if type_id == 10:
        return 1
    if type_id in (5, 8, 1) and any(t in child_keywords for t in tags):
        return 1
    return 0


def make_voucher(type_id: int, shop_id: int) -> tuple[str, str, float, float, str]:
    if type_id in (1, 3, 9):
        return ("满200减30", "FULL_REDUCTION", 200.0, 30.0, "全场通用")
    if type_id in (2, 4, 8):
        return ("满50减8", "FULL_REDUCTION", 50.0, 8.0, "指定商品可用")
    if type_id == 10:
        return ("工作日单大一小门票", "SPECIAL", 198.0, 50.0, "周一至周五可用")
    if type_id == 11:
        return ("家庭3人观影套票", "SPECIAL", 150.0, 40.0, "指定2D/3D场次")
    if type_id == 12:
        return ("夜间购药立减5元", "SPECIAL", 0.0, 5.0, "非处方药可用")
    if type_id == 13:
        return ("门诊检查立减20元", "SPECIAL", 100.0, 20.0, "普通门诊可用")
    return ("第二件9折", "SPECIAL", 0.0, 0.0, "指定商品可用")


def generate(output: Path) -> None:
    total = sum(c.count for c in DISTRIBUTION.values())
    if total != 500:
        raise ValueError(f"配置总数必须为500，当前为 {total}")

    lines: list[str] = []
    lines.append("SET FOREIGN_KEY_CHECKS = 0;")
    lines.append("TRUNCATE TABLE voucher;")
    lines.append("TRUNCATE TABLE shop_detail;")
    lines.append("TRUNCATE TABLE shop;")
    lines.append("TRUNCATE TABLE shop_type;")
    lines.append("SET FOREIGN_KEY_CHECKS = 1;")
    lines.append("")

    lines.append("INSERT INTO shop_type (id, type_code, type_name, description) VALUES")
    for i, (tid, code, name, desc) in enumerate(SHOP_TYPES):
        suffix = "," if i < len(SHOP_TYPES) - 1 else ";"
        lines.append(f"({tid}, '{code}', '{name}', '{desc}'){suffix}")
    lines.append("")

    shop_id = 1001
    voucher_id = 5001

    for type_id, config in DISTRIBUTION.items():
        for _ in range(config.count):
            area = random.choice(AREAS)
            address = f"{area}区建设路{random.randint(1, 999)}号"
            name = f"{area}{config.type_name}_{shop_id}"
            average_price = random.randint(*config.avg_price)
            rating = round(random.uniform(*config.rating), 1)
            business_hours = random.choice(config.hours)

            tags = pick_tags(config)
            tags_text = ",".join(tags)
            suitable_for_elderly = calc_suitable_for_elderly(tags)
            has_children_play_area = calc_has_children_play_area(type_id, tags)

            lines.append(
                "INSERT INTO shop "
                "(id, shop_name, shop_type_id, area, address, average_price, rating, open_now, suitable_for_elderly, has_children_play_area, business_hours, tags) VALUES "
                f"({shop_id}, '{sql_escape(name)}', {type_id}, '{area}', '{sql_escape(address)}', {average_price}, {rating}, 1, {suitable_for_elderly}, {has_children_play_area}, '{business_hours}', '{sql_escape(tags_text)}');"
            )

            lines.append(
                "INSERT INTO shop_detail "
                "(shop_id, review_summary, signature_dishes, service_features, environment_desc) VALUES "
                f"({shop_id}, '客观设施评价: {sql_escape(tags_text)}', '', '支持电子支付,可开发票', '物理环境: {sql_escape(tags_text)}');"
            )

            title, discount_type, threshold_amount, discount_amount, scope = make_voucher(type_id, shop_id)
            lines.append(
                "INSERT INTO voucher "
                "(id, shop_id, voucher_code, title, applicable_scope, discount_type, threshold_amount, discount_amount, valid_from, valid_to, status) VALUES "
                f"({voucher_id}, {shop_id}, 'V{voucher_id}', '{sql_escape(title)}', '{sql_escape(scope)}', '{discount_type}', {threshold_amount:.2f}, {discount_amount:.2f}, "
                "'2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ACTIVE');"
            )

            shop_id += 1
            voucher_id += 1

    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="生成 500 家店铺 SQL 文件")
    parser.add_argument(
        "--output",
        default="500_shops.sql",
        help="输出 SQL 文件路径（默认: 500_shops.sql）",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=2026,
        help="随机种子，保证可复现（默认: 2026）",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    random.seed(args.seed)
    output = Path(args.output)
    generate(output)
    print(f"已生成: {output.resolve()}")


if __name__ == "__main__":
    main()
