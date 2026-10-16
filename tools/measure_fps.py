"""Read-only SurfaceFlinger presentation sampling for the connected test phone."""
import argparse
import json
import re
import shlex
import statistics
import subprocess
import time
from device import EVIDENCE, adb_args

PACKAGE = "com.kiloo.subwaysurf.gw"


def shell(command):
    result = subprocess.run(adb_args("shell", command), capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
    return result.stdout + result.stderr


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("label")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--seconds", type=int, default=12)
    parser.add_argument("--screenshots", action="store_true")
    args = parser.parse_args()
    output = EVIDENCE
    output.mkdir(parents=True, exist_ok=True)
    layers = shell("dumpsys SurfaceFlinger --list")
    candidates = []
    for line in layers.splitlines():
        if args.package in line and "(BLAST)" in line and "SurfaceView" in line:
            match = re.search(r"RequestedLayerState\{(.+?) parentId=", line)
            candidates.append(match.group(1) if match else line.strip())
    if not candidates:
        raise SystemExit("No game SurfaceView found; open the game first.")
    layer = candidates[-1]
    raw = [shell("cmd display get-active-mode 0; dumpsys display | grep 'mFrameRateOverrides='; dumpsys window | grep mCurrentFocus")]
    timestamps = set()
    initial = None
    deadline = time.monotonic() + args.seconds
    windows = []
    timeline = []
    last_shot = 0
    started = time.monotonic()
    while True:
        latency = shell("dumpsys SurfaceFlinger --latency " + shlex.quote(layer))
        raw.append(latency)
        points = []
        for line in latency.splitlines():
            fields = line.split()
            if len(fields) == 3 and all(v.isdigit() for v in fields):
                present = int(fields[1])
                if 0 < present < 2**63 - 1:
                    points.append(present)
        points = sorted(set(points))
        if points:
            if initial is None:
                initial = points[-1]
            timestamps.update(p for p in points if p > initial)
            if len(points) > 1:
                windows.append(round((len(points)-1)*1e9/(points[-1]-points[0]), 2))
                timeline.append({"elapsed_s": round(time.monotonic()-started, 2), "window_fps": windows[-1], "last_present_ns": points[-1]})
        if args.screenshots and time.monotonic()-last_shot >= 3:
            name = f"{args.label}-{int(time.monotonic()-started):03d}.png"
            remote = "/data/local/tmp/joyoseleash-" + name
            shell("screencap -p " + shlex.quote(remote))
            subprocess.run(adb_args("pull", remote, str(output / name)), capture_output=True, timeout=15)
            last_shot = time.monotonic()
        if time.monotonic() >= deadline:
            break
        time.sleep(0.35)
    raw.append(shell("cmd display get-active-mode 0; dumpsys SurfaceFlinger | grep -E 'GameFrameRateOverrides=|FrameRateOverrides=|enableFrameRateOverride=|mPrimaryFrameRates\\[0\\]='"))
    points = sorted(timestamps)
    intervals = [(b-a)/1e6 for a,b in zip(points, points[1:])]
    summary = {
        "label": args.label, "layer": layer, "sample_seconds": args.seconds,
        "frames": len(points),
        "presentation_fps": round((len(points)-1)*1e9/(points[-1]-points[0]), 2) if len(points)>1 else None,
        "median_frame_ms": round(statistics.median(intervals), 3) if intervals else None,
        "window_fps": windows,
        "timeline": timeline,
        "display_before": raw[0], "display_after": raw[-1],
    }
    (output / f"{args.label}.raw.txt").write_text("\n".join(raw), encoding="utf-8")
    (output / f"{args.label}.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
