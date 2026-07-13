# Preserve Profile Launcher Icon Design

## Decision

Use `hk-mixed-keyboard-profile.png` unchanged as the visual design.  Remove
only the black pixels connected to its outside edge, converting them to
transparency.  All non-background pixels, including the existing rounded
cream card, text, keyboard, and skyline, remain intact.

## Android resource model

The resulting full artwork is the adaptive icon foreground.  The adaptive
background is a plain cream colour so transparent corners never display as a
black square.  The app declares both `icon` and `roundIcon`; Android supplies
the device-specific circular or rounded-square mask.

## Constraints and validation

The artwork is fitted, never cropped, inside the adaptive safe zone.  A
resource test verifies transparent outer corners, no opaque black pixels
connected to the canvas edge, adaptive foreground/background separation, and
the Manifest icon declarations.  Emulator checks cover launcher rendering.
