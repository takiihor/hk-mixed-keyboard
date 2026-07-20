# Bottom-row key width design

## Goal

Make the main keyboard's 速、句號、逗號 keys easier to tap by reallocating part of
the Space key's width, without changing any key behavior or the overall row width.

## Layout

The text keyboard action row remains 10 units wide. Space shrinks from 4.25 to
3.50 units. The released 0.75 units are split evenly between the three requested
keys:

| Key | Current | New |
| --- | ---: | ---: |
| 速 | 0.80 | 1.05 |
| Space | 4.25 | 3.50 |
| 句號 | 0.80 | 1.05 |
| 逗號 | 0.80 | 1.05 |

The remaining keys retain their existing widths, including the calculated Enter
key width. Touch handling, accessibility labels, long press, and Space swipe
behavior are unchanged because only geometry is adjusted.

## Verification

Update the keyboard-layout unit tests to assert the four requested widths and
the resulting pixel geometry at a fixed keyboard width. Run the layout test and
the Android unit suite.
