#!/usr/bin/env python3
"""Renders a recorded adoption transcript as an MPEG video.

The transcript `adopt-demo.sh` captures is text, which is the honest artifact but
not one you can watch. This turns it into a terminal-style video: one frame per
line the pipeline logged, paced by the timestamps in the log itself, encoded as
MPEG-1 in an .mpg container so it plays anywhere without a codec to install.

Playback is compressed, not real time: a gap between two lines is clamped to
--max-gap seconds, so the 38 seconds `claude init` spends thinking does not
become 38 seconds of a still frame. The header keeps the run's *real* elapsed
time, read from the log, so nothing about how long the adoption took is hidden.

Needs Pillow and ffmpeg. Usage:

    scripts/linux/adopt-demo-video.py \\
        --transcript target/adopt-demo/adopt-demo.txt \\
        --output docs/adopt-demo.mpg
"""

import argparse
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFont

ANSI = re.compile(r"\x1b\[[0-9;]*m")
# The pipeline's own log lines: a timestamp, a level, an optional [repository]
# prefix naming which repository the line belongs to, then the message.
LOG_LINE = re.compile(
    r"^(?P<time>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\.(?P<millis>\d{3}) "
    r"(?P<level>[A-Z]+) +(?:\[(?P<repo>[^\]]+)\] )?- (?P<message>.*)$"
)
# Lines that belong to the recording apparatus or the JVM rather than the run.
NOISE = ("Picked up JAVA_TOOL_OPTIONS", "WARNING: ", "PropertiesConfiguration",
         "DefaultConfiguration", "Script started on", "Script done on")

BACKGROUND = (13, 17, 23)
CHROME = (22, 27, 34)
BORDER = (48, 54, 61)
TITLE = (139, 148, 158)
DOTS = ((255, 95, 86), (255, 189, 46), (39, 201, 63))
LEVELS = {"INFO": (88, 166, 255), "WARN": (210, 153, 34), "ERROR": (248, 81, 73)}
TIMESTAMP = (110, 118, 129)
MESSAGE = (201, 209, 217)
# A step boundary is the spine of the run, so it is the one message picked out.
STEP = (63, 185, 80)

FONTS = ("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
         "/usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf")


class Line:
    """One rendered line: its coloured segments, and when the log recorded it."""

    def __init__(self, segments, seconds):
        self.segments = segments
        self.seconds = seconds


def load_font(size, bold=False):
    for path in FONTS:
        candidate = path.replace("DejaVuSansMono", "DejaVuSansMono-Bold") if bold else path
        for attempt in (candidate, path):
            if pathlib.Path(attempt).exists():
                return ImageFont.truetype(attempt, size)
    raise SystemExit("No monospace font found; install fonts-dejavu.")


def seconds_of(match, first):
    """Seconds since the first logged line, from the log's own clock."""
    stamp = match.group("time") + "." + match.group("millis")
    if first is None:
        return 0.0, stamp
    from datetime import datetime
    fmt = "%Y-%m-%d %H:%M:%S.%f"
    delta = datetime.strptime(stamp, fmt) - datetime.strptime(first, fmt)
    return delta.total_seconds(), first


def read_transcript(path, columns):
    """The transcript as coloured, wrapped lines, with the repository hoisted out."""
    lines, first, repository = [], None, None
    for raw in path.read_text(errors="replace").splitlines():
        text = ANSI.sub("", raw).rstrip()
        if not text or any(n in text for n in NOISE):
            continue
        match = LOG_LINE.match(text)
        if not match:
            lines.append(Line([(text, MESSAGE)], lines[-1].seconds if lines else 0.0))
            continue
        seconds, first = seconds_of(match, first)
        repository = match.group("repo") or repository
        clock = match.group("time").split(" ")[1]
        level = match.group("level")
        colour = STEP if match.group("message").startswith("Step ") else MESSAGE
        prefix = [(clock + " ", TIMESTAMP), (level.ljust(5) + " ", LEVELS.get(level, MESSAGE))]
        lines.extend(wrapped(prefix, match.group("message"), colour, seconds, columns))
    return lines, repository


def wrapped(prefix, message, colour, seconds, columns):
    """The message across as many lines as it needs, continuations indented."""
    room = columns - sum(len(text) for text, _ in prefix)
    if room < 20:
        room = columns
    chunks = [message[i:i + room] for i in range(0, len(message), room)] or [""]
    rendered = [Line(prefix + [(chunks[0], colour)], seconds)]
    indent = " " * (columns - room)
    rendered.extend(Line([(indent + chunk, colour)], seconds) for chunk in chunks[1:])
    return rendered


def elapsed_label(seconds):
    minutes, remainder = divmod(int(seconds), 60)
    return f"{minutes}m {remainder:02d}s" if minutes else f"{remainder}s"


