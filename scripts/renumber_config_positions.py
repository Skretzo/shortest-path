#!/usr/bin/env python3
"""Fix duplicate @ConfigItem / @ConfigSection position values in a RuneLite config file.

Usage: python3 scripts/renumber_config_positions.py <file>

Scans all `position = N` occurrences in order. Whenever a value is not strictly
greater than the previous one, it is bumped up (and all subsequent values are
incremented by the same delta) so the sequence becomes strictly increasing.
"""

import re
import sys

PATTERN = re.compile(r'(position\s*=\s*)(\d+)')

def renumber(path):
    with open(path) as f:
        text = f.read()

    positions = [(m.start(), m.end(), int(m.group(2))) for m in PATTERN.finditer(text)]

    offset = 0
    prev = -1
    replacements = []  # (start, end, new_value)
    for start, end, value in positions:
        value += offset
        if value <= prev:
            offset += prev + 1 - value
            value = prev + 1
        replacements.append((start, end, value))
        prev = value

    # Apply replacements back-to-front to keep offsets valid
    chars = list(text)
    for start, end, value in reversed(replacements):
        m = PATTERN.match(text, start)
        replacement = m.group(1) + str(value)
        chars[start:end] = list(replacement)

    with open(path, 'w') as f:
        f.write(''.join(chars))

    print(f"Renumbered {len(replacements)} positions in {path}")

if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit(f"Usage: {sys.argv[0]} <file>")
    renumber(sys.argv[1])
