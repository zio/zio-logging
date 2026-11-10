"""WCAG 2.x contrast ratio (relative luminance), used for every ratio in brand/."""
import sys

def lum(hexcode):
    h = hexcode.lstrip('#')
    def ch(c):
        c = int(c, 16) / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (ch(h[i:i + 2]) for i in (0, 2, 4))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def ratio(a, b):
    la, lb = sorted((lum(a), lum(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)

if __name__ == '__main__':
    # usage: wcag.py FG BG [FG BG ...]
    args = sys.argv[1:]
    for fg, bg in zip(args[::2], args[1::2]):
        print(f"{fg} on {bg}: {ratio(fg, bg):.2f}")
