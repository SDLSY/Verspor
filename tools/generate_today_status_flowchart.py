from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


WIDTH = 1920
HEIGHT = 1080
TITLE = "今日状态模块程序逻辑图"
SUBTITLE = "Today Status Module Flow"
OUTPUT_DIR = Path(r"D:\newstart\docs\image\项目开发文档流程图")
SVG_PATH = OUTPUT_DIR / "今日状态模块程序逻辑图.svg"
PNG_PATH = OUTPUT_DIR / "今日状态模块程序逻辑图.png"


@dataclass(frozen=True)
class RectNode:
    key: str
    label: str
    left: int
    top: int
    right: int
    bottom: int
    radius: int
    kind: str


@dataclass(frozen=True)
class DiamondNode:
    key: str
    lines: tuple[str, ...]
    center_x: int
    center_y: int
    width: int
    height: int


def get_font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    candidates = [
        r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\simhei.ttf",
        r"C:\Windows\Fonts\simsun.ttc",
    ]
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default()


def escape_xml(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def rounded(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    radius: int,
    fill: str,
    outline: str,
    width: int,
) -> None:
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def diamond_points(cx: int, cy: int, w: int, h: int) -> list[tuple[int, int]]:
    return [
        (cx, cy - h // 2),
        (cx + w // 2, cy),
        (cx, cy + h // 2),
        (cx - w // 2, cy),
    ]


def draw_diamond(
    draw: ImageDraw.ImageDraw,
    node: DiamondNode,
    fill: str,
    outline: str,
    stroke_width: int,
) -> None:
    points = diamond_points(node.center_x, node.center_y, node.width, node.height)
    draw.polygon(points, fill=fill, outline=outline)
    for offset in range(1, stroke_width):
        inset = diamond_points(
            node.center_x,
            node.center_y,
            node.width - offset * 2,
            node.height - offset * 2,
        )
        draw.polygon(inset, outline=outline)


def draw_centered_text(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    text: str,
    font: ImageFont.ImageFont,
    fill: str,
    spacing: int = 4,
) -> None:
    left, top, right, bottom = box
    bbox = draw.multiline_textbbox((0, 0), text, font=font, spacing=spacing, align="center")
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    x = left + (right - left - width) / 2
    y = top + (bottom - top - height) / 2 - 1
    draw.multiline_text((x, y), text, font=font, fill=fill, spacing=spacing, align="center")


def draw_arrow(
    draw: ImageDraw.ImageDraw,
    start: tuple[int, int],
    end: tuple[int, int],
    color: str,
    width: int,
) -> None:
    x1, y1 = start
    x2, y2 = end
    draw.line((x1, y1, x2, y2), fill=color, width=width)
    head = 12
    if abs(x2 - x1) >= abs(y2 - y1):
        sign = 1 if x2 >= x1 else -1
        points = [(x2, y2), (x2 - sign * head, y2 - 6), (x2 - sign * head, y2 + 6)]
    else:
        sign = 1 if y2 >= y1 else -1
        points = [(x2, y2), (x2 - 6, y2 - sign * head), (x2 + 6, y2 - sign * head)]
    draw.polygon(points, fill=color)


def draw_polyline(
    draw: ImageDraw.ImageDraw,
    points: list[tuple[int, int]],
    color: str,
    width: int,
) -> None:
    for start, end in zip(points, points[1:]):
        draw_arrow(draw, start, end, color, width)


def build_layout() -> tuple[list[RectNode], list[DiamondNode], dict[str, RectNode | DiamondNode]]:
    rects = [
        RectNode("start", "页面启动", 140, 452, 314, 506, 22, "terminal"),
        RectNode("sleep", "读取本地睡眠记录", 364, 452, 566, 506, 14, "io"),
        RectNode("metrics", "读取关键健康指标", 616, 452, 818, 506, 14, "io"),
        RectNode("score", "读取恢复分与设备状态", 868, 452, 1096, 506, 14, "io"),
        RectNode("summary", "聚合生成今日状态摘要", 1146, 452, 1378, 506, 14, "step"),
        RectNode("render", "渲染恢复分主卡片与关键指标区", 1630, 452, 1876, 506, 14, "step"),
        RectNode("wait", "等待用户操作", 1630, 614, 1876, 668, 14, "step"),
        RectNode("end", "当前页持续运行", 1630, 788, 1876, 842, 22, "terminal"),
        RectNode("enhance_yes", "补充建议与解释入口", 1172, 290, 1352, 330, 12, "branch"),
        RectNode("avatar_yes", "发送当前页面上下文到讲解服务", 1410, 290, 1666, 330, 12, "branch"),
        RectNode("entry_yes", "跳转并传递当前状态上下文", 1418, 942, 1658, 982, 12, "branch"),
        RectNode("refresh_yes", "刷新关键展示区域", 1162, 942, 1342, 982, 12, "branch"),
        RectNode("refresh_no", "保持当前展示", 1162, 1000, 1342, 1040, 12, "branch"),
    ]
    diamonds = [
        DiamondNode("enhance", ("是否存在报告 / 问诊 / 推荐", "增强结果"), 1512, 479, 176, 92),
        DiamondNode("avatar", ("是否触发机器人讲解",), 1752, 560, 168, 78),
        DiamondNode("entry", ("是否点击建议 / 干预 / 趋势入口",), 1752, 730, 204, 78),
        DiamondNode("refresh", ("本地事实层是否更新",), 1458, 815, 172, 78),
    ]
    node_map: dict[str, RectNode | DiamondNode] = {node.key: node for node in rects + diamonds}
    return rects, diamonds, node_map


def render_png() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (WIDTH, HEIGHT), "#eef3f6")
    draw = ImageDraw.Draw(image)

    title_font = get_font(34, True)
    subtitle_font = get_font(14, False)
    section_font = get_font(20, True)
    label_font = get_font(16, True)
    note_font = get_font(12, False)

    draw.rounded_rectangle((48, 36, WIDTH - 48, HEIGHT - 36), radius=30, fill="#ffffff", outline="#d6e0e8", width=2)
    draw.rounded_rectangle((48, 36, WIDTH - 48, 128), radius=30, fill="#135f87", outline="#135f87", width=2)
    draw.rectangle((48, 96, WIDTH - 48, 128), fill="#12a2b2")
    draw.text((92, 54), TITLE, font=title_font, fill="#ffffff")
    draw.text((94, 98), SUBTITLE, font=subtitle_font, fill="#d4eef1")

    flow_panel = (86, 178, 1534, 1010)
    legend_panel = (1566, 178, 1834, 548)
    rounded(draw, flow_panel, 24, "#fbfdff", "#d7e1e8", 2)
    rounded(draw, legend_panel, 24, "#fbfdff", "#d7e1e8", 2)
    draw.text((116, 202), "流程区", font=section_font, fill="#164863")
    draw.text((1598, 202), "图形语义", font=section_font, fill="#164863")

    rects, diamonds, node_map = build_layout()

    fills = {
        "terminal": ("#ffffff", "#2a7395"),
        "io": ("#f7fbff", "#2f88af"),
        "step": ("#ffffff", "#5d9cb9"),
        "branch": ("#f4fbfc", "#6cc1cb"),
    }
    text_color = "#153e59"
    decision_outline = "#19a3b1"
    branch_color = "#1494a4"
    main_color = "#2b7395"

    for node in rects:
        fill, outline = fills[node.kind]
        rounded(draw, (node.left, node.top, node.right, node.bottom), node.radius, fill, outline, 2)
        draw_centered_text(draw, (node.left, node.top, node.right, node.bottom), node.label, label_font, text_color)

    for node in diamonds:
        draw_diamond(draw, node, "#ffffff", decision_outline, 2)
        draw_centered_text(
            draw,
            (
                node.center_x - node.width // 2 + 18,
                node.center_y - node.height // 2 + 10,
                node.center_x + node.width // 2 - 18,
                node.center_y + node.height // 2 - 10,
            ),
            "\n".join(node.lines),
            label_font,
            "#0f4d63",
        )

    def rmid_left(key: str) -> tuple[int, int]:
        node = node_map[key]
        assert isinstance(node, RectNode)
        return (node.left, (node.top + node.bottom) // 2)

    def rmid_right(key: str) -> tuple[int, int]:
        node = node_map[key]
        assert isinstance(node, RectNode)
        return (node.right, (node.top + node.bottom) // 2)

    def dleft(key: str) -> tuple[int, int]:
        node = node_map[key]
        assert isinstance(node, DiamondNode)
        return (node.center_x - node.width // 2, node.center_y)

    def dright(key: str) -> tuple[int, int]:
        node = node_map[key]
        assert isinstance(node, DiamondNode)
        return (node.center_x + node.width // 2, node.center_y)

    main_segments = [
        (rmid_right("start"), rmid_left("sleep")),
        (rmid_right("sleep"), rmid_left("metrics")),
        (rmid_right("metrics"), rmid_left("score")),
        (rmid_right("score"), rmid_left("summary")),
        (rmid_right("summary"), dleft("enhance")),
        (dright("enhance"), rmid_left("render")),
        ((1752, 521), (1752, 536)),
        ((1752, 599), (1752, 614)),
        ((1752, 668), (1752, 691)),
        ((1752, 769), (1458, 769)),
        ((1458, 854), (1458, 815)),
        ((1458, 854), (1458, 854)),
        ((1498, 815), rmid_left("end")),
    ]
    for start, end in main_segments:
        if start != end:
            draw_arrow(draw, start, end, main_color, 3)

    draw_polyline(draw, [dleft("avatar"), (1752, 536)], main_color, 3)
    draw_polyline(draw, [(1752, 599), (1752, 691), dleft("entry")], main_color, 3)
    draw_polyline(draw, [(1752, 769), (1752, 815), dright("refresh")], main_color, 3)

    branch_lines = [
        [dright("enhance"), (1172, 310)],
        [(1262, 330), (1262, 452), rmid_left("render")],
        [dleft("avatar"), (1540, 330)],
        [(1538, 330), (1538, 452), rmid_left("render")],
        [dright("entry"), (1418, 962)],
        [(1538, 942), (1538, 842), rmid_left("end")],
        [dleft("refresh"), (1342, 962)],
        [dleft("refresh"), (1342, 1020)],
    ]
    for points in branch_lines:
        draw_polyline(draw, points, branch_color, 3)

    yes_labels = [
        (1168, 286),
        (1534, 286),
        (1412, 938),
        (1158, 938),
    ]
    no_labels = [
        (1662, 434),
        (1654, 582),
        (1654, 748),
        (1338, 996),
    ]
    for x, y in yes_labels:
        draw.text((x, y), "是", font=note_font, fill="#607887")
    for x, y in no_labels:
        draw.text((x, y), "否", font=note_font, fill="#607887")

    legend_items = [
        ("terminal", "开始 / 结束"),
        ("io", "读取 / 输出"),
        ("step", "流程处理"),
        ("decision", "条件判断"),
    ]
    legend_y = 248
    for idx, (kind, label) in enumerate(legend_items):
        top = legend_y + idx * 82
        if kind == "decision":
            demo = DiamondNode("legend", (label,), 1700, top + 24, 120, 60)
            draw_diamond(draw, demo, "#ffffff", decision_outline, 2)
            draw_centered_text(draw, (1646, top + 56, 1754, top + 82), label, note_font, "#214d67")
        else:
            fill, outline = fills[kind]
            rounded(draw, (1654, top, 1746, top + 46), 14 if kind == "terminal" else 10, fill, outline, 2)
            draw_centered_text(draw, (1644, top + 54, 1756, top + 82), label, note_font, "#214d67")

    image.save(PNG_PATH)


def render_svg() -> None:
    rects, diamonds, node_map = build_layout()

    def rect_svg(node: RectNode) -> str:
        return (
            f'<rect class="{node.kind}" x="{node.left}" y="{node.top}" '
            f'width="{node.right - node.left}" height="{node.bottom - node.top}" '
            f'rx="{node.radius}" ry="{node.radius}"/>'
        )

    def diamond_svg(node: DiamondNode) -> str:
        pts = " ".join(f"{x},{y}" for x, y in diamond_points(node.center_x, node.center_y, node.width, node.height))
        return f'<polygon class="decision" points="{pts}"/>'

    def text_svg(x: int, y: int, value: str, cls: str = "label", anchor: str = "middle") -> str:
        return f'<text class="{cls}" x="{x}" y="{y}" text-anchor="{anchor}">{escape_xml(value)}</text>'

    def multiline_svg(node: DiamondNode) -> str:
        start_y = node.center_y - 8
        parts = [f'<text class="label" x="{node.center_x}" y="{start_y}" text-anchor="middle">']
        for idx, line in enumerate(node.lines):
            dy = 0 if idx == 0 else 18
            parts.append(f'<tspan x="{node.center_x}" dy="{dy}">{escape_xml(line)}</tspan>')
        parts.append("</text>")
        return "".join(parts)

    def rmid_left(key: str) -> tuple[int, int]:
        node = node_map[key]
        assert isinstance(node, RectNode)
        return (node.left, (node.top + node.bottom) // 2)

    def rmid_right(key: str) -> tuple[int, int]:
        node = node_map[key]
        assert isinstance(node, RectNode)
        return (node.right, (node.top + node.bottom) // 2)

    def dleft(key: str) -> tuple[int, int]:
        node = node_map[key]
        assert isinstance(node, DiamondNode)
        return (node.center_x - node.width // 2, node.center_y)

    def dright(key: str) -> tuple[int, int]:
        node = node_map[key]
        assert isinstance(node, DiamondNode)
        return (node.center_x + node.width // 2, node.center_y)

    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">',
        """
        <defs>
          <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#f4f7fa"/>
            <stop offset="100%" stop-color="#eaf0f4"/>
          </linearGradient>
          <linearGradient id="header" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stop-color="#135f87"/>
            <stop offset="100%" stop-color="#12a2b2"/>
          </linearGradient>
          <marker id="arrowHead" markerWidth="12" markerHeight="12" refX="9" refY="6" orient="auto">
            <path d="M 0 0 L 12 6 L 0 12 z" fill="#2b7395"/>
          </marker>
          <marker id="branchHead" markerWidth="12" markerHeight="12" refX="9" refY="6" orient="auto">
            <path d="M 0 0 L 12 6 L 0 12 z" fill="#1494a4"/>
          </marker>
          <style>
            .title { font-family: 'Microsoft YaHei', sans-serif; font-size: 34px; font-weight: 700; fill: #ffffff; }
            .subtitle { font-family: 'Microsoft YaHei', sans-serif; font-size: 14px; fill: #d4eef1; }
            .section { font-family: 'Microsoft YaHei', sans-serif; font-size: 20px; font-weight: 700; fill: #164863; }
            .label { font-family: 'Microsoft YaHei', sans-serif; font-size: 16px; font-weight: 700; fill: #153e59; }
            .note { font-family: 'Microsoft YaHei', sans-serif; font-size: 12px; fill: #607887; }
            .panel { fill: #ffffff; stroke: #d6e0e8; stroke-width: 2; }
            .terminal { fill: #ffffff; stroke: #2a7395; stroke-width: 2; }
            .io { fill: #f7fbff; stroke: #2f88af; stroke-width: 2; }
            .step { fill: #ffffff; stroke: #5d9cb9; stroke-width: 2; }
            .branch { fill: #f4fbfc; stroke: #6cc1cb; stroke-width: 2; }
            .decision { fill: #ffffff; stroke: #19a3b1; stroke-width: 2; }
            .mainArrow { fill: none; stroke: #2b7395; stroke-width: 3; marker-end: url(#arrowHead); }
            .branchArrow { fill: none; stroke: #1494a4; stroke-width: 3; marker-end: url(#branchHead); }
          </style>
        </defs>
        """,
        f'<rect width="{WIDTH}" height="{HEIGHT}" fill="url(#bg)"/>',
        '<rect class="panel" x="48" y="36" width="1824" height="1008" rx="30" ry="30"/>',
        '<rect x="48" y="36" width="1824" height="92" rx="30" ry="30" fill="url(#header)"/>',
        '<rect x="48" y="96" width="1824" height="32" fill="#12a2b2"/>',
        f'<text class="title" x="92" y="88">{escape_xml(TITLE)}</text>',
        f'<text class="subtitle" x="94" y="106">{escape_xml(SUBTITLE)}</text>',
        '<rect class="panel" x="86" y="178" width="1448" height="832" rx="24" ry="24"/>',
        '<rect class="panel" x="1566" y="178" width="268" height="370" rx="24" ry="24"/>',
        '<text class="section" x="116" y="204">流程区</text>',
        '<text class="section" x="1598" y="204">图形语义</text>',
    ]

    for node in rects:
        svg.append(rect_svg(node))
        svg.append(text_svg((node.left + node.right) // 2, node.top + 30, node.label))
    for node in diamonds:
        svg.append(diamond_svg(node))
        svg.append(multiline_svg(node))

    main_segments = [
        (rmid_right("start"), rmid_left("sleep")),
        (rmid_right("sleep"), rmid_left("metrics")),
        (rmid_right("metrics"), rmid_left("score")),
        (rmid_right("score"), rmid_left("summary")),
        (rmid_right("summary"), dleft("enhance")),
        (dright("enhance"), rmid_left("render")),
        ((1752, 521), (1752, 536)),
        ((1752, 599), (1752, 614)),
        ((1752, 668), (1752, 691)),
        ((1752, 769), (1458, 769)),
        ((1458, 854), (1458, 815)),
        ((1498, 815), rmid_left("end")),
    ]
    for start, end in main_segments:
        svg.append(f'<line class="mainArrow" x1="{start[0]}" y1="{start[1]}" x2="{end[0]}" y2="{end[1]}"/>')

    branch_lines = [
        [dright("enhance"), (1172, 310)],
        [(1262, 330), (1262, 452), rmid_left("render")],
        [dleft("avatar"), (1540, 330)],
        [(1538, 330), (1538, 452), rmid_left("render")],
        [dright("entry"), (1418, 962)],
        [(1538, 942), (1538, 842), rmid_left("end")],
        [dleft("refresh"), (1342, 962)],
        [dleft("refresh"), (1342, 1020)],
    ]
    for points in branch_lines:
        for start, end in zip(points, points[1:]):
            svg.append(f'<line class="branchArrow" x1="{start[0]}" y1="{start[1]}" x2="{end[0]}" y2="{end[1]}"/>')

    yes_labels = [(1168, 286), (1534, 286), (1412, 938), (1158, 938)]
    no_labels = [(1662, 434), (1654, 582), (1654, 748), (1338, 996)]
    for x, y in yes_labels:
        svg.append(text_svg(x, y, "是", "note", "start"))
    for x, y in no_labels:
        svg.append(text_svg(x, y, "否", "note", "start"))

    legend_items = [("terminal", "开始 / 结束"), ("io", "读取 / 输出"), ("step", "流程处理"), ("decision", "条件判断")]
    legend_y = 248
    for idx, (kind, label) in enumerate(legend_items):
        top = legend_y + idx * 82
        if kind == "decision":
            legend = DiamondNode("legend", (label,), 1700, top + 24, 120, 60)
            svg.append(diamond_svg(legend))
        else:
            radius = 14 if kind == "terminal" else 10
            cls = kind
            svg.append(
                f'<rect class="{cls}" x="1654" y="{top}" width="92" height="46" rx="{radius}" ry="{radius}"/>'
            )
        svg.append(text_svg(1700, top + 76, label, "note"))

    svg.append("</svg>")
    SVG_PATH.write_text("\n".join(svg), encoding="utf-8")


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    render_svg()
    render_png()
    print(SVG_PATH)
    print(PNG_PATH)


if __name__ == "__main__":
    main()
