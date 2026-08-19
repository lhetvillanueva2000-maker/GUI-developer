# Bedrock Edition style folder

Everything that defines how a **Bedrock Edition** screen looks lives under
`styles/bedrock/` and nowhere else. The Java skin in `styles/java/` shares no
code with it beyond the pixel primitives in
`styles/src/commonMain/.../render/PixelDraw.kt`.

## Contents

| Path | What it is |
| --- | --- |
| `styles/bedrock/src/.../BedrockPalette.kt` | Every colour and metric the Bedrock skin uses. |
| `styles/bedrock/src/.../BedrockEditionSkin.kt` | The drawing code, one function per widget. |
| `styles/bedrock/assets/` | This note, plus any raster art you want to add. |

## Design rules the Bedrock skin follows

* **2px borders.** Chunkier than Java's, so controls stay readable on a phone.
* **Softened corners.** 3-6px pixel-chamfered radii, drawn without
  anti-aliasing so they still read as pixel art.
* **Thumb-sized targets.** Interactive widgets default to at least 24x24 GUI
  pixels, and the validator warns below that.
* **Translucent sheets.** Panels sit over the world at ~94% opacity rather than
  Java's opaque stone grey.
* **Soft text shadow, not a hard offset.** Bedrock's smooth font does not use
  Java's 1px drop shadow.
* **No hover state.** Touch has no pointer, so the preview only offers
  normal / pressed / disabled for Bedrock projects.

## Touch-specific widgets

Two components exist only here, and the exporters emit them as JSON-UI panels
carrying their designer type so you can bind your own renderers:

| Widget | Notes |
| --- | --- |
| `bedrock.touchpad` | D-pad, virtual joystick or split layout, with a visible dead-zone ring. |
| `bedrock.actionButton` | Round/rounded/square jump, sneak and fly buttons. |

## Adding raster art

Same as the Java folder: drop PNGs here for reference, then import them through
the app's **Assets** tab so they travel inside the `.mcgui` document. Set
nine-slice insets on an imported texture to use it as a stretchable sheet or
button skin.

## Palette reference

| Token | Value |
| --- | --- |
| Sheet background | `#1B1B1F` at 94% |
| Sheet raised | `#2A2A31` |
| Slot fill | `#3A3A42` |
| Border light | `#6E6E7C` |
| Border dark | `#0E0E12` |
| Control fill | `#3F8F3F` |
| Control hover | `#4EA84E` |
| Control pressed | `#2E6B2E` |
| Touch pad | `#FFFFFF` at 50% |
| Editor accent | `#5CE05C` |
