# Java Edition style folder

Everything that defines how a **Java Edition** screen looks lives under
`styles/java/` and nowhere else. The Bedrock skin in `styles/bedrock/` shares
no code with it beyond the pixel primitives in
`styles/src/commonMain/.../render/PixelDraw.kt`, so either edition can be
redesigned without touching the other.

## Contents

| Path | What it is |
| --- | --- |
| `styles/java/src/.../JavaPalette.kt` | Every colour and metric the Java skin uses. |
| `styles/java/src/.../JavaEditionSkin.kt` | The drawing code, one function per widget. |
| `styles/java/assets/` | This note, plus any raster art you want to add. |

## Design rules the Java skin follows

* **1px bevels.** Light on the top/left, dark on the bottom/right; inverted for
  sunken widgets (inventory slots, text fields).
* **Square corners.** No rounding anywhere - vanilla Java UI has none.
* **20px control height.** Vanilla `widgets.png` buttons are 20px tall, and the
  validator warns when a `java.rectButton` deviates from it.
* **Hard drop-shadowed text.** One pixel down-right, `#3F3F3F`.
* **The vanilla palette.** `#C6C6C6` container body, `#8B8B8B` slot fill,
  `#373737` slot shadow, `#404040` container title.

## Adding raster art

The built-in skin is drawn procedurally, so it stays crisp at every zoom level
and needs no texture atlas. If you would rather ship real PNGs:

1. Drop them in this folder.
2. Import them through the app's **Assets** tab (they are stored inside the
   `.mcgui` document, so projects stay self-contained).
3. Assign them to an element's `texture` property and pick a fit mode -
   `nine_slice` is what you want for a stretchable button or panel skin.

Nothing in this folder is loaded automatically at runtime; textures always
travel inside the project document.

## Palette reference

| Token | Value |
| --- | --- |
| Container body | `#C6C6C6` |
| Container shadow | `#555555` |
| Slot fill | `#8B8B8B` |
| Slot shadow | `#373737` |
| Button fill | `#6C6C6C` |
| Button hover | `#7F7F7F` |
| Button pressed | `#565656` |
| Button disabled | `#3E3E3E` |
| Tooltip background | `#100010` at 94% |
| Tooltip border (top → bottom) | `#5000FF` → `#28007F` |
| Editor accent | `#54FB54` |
