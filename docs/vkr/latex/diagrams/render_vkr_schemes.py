from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "images"


@dataclass
class Box:
    x: int
    y: int
    w: int
    h: int
    text: str
    fill: tuple[int, int, int]
    outline: tuple[int, int, int] = (45, 45, 45)

    @property
    def left(self) -> int:
        return self.x

    @property
    def right(self) -> int:
        return self.x + self.w

    @property
    def top(self) -> int:
        return self.y

    @property
    def bottom(self) -> int:
        return self.y + self.h

    @property
    def cx(self) -> int:
        return self.x + self.w // 2

    @property
    def cy(self) -> int:
        return self.y + self.h // 2


def get_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Helvetica.ttc",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, max_width: int) -> list[str]:
    words = text.split()
    if not words:
        return [""]
    lines: list[str] = []
    cur: list[str] = []
    for w in words:
        candidate = " ".join(cur + [w])
        bbox = draw.textbbox((0, 0), candidate, font=font)
        if bbox[2] - bbox[0] <= max_width or not cur:
            cur.append(w)
        else:
            lines.append(" ".join(cur))
            cur = [w]
    if cur:
        lines.append(" ".join(cur))
    return lines


def draw_box(draw: ImageDraw.ImageDraw, box: Box, font: ImageFont.ImageFont) -> None:
    draw.rounded_rectangle(
        (box.left, box.top, box.right, box.bottom),
        radius=16,
        fill=box.fill,
        outline=box.outline,
        width=3,
    )
    lines = wrap_text(draw, box.text, font, box.w - 24)
    line_height = draw.textbbox((0, 0), "Ag", font=font)[3] + 6
    total_h = line_height * len(lines)
    y = box.cy - total_h // 2
    for line in lines:
        tw = draw.textbbox((0, 0), line, font=font)[2]
        x = box.cx - tw // 2
        draw.text((x, y), line, fill=(24, 24, 24), font=font)
        y += line_height


