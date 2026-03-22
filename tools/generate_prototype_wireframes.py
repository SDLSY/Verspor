from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(r"D:\newstart")
OUT_DIR = ROOT / "docs" / "image" / "项目开发文档原型图"

CANVAS_W = 1800
CANVAS_H = 1080

BG = "#F4F7FB"
SURFACE = "#FFFFFF"
SURFACE_ALT = "#F7FAFF"
PRIMARY = "#1B9CE5"
PRIMARY_DARK = "#0B5D87"
PRIMARY_SOFT = "#DFF2FF"
ACCENT = "#57C6FF"
TEXT = "#182533"
TEXT_MUTED = "#6C7A89"
BORDER = "#D8E3F0"
SHADOW = "#DBE4EF"
SUCCESS = "#49B675"
WARNING = "#FFB86A"
DANGER = "#F26B63"
NAV_BG = "#F2F8FD"
MENU_BG = "#0F1A2A"


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = []
    if bold:
        candidates += [
            r"C:\Windows\Fonts\simhei.ttf",
            r"C:\Windows\Fonts\Dengb.ttf",
            r"C:\Windows\Fonts\msyhbd.ttc",
            r"C:\Windows\Fonts\arialbd.ttf",
        ]
    else:
        candidates += [
            r"C:\Windows\Fonts\simhei.ttf",
            r"C:\Windows\Fonts\Deng.ttf",
            r"C:\Windows\Fonts\msyh.ttc",
            r"C:\Windows\Fonts\arial.ttf",
        ]
    for candidate in candidates:
        p = Path(candidate)
        if p.exists():
            return ImageFont.truetype(str(p), size=size)
    return ImageFont.load_default()


TITLE_FONT = font(38, bold=True)
SUBTITLE_FONT = font(22)
SECTION_FONT = font(24, bold=True)
LABEL_FONT = font(20)
SMALL_FONT = font(16)
NUMBER_FONT = font(18, bold=True)


def rounded(draw: ImageDraw.ImageDraw, box, radius=26, fill=SURFACE, outline=BORDER, width=2):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def shadowed_panel(base: Image.Image, box, radius=28, fill=SURFACE, outline=BORDER, width=2, shadow_offset=(0, 10)):
    shadow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sx1, sy1, sx2, sy2 = box
    dx, dy = shadow_offset
    sd.rounded_rectangle((sx1 + dx, sy1 + dy, sx2 + dx, sy2 + dy), radius=radius, fill=SHADOW)
    shadow = shadow.filter(ImageFilter.GaussianBlur(12))
    base.alpha_composite(shadow)
    draw = ImageDraw.Draw(base)
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def text(draw: ImageDraw.ImageDraw, xy, content: str, fnt, fill=TEXT):
    draw.text(xy, content, font=fnt, fill=fill)


def wrapped_text(draw: ImageDraw.ImageDraw, box, content: str, fnt, fill=TEXT, line_gap=8):
    x1, y1, x2, y2 = box
    words = []
    for part in content.split("\n"):
        if part:
            words.extend(list(part))
        words.append("\n")
    lines: list[str] = []
    current = ""
    for ch in words:
        if ch == "\n":
            lines.append(current)
            current = ""
            continue
        probe = current + ch
        width = draw.textbbox((0, 0), probe, font=fnt)[2]
        if width > (x2 - x1) and current:
            lines.append(current)
            current = ch
        else:
            current = probe
    if current:
        lines.append(current)
    y = y1
    for line in lines:
        draw.text((x1, y), line, font=fnt, fill=fill)
        y += draw.textbbox((0, 0), line or " ", font=fnt)[3] + line_gap


def number_badge(draw: ImageDraw.ImageDraw, x: int, y: int, n: int):
    draw.rounded_rectangle((x, y, x + 38, y + 30), radius=14, fill=PRIMARY_DARK)
    tw = draw.textbbox((0, 0), str(n), font=NUMBER_FONT)[2]
    th = draw.textbbox((0, 0), str(n), font=NUMBER_FONT)[3]
    draw.text((x + (38 - tw) / 2, y + (30 - th) / 2 - 2), str(n), font=NUMBER_FONT, fill="white")


def card(draw: ImageDraw.ImageDraw, box, title: str, n: int | None = None, subtitle: str | None = None, fill=SURFACE):
    rounded(draw, box, radius=26, fill=fill)
    x1, y1, x2, y2 = box
    if n is not None:
        number_badge(draw, x2 - 50, y1 + 12, n)
    text(draw, (x1 + 22, y1 + 18), title, SECTION_FONT)
    if subtitle:
        wrapped_text(draw, (x1 + 22, y1 + 58, x2 - 24, y2 - 16), subtitle, SMALL_FONT, fill=TEXT_MUTED)


def button(draw: ImageDraw.ImageDraw, box, title: str, n: int | None = None, primary=True):
    fill = PRIMARY if primary else SURFACE
    outline = PRIMARY if primary else BORDER
    rounded(draw, box, radius=22, fill=fill, outline=outline)
    x1, y1, x2, y2 = box
    if n is not None:
        number_badge(draw, x2 - 48, y1 + 10, n)
    label_fill = "white" if primary else TEXT
    bbox = draw.textbbox((0, 0), title, font=LABEL_FONT)
    draw.text((x1 + (x2 - x1 - bbox[2]) / 2, y1 + (y2 - y1 - bbox[3]) / 2 - 2), title, font=LABEL_FONT, fill=label_fill)


