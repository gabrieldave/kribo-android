#!/usr/bin/env bash
# Anti-Squish UI Gate: fails when a button/chip/tab is visually collapsed.
# Usage: ./scripts/ui-audit.sh [adb-serial]
set -euo pipefail

SERIAL="${1:-${ANDROID_SERIAL:-}}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB=(adb -s "$SERIAL")
fi

PKG="com.blackeye.kribo"
XML_LOCAL="/tmp/ui.xml"
PNG_LOCAL="/tmp/pixel_live.png"

"${ADB[@]}" shell uiautomator dump /sdcard/ui.xml >/dev/null
"${ADB[@]}" pull /sdcard/ui.xml "$XML_LOCAL" >/dev/null
"${ADB[@]}" exec-out screencap -p > "$PNG_LOCAL"

FONT_SCALE="$("${ADB[@]}" shell settings get system font_scale 2>/dev/null | tr -d '\r')"
DENSITY="$("${ADB[@]}" shell wm density 2>/dev/null | sed -n 's/.*Physical density: *\([0-9]*\).*/\1/p' | head -1)"
if [[ -z "$DENSITY" ]]; then
  DENSITY=480
fi
DP_SCALE="$(python3 -c "print(max(1.0, $DENSITY / 160.0))")"

echo "ui-audit · package=$PKG font_scale=${FONT_SCALE:-unknown} density=${DENSITY}dpi"
echo "screenshot=$PNG_LOCAL hierarchy=$XML_LOCAL"

python3 - "$XML_LOCAL" "$PKG" "$DP_SCALE" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, package, dp_scale = sys.argv[1], sys.argv[2], float(sys.argv[3])
tree = ET.parse(xml_path)
root = tree.getroot()

def bounds(node):
    value = node.attrib.get("bounds", "")
    match = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", value)
    if not match:
        return None
    x1, y1, x2, y2 = map(int, match.groups())
    return x1, y1, x2, y2

nodes = list(root.iter("node"))
screen_w = 0
screen_h = 0
for node in nodes:
    b = bounds(node)
    if b:
        screen_w = max(screen_w, b[2])
        screen_h = max(screen_h, b[3])
if screen_w <= 0 or screen_h <= 0:
    print("FAIL: could not read screen bounds")
    sys.exit(1)

baseline = 48.0 * dp_scale
failures = []
seen = set()

for node in nodes:
    if not node.attrib.get("package", "").startswith(package):
        continue
    text = (node.attrib.get("text") or "").strip()
    desc = (node.attrib.get("content-desc") or "").strip()
    label = text or desc
    b = bounds(node)
    if not label or not b or len(label) <= 4:
        continue
    x1, y1, x2, y2 = b
    width, height = x2 - x1, y2 - y1
    if width <= 0 or height <= 0:
        continue
    key = (label, x1, y1, x2, y2)
    if key in seen:
        continue
    seen.add(key)

    clickable = node.attrib.get("clickable") == "true"
    klass = node.attrib.get("class", "")
    interactive = clickable or any(
        token in klass for token in ("Button", "CheckBox", "RadioButton", "Chip", "Tab")
    )
    full_screen = width > screen_w * 0.9 and height > screen_h * 0.9
    if full_screen:
        continue

    # Narrow *visible text* = letters stacked one per line ("Ed / ita / r ...").
    # Icon-only controls expose only content-desc and are 48dp wide by design.
    has_visible_text = len(text) > 4
    if has_visible_text and width < 90:
        failures.append(
            f"collapsed width {width}px for '{label}' at [{x1},{y1}][{x2},{y2}]"
        )
        continue

    # A normal single-line control is 48dp; 1.8x means it wrapped into a tall stack.
    # Large note cards are excluded by requiring a non-full-width control.
    if interactive and height > baseline * 1.8 and width < screen_w * 0.55 and len(label) <= 40:
        failures.append(
            f"squished height {height}px (>{baseline*1.8:.0f}) for '{label}' at "
            f"[{x1},{y1}][{x2},{y2}]"
        )

if failures:
    print("FAIL: Anti-Squish UI Gate")
    for failure in failures:
        print(" - " + failure)
    sys.exit(1)

print(f"PASS: no squished buttons/chips/tabs ({len(seen)} labeled elements checked)")
PY