def draw_arrow(
    draw: ImageDraw.ImageDraw,
    p1: tuple[int, int],
    p2: tuple[int, int],
    color: tuple[int, int, int] = (40, 40, 40),
    width: int = 4,
    head: int = 12,
) -> None:
    draw.line([p1, p2], fill=color, width=width)
    x1, y1 = p1
    x2, y2 = p2
    if x1 == x2 and y1 == y2:
        return
    if abs(x2 - x1) >= abs(y2 - y1):
        # horizontal-ish
        if x2 >= x1:
            tip = (x2, y2)
            p_a = (x2 - head, y2 - head // 2)
            p_b = (x2 - head, y2 + head // 2)
        else:
            tip = (x2, y2)
            p_a = (x2 + head, y2 - head // 2)
            p_b = (x2 + head, y2 + head // 2)
    else:
        # vertical-ish
        if y2 >= y1:
            tip = (x2, y2)
            p_a = (x2 - head // 2, y2 - head)
            p_b = (x2 + head // 2, y2 - head)
        else:
            tip = (x2, y2)
            p_a = (x2 - head // 2, y2 + head)
            p_b = (x2 + head // 2, y2 + head)
    draw.polygon([tip, p_a, p_b], fill=color)


def draw_poly_arrow(
    draw: ImageDraw.ImageDraw,
    points: Iterable[tuple[int, int]],
    color: tuple[int, int, int] = (40, 40, 40),
    width: int = 4,
) -> None:
    pts = list(points)
    if len(pts) < 2:
        return
    for i in range(len(pts) - 2):
        draw.line([pts[i], pts[i + 1]], fill=color, width=width)
    draw_arrow(draw, pts[-2], pts[-1], color=color, width=width)


def render_fig_2_1() -> None:
    img = Image.new("RGB", (1920, 1080), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    box_font = get_font(50)

    fill = (255, 255, 255)
    b1 = Box(80, 310, 320, 120, "Создание идеи", fill)
    b2 = Box(480, 310, 360, 120, "Обсуждение идеи", fill)
    b3 = Box(920, 310, 360, 140, "Решение по идее", fill)
    b4 = Box(1360, 230, 500, 140, "Одобрена: добавление в маршрут дня", fill)
    b5 = Box(920, 530, 430, 140, "Отклонена: остается в списке идей", fill)
    b6 = Box(1360, 530, 500, 140, "Упорядочивание активностей", fill)

    for box in (b1, b2, b3, b4, b5, b6):
        draw_box(draw, box, box_font)

    draw_arrow(draw, (b1.right, b1.cy), (b2.left, b2.cy))
    draw_arrow(draw, (b2.right, b2.cy), (b3.left, b3.cy))
    draw_arrow(draw, (b3.right, b3.cy - 20), (b4.left, b4.cy))
    draw_arrow(draw, (b3.cx, b3.bottom), (b5.cx, b5.top))
    draw_arrow(draw, (b4.cx, b4.bottom), (b6.cx, b6.top))

    img.save(OUT_DIR / "fig_2_1_user_flow.png", "PNG")


def render_fig_2_2() -> None:
    img = Image.new("RGB", (1920, 1080), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    box_font = get_font(40)
    small_font = get_font(38)

    client_frame = (40, 80, 790, 920)
    backend_frame = (840, 80, 1880, 920)
    draw.rounded_rectangle(client_frame, radius=20, outline=(45, 45, 45), width=4, fill=(255, 255, 255))
    draw.rounded_rectangle(backend_frame, radius=20, outline=(45, 45, 45), width=4, fill=(255, 255, 255))
    draw.text((62, 95), "Android-клиент", fill=(24, 24, 24), font=small_font)
    draw.text((862, 95), "Backend (Ktor)", fill=(24, 24, 24), font=small_font)

    cfill = (255, 255, 255)
    bfill = (255, 255, 255)
    efill = (255, 255, 255)

    ui = Box(90, 170, 280, 100, "Экраны и навигация", cfill)
    vm = Box(420, 170, 280, 100, "ViewModel", cfill)
    repo = Box(90, 340, 280, 100, "Repository", cfill)
    local = Box(420, 340, 280, 100, "Локальное хранилище", cfill)
    queue = Box(255, 510, 300, 110, "Офлайн-очередь", cfill)

    api = Box(900, 190, 380, 110, "REST API / WebSocket", bfill)
    auth = Box(1330, 190, 310, 110, "Аутентификация и роли", bfill)
    svc = Box(1000, 410, 700, 150, "Сервисы: поездки, идеи, маршрут,\nрасходы, приглашения", bfill)

    pg = Box(920, 660, 260, 110, "PostgreSQL", efill)
    weather = Box(1230, 660, 260, 110, "Погодный API", efill)
    ai = Box(1540, 660, 280, 110, "AI-провайдер", efill)
    push = Box(920, 810, 420, 110, "Провайдер уведомлений", efill)

    for box in (ui, vm, repo, local, queue, api, auth, svc, pg, weather, ai, push):
        draw_box(draw, box, box_font)

    draw_arrow(draw, (ui.right, ui.cy), (vm.left, vm.cy))
    draw_arrow(draw, (ui.cx, ui.bottom), (repo.cx, repo.top))
    draw_arrow(draw, (repo.right, repo.cy), (local.left, local.cy))
    draw_arrow(draw, (repo.cx, repo.bottom), (queue.cx, queue.top))
    draw_arrow(draw, (local.right, local.cy), (api.left, api.cy))

    draw_arrow(draw, (api.right, api.cy), (auth.left, auth.cy))
    draw_arrow(draw, (auth.cx, auth.bottom), (svc.cx + 120, svc.top))

    draw_arrow(draw, (svc.cx - 160, svc.bottom), (pg.cx, pg.top))
    draw_arrow(draw, (svc.cx, svc.bottom), (weather.cx, weather.top))
    draw_arrow(draw, (svc.cx + 180, svc.bottom), (ai.cx, ai.top))
    # Route to push without touching Weather/AI blocks.
    draw_poly_arrow(
        draw,
        [
            (svc.left + 40, svc.cy),
            (860, svc.cy),
            (860, push.cy),
            (push.left, push.cy),
        ],
    )

    img.save(OUT_DIR / "fig_2_2_architecture.png", "PNG")


def render_fig_2_3() -> None:
    img = Image.new("RGB", (1920, 1080), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    box_font = get_font(26)

    steps = [
        "Сеть недоступна",
        "Пользователь выполняет действие",
        "Изменение сохраняется локально",
        "Запись в офлайн-очередь",
        "Сеть восстановлена",
        "Очередь отправляется на сервер",
        "Сервер применяет изменения",
        "Клиент обновляет экраны",
    ]

    boxes: list[Box] = []
    x = 24
    y = 445
    w = 222
    h = 190
    gap = 12
    for text in steps:
        boxes.append(Box(x, y, w, h, text, (255, 255, 255)))
        x += w + gap

    for b in boxes:
        draw_box(draw, b, box_font)
    for i in range(len(boxes) - 1):
        draw_arrow(draw, (boxes[i].right, boxes[i].cy), (boxes[i + 1].left, boxes[i + 1].cy))

    img.save(OUT_DIR / "fig_2_3_offline_sync.png", "PNG")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    render_fig_2_1()
    render_fig_2_2()
    render_fig_2_3()


if __name__ == "__main__":
    main()
