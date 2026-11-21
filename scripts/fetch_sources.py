"""
StarTV 每日电视源聚合脚本
=========================
从多个开源维护的 IPTV 直播源(m3u)中抓取中国所有 CCTV 频道 + 37 个省级卫视,
合并去重、校验可用性后, 以固定 JSON 格式输出到仓库根目录 startv.json。

输出格式:
{
  "update_time": "2026-11-06",
  "version": 1,
  "channels": [
    {"name": "CCTV-1 综合", "group": "央视", "logo": "http://...", "urls": ["http://..."]},
    ...
  ]
}
"""

import concurrent.futures
import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone

import requests

# ---------------------------------------------------------------------------
# 配置
# ---------------------------------------------------------------------------

# 多个开源维护的直播源(按优先级排序, 前面的源命中的 URL 排在前面)
SOURCE_URLS = [
    "https://live.fanmingming.com/v6/e.m3u",
    "https://raw.githubusercontent.com/vbskycn/iptv/master/tv/hd/iptv4.m3u",
    "https://raw.githubusercontent.com/iptv-org/iptv/streams/cn.m3u",
    "https://raw.githubusercontent.com/Guovin/iptv-api/gd/output/result.m3u",
    "https://raw.githubusercontent.com/suxuang/myIPTV/main/ipv4.m3u",
    "https://raw.githubusercontent.com/YueChan/Live/main/Aptv.m3u",
]

# 每个频道最多保留几条(已校验)的播放地址
MAX_URLS_PER_CHANNEL = 10
# URL 校验超时(秒)
URL_TIMEOUT = 8
# 校验并发数
MAX_WORKERS = 64

OUTPUT_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "startv.json")

# ---------------------------------------------------------------------------
# 目标频道定义
# ---------------------------------------------------------------------------

# CCTV 频道: (编号, 是否带+, 允许的名称后缀, 显示名)
CCTV_CHANNELS = [
    (1,  False, {"", "综合", "综合频道"},        "CCTV-1 综合"),
    (2,  False, {"", "财经", "财经频道"},        "CCTV-2 财经"),
    (3,  False, {"", "综艺", "综艺频道"},        "CCTV-3 综艺"),
    (4,  False, {"", "中文国际", "国际", "中文国际频道"}, "CCTV-4 中文国际"),
    (5,  False, {"", "体育", "体育频道"},        "CCTV-5 体育"),
    (5,  True,  {"", "体育赛事", "赛事", "体育频道", "体育"}, "CCTV-5+ 体育赛事"),
    (6,  False, {"", "电影", "电影频道"},        "CCTV-6 电影"),
    (7,  False, {"", "国防军事", "军事", "军事农业", "农业"}, "CCTV-7 国防军事"),
    (8,  False, {"", "电视剧", "电视剧频道"},    "CCTV-8 电视剧"),
    (9,  False, {"", "纪录", "纪录频道"},        "CCTV-9 纪录"),
    (10, False, {"", "科教", "科教频道"},        "CCTV-10 科教"),
    (11, False, {"", "戏曲", "戏曲频道"},        "CCTV-11 戏曲"),
    (12, False, {"", "社会与法", "法制", "法律"}, "CCTV-12 社会与法"),
    (13, False, {"", "新闻", "新闻频道"},        "CCTV-13 新闻"),
    (14, False, {"", "少儿", "少儿频道"},        "CCTV-14 少儿"),
    (15, False, {"", "音乐", "音乐频道"},        "CCTV-15 音乐"),
    (16, False, {"", "奥林匹克", "奥运", "奥运频道"}, "CCTV-16 奥林匹克"),
    (17, False, {"", "农业农村", "农业"},        "CCTV-17 农业农村"),
]