class Terminal:
    """Draws the window: chrome, title bar, and the visible tail of the output."""

    def __init__(self, width, height, font_size, repository):
        self.width, self.height = width, height
        self.font = load_font(font_size)
        self.bold = load_font(font_size, bold=True)
        self.repository = repository
        self.advance = self.font.getlength("M")
        self.leading = font_size + 5
        self.chrome_height = font_size * 2 + 14
        self.padding = 14
        self.rows = int((height - self.chrome_height - self.padding * 2) // self.leading)
        self.columns = int((width - self.padding * 2) // self.advance)

    def frame(self, lines, elapsed):
        image = Image.new("RGB", (self.width, self.height), BACKGROUND)
        draw = ImageDraw.Draw(image)
        self.draw_chrome(draw, elapsed)
        top = self.chrome_height + self.padding
        for row, line in enumerate(lines[-self.rows:]):
            x = self.padding
            for text, colour in line.segments:
                draw.text((x, top + row * self.leading), text, font=self.font, fill=colour)
                x += self.advance * len(text)
        return image

    def draw_chrome(self, draw, elapsed):
        draw.rectangle([0, 0, self.width, self.chrome_height], fill=CHROME)
        draw.line([0, self.chrome_height, self.width, self.chrome_height], fill=BORDER)
        centre = self.chrome_height // 2
        for index, colour in enumerate(DOTS):
            x = 18 + index * 20
            draw.ellipse([x, centre - 6, x + 12, centre + 6], fill=colour)
        title = f"adopt --dry-run  {self.repository}" if self.repository else "adopt --dry-run"
        draw.text((90, centre - self.bold.size // 2), title, font=self.bold, fill=TITLE)
        clock = f"elapsed {elapsed_label(elapsed)}"
        draw.text((self.width - self.padding - self.font.getlength(clock) - 4,
                   centre - self.font.size // 2), clock, font=self.font, fill=TIMESTAMP)


def durations(lines, max_gap, min_gap, tail):
    """How long each frame is held, from the log's gaps with the idle ones clamped."""
    held = []
    for index, line in enumerate(lines[:-1]):
        gap = lines[index + 1].seconds - line.seconds
        held.append(min(max(gap, min_gap), max_gap))
    held.append(tail)
    return held


def encode(frames, held, output, fps, codec, quality, gop):
    """ffmpeg's concat demuxer, which takes a per-image duration.

    A terminal recording is nearly all still frames, so the size is decided by how
    often a keyframe is sent: MPEG's default GOP of a dozen frames re-sent this
    screen twice a second and cost 7 MB, where --gop 250 costs 2.4 MB of the same
    picture. Quality is set with -q:v rather than a bitrate, so the still frames
    are cheap and the scrolling ones are paid for.
    """
    listing = frames[0].parent / "frames.txt"
    entries = []
    for frame, duration in zip(frames, held):
        entries.append(f"file '{frame.name}'\nduration {duration:.3f}")
    # The concat demuxer drops the final image without a repeat of its filename.
    entries.append(f"file '{frames[-1].name}'")
    listing.write_text("\n".join(entries) + "\n")
    output.parent.mkdir(parents=True, exist_ok=True)
    command = ["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0",
               "-i", str(listing), "-vf", f"fps={fps},format=yuv420p",
               "-c:v", codec, "-q:v", str(quality), "-g", str(gop), "-bf", "2",
               str(output)]
    subprocess.run(command, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--transcript", type=pathlib.Path,
                        default=pathlib.Path("target/adopt-demo/adopt-demo.txt"))
    parser.add_argument("--output", type=pathlib.Path,
                        default=pathlib.Path("target/adopt-demo/adopt-demo.mpg"))
    parser.add_argument("--width", type=int, default=1280)
    parser.add_argument("--height", type=int, default=720)
    parser.add_argument("--font-size", type=int, default=15)
    # MPEG-1 and MPEG-2 accept only the broadcast frame rates, and ffmpeg refuses
    # anything else outright ("MPEG-1/2 does not support 12/1 fps"), so this is not
    # a free knob: 25 is a standard rate for both.
    parser.add_argument("--fps", type=int, default=25,
                        choices=[24, 25, 30, 50, 60])
    parser.add_argument("--codec", default="mpeg2video",
                        choices=["mpeg2video", "mpeg1video"],
                        help="MPEG-2 by default; MPEG-1 plays on even more, at twice the size")
    parser.add_argument("--quality", type=int, default=9,
                        help="ffmpeg -q:v, 1 best and 31 worst")
    parser.add_argument("--gop", type=int, default=250,
                        help="frames between keyframes; the still frames make it the size lever")
    parser.add_argument("--max-gap", type=float, default=1.2,
                        help="longest a frame is held, so idle time does not stall playback")
    parser.add_argument("--min-gap", type=float, default=0.28)
    parser.add_argument("--tail", type=float, default=3.5,
                        help="how long the final frame is held")
    args = parser.parse_args()

    if shutil.which("ffmpeg") is None:
        raise SystemExit("ffmpeg is not on the PATH.")
    if not args.transcript.is_file():
        raise SystemExit(f"No transcript at {args.transcript}. Run adopt-demo.sh first.")

    # The terminal is built twice: once to learn its width, then again with the
    # repository the transcript names, which the title bar carries.
    terminal = Terminal(args.width, args.height, args.font_size, None)
    lines, repository = read_transcript(args.transcript, terminal.columns)
    if not lines:
        raise SystemExit(f"{args.transcript} holds no lines to render.")
    terminal = Terminal(args.width, args.height, args.font_size, repository)

    held = durations(lines, args.max_gap, args.min_gap, args.tail)
    with tempfile.TemporaryDirectory() as workspace:
        directory = pathlib.Path(workspace)
        frames = []
        for index in range(len(lines)):
            frame = directory / f"frame-{index:05d}.png"
            terminal.frame(lines[:index + 1], lines[index].seconds).save(frame)
            frames.append(frame)
        encode(frames, held, args.output, args.fps, args.codec, args.quality, args.gop)

    real = elapsed_label(lines[-1].seconds)
    playback = sum(held)
    print(f"Wrote {args.output} ({args.output.stat().st_size // 1024} KB): "
          f"{len(lines)} lines, {playback:.0f}s of playback for a {real} run.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
