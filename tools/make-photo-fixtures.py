#!/usr/bin/env python3
"""Regenerates the JPEG fixtures used by the photo-pipeline tests.

Run from the repository root:  python3 tools/make-photo-fixtures.py

Needs Pillow (`pip install pillow`). Pillow is deliberately a different implementation from
androidx.exifinterface, which is what reads these files back in the tests: a fixture written and
read by the same library would only prove that library is self-consistent.

GPS components must be IFDRational. Plain tuples raise "TypeError: bad operand type for abs()".

After changing anything here, update the expected SHA-256 values in DigestTest — they are taken
from `shasum -a 256`, outside Kotlin, so the digest test does not check an implementation against
itself.
"""

import os

from PIL import ExifTags, Image, ImageOps
from PIL.TiffImagePlugin import IFDRational as R

OUT = "data/src/androidHostTest/resources/photos"

# Barcelona, to five decimals: 41.39864, 2.17842
LAT_DMS = (R(41, 1), R(23, 1), R(5512, 100))
LON_DMS = (R(2, 1), R(10, 1), R(4231, 100))


def gradient(size):
    """A flat fill survives any resampling unchanged, so it could not tell a correct resize from
    a broken one. A gradient can."""
    image = Image.new("RGB", size)
    pixels = image.load()
    width, height = size
    for y in range(0, height, 8):
        for x in range(0, width, 8):
            color = ((x * 255) // width, (y * 255) // height, 128)
            for dy in range(min(8, height - y)):
                for dx in range(min(8, width - x)):
                    pixels[x + dx, y + dy] = color
    return image


def landscape_with_gps(path):
    image = gradient((3000, 2250))
    exif = image.getexif()
    ifd = exif.get_ifd(ExifTags.IFD.Exif)
    ifd[ExifTags.Base.DateTimeOriginal] = "2026:07:14 09:31:12"
    ifd[ExifTags.Base.OffsetTimeOriginal] = "+02:00"
    gps = exif.get_ifd(ExifTags.IFD.GPSInfo)
    gps[ExifTags.GPS.GPSLatitudeRef] = "N"
    gps[ExifTags.GPS.GPSLatitude] = LAT_DMS
    gps[ExifTags.GPS.GPSLongitudeRef] = "E"
    gps[ExifTags.GPS.GPSLongitude] = LON_DMS
    image.save(path, exif=exif, quality=45)


def portrait_no_gps(path):
    image = gradient((1200, 1600))
    exif = image.getexif()
    exif.get_ifd(ExifTags.IFD.Exif)[ExifTags.Base.DateTimeOriginal] = "2025:12:31 23:59:01"
    image.save(path, exif=exif, quality=45)


def small_no_exif(path):
    gradient((800, 600)).save(path, quality=45)


UPRIGHT_QUADRANTS = [(255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0)]

# How a camera lays out the pixels for each EXIF orientation, given the picture as it should be seen.
SENSOR_LAYOUT = {
    2: Image.Transpose.FLIP_LEFT_RIGHT,
    3: Image.Transpose.ROTATE_180,
    4: Image.Transpose.FLIP_TOP_BOTTOM,
    5: Image.Transpose.TRANSPOSE,
    6: Image.Transpose.ROTATE_90,
    7: Image.Transpose.TRANSVERSE,
    8: Image.Transpose.ROTATE_270,
}


def upright_quadrants(size):
    """Four distinct corners, so each of the eight orientations lands them in a different order."""
    image = Image.new("RGB", size)
    width, height = size
    for index, color in enumerate(UPRIGHT_QUADRANTS):
        # The right and bottom quadrants take the odd pixel, so an odd size leaves no unpainted edge.
        left, right = (width // 2, width) if index % 2 else (0, width // 2)
        top, bottom = (height // 2, height) if index // 2 else (0, height // 2)
        image.paste(color, (left, top, right, bottom))
    return image


def quadrant_centres(image):
    width, height = image.size
    return [image.getpixel((x * width // 4, y * height // 4)) for y in (1, 3) for x in (1, 3)]


def nearest(color):
    return min(UPRIGHT_QUADRANTS, key=lambda c: sum((a - b) ** 2 for a, b in zip(c, color)))


def oriented(path, orientation, size=(300, 400)):
    upright = upright_quadrants(size)
    sensor = upright.transpose(SENSOR_LAYOUT[orientation]) if orientation in SENSOR_LAYOUT else upright
    exif = sensor.getexif()
    exif[ExifTags.Base.Orientation] = orientation
    sensor.save(path, exif=exif, quality=90)
    # Pillow's own reading of the tag must give back the upright picture, or the fixture is wrong.
    shown = ImageOps.exif_transpose(Image.open(path))
    assert shown.size == upright.size, (orientation, shown.size)
    assert [nearest(c) for c in quadrant_centres(shown)] == UPRIGHT_QUADRANTS, orientation


def main():
    os.makedirs(OUT, exist_ok=True)
    landscape_with_gps(f"{OUT}/landscape_with_gps.jpg")
    portrait_no_gps(f"{OUT}/portrait_no_gps.jpg")
    small_no_exif(f"{OUT}/small_no_exif.jpg")
    for orientation in range(1, 9):
        oriented(f"{OUT}/orientation_{orientation}.jpg", orientation)
    # More than twice the resizer's cap on its longest side, so the decode has to shrink it. Odd sides
    # round differently once halved, so a copy sized from the shrunk decode comes out a pixel off.
    oriented(f"{OUT}/large_orientation_6.jpg", 6, size=(3082, 4099))
    with open(f"{OUT}/landscape_with_gps.jpg", "rb") as source:
        head = source.read(400)
    with open(f"{OUT}/truncated.jpg", "wb") as truncated:
        truncated.write(head)
    open(f"{OUT}/empty.jpg", "wb").close()
    for name in sorted(os.listdir(OUT)):
        print(name, os.path.getsize(f"{OUT}/{name}"))


if __name__ == "__main__":
    main()