# 省级卫视: (匹配关键字列表, 显示名) —— 关键字命中即视为该频道
SATELLITE_CHANNELS = [
    (["北京卫视"],                 "北京卫视"),
    (["湖南卫视"],                 "湖南卫视"),
    (["浙江卫视"],                 "浙江卫视"),
    (["江苏卫视"],                 "江苏卫视"),
    (["东方卫视", "上海卫视"],     "东方卫视"),
    (["广东卫视"],                 "广东卫视"),
    (["深圳卫视"],                 "深圳卫视"),
    (["山东卫视"],                 "山东卫视"),
    (["安徽卫视"],                 "安徽卫视"),
    (["天津卫视"],                 "天津卫视"),
    (["重庆卫视"],                 "重庆卫视"),
    (["湖北卫视"],                 "湖北卫视"),
    (["四川卫视"],                 "四川卫视"),
    (["辽宁卫视"],                 "辽宁卫视"),
    (["黑龙江卫视", "龙视卫视"],   "黑龙江卫视"),
    (["吉林卫视"],                 "吉林卫视"),
    (["河南卫视"],                 "河南卫视"),
    (["河北卫视"],                 "河北卫视"),
    (["山西卫视"],                 "山西卫视"),
    (["陕西卫视"],                 "陕西卫视"),
    (["甘肃卫视"],                 "甘肃卫视"),
    (["青海卫视"],                 "青海卫视"),
    (["云南卫视"],                 "云南卫视"),
    (["贵州卫视"],                 "贵州卫视"),
    (["广西卫视"],                 "广西卫视"),
    (["海南卫视", "旅游卫视"],     "海南卫视"),
    (["江西卫视"],                 "江西卫视"),
    (["福建卫视"],                 "福建卫视"),
    (["东南卫视"],                 "东南卫视"),
    (["宁夏卫视"],                 "宁夏卫视"),
    (["新疆卫视"],                 "新疆卫视"),
    (["西藏卫视", "西藏藏语"],     "西藏卫视"),
    (["内蒙古卫视", "蒙古语卫视频道"], "内蒙古卫视"),
    (["兵团卫视"],                 "兵团卫视"),
    (["三沙卫视"],                 "三沙卫视"),
    (["厦门卫视"],                 "厦门卫视"),
    (["延边卫视"],                 "延边卫视"),
    (["康巴卫视"],                 "康巴卫视"),
    (["安多卫视"],                 "安多卫视"),
]

# ---------------------------------------------------------------------------


def normalize_name(name: str) -> str:
    """归一化频道名, 便于匹配。"""
    n = (name or "").strip().upper()
    n = re.sub(r"\s+", "", n)
    n = n.replace("－", "-").replace("—", "-").replace("–", "-")
    # 去掉括号备注
    n = re.sub(r"[(（\[【].*?[)）\]】]", "", n)
    # 去掉画质/清晰度等修饰词
    n = re.sub(r"(高清|标清|超清|蓝光|真高清|4K|8K|FHD|UHD|1080P|720P|480P|频道)", "", n, flags=re.I)
    return n


def match_channel(raw_name: str):
    """把源里的频道名映射到目标频道, 返回 (group, display_name) 或 None。"""
    n = normalize_name(raw_name)

    # --- CCTV ---
    m = re.match(r"^CCTV[-]?(\d{1,2})(\+?)", n)
    if m:
        num = int(m.group(1))
        plus = m.group(2) == "+"
        suffix = n[m.end():]
        # 去掉可能的尾缀 "NEW"/"VIP" 等
        suffix = re.sub(r"(NEW|VIP|CHANNEL)", "", suffix)
        for cnum, cplus, allowed, display in CCTV_CHANNELS:
            if cnum == num and cplus == plus and suffix in allowed:
                return "央视", display
        return None

    # --- 卫视 ---
    for keys, display in SATELLITE_CHANNELS:
        for k in keys:
            if k in n:
                # 防止 "湖南卫视金鹰卡通" 之类的干扰(很少见, 保留亦可, 此处直接放行)
                return "卫视", display
    return None


def parse_m3u(text: str):
    """解析 m3u 文本, 返回 [(raw_name, logo, url), ...]"""
    result = []
    lines = text.splitlines()
    name, logo = None, ""
    for line in lines:
        line = line.strip()
        if not line:
            continue
        if line.startswith("#EXTINF"):
            m = re.search(r'tvg-name="([^"]*)"', line)
            name = m.group(1) if m else None
            if not name:
                # 回退: 取最后一个逗号后面的文本
                idx = line.rfind(",")
                name = line[idx + 1:] if idx >= 0 else ""
            m = re.search(r'tvg-logo="([^"]*)"', line)
            logo = m.group(1) if m else ""
        elif line.startswith("#"):
            continue
        else:
            url = line
            if name:
                result.append((name, logo, url))
            name, logo = None, ""
    return result


