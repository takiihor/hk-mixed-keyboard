# Preserve Profile Launcher Icon Design

## Decision

Use `hk-mixed-keyboard-profile.png` unchanged as the visual design.  Replace
only the black pixels connected to its outside edge with the adaptive
background's cream colour.  This avoids black alpha fringes during Android
resource scaling.  All non-background pixels, including the existing rounded
cream card, text, keyboard, and skyline, remain intact.

## Android resource model

The resulting full artwork is the adaptive icon foreground.  The adaptive
background is a plain cream colour so transparent corners never display as a
black square.  The app declares both `icon` and `roundIcon`; Android supplies
the device-specific circular or rounded-square mask.

## Constraints and validation

The artwork is fitted, never cropped, inside the adaptive safe zone.  A
resource test verifies transparent outer padding, no black pixels exposed at
the foreground's transparent edge, adaptive foreground/background separation,
and the Manifest icon declarations.  Emulator checks cover launcher
rendering.