def chip(draw: ImageDraw.ImageDraw, box, title: str, active=False):
    fill = PRIMARY_SOFT if active else NAV_BG
    outline = PRIMARY if active else BORDER
    rounded(draw, box, radius=18, fill=fill, outline=outline)
    x1, y1, x2, y2 = box
    bbox = draw.textbbox((0, 0), title, font=SMALL_FONT)
    draw.text((x1 + (x2 - x1 - bbox[2]) / 2, y1 + (y2 - y1 - bbox[3]) / 2 - 1), title, font=SMALL_FONT, fill=PRIMARY_DARK if active else TEXT_MUTED)


def icon_circle(draw: ImageDraw.ImageDraw, cx, cy, r, fill=PRIMARY, label=None):
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=fill)
    if label:
        bbox = draw.textbbox((0, 0), label, font=SMALL_FONT)
        draw.text((cx - bbox[2] / 2, cy - bbox[3] / 2 - 1), label, font=SMALL_FONT, fill="white")


def draw_chart(draw: ImageDraw.ImageDraw, box, bars=False):
    x1, y1, x2, y2 = box
    rounded(draw, box, radius=24, fill=SURFACE_ALT)
    for i in range(4):
        yy = y1 + 30 + i * ((y2 - y1 - 60) / 3)
        draw.line((x1 + 24, yy, x2 - 24, yy), fill=BORDER, width=2)
    if bars:
        vals = [0.35, 0.55, 0.48, 0.72, 0.66, 0.8]
        width = (x2 - x1 - 80) / len(vals)
        for idx, v in enumerate(vals):
            bx1 = x1 + 40 + idx * width
            bx2 = bx1 + width * 0.55
            by2 = y2 - 30
            by1 = by2 - (y2 - y1 - 80) * v
            draw.rounded_rectangle((bx1, by1, bx2, by2), radius=10, fill=ACCENT if idx % 2 == 0 else PRIMARY)
    else:
        pts = []
        vals = [0.38, 0.52, 0.46, 0.64, 0.58, 0.74, 0.68]
        for idx, v in enumerate(vals):
            px = x1 + 40 + idx * ((x2 - x1 - 80) / (len(vals) - 1))
            py = y2 - 30 - (y2 - y1 - 80) * v
            pts.append((px, py))
        draw.line(pts, fill=PRIMARY, width=6, joint="curve")
        for px, py in pts:
            draw.ellipse((px - 7, py - 7, px + 7, py + 7), fill=SURFACE, outline=PRIMARY, width=4)


def draw_mobile_shell(img: Image.Image, title: str, fig_title: str, legend: list[tuple[int, str]], footer: str):
    draw = ImageDraw.Draw(img)
    text(draw, (90, 52), fig_title, TITLE_FONT)
    text(draw, (92, 100), "手机端模块原型图", SUBTITLE_FONT, fill=TEXT_MUTED)

    phone = (120, 150, 700, 1020)
    shadowed_panel(img, phone, radius=54, fill="#111828", outline="#111828")
    draw = ImageDraw.Draw(img)
    screen = (150, 185, 670, 985)
    rounded(draw, screen, radius=42, fill=SURFACE)
    draw.rounded_rectangle((350, 160, 470, 175), radius=8, fill="#2A3240")
    draw.rounded_rectangle((355, 996, 465, 1008), radius=6, fill="#D9E3EF")

    note = (770, 165, 1710, 980)
    rounded(draw, note, radius=34, fill=SURFACE, outline=BORDER)
    text(draw, (805, 205), "图注与组件说明", TITLE_FONT)
    wrapped_text(draw, (805, 262, 1650, 370), "统一规则：手机外框、纵向内容分区、卡片化组件、底部五导航固定保留。编号按从上到下、从左到右布置。", LABEL_FONT, fill=TEXT_MUTED)
    text(draw, (805, 405), "组件编号", SECTION_FONT)
    y = 452
    for n, label in legend:
        number_badge(draw, 805, y - 4, n)
        wrapped_text(draw, (855, y, 1648, y + 48), label, LABEL_FONT)
        y += 52
    text(draw, (805, 850), "布局重点", SECTION_FONT)
    wrapped_text(draw, (805, 892, 1655, 970), footer, LABEL_FONT, fill=PRIMARY_DARK)
    text(draw, (175, 208), title, SECTION_FONT)
    return draw, screen


def draw_web_shell(img: Image.Image, fig_title: str, legend: list[tuple[int, str]], footer: str):
    draw = ImageDraw.Draw(img)
    text(draw, (90, 52), fig_title, TITLE_FONT)
    text(draw, (92, 100), "后台网页模块原型图", SUBTITLE_FONT, fill=TEXT_MUTED)

    browser = (90, 150, 1710, 980)
    shadowed_panel(img, browser, radius=34, fill=SURFACE, outline=BORDER)
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((90, 150, 1710, 205), radius=34, fill="#EEF4FA", outline=BORDER)
    for i, c in enumerate([DANGER, WARNING, SUCCESS]):
        draw.ellipse((120 + i * 26, 171, 136 + i * 26, 187), fill=c)
    text(draw, (205, 167), "后台工作台原型", LABEL_FONT, fill=TEXT_MUTED)

    note = (1360, 235, 1680, 930)
    rounded(draw, note, radius=24, fill=SURFACE_ALT, outline=BORDER)
    text(draw, (1390, 265), "组件编号", SECTION_FONT)
    y = 312
    for n, label in legend:
        number_badge(draw, 1390, y - 4, n)
        wrapped_text(draw, (1440, y, 1650, y + 48), label, LABEL_FONT)
        y += 52
    text(draw, (1390, 775), "布局重点", SECTION_FONT)
    wrapped_text(draw, (1390, 818, 1650, 920), footer, LABEL_FONT, fill=PRIMARY_DARK)

    content = (120, 235, 1330, 940)
    return draw, content


