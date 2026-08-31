#!/usr/bin/env python3
"""Crop a PNG to an exact size from the top-left corner.

Headless Chrome writes a screenshot the size of the *window*, but reserves part
of that window for browser chrome, so the painted page occupies only the top of
the image. Rendering into a deliberately over-tall window and cropping back is
the way to get an image that is both exactly the size Play requires and fully
painted.

Pure standard library, so it runs anywhere the render script does.

    python3 crop_png.py in.png out.png 1024 500
"""
import struct
import sys
import zlib


def read_png(path):
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")

    pos, idat, header = 8, bytearray(), None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
        pos += 12 + length

    width, height, depth, colour, compression, filt, interlace = header
    if depth != 8 or colour not in (2, 6) or interlace != 0:
        raise ValueError("expected a non-interlaced 8-bit RGB or RGBA PNG")

    channels = 3 if colour == 2 else 4
    return width, height, channels, colour, zlib.decompress(bytes(idat))


def unfilter(raw, width, height, channels):
    """Undo the per-scanline filters PNG applies before compression."""
    stride = width * channels
    out = bytearray(stride * height)
    previous = bytearray(stride)

    pos = 0
    for row in range(height):
        method = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride

        for i in range(stride):
            left = line[i - channels] if i >= channels else 0
            up = previous[i]
            upper_left = previous[i - channels] if i >= channels else 0
            if method == 0:
                value = line[i]
            elif method == 1:
                value = line[i] + left
            elif method == 2:
                value = line[i] + up
            elif method == 3:
                value = line[i] + (left + up) // 2
            elif method == 4:
                p = left + up - upper_left
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - upper_left)
                nearest = left if (pa <= pb and pa <= pc) else (up if pb <= pc else upper_left)
                value = line[i] + nearest
            else:
                raise ValueError(f"unknown filter {method}")
            line[i] = value & 0xFF

        out[row * stride:(row + 1) * stride] = line
        previous = line

    return out


def write_png(path, pixels, width, height, channels, colour):
    stride = width * channels
    raw = bytearray()
    for row in range(height):
        raw.append(0)  # filter 0: none
        raw += pixels[row * stride:(row + 1) * stride]

    def chunk(kind, body):
        return (struct.pack(">I", len(body)) + kind + body +
                struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF))

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, colour, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        f.write(chunk(b"IEND", b""))


def main():
    source, target, want_w, want_h = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
    width, height, channels, colour, raw = read_png(source)
    if want_w > width or want_h > height:
        raise SystemExit(f"cannot crop {width}x{height} up to {want_w}x{want_h}")

    pixels = unfilter(raw, width, height, channels)
    stride = width * channels
    cropped = bytearray()
    for row in range(want_h):
        start = row * stride
        cropped += pixels[start:start + want_w * channels]

    write_png(target, cropped, want_w, want_h, channels, colour)
    print(f"{target}: {want_w}x{want_h}")


if __name__ == "__main__":
    main()
