import struct
import unittest
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


def _paeth(a, b, c):
    estimate = a + b - c
    distances = (abs(estimate - a), abs(estimate - b), abs(estimate - c))
    return (a, b, c)[distances.index(min(distances))]


def read_rgba(path):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError(f"{path} is not a PNG")

    position = 8
    compressed = bytearray()
    width = height = color_type = None
    while position < len(data):
        length = struct.unpack(">I", data[position:position + 4])[0]
        chunk_type = data[position + 4:position + 8]
        payload = data[position + 8:position + 8 + length]
        position += 12 + length
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type = struct.unpack(">IIBB", payload[:10])
            if bit_depth != 8:
                raise AssertionError(f"{path} must use 8-bit channels")
        elif chunk_type == b"IDAT":
            compressed.extend(payload)
        elif chunk_type == b"IEND":
            break

    if color_type != 6:
        raise AssertionError(f"{path} must be RGBA to provide transparent padding")

    raw = zlib.decompress(compressed)
    bytes_per_pixel = 4
    stride = width * bytes_per_pixel
    rows = []
    previous = bytearray(stride)
    offset = 0
    for _ in range(height):
        filter_type = raw[offset]
        source = raw[offset + 1:offset + 1 + stride]
        offset += stride + 1
        row = bytearray(stride)
        for index, value in enumerate(source):
            left = row[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
            above = previous[index]
            upper_left = previous[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
            if filter_type == 0:
                decoded = value
            elif filter_type == 1:
                decoded = value + left
            elif filter_type == 2:
                decoded = value + above
            elif filter_type == 3:
                decoded = value + ((left + above) // 2)
            elif filter_type == 4:
                decoded = value + _paeth(left, above, upper_left)
            else:
                raise AssertionError(f"{path} uses unsupported PNG filter {filter_type}")
            row[index] = decoded & 0xFF
        rows.append(row)
        previous = row
    return width, height, rows


class LauncherIconSafeAreaTest(unittest.TestCase):
    def test_adaptive_foregrounds_stay_inside_centered_safe_area(self):
        icons = {
            "mipmap-mdpi": 108,
            "mipmap-hdpi": 162,
            "mipmap-xhdpi": 216,
            "mipmap-xxhdpi": 324,
            "mipmap-xxxhdpi": 432,
        }
        for directory, expected_size in icons.items():
            with self.subTest(directory=directory):
                path = (ROOT / "android/app/src/main/res" / directory /
                        "ic_launcher_foreground.png")
                width, height, rows = read_rgba(path)
                self.assertEqual((expected_size, expected_size), (width, height))

                visible = [
                    (x, y)
                    for y, row in enumerate(rows)
                    for x in range(width)
                    if row[x * 4 + 3] != 0
                ]
                self.assertTrue(visible, f"{path} has no visible artwork")
                min_x = min(x for x, _ in visible)
                max_x = max(x for x, _ in visible)
                min_y = min(y for _, y in visible)
                max_y = max(y for _, y in visible)
                max_visible = int(expected_size * 0.62) + 1
                self.assertLessEqual(max_x - min_x + 1, max_visible)
                self.assertLessEqual(max_y - min_y + 1, max_visible)
                self.assertLessEqual(abs((min_x + max_x + 1) - width), 2)
                self.assertLessEqual(abs((min_y + max_y + 1) - height), 2)

    def test_launcher_uses_a_cream_background_and_round_adaptive_icon(self):
        resources = ROOT / "android/app/src/main/res"
        colors = (resources / "values/colors.xml").read_text(encoding="utf-8")
        manifest = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(
            encoding="utf-8"
        )
        launcher = (resources / "mipmap-anydpi/ic_launcher.xml").read_text(
            encoding="utf-8"
        )

        self.assertIn('<color name="ic_launcher_background">#FFF8E8</color>', colors)
        self.assertIn('android:roundIcon="@mipmap/ic_launcher_round"', manifest)
        self.assertIn('@color/ic_launcher_background', launcher)
        self.assertTrue((resources / "mipmap-anydpi/ic_launcher_round.xml").is_file())


if __name__ == "__main__":
    unittest.main()