def draw_mobile_nav(draw, screen, active="今日"):
    x1, y1, x2, y2 = screen
    nav = (x1 + 28, y2 - 86, x2 - 28, y2 - 18)
    rounded(draw, nav, radius=28, fill=NAV_BG)
    items = ["今日", "医生", "趋势", "设备", "我的"]
    seg = (nav[2] - nav[0]) / 5
    for idx, item in enumerate(items):
        cx = nav[0] + seg * idx + seg / 2
        if item == active:
            draw.rounded_rectangle((cx - 40, nav[1] + 10, cx + 40, nav[1] + 42), radius=18, fill=PRIMARY_SOFT)
        bbox = draw.textbbox((0, 0), item, font=SMALL_FONT)
        draw.text((cx - bbox[2] / 2, nav[1] + 48), item, font=SMALL_FONT, fill=PRIMARY_DARK if item == active else TEXT_MUTED)
    return nav


@dataclass
class FigureSpec:
    filename: str
    title: str
    renderer: Callable[[Path], None]


def fig_4_1(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部标题栏"), (2, "日期/时间区"), (3, "恢复分主卡片"), (4, "核心指标区"), (5, "风险摘要区"), (6, "建议入口区"), (7, "干预快捷入口区"), (8, "机器人入口"), (9, "底部导航栏")]
    draw, screen = draw_mobile_shell(img, "今日", "图4-1 今日状态模块原型图", legend, "状态展示 → 建议理解 → 执行入口。恢复分、建议入口、干预快捷入口形成主视觉主线。")
    x1, y1, x2, y2 = screen
    card(draw, (x1 + 22, y1 + 60, x2 - 22, y1 + 105), "日期 / 今日 / 本周切换", 2, "2026-03-21   今日状态  ·  本周概览")
    card(draw, (x1 + 22, y1 + 118, x2 - 22, y1 + 300), "恢复分主卡片", 3, "恢复分 78\n恢复状态稳定，建议保持低负荷节律。", fill=PRIMARY_SOFT)
    icon_circle(draw, x1 + 100, y1 + 210, 42, fill=PRIMARY, label="78")
    small_cards = [
        ("心率", 4, (x1 + 22, y1 + 318, x1 + 246, y1 + 430)),
        ("血氧", 4, (x1 + 266, y1 + 318, x2 - 22, y1 + 430)),
        ("体温", 4, (x1 + 22, y1 + 442, x1 + 246, y1 + 554)),
        ("HRV", 4, (x1 + 266, y1 + 442, x2 - 22, y1 + 554)),
    ]
    for title, n, box in small_cards:
        card(draw, box, title, n if title == "心率" else None, "指标占位")
    card(draw, (x1 + 22, y1 + 568, x2 - 22, y1 + 646), "风险摘要区", 5, "近期无明显新风险，保持规律作息与低刺激干预。")
    button(draw, (x1 + 22, y1 + 660, x2 - 22, y1 + 726), "查看今日建议", 6, primary=True)
    mini = [
        ("呼吸训练", (x1 + 22, y1 + 742, x1 + 175, y1 + 832)),
        ("Zen", (x1 + 193, y1 + 742, x1 + 346, y1 + 832)),
        ("音景", (x1 + 364, y1 + 742, x2 - 22, y1 + 832)),
    ]
    for idx, (title, box) in enumerate(mini):
        card(draw, box, title, 7 if idx == 0 else None, "快捷入口", fill=SURFACE_ALT)
    icon_circle(draw, x2 - 52, y2 - 132, 34, fill=PRIMARY_DARK)
    number_badge(draw, x2 - 105, y2 - 182, 8)
    draw_mobile_nav(draw, screen, active="今日")
    number_badge(draw, x2 - 60, y2 - 74, 9)
    img.save(path)


def fig_4_2(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部标题栏"), (2, "扫描按钮"), (3, "设备列表区"), (4, "当前连接状态卡"), (5, "实时指标区"), (6, "操作按钮区"), (7, "同步状态区"), (8, "底部导航栏")]
    draw, screen = draw_mobile_shell(img, "设备", "图4-2 设备接入与采集模块原型图", legend, "突出“扫描阶段”和“连接后采集阶段”是两个状态，设备列表与连接状态卡必须明显分离。")
    x1, y1, x2, y2 = screen
    button(draw, (x1 + 22, y1 + 70, x2 - 22, y1 + 136), "扫描设备", 2, primary=True)
    card(draw, (x1 + 22, y1 + 152, x2 - 22, y1 + 330), "附近设备列表", 3, "长庚环 智能戒指\nCH-Ring A1\nNearby Ring Demo")
    card(draw, (x1 + 22, y1 + 346, x2 - 22, y1 + 500), "当前连接设备状态", 4, "已连接 · 长庚环 智能戒指\n连接质量良好，采集中。", fill=PRIMARY_SOFT)
    cards = [
        ("心率", (x1 + 22, y1 + 516, x1 + 175, y1 + 606)),
        ("血氧", (x1 + 193, y1 + 516, x1 + 346, y1 + 606)),
        ("体温", (x1 + 364, y1 + 516, x2 - 22, y1 + 606)),
    ]
    for idx, (title, box) in enumerate(cards):
        card(draw, box, title, 5 if idx == 0 else None, "实时指标")
    button(draw, (x1 + 22, y1 + 624, x1 + 175, y1 + 686), "连接", 6, primary=False)
    button(draw, (x1 + 193, y1 + 624, x1 + 346, y1 + 686), "断开", primary=False)
    button(draw, (x1 + 364, y1 + 624, x2 - 22, y1 + 686), "重新连接", primary=True)
    card(draw, (x1 + 22, y1 + 704, x2 - 22, y1 + 762), "同步状态", 7, "最近同步：12:06  ·  采集状态：正常")
    draw_mobile_nav(draw, screen, active="设备")
    number_badge(draw, x2 - 60, y2 - 74, 8)
    img.save(path)


def fig_4_3(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部标题栏"), (2, "主诉输入区"), (3, "问诊对话区"), (4, "快捷症状区"), (5, "结构化结果摘要区"), (6, "操作按钮区"), (7, "底部导航栏")]
    draw, screen = draw_mobile_shell(img, "医生", "图4-3 医生问诊模块原型图", legend, "问诊页必须突出交互性，因此聊天区占最大面积，摘要卡只承担收束作用。")
    x1, y1, x2, y2 = screen
    card(draw, (x1 + 22, y1 + 62, x2 - 22, y1 + 124), "请输入不适描述", 2, "例如：最近持续疲劳并伴随失眠。")
    chat = (x1 + 22, y1 + 138, x2 - 22, y1 + 520)
    card(draw, chat, "问诊对话区", 3, None)
    draw.rounded_rectangle((x1 + 54, y1 + 194, x1 + 270, y1 + 270), radius=24, fill=PRIMARY_SOFT)
    draw.rounded_rectangle((x2 - 290, y1 + 300, x2 - 54, y1 + 388), radius=24, fill="#EAF4FE")
    draw.rounded_rectangle((x1 + 54, y1 + 410, x1 + 320, y1 + 486), radius=24, fill=PRIMARY_SOFT)
    wrapped_text(draw, (x1 + 70, y1 + 208, x1 + 248, y1 + 262), "请描述主要不适、持续时间和伴随症状。", SMALL_FONT)
    wrapped_text(draw, (x2 - 275, y1 + 315, x2 - 72, y1 + 382), "最近三天睡不实，白天头重。", SMALL_FONT)
    wrapped_text(draw, (x1 + 70, y1 + 424, x1 + 298, y1 + 478), "是否伴随心悸、胸闷或情绪波动？", SMALL_FONT)
    chips = ["头痛", "疲劳", "失眠", "胸闷"]
    cx = x1 + 22
    for idx, label in enumerate(chips):
        chip(draw, (cx, y1 + 540, cx + 108, y1 + 584), label, active=(idx == 2))
        if idx == 0:
            number_badge(draw, cx + 68, y1 + 498, 4)
        cx += 122
    card(draw, (x1 + 22, y1 + 604, x2 - 22, y1 + 700), "结构化结果摘要", 5, "当前已形成主诉、风险等级、建议继续追问的结构化摘要。")
    button(draw, (x1 + 22, y1 + 716, x1 + 246, y1 + 782), "继续问诊", 6, primary=False)
    button(draw, (x1 + 266, y1 + 716, x2 - 22, y1 + 782), "生成结果", primary=True)
    draw_mobile_nav(draw, screen, active="医生")
    number_badge(draw, x2 - 60, y2 - 74, 7)
    img.save(path)


def fig_4_4(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部标题栏"), (2, "上传入口"), (3, "报告预览区"), (4, "解析状态区"), (5, "关键指标区"), (6, "异常提示区"), (7, "可读化说明区"), (8, "后续入口区")]
    draw, screen = draw_mobile_shell(img, "报告理解", "图4-4 医检报告理解模块原型图", legend, "重点是 上传 → 解析 → 结果理解 → 进入后续链路，而不是单纯文件上传。")
    x1, y1, x2, y2 = screen
    button(draw, (x1 + 22, y1 + 62, x2 - 22, y1 + 128), "上传报告 / 拍照上传", 2, primary=True)
    card(draw, (x1 + 22, y1 + 142, x2 - 22, y1 + 348), "报告预览区", 3, "PDF / 图片预览占位", fill=SURFACE_ALT)
    draw.rectangle((x1 + 80, y1 + 192, x2 - 80, y1 + 318), outline=PRIMARY, width=3)
    card(draw, (x1 + 22, y1 + 362, x2 - 22, y1 + 420), "解析状态区", 4, "已完成 · 结构化提取成功")
    for idx, label in enumerate(["血氧", "HRV", "体温"]):
        bx1 = x1 + 22 + idx * 164
        bx2 = bx1 + 146
        card(draw, (bx1, y1 + 436, bx2, y1 + 524), label, 5 if idx == 0 else None, "关键指标")
    card(draw, (x1 + 22, y1 + 538, x2 - 22, y1 + 620), "异常提示区", 6, "检测到需进一步关注的异常波动。", fill="#FFF3F0")
    card(draw, (x1 + 22, y1 + 634, x2 - 22, y1 + 752), "可读化说明区", 7, "系统将原始报告内容转写为用户可理解的说明，突出关键指标、异常趋势及后续建议。")
    button(draw, (x1 + 22, y1 + 768, x1 + 246, y1 + 832), "进入问诊", 8, primary=False)
    button(draw, (x1 + 266, y1 + 768, x2 - 22, y1 + 832), "保存结果", primary=True)
    img.save(path)


def fig_4_5(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部标题栏"), (2, "当前建议摘要区"), (3, "干预分类导航"), (4, "干预卡片列表区"), (5, "最近执行记录入口"), (6, "机器人入口"), (7, "底部导航栏")]
    draw, screen = draw_mobile_shell(img, "干预中心", "图4-5 干预中心模块原型图", legend, "这里要突出“建议承接感”，不能画成功能菜单页。")
    x1, y1, x2, y2 = screen
    card(draw, (x1 + 22, y1 + 62, x2 - 22, y1 + 162), "当前推荐干预", 2, "建议优先进行低刺激呼吸训练，其次进入 Zen 与音景会话。", fill=PRIMARY_SOFT)
    tabs = ["呼吸", "Zen", "音景", "其他"]
    cx = x1 + 22
    for idx, label in enumerate(tabs):
        chip(draw, (cx, y1 + 178, cx + 110, y1 + 220), label, active=(idx == 0))
        if idx == 0:
            number_badge(draw, cx + 68, y1 + 138, 3)
        cx += 122
    boxes = [
        (x1 + 22, y1 + 236, x2 - 22, y1 + 340),
        (x1 + 22, y1 + 354, x2 - 22, y1 + 458),
        (x1 + 22, y1 + 472, x2 - 22, y1 + 576),
    ]
    for idx, box in enumerate(boxes):
        card(draw, box, f"干预卡片 {idx + 1}", 4 if idx == 0 else None, "标题 + 简短说明 + 开始按钮")
        button(draw, (box[2] - 150, box[3] - 58, box[2] - 22, box[3] - 18), "开始", primary=(idx == 0))
    button(draw, (x1 + 22, y1 + 592, x2 - 22, y1 + 654), "最近执行记录", 5, primary=False)
    icon_circle(draw, x2 - 52, y2 - 132, 34, fill=PRIMARY_DARK)
    number_badge(draw, x2 - 105, y2 - 182, 6)
    draw_mobile_nav(draw, screen, active="今日")
    number_badge(draw, x2 - 60, y2 - 74, 7)
    img.save(path)


def fig_4_6(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部标题栏"), (2, "会话名称区"), (3, "计时/进度区"), (4, "训练主区域"), (5, "状态提示区"), (6, "控制按钮区"), (7, "结束结果入口")]
    draw, screen = draw_mobile_shell(img, "呼吸训练", "图4-6 干预执行模块原型图", legend, "核心是“执行中”，所以中间主区域必须最大，其他区只辅助说明状态。")
    x1, y1, x2, y2 = screen
    card(draw, (x1 + 22, y1 + 62, x2 - 22, y1 + 132), "当前会话名称区", 2, "4-6 呼吸节律训练 · 慢节奏")
    card(draw, (x1 + 136, y1 + 150, x2 - 136, y1 + 278), "计时/进度区", 3, "10:00\n进度 42%", fill=PRIMARY_SOFT)
    draw.ellipse((x1 + 190, y1 + 302, x2 - 190, y1 + 618), outline=PRIMARY, width=8)
    draw.ellipse((x1 + 245, y1 + 357, x2 - 245, y1 + 563), outline=ACCENT, width=5)
    number_badge(draw, x2 - 150, y1 + 320, 4)
    text(draw, (x1 + 182, y1 + 636), "状态提示区", SECTION_FONT)
    number_badge(draw, x2 - 72, y1 + 632, 5)
    wrapped_text(draw, (x1 + 182, y1 + 674, x2 - 60, y1 + 720), "保持呼吸节律，系统正结合实时反馈与触觉辅助调整节奏。", SMALL_FONT, fill=TEXT_MUTED)
    button(draw, (x1 + 22, y1 + 736, x1 + 168, y1 + 804), "暂停", 6, primary=False)
    button(draw, (x1 + 188, y1 + 736, x1 + 334, y1 + 804), "继续", primary=False)
    button(draw, (x1 + 354, y1 + 736, x2 - 22, y1 + 804), "结束", primary=True)
    card(draw, (x1 + 22, y1 + 818, x2 - 22, y1 + 874), "完成后生成反馈", 7, "结束会话后写入执行记录并生成反馈摘要。")
    img.save(path)


def fig_4_7(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部标题栏"), (2, "时间范围区"), (3, "趋势图区"), (4, "指标对比区"), (5, "反馈摘要区"), (6, "历史记录区"), (7, "底部导航栏")]
    draw, screen = draw_mobile_shell(img, "趋势", "图4-7 趋势复盘模块原型图", legend, "趋势页强调长期观察，图表必须占主视觉，摘要与历史作为解释层。")
    x1, y1, x2, y2 = screen
    chip(draw, (x1 + 22, y1 + 62, x1 + 118, y1 + 104), "近7天", active=False)
    chip(draw, (x1 + 132, y1 + 62, x1 + 248, y1 + 104), "近30天", active=True)
    number_badge(draw, x2 - 60, y1 + 70, 2)
    chart_box = (x1 + 22, y1 + 122, x2 - 22, y1 + 430)
    draw_chart(draw, chart_box, bars=False)
    number_badge(draw, x2 - 60, y1 + 132, 3)
    cards = [
        ("恢复分", (x1 + 22, y1 + 446, x1 + 175, y1 + 536)),
        ("睡眠时长", (x1 + 193, y1 + 446, x1 + 346, y1 + 536)),
        ("深睡占比", (x1 + 364, y1 + 446, x2 - 22, y1 + 536)),
    ]
    for idx, (title, box) in enumerate(cards):
        card(draw, box, title, 4 if idx == 0 else None, "对比小卡")
    card(draw, (x1 + 22, y1 + 552, x2 - 22, y1 + 640), "近期反馈摘要", 5, "过去两周恢复分稳步上升，建议继续保持规律干预。")
    card(draw, (x1 + 22, y1 + 654, x2 - 22, y1 + 804), "历史记录列表", 6, "03-18  呼吸训练已完成\n03-17  Zen 会话已完成\n03-16  音景干预已完成")
    draw_mobile_nav(draw, screen, active="趋势")
    number_badge(draw, x2 - 60, y2 - 74, 7)
    img.save(path)


def fig_4_8(path: Path, title_text: str, fig_title: str):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部标题栏"), (2, "拍照上传入口"), (3, "图片预览区"), (4, "识别状态区"), (5, "结果摘要区"), (6, "结构化说明区"), (7, "保存继续入口")]
    draw, screen = draw_mobile_shell(img, title_text, fig_title, legend, "药物分析与饮食分析应尽量用同一套版式，形成系列感和统一认知。")
    x1, y1, x2, y2 = screen
    button(draw, (x1 + 22, y1 + 62, x2 - 22, y1 + 128), "拍照 / 上传", 2, primary=True)
    card(draw, (x1 + 22, y1 + 144, x2 - 22, y1 + 360), "图片预览区", 3, "识图预览占位", fill=SURFACE_ALT)
    draw.rectangle((x1 + 88, y1 + 200, x2 - 88, y1 + 334), outline=PRIMARY, width=3)
    card(draw, (x1 + 22, y1 + 376, x2 - 22, y1 + 432), "识别状态区", 4, "识别中 / 已完成")
    card(draw, (x1 + 22, y1 + 448, x2 - 22, y1 + 560), "结果摘要区", 5, "结构化识别结果摘要")
    card(draw, (x1 + 22, y1 + 576, x2 - 22, y1 + 726), "结构化说明区", 6, "说明内容：作用 / 注意点 / 营养提示 / 风险提示 / 后续建议。")
    button(draw, (x1 + 22, y1 + 744, x1 + 246, y1 + 808), "保存", 7, primary=False)
    button(draw, (x1 + 266, y1 + 744, x2 - 22, y1 + 808), "继续", primary=True)
    img.save(path)


def fig_4_10(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "页面背景区"), (2, "机器人入口"), (3, "弹层标题区"), (4, "讲解文本区"), (5, "播放控制区"), (6, "推荐下一步区"), (7, "关闭按钮")]
    draw, screen = draw_mobile_shell(img, "机器人讲解", "图4-10 机器人讲解与语音播报模块原型图", legend, "重点体现机器人附着在其他页面之上的解释能力，而不是独立页面。")
    x1, y1, x2, y2 = screen
    card(draw, (x1 + 22, y1 + 62, x2 - 22, y2 - 110), "页面背景区", 1, "当前页面内容被弱化为背景缩略层。", fill="#F8FBFF")
    icon_circle(draw, x2 - 50, y2 - 140, 34, fill=PRIMARY_DARK)
    number_badge(draw, x2 - 106, y2 - 188, 2)
    panel = (x1 + 56, y1 + 180, x2 - 56, y1 + 600)
    rounded(draw, panel, radius=30, fill=SURFACE, outline=BORDER)
    number_badge(draw, panel[2] - 52, panel[1] + 12, 3)
    text(draw, (panel[0] + 26, panel[1] + 22), "讲解弹层标题区", SECTION_FONT)
    number_badge(draw, panel[2] - 52, panel[1] + 82, 4)
    wrapped_text(draw, (panel[0] + 26, panel[1] + 98, panel[2] - 26, panel[1] + 260), "当前页面展示的是恢复状态与干预入口，建议优先查看恢复分摘要，再进入推荐干预。", LABEL_FONT)
    button(draw, (panel[0] + 26, panel[1] + 290, panel[0] + 204, panel[1] + 350), "播放 / 暂停", 5, primary=False)
    button(draw, (panel[0] + 226, panel[1] + 290, panel[2] - 26, panel[1] + 350), "去执行 / 查看详情", 6, primary=True)
    number_badge(draw, panel[2] - 52, panel[1] + 364, 7)
    button(draw, (panel[2] - 146, panel[1] + 380, panel[2] - 26, panel[1] + 438), "关闭", primary=False)
    img.save(path)


def fig_4_11(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部标题栏"), (2, "用户信息区"), (3, "设备绑定状态区"), (4, "历史记录入口区"), (5, "设置入口区"), (6, "帮助与反馈区"), (7, "底部导航栏")]
    draw, screen = draw_mobile_shell(img, "我的", "图4-11 账号与个人中心模块原型图", legend, "“我的页”更适合列表式组织，不宜过度卡片堆叠。")
    x1, y1, x2, y2 = screen
    card(draw, (x1 + 22, y1 + 62, x2 - 22, y1 + 170), "用户信息区", 2, "头像 + 昵称 + 状态概览", fill=PRIMARY_SOFT)
    card(draw, (x1 + 22, y1 + 186, x2 - 22, y1 + 272), "设备绑定状态区", 3, "已绑定设备 / 未绑定设备")
    card(draw, (x1 + 22, y1 + 288, x2 - 22, y1 + 476), "历史记录入口区", 4, "报告历史\n执行历史\n恢复历史")
    card(draw, (x1 + 22, y1 + 492, x2 - 22, y1 + 680), "设置入口区", 5, "账号设置\n通知与权限\n隐私与安全")
    card(draw, (x1 + 22, y1 + 696, x2 - 22, y1 + 784), "帮助与反馈区", 6, "帮助中心 / 反馈入口")
    draw_mobile_nav(draw, screen, active="我的")
    number_badge(draw, x2 - 60, y2 - 74, 7)
    img.save(path)


def fig_4_12(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部全局标题"), (2, "当前页面主区域"), (3, "机器人入口"), (4, "今日导航"), (5, "医生导航"), (6, "趋势导航"), (7, "设备导航"), (8, "我的导航")]
    draw, screen = draw_mobile_shell(img, "系统主导航", "图4-12 系统主导航总览原型图", legend, "该图重点说明系统入口组织，不追求页面内部细节。")
    x1, y1, x2, y2 = screen
    card(draw, (x1 + 22, y1 + 62, x2 - 22, y1 + 180), "当前页面主区域", 2, "这里只作为当前模块的简化占位，强调导航不是强调内容。")
    icon_circle(draw, x2 - 50, y2 - 140, 34, fill=PRIMARY_DARK)
    number_badge(draw, x2 - 106, y2 - 188, 3)
    nav = draw_mobile_nav(draw, screen, active="今日")
    xs = [nav[0] + (nav[2] - nav[0]) / 10 - 12, nav[0] + (nav[2] - nav[0]) * 3 / 10 - 12, nav[0] + (nav[2] - nav[0]) * 5 / 10 - 12, nav[0] + (nav[2] - nav[0]) * 7 / 10 - 12, nav[0] + (nav[2] - nav[0]) * 9 / 10 - 12]
    for idx, xx in enumerate(xs, start=4):
        number_badge(draw, int(xx), nav[1] - 38, idx)
    img.save(path)


def fig_4_13(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "顶部栏"), (2, "左侧菜单栏"), (3, "总览卡片区"), (4, "患者列表区"), (5, "搜索筛选区"), (6, "详情预览区"), (7, "管理员信息区")]
    draw, content = draw_web_shell(img, "图4-13 后台总览与患者工作台原型图", legend, "后台图保持标准中后台风格，重点是总览、患者池和详情工作台之间的层级关系。")
    x1, y1, x2, y2 = content
    menu = (x1, y1, x1 + 220, y2)
    rounded(draw, menu, radius=24, fill=MENU_BG, outline=MENU_BG)
    number_badge(draw, menu[2] - 52, menu[1] + 12, 2)
    text(draw, (x1 + 28, y1 + 26), "总览驾驶舱", LABEL_FONT, fill="white")
    for idx, label in enumerate(["闭环故事", "患者工作台", "报告与问诊", "建议与效果", "系统运维"]):
        draw.rounded_rectangle((x1 + 18, y1 + 78 + idx * 74, menu[2] - 18, y1 + 130 + idx * 74), radius=18, fill="#1A2A40" if idx == 1 else MENU_BG, outline="#24344A")
        text(draw, (x1 + 34, y1 + 92 + idx * 74), label, LABEL_FONT, fill="#DDEBFA")
    top = (x1 + 240, y1, x2, y1 + 78)
    rounded(draw, top, radius=22, fill=SURFACE)
    text(draw, (top[0] + 24, top[1] + 24), "患者工作台", SECTION_FONT)
    number_badge(draw, top[2] - 52, top[1] + 12, 1)
    admin_box = (x2 - 220, y1 + 94, x2, y1 + 188)
    card(draw, admin_box, "管理员信息区", 7, "管理员头像 / 当前环境 / 切换入口")
    for idx in range(4):
        bx1 = x1 + 240 + idx * 170
        card(draw, (bx1, y1 + 94, bx1 + 154, y1 + 188), f"总览卡片 {idx + 1}", 3 if idx == 0 else None, "关键数量")
    card(draw, (x1 + 240, y1 + 204, x1 + 780, y1 + 284), "搜索筛选区", 5, "风险等级 / demo 场景 / 状态筛选")
    card(draw, (x1 + 240, y1 + 300, x1 + 780, y2 - 20), "患者列表区", 4, "列表项：场景标签 / 风险等级 / 最近状态 / 推荐讲解入口")
    card(draw, (x1 + 800, y1 + 204, x2, y2 - 20), "患者详情预览区", 6, "当前判断 / 关键证据 / 报告与问诊 / 建议与干预 / 时间线摘要")
    img.save(path)


def fig_4_14(path: Path):
    img = Image.new("RGBA", (CANVAS_W, CANVAS_H), BG)
    legend = [(1, "左侧菜单栏"), (2, "顶部栏"), (3, "报告工作区"), (4, "推荐管理区"), (5, "系统状态区"), (6, "日志审计区"), (7, "操作按钮区")]
    draw, content = draw_web_shell(img, "图4-14 报告/推荐/系统管理后台原型图", legend, "图4-13 和图4-14必须保持同一套后台视觉语言，避免看起来像两个系统。")
    x1, y1, x2, y2 = content
    menu = (x1, y1, x1 + 220, y2)
    rounded(draw, menu, radius=24, fill=MENU_BG, outline=MENU_BG)
    number_badge(draw, menu[2] - 52, menu[1] + 12, 1)
    text(draw, (x1 + 28, y1 + 26), "报告与推荐", LABEL_FONT, fill="white")
    for idx, label in enumerate(["总览驾驶舱", "患者工作台", "报告与问诊", "建议与效果", "系统运维"]):
        draw.rounded_rectangle((x1 + 18, y1 + 78 + idx * 74, menu[2] - 18, y1 + 130 + idx * 74), radius=18, fill="#1A2A40" if idx >= 2 else MENU_BG, outline="#24344A")
        text(draw, (x1 + 34, y1 + 92 + idx * 74), label, LABEL_FONT, fill="#DDEBFA")
    top = (x1 + 240, y1, x2, y1 + 78)
    rounded(draw, top, radius=22, fill=SURFACE)
    text(draw, (top[0] + 24, top[1] + 24), "报告 / 推荐 / 系统管理", SECTION_FONT)
    number_badge(draw, top[2] - 52, top[1] + 12, 2)
    button(draw, (x2 - 220, y1 + 92, x2, y1 + 148), "执行操作", 7, primary=True)
    card(draw, (x1 + 240, y1 + 94, x1 + 700, y1 + 472), "报告工作区", 3, "待解析 / 高风险 / 待问诊 / 已形成建议")
    card(draw, (x1 + 724, y1 + 94, x2, y1 + 472), "推荐管理区", 4, "建议内容 / 原因 / 执行情况 / 效果")
    card(draw, (x1 + 240, y1 + 496, x1 + 700, y2 - 20), "系统状态区", 5, "模型状态 / 作业状态 / 风险影响面")
    card(draw, (x1 + 724, y1 + 496, x2, y2 - 20), "日志审计区", 6, "最近审计记录 / 失败日志 / 关键变更")
    img.save(path)


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    specs = [
        FigureSpec("图4-1_今日状态模块原型图.png", "图4-1 今日状态模块原型图", fig_4_1),
        FigureSpec("图4-2_设备接入与采集模块原型图.png", "图4-2 设备接入与采集模块原型图", fig_4_2),
        FigureSpec("图4-3_医生问诊模块原型图.png", "图4-3 医生问诊模块原型图", fig_4_3),
        FigureSpec("图4-4_医检报告理解模块原型图.png", "图4-4 医检报告理解模块原型图", fig_4_4),
        FigureSpec("图4-5_干预中心模块原型图.png", "图4-5 干预中心模块原型图", fig_4_5),
        FigureSpec("图4-6_干预执行模块原型图.png", "图4-6 干预执行模块原型图", fig_4_6),
        FigureSpec("图4-7_趋势复盘模块原型图.png", "图4-7 趋势复盘模块原型图", fig_4_7),
        FigureSpec("图4-8_药物分析模块原型图.png", "图4-8 药物分析模块原型图", lambda p: fig_4_8(p, "药物分析", "图4-8 药物分析模块原型图")),
        FigureSpec("图4-9_饮食分析模块原型图.png", "图4-9 饮食分析模块原型图", lambda p: fig_4_8(p, "饮食分析", "图4-9 饮食分析模块原型图")),
        FigureSpec("图4-10_机器人讲解与语音播报模块原型图.png", "图4-10 机器人讲解与语音播报模块原型图", fig_4_10),
        FigureSpec("图4-11_账号与个人中心模块原型图.png", "图4-11 账号与个人中心模块原型图", fig_4_11),
        FigureSpec("图4-12_系统主导航总览原型图.png", "图4-12 系统主导航总览原型图", fig_4_12),
        FigureSpec("图4-13_后台总览与患者工作台原型图.png", "图4-13 后台总览与患者工作台原型图", fig_4_13),
        FigureSpec("图4-14_报告推荐系统管理后台原型图.png", "图4-14 报告/推荐/系统管理后台原型图", fig_4_14),
    ]
    for spec in specs:
        spec.renderer(OUT_DIR / spec.filename)
        print(OUT_DIR / spec.filename)


if __name__ == "__main__":
    main()