def fetch_source(url: str):
    """下载一个 m3u 源, 返回 [(raw_name, logo, url), ...]"""
    try:
        r = requests.get(url, timeout=20, headers={"User-Agent": "Mozilla/5.0 StarTV/1.0"})
        r.raise_for_status()
        items = parse_m3u(r.text)
        print(f"[source] {url} -> {len(items)} entries")
        return items
    except Exception as e:  # noqa: BLE001
        print(f"[source] {url} FAILED: {e}")
        return []


def check_url(url: str):
    """快速校验直播地址是否可用, 可用返回 url, 否则 None。"""
    try:
        with requests.get(
            url,
            timeout=URL_TIMEOUT,
            stream=True,
            headers={"User-Agent": "Mozilla/5.0 StarTV/1.0"},
        ) as r:
            if r.status_code != 200:
                return None
            chunk = next(r.iter_content(chunk_size=2048), b"")
            if not chunk:
                return None
            # HTML 错误页直接判死
            head = chunk[:64].lstrip().lower()
            if head.startswith(b"<") or head.startswith(b"<!"):
                return None
            return url
    except Exception:  # noqa: BLE001
        return None


def main():
    # 1. 抓取所有源
    all_entries = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(SOURCE_URLS)) as ex:
        for items in ex.map(fetch_source, SOURCE_URLS):
            all_entries.append(items)

    # 2. 按目标频道聚合(保序: 源优先级 -> 出现顺序)
    #    channels[name] = {"group", "logo", "urls"(有序去重)}
    channels = {}
    order_hint = {}
    for source_idx, items in enumerate(all_entries):
        for raw_name, logo, url in items:
            matched = match_channel(raw_name)
            if not matched:
                continue
            group, display = matched
            c = channels.setdefault(display, {"group": group, "logo": "", "urls": []})
            if not c["logo"] and logo:
                c["logo"] = logo
            url = url.strip()
            if url not in c["urls"]:
                c["urls"].append(url)
            order_hint.setdefault(display, len(order_hint))

    # 3. 并发校验所有 URL
    tasks = []
    for display, c in channels.items():
        for url in c["urls"]:
            tasks.append((display, url))
    print(f"[validate] {len(tasks)} urls to check ...")

    ok_map = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as ex:
        future_map = {ex.submit(check_url, u): (d, u) for d, u in tasks}
        for fut in concurrent.futures.as_completed(future_map):
            display, url = future_map[fut]
            if fut.result():
                ok_map.setdefault(display, []).append(url)

    # 4. 组装输出(按预定义顺序: 央视在前, 卫视在后)
    target_order = [d for _, _, _, d in CCTV_CHANNELS] + [d for _, d in SATELLITE_CHANNELS]
    out_channels = []
    for display in sorted(channels.keys(), key=lambda d: order_hint.get(d, 999)):
        display_rank = target_order.index(display) if display in target_order else 999
        c = channels[display]
        urls = ok_map.get(display, [])[:MAX_URLS_PER_CHANNEL]
        if not urls:
            continue
        out_channels.append({
            "name": display,
            "group": c["group"],
            "logo": c["logo"],
            "urls": urls,
        })
        _ = display_rank

    # 排序: 央视 -> 卫视
    out_channels.sort(key=lambda ch: (0 if ch["group"] == "央视" else 1, target_order.index(ch["name"]) if ch["name"] in target_order else 999))

    # 5. 输出 JSON (北京时间)
    tz = timezone(timedelta(hours=8))
    update_time = datetime.now(tz).strftime("%Y-%m-%d")
    data = {"update_time": update_time, "version": 1, "channels": out_channels}

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    total_urls = sum(len(c["urls"]) for c in out_channels)
    print(f"[done] update_time={update_time} channels={len(out_channels)} urls={total_urls}")
    print(f"[done] written to {OUTPUT_PATH}")
    if len(out_channels) == 0:
        print("[warn] no channels fetched, keep previous file!", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
