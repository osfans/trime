# SPDX-License-Identifier: GPL-3.0-or-later
"""Nonblank-key gate for T9 screenshots, including legacy rotated captures."""
import re

from PIL import Image


BOUNDS = re.compile(r'\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]')


def normalize_screenshot(image, root, display_rotation):
    """Align a screencap with the logical coordinates in a UI hierarchy.

    Android 5's ``screencap`` can keep the natural portrait pixel buffer while
    UIAutomator reports logical landscape coordinates. Newer Android versions
    already rotate the buffer. Only transpose when those two sizes prove that
    the legacy behavior occurred; otherwise a current screenshot is untouched.
    """
    bounds = []
    for node in root.iter('node'):
        match = BOUNDS.fullmatch(node.get('bounds', ''))
        if match is not None:
            bounds.append(tuple(map(int, match.groups())))
    if not bounds:
        raise ValueError('UI hierarchy has no valid bounds')
    logical_size = (max(box[2] for box in bounds), max(box[3] for box in bounds))
    if logical_size[0] <= 0 or logical_size[1] <= 0:
        raise ValueError('UI hierarchy has no positive display extent')

    raw_size = image.size
    transform = 'none'
    normalized = image
    if raw_size != logical_size:
        if raw_size != logical_size[::-1]:
            raise ValueError('Screenshot and UI hierarchy dimensions differ')
        if display_rotation == 1:
            normalized = image.transpose(Image.Transpose.ROTATE_90)
            transform = 'rotate_90_counterclockwise'
        elif display_rotation == 3:
            normalized = image.transpose(Image.Transpose.ROTATE_270)
            transform = 'rotate_90_clockwise'
        else:
            raise ValueError('Transposed screenshot has no matching display rotation')
    if normalized.size != logical_size:
        raise ValueError('Screenshot normalization did not reach logical display dimensions')
    return normalized, {
        'raw_size': list(raw_size),
        'logical_size': list(logical_size),
        'display_rotation': display_rotation,
        'transform': transform,
    }


def audit_t9_pixels(image, root, expected_size):
    if image.size != tuple(expected_size):
        raise ValueError('Screenshot and portrait display coordinates differ')
    checks = {}
    for label in ('2 ABC', '3 DEF', '4 GHI', '5 JKL', '6 MNO', '7 PQRS', '8 TUV', '9 WXYZ'):
        matches = [n for n in root.iter('node') if n.get('content-desc') == label]
        if len(matches) != 1:
            raise ValueError('Expected exactly one visible key: ' + label)
        bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', matches[0].get('bounds', ''))
        if bounds is None:
            raise ValueError('Invalid key bounds: ' + label)
        left, top, right, bottom = map(int, bounds.groups())
        if not (0 <= left < right <= image.width and 0 <= top < bottom <= image.height):
            raise ValueError('Key is outside screenshot: ' + label)
        # Ignore the border: a key-shaped outline alone must not count as text.
        dx, dy = max(1, (right - left) // 10), max(1, (bottom - top) // 10)
        if right - left <= 2 * dx or bottom - top <= 2 * dy:
            raise ValueError('Key interior is empty: ' + label)
        interior = image.crop((left + dx, top + dy, right - dx, bottom - dy)).convert('L')
        histogram = interior.histogram()
        dominant = max(range(256), key=histogram.__getitem__)
        contrast_pixels = sum(count for value, count in enumerate(histogram) if abs(value - dominant) >= 48)
        required_pixels = max(16, int(interior.width * interior.height * .003))
        checks[label] = {'bounds': [left, top, right, bottom], 'dominant_luminance': dominant,
                         'contrast_pixels': contrast_pixels, 'required_pixels': required_pixels,
                         'nonblank': contrast_pixels >= required_pixels}
    return {'passed': all(check['nonblank'] for check in checks.values()), 'keys': checks}
